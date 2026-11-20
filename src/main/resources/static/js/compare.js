import {$, api, read, write, e, status, showTrip, showError, panel, won, minutes, platforms, inclusionTags,
    priceType, moneyDetails, missing, legCards, connectLegs, busy, cityOptions, fareFields, readFare, itemRow, nearby} from './common.js';

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
    const locations = cities.filter(city => nearby(city.id, detail.destination));
    const fallback = {tourId: id, date: trip?.start ?? detail.prices[0].from, party: trip?.party ?? {adults: 1, children: 0}};
    let request = saved?.tourId === id ? saved : fallback;
    if (!locations.some(city => city.id === request.lodgingCity)) {
        request = {...request, lodgingCity: detail.destination, legs: {}, stops: null};
    }
    let latest = null;
    const settings = $('#comparison-settings');
    const legsRoot = $('#comparison-legs');
    const plan = read('plan');

    function renderSettings() {
        settings.innerHTML = panel('<h2>비교 조건</h2><p class="muted">'
            + ' · 숙소는 선택한 도시의 중심 위치를 기준으로 가정합니다.</p>'
            + '<form id="compare-form"><div class="form-grid compact"><label>투어 날짜<input name="date" type="date" required value="'
            + e(request.date) + '"></label><label>숙소 도시<select name="lodgingCity">' + cityOptions(locations, request.lodgingCity)
            + '</select></label><label>성인<input name="adults" type="number" min="1" max="30" required value="' + request.party.adults
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
        form.onsubmit = event => {
            event.preventDefault();
            const fields = form.elements;
            const nextDate = fields.date.value;
            const nextCity = fields.lodgingCity.value;
            const usePlan = fields.usePlan?.checked ?? false;
            const day = plan?.days.find(item => item.date === nextDate);
            if (usePlan && !day?.stops.length) {
                status('선택한 날짜의 내 일정에 관광지가 없습니다. 일정 페이지에서 관광지를 추가해 주세요.', 'error');
                return;
            }
            const same = request.usePlan === usePlan && request.date === nextDate && request.lodgingCity === nextCity;
            const sameBase = plan?.trip.destination === nextCity;
            const legs = same ? request.legs : usePlan && sameBase ? day.legs : {};
            run({...request, date: nextDate, lodgingCity: nextCity, usePlan, stops: usePlan ? day.stops : null,
                party: {adults: +fields.adults.value, children: +fields.children.value}, legs,
                tourOutbound: readFare(form, 'out-'), tourReturn: readFare(form, 'back-'),
                independentExtras: readFare(form, 'extra-')}, '/compare');
        };
    }

    function side(title, side, stops, variant, details = '') {
        return panel('<div class="side-header"><span class="eyebrow">' + e(title) + '</span>'
            + '<span class="side-amount">' + won(side.cost.totalKrw) + '</span><p class="muted">'
            + (side.cost.missing.length ? '확인된 항목의 부분 합계 · ' : '') + moneyDetails(side.cost) + '</p></div>'
            + '<div class="side-facts"><span>이동 ' + minutes(side.travelMinutes) + '</span><span>일정 ' + minutes(side.scheduledMinutes)
            + '</span></div><p>' + e(side.convenience) + '</p><p class="muted">' + stops.map(stop => e(stop.name)).join(' → ') + '</p>' + details
            + '<ul class="cost-list">' + side.items.map(itemRow).join('') + '</ul>' + missing(side.cost), variant);
    }

    function render(result) {
        const gap = result.tourMinusIndependentKrw;
        const headline = gap == null ? '비용 차이 계산 보류' : gap === 0 ? '두 방식의 예상 비용이 같습니다'
            : (gap > 0 ? '자유여행이 ' : '일일투어가 ') + won(Math.abs(gap)) + ' 저렴해요';
        $('#edit-transport').hidden = false;
        $('#comparison-summary').innerHTML = panel('<h2>' + headline + '</h2><p class="muted">' + e(detail.name) + ' · '
            + e(platforms[detail.platform]) + ' · ' + e(request.date) + '</p><p>' + e(result.recommendation)
            + '</p>' + result.notices.map(note => '<p class="notice">' + e(note) + '</p>').join(''), 'comparison-note');
        $('#comparison-sides').innerHTML = '<div class="comparison-grid">'
            + side('일일투어', result.guided, result.tourStops, 'guided', priceType(tour.source, tour.checkedAt) + inclusionTags(detail))
            + side('자유여행', result.independent, result.independentStops, 'independent') + '</div>';
        legsRoot.innerHTML = panel('<h2>자유여행 교통수단 조정</h2><p class="muted">이동 방법을 변경하면 두 여행 방식의 비용 차이를 다시 계산합니다.</p>'
            + legCards(result.journey.sections, request.legs));
        connectLegs(legsRoot, (key, leg) => run({...request, legs: {...request.legs, [key]: leg}}, '/compare/recalculate'));
    }

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
