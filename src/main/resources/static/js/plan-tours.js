import {$, $$, api, e, won, write} from './common.js';
import {createMap, drawDays} from './route-map.js';

// 등록 상품은 날짜별 요금을, 예상가 상품은 1인 요금과 현재 일행의 합계를 보여 줍니다.
function priceLabel({tour, fare}, party) {
    const reference = tour.reference;
    if (tour.source === 'ESTIMATED') {
        const people = party.adults + party.children;
        return '1인 예상가 ' + won(fare.adult) + ', ' + people + '명 합계 ' + won(fare.adult * people);
    }
    const label = reference ? '공개 요금' : '선택 날짜 등록 요금';
    return label + ', 성인 1인 ' + won(fare?.adult) + (reference?.fromPrice && fare?.adult != null ? '부터' : '')
        + (party.children ? ', 아동 1인 ' + won(fare?.child) : '');
}

export function connectPlanTours() {
    const section = $('#related-tours');
    const list = $('#related-tours-list');
    const status = $('#related-tours-status');
    const dialog = $('#tour-preview');
    const mapSlot = $('#tour-preview-map');
    const note = $('#tour-preview-note');
    let mapReady;
    let activePreview = 0;
    let lastKey;
    let request = 0;
    let currentDay;

    // 일정 지도와 별개로 투어 장소의 순번을 표시합니다. SDK가 없어도 대화상자에서 전체 순서를 볼 수 있습니다.
    async function preview({tour, stops}) {
        const token = ++activePreview;
        $('#tour-preview-title').textContent = tour.details.name;
        $('#tour-preview-stops').innerHTML = stops.map(stop => '<li>' + e(stop.name) + '</li>').join('');
        mapSlot.hidden = true;
        const hasMap = !!$('meta[name="kakao-map-key"]');
        note.textContent = hasMap ? '지도 번호는 저장된 방문 순서입니다.' : '지도를 사용할 수 없어 방문 순서를 목록으로 표시합니다.';
        dialog.showModal();
        if (!hasMap) return;
        try {
            mapSlot.hidden = false;
            const view = await (mapReady ??= createMap(mapSlot));
            if (!dialog.open || token !== activePreview) return;
            // 카탈로그에는 정류장 사이의 실제 운행 좌표가 없어 경로 선 없이 번호 핀을 그립니다.
            const drawn = await drawDays(view, [{stops, journey: {sections: []}}], 0);
            if (!drawn || !dialog.open || token !== activePreview) return;
            view.onResize = drawn.fit;
            view.map.relayout();
            drawn.fit();
        } catch {
            mapReady = undefined;
            if (!dialog.open || token !== activePreview) return;
            mapSlot.hidden = true;
            note.textContent = '지도를 불러오지 못해 방문 순서를 목록으로 표시합니다.';
        }
    }

    // 날짜, 지역, 인원 또는 방문지가 바뀔 때만 조회합니다. 늦게 온 다른 날짜의 응답은 화면에 쓰지 않습니다.
    return async (day, trip) => {
        currentDay = day;
        const {destination, party} = trip;
        const {date, stops} = day;
        const key = JSON.stringify([destination, date, party.adults, party.children, [...stops].sort()]);
        if (key === lastKey) return;
        lastKey = key;
        const token = ++request;
        list.replaceChildren();
        status.textContent = '';
        section.hidden = true;
        if (!stops.some(id => !id.startsWith('kakao-'))) return;
        try {
            const tours = await api('/tours/recommend', {destination, date, party, stops});
            if (token !== request || !tours.length) return;
            list.innerHTML = tours.map((result, index) => '<article class="related-tour"><h3>'
                + e(result.tour.details.name) + '</h3><p class="num">' + e(priceLabel(result, party)) + '</p><p>'
                + result.stops.map(stop => e(stop.name)).join(' → ') + '</p><div class="actions">'
                + '<button class="outline" type="button" data-preview-tour="' + index + '">지도에서 보기</button>'
                + '<button class="outline" type="button" data-compare-tour="' + index + '">자유여행과 비용 비교</button></div></article>').join('');
            $$('[data-preview-tour]', list).forEach(button => button.onclick = () => preview(tours[button.dataset.previewTour]));
            $$('[data-compare-tour]', list).forEach(button => button.onclick = () => {
                const tourId = tours[button.dataset.compareTour].tour.id;
                write('comparison', {tourId, date, party, usePlan: true, stops: currentDay.stops,
                    places: currentDay.places ?? [], legs: currentDay.legs ?? {}});
                location.assign('/compare?tour=' + encodeURIComponent(tourId));
            });
            section.hidden = false;
        } catch {
            if (token !== request) return;
            lastKey = undefined;
            status.textContent = '관련 일일투어를 불러오지 못했습니다.';
            section.hidden = false;
        }
    };
}
