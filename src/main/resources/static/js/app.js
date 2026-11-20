import {$, $$, api, read, write, e, modeOptions, dateAfter, status, showError} from './common.js';

const loaders = {
    routes: () => import('./routes.js'),
    plan: () => import('./plan.js'),
    tours: () => import('./tours.js'),
    compare: () => import('./compare.js')
};

async function home(cities) {
    const form = $('#trip-form');
    const fields = form.elements;
    const previous = read('trip');
    const cityName = id => cities.find(city => city.id === id)?.name ?? id;
    fields.start.value = previous?.start ?? dateAfter(14);
    fields.end.value = previous?.end ?? dateAfter(17);
    fields.end.min = fields.start.value;
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
        + '"><span>' + (city.country === 'KR' ? '대한민국' : '일본') + '</span><strong>'
        + e(city.name.replace(/^일본 /, '')) + '</strong><span class="tile-plus" aria-hidden="true">+</span></button>').join('');
    $$('.city-tile').forEach(tile => tile.addEventListener('click', () => {
        fields.destination.value = cityName(tile.dataset.city);
        fields.destination.focus();
    }));
    $('#example').addEventListener('click', () => {
        Object.entries({origin: '서울', destination: '일본 오사카', start: '2027-03-10', end: '2027-03-13',
            adults: '2', children: '0', budget: '1500000', preferredMode: '', type: 'COUPLE'})
            .forEach(([key, value]) => fields[key].value = value);
        fields.end.min = fields.start.value;
        fields.origin.focus();
    });
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
    const current = document.body.dataset.page;
    if (current === 'home') await home(cities);
    else if (loaders[current]) await (await loaders[current]()).init(cities);
} catch (error) { showError(error); }
