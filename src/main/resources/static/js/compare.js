import {$, api, read, write, e, status, showTrip, showError, panel, won, minutes, platforms, inclusionTags,
    priceType, moneyDetails, missing, legCards, connectLegs, busy, fareFields, readFare, itemRow, dateAfter,
    tourReferenceLinks} from './common.js';

// 선택한 투어와 현재 여행 날짜를 읽어 자유여행·투어 비교 화면을 준비합니다.
// URL 또는 현재 탭의 comparison 상태로 투어를 찾습니다. 저장된 일정은 투어 목적지와 같을 때만 날짜별 자유여행 계획으로 제시합니다.
export async function init(cities) {
    const trip = read('trip');
    showTrip(trip, cities);
    const saved = read('comparison');
    const id = new URLSearchParams(location.search).get('tour') || saved?.tourId;
    if (!id) {
        $('#comparison-settings').innerHTML = panel('<h2>비교할 투어를 선택해 주세요</h2><a class="primary" href="/tours">일일투어 둘러보기</a>');
        return;
    }
    const tour = await api('/tours/' + encodeURIComponent(id));
    $('#edit-comparison').hidden = false;
    const detail = tour.details;
    const fallback = {tourId: id, date: trip?.start ?? (tour.reference ? dateAfter(0) : detail.prices[0].from),
        party: trip?.party ?? {adults: tour.reference?.estimate ? 10 : 1, children: 0}};
    let request = saved?.tourId === id ? saved : fallback;
    let latest = null;
    const settings = $('#comparison-settings');
    const legsRoot = $('#comparison-legs');
    const currentPlan = read('plan');
    const plan = currentPlan?.trip.destination === detail.destination ? currentPlan : null;

    // 날짜·인원·직접 입력 비용을 수정하는 폼을 그리고 변경을 재계산 요청에 연결합니다.
    // 제출 시 선택 날짜의 내 일정에 방문지가 있는지 확인합니다. 날짜나 내 일정 사용 여부가 바뀌면 다른 조건의 교통수단 선택이 남지 않도록 구간 설정을 조정합니다.
    function renderSettings() {
        settings.innerHTML = panel('<h2>비교 조건</h2><p class="muted">'
            + (tour.reference ? '자유여행 경로는 첫 방문지에서 시작해 마지막 방문지에서 끝납니다.'
                : '투어의 숙소 기준은 도시 중심입니다. 내 일정의 자유여행 경로는 첫 방문지에서 출발합니다.') + '</p>'
            + (tour.reference?.estimate ? '<p class="muted">예상가는 성인과 아동을 합해 6~20명일 때 계산됩니다.</p>' : '')
            + '<form id="compare-form"><div class="form-grid compact"><label>투어 날짜<input name="date" type="date" required value="'
            + e(request.date) + '"></label><label>성인<input name="adults" type="number" min="1" max="30" required value="' + request.party.adults
            + '"></label><label>아동<input name="children" type="number" min="0" max="30" required value="' + request.party.children
            + '"></label></div>'
            + (plan ? '<label class="check"><input name="usePlan" type="checkbox"' + (request.usePlan ? ' checked' : '')
                + '> 선택한 날짜의 내 일정과 비교하기</label>' : '')
            + '<details><summary>집합 장소 교통비 및 자유여행 추가비 직접 입력</summary>'
            + '<p class="muted">성인·아동 1인 요금 또는 일행 공동 요금을 입력하세요. 무료로 처리하려면 0을 입력하세요.</p>'
            + '<fieldset><legend>숙소에서 집합 장소까지</legend>' + fareFields(request.tourOutbound, 'out-') + '</fieldset>'
            + '<fieldset><legend>종료 장소에서 숙소까지</legend>' + fareFields(request.tourReturn, 'back-') + '</fieldset>'
            + '<fieldset><legend>자유여행의 기타 필수 비용</legend>' + fareFields(request.independentExtras, 'extra-') + '</fieldset></details>'
            + '<div class="actions"><button class="primary" type="submit">비용 계산</button></div></form>');
        const form = $('#compare-form');
        // 비교 조건과 선택한 날짜의 내 일정을 검증하고 새 비용 계산을 요청합니다.
        form.onsubmit = event => {
            event.preventDefault();
            const fields = form.elements;
            const nextDate = fields.date.value;
            const usePlan = fields.usePlan?.checked ?? false;
            const day = plan?.days.find(item => item.date === nextDate);
            if (usePlan && !day?.stops.length) {
                status('선택한 날짜의 내 일정에 관광지가 없습니다. 일정 페이지에서 관광지를 추가해 주세요.', 'error');
                return;
            }
            const same = request.usePlan === usePlan && request.date === nextDate;
            const legs = same ? request.legs : usePlan ? day.legs : {};
            run({...request, date: nextDate, usePlan, stops: usePlan ? day.stops : null,
                places: usePlan ? (day.places ?? []) : [],
                party: {adults: +fields.adults.value, children: +fields.children.value}, legs,
                tourOutbound: readFare(form, 'out-'), tourReturn: readFare(form, 'back-'),
                independentExtras: readFare(form, 'extra-')}, '/compare');
        };
    }

    // 투어 또는 자유여행 한쪽의 비용, 소요 시간, 방문지와 세부 비용을 카드로 표시합니다.
    function side(title, side, stops, variant, details = '') {
        return panel('<div class="side-header"><span class="eyebrow">' + e(title) + '</span>'
            + '<span class="side-amount">' + won(side.cost.totalKrw) + '</span><p class="muted">'
            + (side.cost.missing.length ? '확인된 항목의 부분 합계 · ' : '') + moneyDetails(side.cost) + '</p></div>'
            + '<div class="side-facts"><span>이동 ' + minutes(side.travelMinutes) + '</span><span>일정 ' + minutes(side.scheduledMinutes)
            + '</span></div><p>' + e(side.convenience) + '</p><p class="muted">' + stops.map(stop => e(stop.name)).join(' → ') + '</p>' + details
            + '<ul class="cost-list">' + side.items.map(itemRow).join('') + '</ul>' + missing(side.cost), variant);
    }

    // 최신 비교 결과로 비용 차이와 권장 사항을 표시하고 자유여행 교통수단 변경을 연결합니다.
    // 서버가 확정한 경우에만 비용 차액을 표시합니다. 두 견적과 주의사항을 다시 그리고 자유여행 구간 변경을 /api/compare/recalculate에 연결합니다.
    function render(result) {
        const gap = result.tourMinusIndependentKrw;
        const headline = gap == null ? '비용 차이 계산 보류' : gap === 0 ? '두 방식의 예상 비용이 같습니다'
            : (gap > 0 ? '자유여행이 ' : '일일투어가 ') + won(Math.abs(gap)) + ' 저렴해요';
        $('#edit-transport').hidden = false;
        $('#comparison-summary').innerHTML = panel('<h2>' + headline + '</h2><p class="muted">' + e(detail.name) + ' · '
            + e(platforms[detail.platform]) + ' · ' + e(request.date) + '</p><p>' + e(result.recommendation)
            + '</p>' + result.notices.map(note => '<p class="notice">' + e(note) + '</p>').join(''), 'comparison-note');
        const tourDetails = tour.reference ? '<div class="actions">' + tourReferenceLinks(tour.reference) + '</div>' : inclusionTags(detail);
        $('#comparison-sides').innerHTML = '<div class="comparison-grid">'
            + side('일일투어', result.guided, result.tourStops, 'guided', priceType(tour.source, tour.checkedAt) + tourDetails)
            + side('자유여행', result.independent, result.independentStops, 'independent') + '</div>';
        legsRoot.innerHTML = panel('<h2>자유여행 교통수단 조정</h2><p class="muted">이동 방법을 변경하면 두 여행 방식의 비용 차이를 다시 계산합니다.</p>'
            + legCards(result.journey.sections, request.legs));
        connectLegs(legsRoot, (key, leg) => run({...request, legs: {...request.legs, [key]: leg}}, '/compare/recalculate'));
    }

    // 비용 비교 또는 재계산 API를 호출해 결과를 반영하고 실패하면 마지막 결과를 유지합니다.
    // 서버가 사용하지 않는 화면 전용 usePlan 플래그를 요청 본문에서 제외합니다. 성공한 요청만 세션에 기록하고 오류가 나면 이전 결과를 다시 표시합니다.
    async function run(next, endpoint) {
        await busy(document.body, async () => {
            status('비용을 계산하는 중입니다.');
            const {usePlan, ...body} = next;
            latest = await api(endpoint, body);
            request = next;
            write('comparison', request);
            render(latest);
            status('');
        }).catch(error => {
            if (latest) render(latest);
            showError(error);
        });
    }

    renderSettings();
    await run(request, '/compare');
}
