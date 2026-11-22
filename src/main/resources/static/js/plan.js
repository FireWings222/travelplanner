import {$, $$, api, read, write, e, panel, busy, options, status, showError, showTrip, needsTrip,
    metrics, metric, won, minutes, missing, legCards, connectLegs, itemRow} from './common.js';

// 일정 화면에서 현재 여행 조건·저장 일정 목록을 불러오고 편집과 저장을 연결합니다.
// 여행 계획과 저장 ID는 현재 탭의 세션에서 읽습니다. 목록은 서버에서 조회하며 현재 여행 조건이 있으면 편집할 계획도 함께 로드합니다.
export async function init(cities) {
    const root = $('#plan-days');
    const save = $('#save-plan');
    let plan = read('plan');
    let id = read('planId');
    let date;
    let quote;

    // 저장 목록에서 받은 계획을 현재 작업 상태로 열고 해당 일정 견적을 불러옵니다.
    // 저장 API가 반환한 계획과 ID를 편집 대상으로 삼고 시작일을 선택합니다. 이전 여행의 경로 선택과 투어 비교 상태는 초기화합니다.
    async function open(stored) {
        plan = stored.plan;
        id = stored.id;
        date = plan.trip.start;
        write('trip', plan.trip);
        write('legs', null);
        write('comparison', null);
        await load(plan.trip);
    }

    // 일정 목록 API를 호출해 저장한 여행을 표시하고 열기·삭제 버튼을 연결합니다.
    async function listSaved() {
        const records = await api('/travel-plans');
        $('#saved-plans').innerHTML = records.length ? records.map(record => {
            const city = cities.find(item => item.id === record.trip.destination);
            const supported = city && cities.some(item => item.id === record.trip.origin);
            return '<div class="saved-row"><div><strong>' + e(city?.name ?? record.trip.destination) + '</strong><p class="muted">' + e(record.trip.start) + ' – '
                + e(record.trip.end) + '</p></div><div class="actions"><button type="button" class="soft" data-open="' + e(record.id)
                + '"' + (supported ? '>열기' : ' disabled>지원 종료') + '</button><button type="button" class="danger" data-delete="' + e(record.id) + '">삭제</button></div></div>';
        }).join('') : '<p class="empty">저장한 여행이 없습니다.</p>';
        // 저장한 계획 상세를 조회하고 현재 일정으로 엽니다.
        $$('[data-open]').forEach(button => button.onclick = () => busy($('#saved-plans'), async () =>
            open(await api('/travel-plans/' + button.dataset.open))).catch(showError));
        // 삭제 확인 뒤 저장 일정 API와 화면의 저장 목록을 갱신합니다.
        $$('[data-delete]').forEach(button => button.onclick = () => {
            if (!window.confirm('저장한 여행을 삭제할까요?')) return;
            busy($('#saved-plans'), async () => {
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
    }

    // 서버가 확인한 최신 일정을 세션에 저장하고 요약과 날짜별 화면을 다시 그립니다.
    // result.plan은 수정 후 사용할 계획이고 result.days는 날짜별 이동과 입장료 계산 결과입니다. 날짜별 상세 화면은 renderDay에서 읽습니다.
    function accept(result) {
        quote = result;
        plan = result.plan;
        date ??= plan.trip.start;
        write('plan', plan);
        write('planId', id);
        showTrip(plan.trip, cities);
        save.hidden = false;
        save.textContent = id ? '저장한 일정 갱신' : '일정 저장';
        const travel = result.days.reduce((sum, day) => sum + day.journey.minutes, 0);
        $('#plan-summary').innerHTML = panel('<h2>현지 일정 예상 합계</h2>'
            + metrics(metric('일차', plan.days.length + '일'), metric('현지 이동 시간', minutes(travel)),
                metric('전체 입장료 + 이동비', won(result.total.totalKrw)))
            + '<p class="muted">각 날짜의 첫 장소에서 마지막 장소까지의 이동을 계산합니다. 목적지 왕복, 숙박과 식비는 별도입니다.</p>' + missing(result.total));
        status('');
        renderDay();
    }

    // 한 날짜의 변경 내용을 전체 계획에 반영해 서버에 비용 재계산을 요청합니다.
    // 수정한 날짜 객체만 전체 계획에 교체해 서버에 전달합니다. 서버가 계산한 결과를 받으면 accept가 화면과 편집 상태를 함께 갱신합니다.
    async function change(day) {
        await busy(root, async () => {
            status('일정을 다시 계산하는 중입니다.');
            const next = {...plan, days: plan.days.map(item => item.date === day.date ? day : item)};
            accept(await api('/itineraries/quote', next));
        }).catch(showError);
    }

    // 선택한 날짜의 방문지 순서, 예상 비용과 이동 구간을 그리고 편집 이벤트를 연결합니다.
    // 선택한 날짜의 입력용 Day와 서버 계산 결과를 각각 찾습니다. Day에는 관광지 ID와 구간 선택이, 견적에는 비용과 소요 시간이 들어 있습니다.
    function renderDay() {
        const day = plan.days.find(item => item.date === date);
        const summary = quote.days.find(item => item.date === date);
        const hasUnscheduledStop = summary.stops.some(sight => sight.id.startsWith('kakao-'));
        root.innerHTML = panel('<div class="panel-heading"><div><h2>방문 일정</h2><p>첫 장소가 출발지입니다. 숙소에서 출발하려면 숙소를 첫 번째로 옮기세요.</p></div>'
            + '<label>일자<select id="pick-day">' + options(plan.days.map((item, index) => [item.date,
                (index + 1) + '일차 · ' + item.date]), date) + '</select></label></div>'
            + metrics(metric('관광지 체류', hasUnscheduledStop
                ? (summary.visitMinutes ? minutes(summary.visitMinutes) + ' (확인된 장소)' : '미확인')
                : minutes(summary.visitMinutes)), metric('이동 시간', minutes(summary.journey.minutes)),
                metric('일행 예상 지출', won(summary.total.totalKrw))) + missing(summary.total)
            + (summary.stops.length ? '<ol class="stop-list">' + summary.stops.map((sight, index) => {
                const cost = summary.admissions[index];
                const searched = sight.id.startsWith('kakao-');
                return '<li><div><strong>' + e(sight.name) + '</strong>'
                    + (index === 0 ? ' <span class="tag">출발지</span>' : '')
                    + '<p class="muted">' + e(sight.description)
                    + (searched ? ' · 카카오맵 · 체류 시간 및 입장료 미확인'
                        : ' · 체류 ' + minutes(sight.visitMinutes) + ' · 입장료 일행 ' + won(cost.totalKrw))
                    + '</p></div>'
                    + '<div class="actions"><button class="soft" type="button" aria-label="' + e(sight.name) + ' 위로" data-index="' + index
                    + '" data-move="-1"' + (index === 0 ? ' disabled' : '') + '>↑</button>'
                    + '<button class="soft" type="button" aria-label="' + e(sight.name) + ' 아래로" data-index="' + index
                    + '" data-move="1"' + (index + 1 === summary.stops.length ? ' disabled' : '') + '>↓</button>'
                    + '<button class="danger" type="button" data-index="' + index + '" data-remove>삭제</button></div></li>';
            }).join('') + '</ol>' : '<p class="empty">추가된 관광지가 없습니다.</p>')
            + (day.stops.length < 15 ? '<form id="add-stop" class="actions">'
                + '<label>검색 방법<select name="searchType"><option value="name">장소명</option>'
                + '<option value="address">주소</option></select></label>'
                + '<label>카카오맵 검색<input name="query" type="search" maxlength="80" required placeholder="관광지 또는 숙소 이름"></label>'
                + '<button type="submit" class="primary">검색</button></form>'
                + '<p id="place-status" class="muted" role="status"></p><ul id="place-results" class="cost-list"></ul>'
                : '<p class="muted">하루에 최대 15곳까지 추가할 수 있습니다.</p>')
            + '<details><summary>입장료 내역</summary><ul class="cost-list">' + summary.admissions.map(itemRow).join('') + '</ul></details>')
            + panel('<h2>관광지 간 이동</h2>' + legCards(summary.journey.sections, day.legs, date));
        // 날짜 선택에 따라 해당 일차의 상세 내용만 다시 표시합니다.
        $('#pick-day').onchange = event => { date = event.target.value; renderDay(); };
        // 방문 순서가 바뀌면 사라진 구간의 요금·교통수단 설정을 제거한 뒤 하루 일정을 갱신합니다.
        // 첫 장소부터 마지막 장소까지 인접한 두 곳이 한 구간입니다. 새 순서에도 남는 구간만 기존 선택을 재사용합니다.
        function changeStops(stops, places = day.places ?? []) {
            const valid = stops.slice(1).map((next, index) => stops[index] + '~' + next);
            const legs = Object.fromEntries(Object.entries(day.legs ?? {}).filter(([key]) => valid.includes(key)));
            change({...day, stops, legs, places: places.filter(place => stops.includes(place.id))});
        }
        const add = $('#add-stop');
        if (add) {
            add.elements.searchType.onchange = () => {
                const address = add.elements.searchType.value === 'address';
                add.elements.query.maxLength = address ? 160 : 80;
                add.elements.query.placeholder = address ? '도로명 주소 또는 지번 주소' : '관광지 또는 숙소 이름';
                $('#place-results', root).replaceChildren();
                $('#place-status', root).textContent = '';
            };
            // 카카오맵 검색 결과 중 한 장소를 선택하면 현재 날짜의 방문 순서 끝에 추가합니다.
            add.onsubmit = async event => {
                event.preventDefault();
                const results = $('#place-results', root);
                const message = $('#place-status', root);
                const address = add.elements.searchType.value === 'address';
                const params = new URLSearchParams({destination: plan.trip.destination,
                    q: add.elements.query.value.trim(), address});
                results.replaceChildren();
                message.textContent = '카카오맵에서 장소를 검색하는 중입니다.';
                try {
                    const places = await busy(root, () => api('/itineraries/places?' + params));
                    if (!add.isConnected) return;
                    message.textContent = places.length ? '카카오맵 검색 결과' : address
                        ? '주소를 찾지 못했습니다. 건물 번호나 지번까지 입력해 주세요.'
                        : '장소를 찾지 못했습니다. 숙소가 검색되지 않으면 주소로 검색해 보세요.';
                    results.innerHTML = places.map((place, index) => {
                        const selected = day.stops.includes(place.id);
                        return '<li><div><strong>' + e(place.name) + '</strong><small>' + e(place.address)
                            + '</small></div><button type="button" class="soft" data-place="' + index + '"'
                            + (selected ? ' disabled>추가됨' : '>추가') + '</button></li>';
                    }).join('');
                    $$('[data-place]', results).forEach(button => button.onclick = () => {
                        const place = places[Number(button.dataset.place)];
                        changeStops([...day.stops, place.id], [...(day.places ?? []), place]);
                    });
                } catch (error) {
                    message.textContent = error.message;
                }
            };
        }
        // 위·아래 버튼으로 변경한 방문 순서를 일정 재계산에 전달합니다.
        $$('[data-move]', root).forEach(button => button.onclick = () => {
            const index = +button.dataset.index;
            const target = index + +button.dataset.move;
            const stops = [...day.stops];
            [stops[index], stops[target]] = [stops[target], stops[index]];
            changeStops(stops);
        });
        // 선택한 방문지를 해당 날짜 일정에서 제거합니다.
        $$('[data-remove]', root).forEach(button => button.onclick = () =>
            changeStops(day.stops.filter((_, index) => index !== +button.dataset.index)));
        connectLegs(root, (key, leg) => change({...day, legs: {...day.legs, [key]: leg}}));
    }

    // 현재 일정이 신규인지 확인해 등록 또는 수정 API를 호출합니다.
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
    if (!trip) needsTrip('#plan-summary');
    const outcomes = await Promise.allSettled([listSaved(), ...(trip ? [load(trip)] : [])]);
    outcomes.filter(outcome => outcome.status === 'rejected').forEach(outcome => showError(outcome.reason));
}
