import {$, $$, api, read, write, e, status, showError, renderHeader} from './common.js';

const loaders = {
    routes: () => import('./routes.js'),
    plan: () => import('./plan.js'),
    tours: () => import('./tours.js'),
    compare: () => import('./compare.js')
};

// 출발지와 목적지 각각에 장소 검색과 선택 상태를 둡니다. 시·도 이름만 입력한 경우에는 서버가 대표 지점을 사용합니다.
function tripPlaceField(field, previous) {
    const input = $('#trip-' + field);
    const results = $('#' + field + '-place-results');
    const message = $('#' + field + '-place-status');
    let selected;
    let request = 0;

    // 입력값이 바뀌거나 장소를 선택하면 기존 요청 번호를 무효화해 늦게 도착한 검색 응답을 무시합니다.
    function set(place) {
        request++;
        selected = place;
        if (place) input.value = place.name;
        message.textContent = place?.address ?? '';
        results.replaceChildren();
        results.hidden = true;
    }
    input.addEventListener('input', () => set(null));
    // 한글 조합에 쓰이는 Enter는 건드리지 않습니다. 검색용 Enter에서는 여행 폼 제출을 막고 해당 장소만 찾습니다.
    input.addEventListener('keydown', async event => {
        if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229 || event.defaultPrevented) return;
        event.preventDefault();
        if (event.repeat) return;
        const term = input.value.trim();
        if (!term) {
            message.textContent = '주소나 장소 이름을 입력해 주세요.';
            input.focus();
            return;
        }
        const token = ++request;
        results.replaceChildren();
        results.hidden = true;
        message.textContent = '전국에서 장소를 찾고 있습니다.';
        const search = address => api('/places/search?' + new URLSearchParams({q: term, address}));
        try {
            // 주소로 보이는 입력을 먼저 주소 검색에 보냅니다. 간판 이름처럼 숫자로 끝나는 장소도 찾을 수 있게 결과가 없으면 이름으로 재검색합니다.
            const address = term.length > 80 || /\d+(?:-\d+)?$/.test(term);
            let places = await search(address);
            if (token !== request) return;
            if (!places.length && address && term.length <= 80) places = await search(false);
            if (token !== request) return;
            message.textContent = places.length ? '주소를 확인하고 장소를 선택해 주세요.'
                : '결과가 없습니다. 시·군 이름을 함께 쓰거나 건물 번호까지 입력해 보세요.';
            results.innerHTML = places.map((place, index) => '<li><button type="button" data-trip-place="' + index
                + '"><strong>' + e(place.name) + '</strong><small>' + e(place.address) + '</small></button></li>').join('');
            results.hidden = places.length === 0;
            $$('[data-trip-place]', results).forEach(button => button.onclick = () => {
                set(places[Number(button.dataset.tripPlace)]);
                input.focus();
            });
        } catch (error) {
            if (token === request) message.textContent = error.message;
        }
    });
    set(previous ?? null);
    return () => selected;
}

// 홈 화면에서 여행 조건을 입력받고, 검색이 성공하면 경로 비교 화면으로 이동합니다.
// cities는 /api/destinations/search에서 받은 목록입니다. 검색을 제출하면 /api/routes/compare 응답의 표준 도시 ID가 포함된 여행 조건을 세션에 저장합니다.
// 새 여행을 시작할 때 기존 legs, plan, planId, comparison을 비워 이전 선택이 새 경로에 섞이지 않게 합니다.
async function home(cities) {
    const form = $('#trip-form');
    const fields = form.elements;
    const previous = read('trip');
    // 홈 입력창에 표시할 도시 이름을 ID로 찾습니다.
    const cityName = id => cities.find(city => city.id === id)?.name ?? id;
    fields.start.value = previous?.start ?? '';
    fields.end.value = previous?.end ?? '';
    fields.end.min = fields.start.value;
    // 시작일을 바꾸면 종료일의 최솟값을 맞춰 잘못된 기간이 제출되는 것을 막습니다.
    fields.start.addEventListener('change', () => {
        fields.end.min = fields.start.value;
        if (fields.end.value && fields.end.value < fields.start.value) fields.end.value = fields.start.value;
    });
    $('#city-list').innerHTML = cities.map(city => '<option value="' + e(city.name) + '"></option>').join('');
    if (previous) {
        fields.origin.value = cityName(previous.origin);
        fields.destination.value = cityName(previous.destination);
        fields.adults.value = previous.party.adults;
        fields.children.value = previous.party.children;
    }
    const originPlace = tripPlaceField('origin', previous?.originPlace);
    const destinationPlace = tripPlaceField('destination', previous?.destinationPlace);
    // 검색 폼을 경로 API에 보내고 승인된 여행 조건을 다음 화면에 전달합니다.
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = $('.search-submit');
        button.disabled = true;
        status('여행 조건을 확인하는 중입니다.');
        // 예산과 선호 교통수단은 비워 두고, API 필수 항목인 여행 유형에는 기본값 OTHER를 보냅니다.
        const trip = {origin: fields.origin.value, destination: fields.destination.value,
            originPlace: originPlace(), destinationPlace: destinationPlace(),
            start: fields.start.value, end: fields.end.value, party: {adults: +fields.adults.value, children: +fields.children.value},
            budget: null, preferredMode: null, type: 'OTHER'};
        try {
            const result = await api('/routes/compare', {trip});
            write('trip', result.trip);
            ['legs', 'plan', 'planId', 'comparison'].forEach(key => write(key, null));
            location.assign('/search');
        } catch (error) { showError(error); button.disabled = false; }
    });
}

try {
    const cities = await api('/destinations/search');
    const trip = read('trip');
    if (trip && ![trip.origin, trip.destination].every(id => cities.some(city => city.id === id))) {
        ['trip', 'legs', 'plan', 'planId', 'comparison'].forEach(key => write(key, null));
    }
    renderHeader(cities);
    // 템플릿의 data-page를 보고 화면 모듈을 선택합니다. 모든 화면은 같은 지역 목록과 공통 머리글을 사용합니다.
    const current = document.body.dataset.page;
    if (current === 'home') await home(cities);
    else if (loaders[current]) await (await loaders[current]()).init(cities);
} catch (error) { showError(error); }
