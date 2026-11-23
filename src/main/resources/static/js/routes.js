import {$, $$, api, read, write, e, modes, won, minutes, distance, status, showError, fareFields, readFare,
    kakaoDirectionsUrl, modeIcon, estimateCard, connectEstimate, animateValues, renderHeader, transitSteps} from './common.js';
import {createMap, drawJourney} from './route-map.js';

// 서버 추천 이름을 경로 카드 꼬리표에 쓰는 짧은 이름으로 바꿉니다. 목록에 없는 추천은 서버 이름을 그대로 씁니다.
const PICK_LABEL = {'가장 저렴한 이동': '최저가', '가장 짧은 이동': '최단 시간', '가장 적은 환승': '최소 환승', '비용과 시간 균형': '균형'};
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');

// 요금 근거가 없는 0원 합계는 무료로 보이지 않게 "미확인"으로, 일부만 있으면 "(부분 합계)"를 붙여 표시합니다.
function fare(cost) {
    if (!cost.missing.length) return won(cost.totalKrw);
    return Number(cost.totalKrw) === 0 ? '미확인' : won(cost.totalKrw) + ' (부분 합계)';
}

// 선택한 경로의 1인 금액과 공동 비용 배분을 한 줄로 씁니다. 문구 규칙(DESIGN.md)에 맞춰 쉼표로 잇습니다.
function costNote(cost) {
    return ['평균 1인 ' + won(cost.perPersonKrw), '성인 1인 ' + won(cost.adultPerPersonKrw),
        cost.childPerPersonKrw == null ? '' : '아동 1인 ' + won(cost.childPerPersonKrw),
        Number(cost.sharedKrw) ? '공동 비용 ' + won(cost.sharedKrw) + '은 인원수로 나눔' : ''].filter(Boolean).join(', ');
}

