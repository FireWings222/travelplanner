import {$, $$, api, read, write, e, busy, status, showError, modes, won, minutes, distance, fareFields, readFare,
    kakaoDirectionsUrl, modeIcon, estimateCard, connectEstimate, animateValues, renderHeader, transitSteps} from './common.js';
import {createMap, drawDays, dayPin, dayColor} from './route-map.js';

const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');
const GRIP = '<svg width="10" height="16" viewBox="0 0 10 16" fill="currentColor" aria-hidden="true"><circle cx="2.5" cy="3" r="1.5"/>'
    + '<circle cx="7.5" cy="3" r="1.5"/><circle cx="2.5" cy="8" r="1.5"/><circle cx="7.5" cy="8" r="1.5"/><circle cx="2.5" cy="13" r="1.5"/>'
    + '<circle cx="7.5" cy="13" r="1.5"/></svg>';
// 요금 근거가 없는 0원은 "미확인", 일부만 있으면 "(부분 합계)"로 씁니다.
const fare = cost => !cost.missing.length ? won(cost.totalKrw)
    : Number(cost.totalKrw) === 0 ? '미확인' : won(cost.totalKrw) + ' (부분 합계)';

// 여행 일정 화면(시안 A 지도 분할): 왼쪽 날짜 버튼과 장소 타임라인, 오른쪽 카카오맵, 지도 위 여행 견적 카드를 그립니다.
// 여행 계획과 저장 ID는 현재 탭의 세션에서 읽습니다. 저장한 여행 목록은 서버에서 조회하고, 여행 조건이 있으면 일정 견적을 불러옵니다.
export async function init(cities) {
    const list = $('#plan-day');
    const save = $('#save-plan');
    const estimate = $('#estimate');
    const mapSlot = $('#plan-map');
    // plan은 편집 중인 계획, quote는 그 계획의 견적 응답입니다. id가 있을 때만 저장된 DB 일정을 갱신합니다.
    let plan = read('plan');
    let id = read('planId');
    let date;
    let quote;
    let transport;
    let pending = false;
    let moved = null;
    let drawn = null;
    let drawnKey = '';
    // 도시 ID를 화면에 보여 줄 이름으로 바꿉니다.
    const city = cityId => cities.find(item => item.id === cityId)?.name ?? cityId;

    // 지도: 키가 없거나 SDK를 못 불러오면 지도 칸에 이유를 보여 주고 일정 편집은 그대로 씁니다.
    const mapFail = message => { mapSlot.innerHTML = '<p class="mapfail">' + e(message) + '</p>'; return null; };
    // 지도는 처음 그릴 일정이 생길 때 만듭니다(여행 조건 없이 들어오면 안내만 보여 줌).
    let mapReady;
    const getMap = () => mapReady ??= $('meta[name="kakao-map-key"]')
        ? (mapSlot.replaceChildren(), createMap(mapSlot).catch(error => mapFail(error.message)))
        : Promise.resolve(mapFail('카카오맵 JavaScript 키가 없어 지도를 표시할 수 없습니다. 저장소 README의 카카오맵 설정을 확인하세요.'));
    const rightPad = () => !estimate.hidden && innerWidth > 860 ? estimate.offsetWidth + 50 : 0;

    // 머리글의 "저장한 여행" 링크(#saved)로 들어오면 저장한 여행 구역을 펼칩니다.
    const openSaved = () => { if (location.hash === '#saved') { $('#saved').open = true; $('#saved').scrollIntoView({block: 'nearest'}); } };
    addEventListener('hashchange', openSaved);

    // 저장 목록에서 받은 계획을 현재 작업 상태로 열고 해당 일정 견적을 불러옵니다.
    // 저장 API가 반환한 계획과 ID를 편집 대상으로 삼고 시작일을 선택합니다. 이전 여행의 경로 선택과 투어 비교 상태는 초기화합니다.
    async function open(stored) {
        plan = stored.plan;
        id = stored.id;
        date = plan.trip.start;
        write('trip', plan.trip);
        write('legs', null);
        write('comparison', null);
        transport = undefined;
        await load(plan.trip);
    }

    // 일정 목록 API를 호출해 저장한 여행을 표시하고 열기, 삭제 버튼을 연결합니다.
    async function listSaved() {
        const records = await api('/travel-plans');
        const root = $('#saved-plans');
        root.innerHTML = records.length ? records.map(record => {
            const supported = cities.some(item => item.id === record.trip.destination) && cities.some(item => item.id === record.trip.origin);
            return '<div class="saved-item"><div><b>' + e(city(record.trip.destination)) + '</b><small class="num">' + e(record.trip.start) + ' ~ '
                + e(record.trip.end) + '</small></div><button type="button" class="outline" data-open="' + e(record.id) + '"'
                + (supported ? '>열기' : ' disabled>지원 종료') + '</button><button type="button" class="textbtn danger" data-delete="'
                + e(record.id) + '">삭제</button></div>';
        }).join('') : '<p class="fine">저장한 여행이 없습니다.</p>';
        // 저장한 계획 상세를 조회하고 현재 일정으로 엽니다.
        $$('[data-open]', root).forEach(button => button.onclick = () => busy(root, async () =>
            open(await api('/travel-plans/' + button.dataset.open))).catch(showError));
        // 삭제 확인 뒤 저장 일정 API와 화면의 저장 목록을 갱신합니다.
        $$('[data-delete]', root).forEach(button => button.onclick = () => {
            if (!window.confirm('저장한 여행을 삭제할까요?')) return;
            busy(root, async () => {
                await api('/travel-plans/' + button.dataset.delete, undefined, 'DELETE');
                if (id === button.dataset.delete) {
                    id = null;
                    write('planId', null);
                    save.textContent = '일정 저장';
                }
                await listSaved();
                status('저장한 여행을 삭제했습니다.');
            }).catch(showError);
        });
    }

    // 기존 일정 견적 또는 새 기본 일정을 서버에서 가져옵니다.
    // plan이 있으면 /api/itineraries/quote로 기존 계획을 보냅니다. 새 여행이면 /api/itineraries/generate에 trip만 보냅니다.
    async function load(trip) {
        status('일정을 계산하는 중입니다.');
        const result = plan ? await api('/itineraries/quote', plan) : await api('/itineraries/generate', trip);
        accept(result);
        loadTransport(plan.trip);
    }

    // 견적 카드의 왕복 교통 금액을 경로 비교 화면에서 고른 경로(세션의 legs)로 계산합니다. 실패하면 "미확인"으로 둡니다.
    async function loadTransport(trip) {
        try {
            let result;
            try { result = await api('/routes/compare', {trip, legs: read('legs') ?? {}}); }
            // 저장된 구간 선택을 적용한 요청이 실패하면 같은 여행의 기본 경로로 다시 시도합니다.
            catch { result = await api('/routes/compare', {trip}); }
            const unknown = result.selected.cost.missing.length > 0;
            transport = {value: unknown && Number(result.roundTripKrw) === 0 ? null : result.roundTripKrw, partial: unknown};
        } catch {
            transport = null;
        }
        renderEstimate();
    }

    // 서버가 확인한 최신 일정을 세션에 저장하고 화면을 다시 그립니다.
    function accept(result) {
        quote = result;
        plan = result.plan;
        if (!plan.days.some(day => day.date === date)) date = plan.trip.start;
        write('plan', plan);
        write('planId', id);
        save.hidden = false;
        save.textContent = id ? '저장한 일정 갱신' : '일정 저장';
        status('');
        renderHeader(cities);
        render();
    }

    // 한 날짜의 변경 내용을 전체 계획에 반영해 서버에 다시 계산을 요청합니다.
    async function change(day) {
        if (pending) return;
        pending = true;
        status('일정을 다시 계산하는 중입니다.');
        try {
            const next = {...plan, days: plan.days.map(item => item.date === day.date ? day : item)};
            accept(await api('/itineraries/quote', next));
        } catch (error) {
            render();
            showError(error);
        } finally {
            pending = false;
        }
    }

    // 방문 순서가 바뀌면 사라진 구간의 요금, 교통수단 설정을 지운 뒤 하루 일정을 다시 계산합니다.
    // 첫 장소부터 마지막 장소까지 인접한 두 곳이 한 구간입니다. 새 순서에도 남는 구간만 기존 선택을 다시 씁니다.
    function changeStops(day, stops, places = day.places ?? []) {
        const valid = stops.slice(1).map((next, index) => stops[index] + '~' + next);
        const legs = Object.fromEntries(Object.entries(day.legs ?? {}).filter(([key]) => valid.includes(key)));
        return change({...day, stops, legs, places: places.filter(place => stops.includes(place.id))});
    }

    // 여행 견적 카드: 왕복 교통, 현지 일정, 일일투어를 더하고 예산과 비교합니다.
    function renderEstimate() {
        if (!quote) return;
        const local = quote.total;
        const localUnknown = local.missing.length > 0;
        const hasStops = quote.days.some(day => day.stops.length);
        estimate.hidden = false;
        estimate.innerHTML = estimateCard({
            budget: plan.trip.budget,
            open: estimate.classList.contains('open'),
            rows: [
                {key: 'transport', label: '왕복 교통 (편도의 2배)', value: transport?.value ?? null, partial: Boolean(transport?.partial),
                    text: transport === undefined ? '계산 중' : '미확인'},
                {key: 'local', label: '현지 일정 ' + plan.days.length + '일', partial: localUnknown,
                    value: !hasStops || (localUnknown && Number(local.totalKrw) === 0) ? null : local.totalKrw,
                    text: hasStops ? '미확인' : '장소 추가 전'},
                {key: 'tour', label: '일일투어', value: null, text: '선택 안 함'}
            ],
            action: '<a class="outline" href="/tours">일일투어 더해 보기</a>'
        });
        connectEstimate(estimate);
        animateValues(estimate);
    }

    // 날짜 버튼: 날짜 색 점과 "1일차", 아래 작은 글씨로 날짜
    function renderDays() {
        $('#plan-days').innerHTML = plan.days.map((day, index) => '<button type="button" class="daybtn" data-date="' + e(day.date)
            + '" aria-pressed="' + (day.date === date) + '"><b><i style="background:var(' + dayColor(index) + ')"></i>' + (index + 1)
            + '일차</b><span class="num">' + e(day.date.slice(5).replace('-', '.')) + '</span></button>').join('');
    }

    // 장소 사이 이동 구간 줄: 교통수단 아이콘과 선택 상자, 시간과 거리, 요금, 길찾기, 요금 직접 입력, 탑승 순서
    function hop(section, day) {
        const chosen = section.chosen;
        const selection = day.legs?.[section.key];
        const override = selection?.mode === chosen.mode ? selection.override : null;
        const link = kakaoDirectionsUrl(chosen);
        return '<div class="between">' + modeIcon(chosen.mode, 18)
            + '<select class="hop-mode" data-key="' + e(section.key) + '" aria-label="' + e(chosen.from.name) + '에서 ' + e(chosen.to.name) + ' 이동 수단">'
            + section.options.map(option => '<option value="' + e(option.mode) + '"' + (option.mode === chosen.mode ? ' selected' : '') + '>'
                + e(modes[option.mode] ?? option.mode) + '</option>').join('') + '</select>'
            + '<span class="num">' + minutes(chosen.minutes) + ', ' + distance(chosen.kilometers) + '</span>'
            + '<span class="num amt">' + (section.cost.missing.length && Number(section.cost.totalKrw) === 0
                ? '<span class="warntext">미확인</span>' : e(fare(section.cost))) + '</span>'
            + transitSteps(chosen)
            + '<div class="hop-extra">'
            + (link ? '<a class="textbtn" href="' + e(link) + '" target="_blank" rel="noopener noreferrer">카카오맵에서 길찾기</a>' : '')
            + '<details' + (override || section.cost.missing.length ? ' open' : '') + '><summary>요금 직접 입력</summary>'
            + '<form class="hop-fare" data-key="' + e(section.key) + '" data-mode="' + e(chosen.mode) + '">' + fareFields(override, 'leg-')
            + '<button type="submit" class="outline">요금 적용</button>'
            + '<p>성인, 아동 1인 요금이나 일행 공동 요금을 입력하세요. 값을 비우고 적용하면 기본 요금으로 돌아갑니다.</p></form></details>'
            + '</div></div>';
    }

    // 고른 날짜의 타임라인에 요약과 방문 순서, 이동 구간, 장소 추가 기능을 표시합니다.
    function renderDay() {
        const index = plan.days.findIndex(item => item.date === date);
        const day = plan.days[index];
        const summary = quote.days[index];
        const color = 'var(' + dayColor(index) + ')';
        const travel = summary.journey.minutes;
        const facts = summary.stops.length ? '<p class="dayfacts num">머무름 ' + minutes(summary.visitMinutes)
            + ', 이동 ' + (travel === 0 ? '없음' : minutes(travel)) + ', 일행 ' + e(fare(summary.total)) + '</p>' : '';
        const rows = summary.stops.map((stop, n) => {
            const admission = summary.admissions[n];
            const fee = !admission.missing.length && Number(admission.totalKrw) === 0 ? '무료' : fare(admission);
            const section = summary.journey.sections[n];
            return '<div class="stop-row" data-index="' + n + '"><div class="stop" data-stop="' + e(stop.id) + '">'
                + '<button type="button" class="drag" aria-label="' + e(stop.name) + ' 순서 바꾸기, 위아래 화살표 키로 옮기기">' + GRIP + '</button>'
                + dayPin(n + 1, color)
                + '<div class="stop-body"><h2>' + e(stop.name) + '</h2><p>' + e(stop.description)
                + (stop.visitMinutes == null ? '' : ', ' + minutes(stop.visitMinutes) + ' 머무름') + '</p></div>'
                + '<div class="stop-side"><span class="fee num">' + fee + '</span><button type="button" class="textbtn danger" data-remove="' + n
                + '" aria-label="' + e(stop.name) + ' 삭제">삭제</button></div></div>'
                + (section ? hop(section, day) : '') + '</div>';
        }).join('');
        list.innerHTML = '<div class="timeline">' + facts + (summary.stops.length ? rows
                + '<div class="endrow">일정 끝</div>'
            : '<p class="emptyday">아직 장소가 없습니다. 아래에서 장소를 검색해 추가하세요.</p>') + '</div>'
            + (day.stops.length < 15
                ? '<button type="button" class="addplace" aria-expanded="false"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M8 3v10M3 8h10"/></svg>장소 검색해서 추가</button>'
                    + '<form id="add-stop" class="addform" hidden><label>검색 방법<select name="searchType"><option value="name">장소명</option>'
                    + '<option value="address">주소</option></select></label><label>카카오맵 검색<input name="query" type="search" maxlength="80" required placeholder="관광지 또는 숙소 이름"></label>'
                    + '<button type="submit" class="outline">검색</button></form><p id="place-status" class="fine" role="status"></p><ul id="place-results" class="place-results"></ul>'
                : '<p class="fine">하루에 최대 15곳까지 추가할 수 있습니다.</p>');
        connectDay(day);
        if (moved) {
            const card = $('[data-stop="' + CSS.escape(moved) + '"]', list);
            if (card && !reduceMotion.matches) card.classList.add('moved');
            moved = null;
        }
    }

    // 화면 전체를 그립니다: 제목, 날짜 버튼, 타임라인, 안내, 견적, 지도
    function render() {
        $('#plan-title').textContent = city(plan.trip.destination) + ' 현지 일정';
        $('#plan-note').textContent = '입장료와 이동 요금은 일행 전체 금액입니다.';
        renderDays();
        renderDay();
        renderEstimate();
        renderMap().catch(error => mapFail(error.message));
    }

    // 일정이나 고른 날짜가 바뀌면 지도를 다시 그리고 범위를 맞춥니다.
    async function renderMap() {
        const view = await getMap();
        if (!view) return;
        const focus = plan.days.findIndex(item => item.date === date);
        // 선택 날짜나 방문 순서가 바뀌거나 공급자 경로가 새로 조회되면 지도를 다시 그립니다.
        const key = JSON.stringify([focus, quote.days.map(day => day.stops.map(stop => stop.id)),
            quote.days.map(day => day.journey.sections.map(({chosen}) => [chosen.mode, chosen.checkedAt, chosen.mapObject]))]);
        if (key === drawnKey) return;
        drawnKey = key;
        const next = await drawDays(view, quote.days, focus, rightPad);
        if (!next) return;
        drawn = next;
        const note = $('#plan-geometry-note');
        note.hidden = !drawn.hasMissingGeometry;
        note.textContent = drawn.hasMissingGeometry ? '경로 좌표를 확인할 수 없는 구간은 위치만 표시합니다.' : '';
        view.onResize = () => drawn.fit();
        drawn.fit();
    }

    // 타임라인의 버튼, 선택 상자, 검색 폼, 끌기를 연결합니다.
    function connectDay(day) {
        // 장소 삭제
        $$('[data-remove]', list).forEach(button => button.onclick = () =>
            changeStops(day, day.stops.filter((_, index) => index !== +button.dataset.remove)));
        // 구간 교통수단 변경: 이전 수단의 직접 입력 요금은 지웁니다.
        $$('.hop-mode', list).forEach(select => select.onchange = () => {
            showCalc(select.closest('.between'));
            change({...day, legs: {...day.legs, [select.dataset.key]: {mode: select.value, override: null}}});
        });
        // 구간 요금 직접 입력
        $$('.hop-fare', list).forEach(form => form.onsubmit = event => {
            event.preventDefault();
            change({...day, legs: {...day.legs, [form.dataset.key]: {mode: form.dataset.mode, override: readFare(form, 'leg-')}}});
        });
        connectSearch(day);
        connectDrag(day);
    }

    // "장소 검색해서 추가"를 누르면 카카오맵 검색 폼을 열고, 고른 장소를 그날 마지막에 더합니다.
    function connectSearch(day) {
        const toggle = $('.addplace', list);
        const add = $('#add-stop', list);
        if (!add) return;
        toggle.onclick = () => {
            add.hidden = !add.hidden;
            toggle.setAttribute('aria-expanded', String(!add.hidden));
            if (!add.hidden) add.elements.query.focus();
        };
        add.elements.searchType.onchange = () => {
            const address = add.elements.searchType.value === 'address';
            add.elements.query.maxLength = address ? 160 : 80;
            add.elements.query.placeholder = address ? '도로명 주소 또는 지번 주소' : '관광지 또는 숙소 이름';
            $('#place-results', list).replaceChildren();
            $('#place-status', list).textContent = '';
        };
        // 카카오맵 검색 결과 중 한 장소를 고르면 현재 날짜의 방문 순서 끝에 추가합니다.
        add.onsubmit = async event => {
            event.preventDefault();
            const results = $('#place-results', list);
            const message = $('#place-status', list);
            const address = add.elements.searchType.value === 'address';
            const params = new URLSearchParams({destination: plan.trip.destination, q: add.elements.query.value.trim(), address});
            results.replaceChildren();
            message.textContent = '카카오맵에서 장소를 검색하는 중입니다.';
            try {
                const places = await busy(add, () => api('/itineraries/places?' + params));
                // 검색 도중 날짜를 바꾸면 기존 폼이 교체됩니다. 이전 날짜의 결과를 새 타임라인에 붙이지 않습니다.
                if (!add.isConnected) return;
                message.textContent = places.length ? '카카오맵 검색 결과' : address
                    ? '주소를 찾지 못했습니다. 건물 번호나 지번까지 입력하세요.'
                    : '장소를 찾지 못했습니다. 숙소가 검색되지 않으면 주소로 검색하세요.';
                results.innerHTML = places.map((place, index) => '<li><div><b>' + e(place.name) + '</b><small>' + e(place.address)
                    + '</small></div><button type="button" class="outline" data-place="' + index + '"'
                    + (day.stops.includes(place.id) ? ' disabled>추가됨' : '>추가') + '</button></li>').join('');
                $$('[data-place]', results).forEach(button => button.onclick = () => {
                    const place = places[Number(button.dataset.place)];
                    moved = place.id;
                    changeStops(day, [...day.stops, place.id], [...(day.places ?? []), place]);
                });
            } catch (error) {
                message.textContent = error.message;
            }
        };
    }

    // 이동 구간 줄에 "다시 계산 중"과 진행 선을 보여 줍니다(DESIGN.md recalculating).
    function showCalc(between) {
        if (!between) return;
        between.replaceChildren();
        between.insertAdjacentHTML('beforeend', '<span class="calc">다시 계산 중<i></i></span>');
    }

    // 장소 카드 끌어서 옮기기(마우스와 터치 공통, Pointer Events).
    // 손잡이를 누른 채 움직이면 카드가 손가락이나 마우스를 따라오고, 놓일 자리에 "여기에 놓기" 점선 칸이 생깁니다.
    // 다른 카드는 점선 칸이 옮겨질 때 새 자리로 미끄러집니다. 놓으면 새 순서로 다시 계산하고, 그동안 이동 구간 줄에 "다시 계산 중"을 보여 줍니다.
    // 손잡이에서 위아래 화살표 키를 누르면 한 칸씩 옮깁니다.
    function connectDrag(day) {
        const timeline = $('.timeline', list);
        if (!timeline) return;
        const pane = list.closest('.listpane');
        $$('.drag', timeline).forEach(handle => {
            const row = handle.closest('.stop-row');
            const from = Number(row.dataset.index);
            handle.addEventListener('keydown', event => {
                const step = {ArrowUp: -1, ArrowDown: 1}[event.key];
                const to = from + (step ?? 0);
                if (!step || to < 0 || to >= day.stops.length || pending) return;
                event.preventDefault();
                const stops = [...day.stops];
                [stops[from], stops[to]] = [stops[to], stops[from]];
                const stopId = day.stops[from];
                moved = stopId;
                // 다시 그린 뒤에도 옮긴 장소의 손잡이에 초점을 두어 화살표 키로 계속 옮길 수 있게 합니다.
                changeStops(day, stops).then(() => $('[data-stop="' + CSS.escape(stopId) + '"] .drag', list)?.focus());
            });
            handle.addEventListener('pointerdown', down => {
                if (pending || down.button !== 0) return;
                down.preventDefault();
                // 포인터가 손잡이나 목록 밖으로 나가도 이동과 놓기 이벤트를 같은 손잡이에서 받습니다.
                handle.setPointerCapture(down.pointerId);
                let drag = null;
                let slot = null;
                // 끌기를 시작합니다: 카드를 띄우고 원래 자리에 점선 칸을 둡니다.
                const begin = () => {
                    const rect = row.getBoundingClientRect();
                    const card = $('.stop', row);
                    slot = document.createElement('div');
                    slot.className = 'dropslot';
                    slot.style.height = card.offsetHeight + 'px';
                    slot.textContent = '여기에 놓기';
                    row.before(slot);
                    $('.between', row)?.setAttribute('hidden', '');
                    Object.assign(row.style, {position: 'fixed', left: rect.left + 'px', top: rect.top + 'px', width: rect.width + 'px', zIndex: 50, pointerEvents: 'none'});
                    row.classList.add('lifted');
                    drag = {top: rect.top};
                };
                // 점선 칸을 옮기고 나머지 카드는 새 자리로 미끄러지게 합니다.
                const moveSlot = y => {
                    const rows = $$('.stop-row', timeline).filter(item => item !== row);
                    const target = rows.find(item => { const box = $('.stop', item).getBoundingClientRect(); return y < box.top + box.height / 2; });
                    const ref = target ?? $('.endrow', timeline);
                    if (slot.nextElementSibling === ref) return;
                    const before = new Map(rows.map(item => [item, item.getBoundingClientRect().top]));
                    ref.before(slot);
                    if (reduceMotion.matches) return;
                    rows.forEach(item => {
                        const dy = before.get(item) - item.getBoundingClientRect().top;
                        if (Math.abs(dy) >= 1) item.animate([{transform: 'translateY(' + dy + 'px)'}, {transform: 'none'}], {duration: 160, easing: 'ease-out'});
                    });
                };
                // 가벼운 누름은 끌기로 처리하지 않습니다. 목록 가장자리까지 이동하면 스크롤해 화면 밖 장소도 대상으로 삼습니다.
                const onMove = event => {
                    const dy = event.clientY - down.clientY;
                    if (!drag && Math.abs(dy) < 4) return;
                    if (!drag) begin();
                    row.style.top = drag.top + dy + 'px';
                    const box = pane.getBoundingClientRect();
                    if (event.clientY < box.top + 40) pane.scrollTop -= 8;
                    else if (event.clientY > box.bottom - 40) pane.scrollTop += 8;
                    moveSlot(event.clientY);
                };
                // 놓으면 카드가 점선 칸 자리로 들어가고, 순서가 바뀌었으면 다시 계산합니다.
                const finish = commit => {
                    handle.removeEventListener('pointermove', onMove);
                    handle.removeEventListener('pointerup', onUp);
                    handle.removeEventListener('pointercancel', onCancel);
                    if (!drag) return;
                    const floating = row.getBoundingClientRect().top;
                    const order = $$('.stop-row, .dropslot', timeline).filter(item => item !== row);
                    const at = order.indexOf(slot);
                    slot.replaceWith(row);
                    row.removeAttribute('style');
                    row.classList.remove('lifted');
                    if (!reduceMotion.matches) {
                        const dy = floating - row.getBoundingClientRect().top;
                        row.animate([{transform: 'translateY(' + dy + 'px)'}, {transform: 'none'}], {duration: 320, easing: 'cubic-bezier(.2,.7,.2,1)'});
                    }
                    const stops = day.stops.filter((_, index) => index !== from);
                    stops.splice(at, 0, day.stops[from]);
                    if (!commit || stops.every((stop, index) => stop === day.stops[index])) {
                        $('.between', row)?.removeAttribute('hidden');
                        return;
                    }
                    $$('.between', timeline).forEach(showCalc);
                    $$('.between[hidden]', timeline).forEach(item => item.removeAttribute('hidden'));
                    moved = day.stops[from];
                    changeStops(day, stops);
                };
                // 취소는 서버에 새 순서를 보내지 않습니다. 놓기에서도 실제 순서가 달라졌을 때만 견적을 다시 요청합니다.
                const onUp = () => finish(true);
                const onCancel = () => finish(false);
                handle.addEventListener('pointermove', onMove);
                handle.addEventListener('pointerup', onUp);
                handle.addEventListener('pointercancel', onCancel);
            });
        });
    }

    // 날짜 버튼을 누르면 그날 타임라인과 지도만 바꿉니다.
    $('#plan-days').addEventListener('click', event => {
        const button = event.target.closest('[data-date]');
        if (!button || button.dataset.date === date) return;
        date = button.dataset.date;
        renderDays();
        renderDay();
        renderMap().catch(error => mapFail(error.message));
    });

    // 현재 일정이 새 일정인지 확인해 등록 또는 수정 API를 호출합니다.
    save.onclick = async () => {
        save.disabled = true;
        try {
            const stored = await api(id ? '/travel-plans/' + id : '/travel-plans', plan, id ? 'PUT' : 'POST');
            id = stored.id;
            write('planId', id);
            save.textContent = '저장한 일정 갱신';
            await listSaved();
            status('여행 일정을 저장했습니다.');
        } catch (error) { showError(error); }
        finally { save.disabled = false; }
    };

    const trip = read('trip');
    if (!trip) {
        list.innerHTML = '<div class="emptynote"><b>여행 조건을 먼저 선택하세요</b><p>출발지, 목적지와 여행 날짜가 필요합니다. 저장한 여행을 열어도 됩니다.</p>'
            + '<a class="primary" href="/">여행 검색</a></div>';
        $('#saved').open = true;
        mapFail('여행 조건을 고르면 일정이 지도에 표시됩니다.');
    }
    date = plan?.trip.start ?? trip?.start;
    const outcomes = await Promise.allSettled([listSaved().then(openSaved), ...(trip ? [load(trip)] : [])]);
    outcomes.filter(outcome => outcome.status === 'rejected').forEach(outcome => showError(outcome.reason));
}
