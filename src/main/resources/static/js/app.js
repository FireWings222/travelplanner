import {$, $$, api, read, write, e, modeOptions, dateAfter, status, showError} from './common.js';

const loaders = {
    routes: () => import('./routes.js'),
    plan: () => import('./plan.js'),
    tours: () => import('./tours.js'),
    compare: () => import('./compare.js')
};

// 홈 화면의 날짜·도시 입력과 제주 예제 버튼을 준비하고, 검색이 성공하면 경로 비교 화면으로 이동합니다.
// cities는 /api/destinations/search에서 받은 목록입니다. 검색을 제출하면 /api/routes/compare 응답의 표준 도시 ID가 포함된 여행 조건을 세션에 저장합니다.
// 새 여행을 시작할 때 기존 legs, plan, planId, comparison을 비워 이전 선택이 새 경로에 섞이지 않게 합니다.
async function home(cities) {
    const form = $('#trip-form');
    const fields = form.elements;
    const previous = read('trip');
    // 홈 입력창에 표시할 도시 이름을 ID로 찾습니다.
    const cityName = id => cities.find(city => city.id === id)?.name ?? id;
    fields.start.value = previous?.start ?? dateAfter(14);
    fields.end.value = previous?.end ?? dateAfter(17);
    fields.end.min = fields.start.value;
    // 시작일을 바꾸면 종료일의 최솟값을 맞춰 잘못된 기간이 제출되는 것을 막습니다.
    fields.start.addEventListener('change', () => {
        fields.end.min = fields.start.value;
        if (fields.end.value < fields.start.value) fields.end.value = fields.start.value;
    });
    $('#preferred-mode').innerHTML = modeOptions(previous?.preferredMode ?? '', [['', '선호 없음']]);
    $('#city-list').innerHTML = cities.map(city => '<option value="' + e(city.name) + '"></option>').join('');
    if (previous) {
        fields.origin.value = cityName(previous.origin);
        fields.destination.value = cityName(previous.destination);
        fields.adults.value = previous.party.adults;
        fields.children.value = previous.party.children;
        fields.budget.value = previous.budget ?? '';
        fields.type.value = previous.type;
    }
    $('#catalog').innerHTML = cities.map(city => '<button type="button" class="city-tile" data-city="' + e(city.id)
        + '"><strong>' + e(city.name) + '</strong><span class="tile-plus" aria-hidden="true">+</span></button>').join('');
    // 홈의 추천 도시 버튼을 누르면 목적지 입력란에 해당 도시를 채웁니다.
    $$('.city-tile').forEach(tile => tile.addEventListener('click', () => {
        fields.destination.value = cityName(tile.dataset.city);
        fields.destination.focus();
    }));
    // 제주 예제 버튼으로 날짜, 인원과 예산을 검색 폼에 채웁니다.
    $('#example').addEventListener('click', () => {
        Object.entries({origin: '서울', destination: '제주', start: '2027-03-10', end: '2027-03-12',
            adults: '2', children: '0', budget: '450000', preferredMode: '', type: 'COUPLE'})
            .forEach(([key, value]) => fields[key].value = value);
        fields.end.min = fields.start.value;
        fields.origin.focus();
    });
    // 검색 폼을 경로 API에 보내고 승인된 여행 조건을 다음 화면에 전달합니다.
    form.addEventListener('submit', async event => {
        event.preventDefault();
        const button = $('.search-submit');
        button.disabled = true;
        status('여행 조건을 확인하는 중입니다.');
        const trip = {origin: fields.origin.value, destination: fields.destination.value,
            start: fields.start.value, end: fields.end.value, party: {adults: +fields.adults.value, children: +fields.children.value},
            budget: fields.budget.value ? +fields.budget.value : null, preferredMode: fields.preferredMode.value || null, type: fields.type.value};
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
    const current = document.body.dataset.page;
    if (current === 'home') await home(cities);
    else if (loaders[current]) await (await loaders[current]()).init(cities);
} catch (error) { showError(error); }