// 경로 비교 화면(시안 A 지도 분할): 왼쪽 경로 카드 목록, 오른쪽 카카오맵, 지도 위 여행 견적 카드를 그립니다.
// 홈에서 저장한 trip과 지금까지 고른 구간 교통수단(legs)을 세션에서 읽고, /api/routes/compare 응답으로 화면을 그립니다.
// 경로 카드를 누르면 그 경로의 구간별 교통수단을 legs에 넣어 다시 계산합니다. 같은 교통수단의 직접 입력 요금은 유지합니다.
export async function init(cities) {
    const trip = read('trip');
    const list = $('#route-list');
    const estimate = $('#estimate');
    const mapSlot = $('#route-map');
    if (!trip) {
        list.innerHTML = '<div class="emptynote"><b>여행 조건을 먼저 선택하세요</b><p>출발지, 목적지와 여행 날짜가 필요합니다.</p>'
            + '<a class="primary" href="/">여행 검색</a></div>';
        mapSlot.innerHTML = '<p class="mapfail">여행 조건을 고르면 이동 경로가 지도에 표시됩니다.</p>';
        return;
    }
    // 도시 ID를 화면에 보여 줄 이름으로 바꿉니다.
    const city = id => cities.find(item => item.id === id)?.name ?? id;

    let legs = read('legs') ?? {};
    let sort = 'rec';
    let result = null;
    // undefined는 현지 견적 조회 중, null은 견적을 가져오지 못했거나 대상 일정이 없음을 뜻합니다.
    let local;
    let pending = false;
    let view = null;
    let drawn = null;
    let drawnKey = '';
    let drawnModes = '';
    let focus = 'all';

    // 지도: 카카오맵 키가 없거나 SDK를 못 불러오면 지도 칸에 이유를 보여 주고 목록과 견적은 그대로 씁니다.
    const mapFail = message => { mapSlot.innerHTML = '<p class="mapfail">' + e(message) + '</p>'; return null; };
    const mapReady = $('meta[name="kakao-map-key"]')
        ? createMap(mapSlot).catch(error => mapFail(error.message))
        : Promise.resolve(mapFail('카카오맵 JavaScript 키가 없어 지도를 표시할 수 없습니다. 저장소 README의 카카오맵 설정을 확인하세요.'));
    // 지도 오른쪽 아래를 가리는 견적 카드만큼 지도 범위를 비웁니다. 모바일에서는 카드가 지도 밖에 있어서 비우지 않습니다.
    const rightPad = () => !estimate.hidden && innerWidth > 860 ? estimate.offsetWidth + 50 : 0;

    // 현재 탭에서 만든 일정이 같은 여행이면 /api/itineraries/quote로 현지 일정 합계를 받아 견적에 더합니다.
    const plan = read('plan');
    const samePlan = plan && ['origin', 'destination', 'start', 'end'].every(key => plan.trip[key] === trip[key]);
    const localReady = samePlan && plan.days.some(day => day.stops.length) ? api('/itineraries/quote', plan).then(quote => {
        const total = quote.total;
        local = {value: total.missing.length && Number(total.totalKrw) === 0 ? null : total.totalKrw, partial: total.missing.length > 0};
    }).catch(() => { local = null; }) : Promise.resolve(local = null);

    // 경로 하나의 꼬리표: 추천이 여러 개면 "최저가, 균형"처럼 한 꼬리표에 합칩니다.
    function tags(option) {
        return Object.entries(result.recommendations).filter(([, pick]) => pick.optionId === option.id)
            .map(([title]) => PICK_LABEL[title] ?? title);
    }
    // 정렬: 추천순은 추천이 많은 경로부터, 최저가는 요금이 모두 확인된 경로부터 일행 편도 금액순, 최단 시간은 편도 시간순입니다.
    function sorted() {
        const options = result.alternatives.map((option, index) => ({option, index, picks: tags(option).length}));
        const price = item => item.option.journey.cost.missing.length ? Infinity : Number(item.option.journey.cost.totalKrw);
        const time = item => item.option.journey.minutes ?? Infinity;
        const by = {
            rec: (a, b) => b.picks - a.picks || a.index - b.index,
            price: (a, b) => price(a) - price(b) || time(a) - time(b),
            time: (a, b) => time(a) - time(b) || price(a) - price(b)
        }[sort];
        return options.sort(by).map(item => item.option);
    }
    const sameModes = (a, b) => a.length === b.length && a.every((mode, i) => mode === b[i]);

    // 선택한 경로 카드 안에 펼치는 구간 내역: 교통수단, 구간, 시간과 거리, 요금, 탑승 순서, 길찾기 링크, 요금 직접 입력
    function legRows(sections) {
        return sections.map(section => {
            const chosen = section.chosen;
            const override = legs[section.key]?.mode === chosen.mode ? legs[section.key].override : null;
            const link = kakaoDirectionsUrl(chosen);
            const unknown = section.cost.missing.length > 0;
            return '<div class="leg">' + modeIcon(chosen.mode, 20)
                + '<span><b>' + e(modes[chosen.mode] ?? chosen.mode) + '</b> ' + e(chosen.from.name) + '에서 ' + e(chosen.to.name)
                + '<small class="num">' + minutes(chosen.minutes) + ', ' + distance(chosen.kilometers)
                + (chosen.transfers ? ', 환승 ' + chosen.transfers + '회' : '') + '</small></span>'
                + '<span class="num">' + (unknown && Number(section.cost.totalKrw) === 0 ? '<span class="warntext">미확인</span>' : e(fare(section.cost))) + '</span>'
                + transitSteps(chosen)
                + '<div class="leg-extra">'
                + (link ? '<a class="textbtn" href="' + e(link) + '" target="_blank" rel="noopener noreferrer">카카오맵에서 길찾기</a>' : '')
                + '<details' + (override || unknown ? ' open' : '') + '><summary>요금 직접 입력</summary>'
                + '<form class="leg-fare" data-key="' + e(section.key) + '" data-mode="' + e(chosen.mode) + '">'
                + fareFields(override, 'leg-') + '<button type="submit" class="outline">요금 적용</button>'
                + '<p>성인, 아동 1인 요금이나 일행 공동 요금을 입력하세요. 값을 비우고 적용하면 기본 요금으로 돌아갑니다.</p></form></details>'
                + '</div></div>';
        }).join('');
    }

    // 경로 카드 하나를 그립니다. 선택한 경로는 진한 테두리와 함께 구간 내역이 펼쳐집니다.
    function card(option, selected) {
        const on = sameModes(option.modes, selected.modes);
        const cost = option.journey.cost;
        const picks = tags(option);
        const unknown = cost.missing.length > 0;
        const price = unknown && Number(cost.totalKrw) === 0 ? '미확인' : won(cost.totalKrw);
        return '<article class="routecard' + (on ? ' on' : '') + '" data-option="' + e(option.modes.join('/')) + '">'
            + '<button type="button" class="rc-main" aria-pressed="' + on + '" data-apply="' + e(option.id) + '">'
            + '<span class="rc-title">' + e(option.modes.map(mode => modes[mode] ?? mode).join(', ')) + '</span>'
            + '<span class="rc-price num' + (unknown ? ' none' : '') + '">' + price + '</span>'
            + '<span class="rc-meta"><span class="icons">' + option.modes.map(mode => modeIcon(mode)).join('') + '</span>'
            + '<span class="num">' + minutes(option.journey.minutes) + '</span>'
            + (picks.length ? '<span class="tag' + (picks.includes('최저가') ? ' low' : '') + '">' + e(picks.join(', ')) + '</span>' : '') + '</span>'
            + '<span class="rc-sub num">' + (unknown ? '<span class="warntext">부분 합계, 요금 입력 필요</span>' : '1인 ' + won(cost.perPersonKrw)) + '</span>'
            + '</button>'
            + (on ? '<div class="rc-legs">' + legRows(result.selected.sections)
                + '<p class="rc-note">' + e(costNote(result.selected.cost)) + '</p></div>' : '')
            + '</article>';
    }

    // 여행 견적 카드: 왕복 교통, 현지 일정, 일일투어를 더하고 예산과 비교합니다.
    function renderEstimate() {
        const selected = result.selected;
        const transportUnknown = selected.cost.missing.length > 0;
        estimate.hidden = false;
        estimate.innerHTML = estimateCard({
            budget: trip.budget,
            open: estimate.classList.contains('open'),
            rows: [
                {key: 'transport', label: '왕복 교통 (편도의 2배)', partial: transportUnknown,
                    value: transportUnknown && Number(result.roundTripKrw) === 0 ? null : result.roundTripKrw},
                {key: 'local', label: '현지 일정', value: local?.value ?? null, partial: Boolean(local?.partial),
                    text: local === undefined ? '계산 중' : '만들기 전'},
                {key: 'tour', label: '일일투어', value: null, text: '선택 안 함'}
            ],
            action: '<a class="outline" href="/tours">일일투어 더해 보기</a>'
        });
        connectEstimate(estimate);
        animateValues(estimate);
    }

    // 지도 위 범위 버튼: 전체, 출발지 주변, 도착지 주변
    function renderFocus() {
        let bar = $('.mapfocus');
        if (!drawn?.focuses.length) { bar?.remove(); return; }
        if (!bar) {
            bar = document.createElement('div');
            bar.className = 'mapfocus';
            bar.setAttribute('role', 'group');
            bar.setAttribute('aria-label', '지도 범위');
            $('.mappane').append(bar);
            // 범위 버튼을 누르면 그 범위로 지도를 다시 맞춥니다.
            bar.addEventListener('click', event => {
                const button = event.target.closest('[data-focus]');
                if (!button || !drawn) return;
                focus = button.dataset.focus;
                renderFocus();
                drawn.fit(focus);
            });
        }
        const label = {all: '전체', start: city(trip.origin) + ' 주변', end: city(trip.destination) + ' 주변'};
        bar.innerHTML = drawn.focuses.map(key => '<button type="button" data-focus="' + key + '" aria-pressed="' + (focus === key) + '">'
            + e(label[key]) + '</button>').join('');
    }
    // 고른 경로를 지도에 그립니다. 교통수단이나 요금이 바뀔 때만 다시 그리고, 경로가 바뀌면 전체 범위로 다시 맞춥니다.
    async function renderMap() {
        const sections = result.selected.sections;
        view = await mapReady;
        if (!view || !sections.length) return;
        // 지도 이름표에 구간 금액도 표시하므로 요금만 바뀌어도 다시 그립니다. 확대 범위는 교통수단 구성이 바뀌지 않으면 유지합니다.
        const key = JSON.stringify(sections.map(({chosen, cost}) =>
            [chosen.mode, chosen.checkedAt, chosen.mapObject, cost.totalKrw]));
        if (key === drawnKey) return;
        const nextModes = sections.map(section => section.chosen.mode).join();
        const modesChanged = nextModes !== drawnModes;
        drawnKey = key;
        drawnModes = nextModes;
        const next = await drawJourney(view, sections, rightPad);
        if (!next) return;
        drawn = next;
        const note = $('#route-geometry-note');
        note.hidden = !drawn.hasMissingGeometry;
        note.textContent = drawn.hasMissingGeometry ? '경로 좌표를 확인할 수 없는 구간은 위치만 표시합니다.' : '';
        if (modesChanged || !drawn.focuses.includes(focus)) focus = 'all';
        view.onResize = () => drawn.fit(focus);
        renderFocus();
        if (modesChanged) drawn.fit(focus);
    }

    // 목록을 다시 그립니다. 정렬을 바꾼 경우에는 카드가 새 자리로 미끄러지게 합니다(DESIGN.md reorder).
    function renderList(slide = false) {
        const before = {};
        // 교체 전후의 카드 위치를 ID로 맞춰, 같은 카드가 정렬 전 위치에서 정렬 후 위치로 이동하는 것처럼 보이게 합니다.
        if (slide && !reduceMotion.matches) $$('.routecard', list).forEach(node => before[node.dataset.option] = node.getBoundingClientRect().top);
        // 서버는 현재 적용한 경로를 첫 번째 후보로 보냅니다. 표시 순서를 바꿔도 선택 기준은 이 순서를 따릅니다.
        const selected = result.alternatives[0];
        list.innerHTML = sorted().map(option => card(option, selected)).join('');
        if (!slide || reduceMotion.matches) return;
        $$('.routecard', list).forEach(node => {
            const top = before[node.dataset.option];
            const dy = top == null ? 0 : top - node.getBoundingClientRect().top;
            if (Math.abs(dy) >= 1) node.animate([{transform: 'translateY(' + dy.toFixed(1) + 'px)'}, {transform: 'none'}],
                {duration: 320, easing: 'cubic-bezier(.2,.7,.2,1)'});
        });
    }

    // 화면 전체를 그립니다: 제목, 정렬 버튼, 경로 카드, 안내, 견적, 지도
    function render() {
        const sections = result.selected.sections;
        $('#route-title').textContent = sections.length
            ? city(trip.origin) + '에서 ' + city(trip.destination) + '까지 ' + result.alternatives.length + '가지 방법'
            : '출발 지점과 도착 지점이 같습니다';
        $('#route-sort').hidden = !sections.length;
        $$('#route-sort [data-sort]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.sort === sort)));
        renderList();
        const unconfirmed = sections.some(section => section.chosen.minutes == null);
        $('#route-note').textContent = (unconfirmed ? '조회하지 못한 구간의 시간과 거리는 미확인으로 표시합니다. ' : '')
            + '왕복 금액은 확인된 편도 요금의 두 배로 추정합니다.';
        renderEstimate();
        renderHeader(cities);
        renderMap().catch(error => mapFail(error.message));
    }

    // 선택을 서버에 보내 다시 계산합니다. 계산하는 동안 누른 카드에 진행 선을 보여 주고, 성공한 뒤에만 선택을 세션에 저장합니다.
    async function update(next, cardNode) {
        if (pending) return;
        pending = true;
        status('이동 경로를 계산하는 중입니다.');
        cardNode?.insertAdjacentHTML('beforeend', '<span class="calcbar" aria-hidden="true"></span>');
        try {
            result = await api('/routes/compare', {trip, legs: next});
            legs = next;
            write('legs', legs);
            status('');
            render();
        } catch (error) {
            $('.calcbar', list)?.remove();
            showError(error);
        } finally {
            pending = false;
        }
    }

    // 경로 카드를 누르면 그 경로의 구간별 교통수단을 적용합니다.
    list.addEventListener('click', event => {
        const button = event.target.closest('[data-apply]');
        if (!button || button.getAttribute('aria-pressed') === 'true') return;
        const option = result.alternatives.find(item => item.id === button.dataset.apply);
        const next = Object.fromEntries(option.journey.sections.map(section => {
            const mode = section.chosen.mode;
            return [section.key, {mode, override: legs[section.key]?.mode === mode ? legs[section.key].override : null}];
        }));
        update(next, button.closest('.routecard'));
    });
    // 구간 요금 직접 입력을 적용합니다. 지금 고른 교통수단과 함께 보냅니다.
    list.addEventListener('submit', event => {
        const form = event.target.closest('.leg-fare');
        if (!form) return;
        event.preventDefault();
        update({...legs, [form.dataset.key]: {mode: form.dataset.mode, override: readFare(form, 'leg-')}}, form.closest('.routecard'));
    });
    // 정렬 버튼
    $('#route-sort').addEventListener('click', event => {
        const button = event.target.closest('[data-sort]');
        if (!button || !result || button.dataset.sort === sort) return;
        sort = button.dataset.sort;
        $$('#route-sort [data-sort]').forEach(item => item.setAttribute('aria-pressed', String(item.dataset.sort === sort)));
        renderList(true);
    });

    status('이동 경로를 계산하는 중입니다.');
    result = await api('/routes/compare', {trip, legs});
    write('legs', legs);
    status('');
    render();
    // 현지 일정 합계가 늦게 오면 견적만 다시 그려 숫자가 전환되게 합니다.
    localReady.then(() => renderEstimate());
}
