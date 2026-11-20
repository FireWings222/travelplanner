import {$, $$, api, read, write, e, panel, empty, cityOptions, modeOptions, modes, platforms, inclusions, inclusionTags,
    number, priceType, status, showError, showTrip, safeUrl, fareFields, readFare, dateAfter, busy, nearby} from './common.js';

const time = value => e(value?.slice(0, 5));

export async function init(cities) {
    const trip = read('trip');
    showTrip(trip, cities);
    const search = $('#tour-search');
    const form = $('#tour-form');
    const selected = trip?.destination ?? 'kyoto';
    search.elements.destination.innerHTML = cityOptions(cities, selected);
    search.elements.date.value = trip?.start ?? '';
    if (trip) {
        search.elements.date.min = trip.start;
        search.elements.date.max = trip.end;
    }
    form.elements.destination.innerHTML = cityOptions(cities, selected);
    form.elements.mode.innerHTML = modeOptions('CITY_BUS');
    const sights = await api('/attractions');
    const nameOf = id => sights.find(sight => sight.id === id)?.name ?? id;
    const selectedStops = [];
    let shortlist = read('tourShortlist') ?? [];
    let nextPrice = 0;
    let latestSearch = 0;

    function renderStops() {
        selectedStops.length = 0;
        const city = form.elements.destination.value;
        $('#tour-stops').innerHTML = sights.filter(sight => nearby(sight.destinationId, city))
            .map(sight => '<label><input type="checkbox" value="' + e(sight.id) + '"> ' + e(sight.name) + '</label>').join('');
        $('#tour-stop-order').textContent = '체크한 순서로 방문 장소를 등록합니다.';
        $$('#tour-stops input').forEach(input => input.onchange = () => {
            if (input.checked) selectedStops.push(input.value);
            else selectedStops.splice(selectedStops.indexOf(input.value), 1);
            $('#tour-stop-order').textContent = selectedStops.map(nameOf).join(' → ') || '체크한 순서로 방문 장소를 등록합니다.';
        });
    }

    function addPrice(from, to) {
        const index = nextPrice++;
        const node = document.createElement('div');
        node.className = 'price-period';
        node.dataset.index = index;
        node.innerHTML = '<div class="date-pair"><label>시작일<input type="date" name="from" value="' + e(from)
            + '" required></label><label>종료일<input type="date" name="to" value="' + e(to)
            + '" required></label></div>' + fareFields(null, 'price-' + index + '-', true)
            + '<button type="button" class="danger">삭제</button>';
        $('button', node).onclick = () => {
            if ($$('.price-period').length > 1) node.remove();
        };
        $('#tour-prices').append(node);
    }
    renderStops();
    form.elements.destination.onchange = renderStops;
    addPrice(trip?.start ?? dateAfter(0), trip?.end ?? dateAfter(30));
    $('#add-price').onclick = () => addPrice('', '');
    $('#tour-extras').innerHTML = '<fieldset><legend>기타 필수 비용</legend><p class="muted">없으면 0원을 입력하세요. 빈 가격은 미확인으로 표시됩니다.</p>'
        + fareFields(null, 'extras-') + '</fieldset><fieldset id="excluded-transport" hidden><legend>불포함 관광지 이동비</legend>'
        + fareFields(null, 'transport-') + '</fieldset>';
    form.elements.transportIncluded.onchange = () => {
        $('#excluded-transport').hidden = form.elements.transportIncluded.checked;
    };

    function compareTour(tour) {
        write('comparison', {tourId: tour.id, date: tour.date,
            party: trip?.party ?? {adults: 1, children: 0}, lodgingCity: trip?.destination ?? null});
        location.assign('/compare?tour=' + encodeURIComponent(tour.id));
    }

    function renderShortlist() {
        const root = $('#tour-shortlist');
        root.innerHTML = shortlist.length ? panel('<h2>비교할 투어</h2>' + shortlist.map(tour =>
            '<div class="saved-row"><div><strong>' + e(tour.name) + '</strong><p class="muted">' + e(tour.date)
            + '</p></div><div class="actions"><button class="primary" type="button" data-compare="' + e(tour.id)
            + '">비교</button><button class="soft" type="button" data-unpin="' + e(tour.id) + '">목록에서 제거</button></div></div>')
            .join('')) : '';
        $$('[data-compare]', root).forEach(button => button.onclick = () =>
            compareTour(shortlist.find(tour => tour.id === button.dataset.compare)));
        $$('[data-unpin]', root).forEach(button => button.onclick = () => {
            shortlist = shortlist.filter(tour => tour.id !== button.dataset.unpin);
            write('tourShortlist', shortlist);
            renderShortlist();
        });
    }

    function tourCard(tour, date) {
        const detail = tour.details;
        const applied = date ? detail.prices.find(price => price.from <= date && date <= price.to) : detail.prices[0];
        const chosenDate = date || (trip?.start >= applied?.from && trip?.start <= applied?.to ? trip.start : applied?.from);
        const image = safeUrl(detail.imageUrl) || (tour.id === 'kyoto-day' ? '/images/kyoto.png' : '');
        const booking = safeUrl(detail.bookingUrl);
        return '<article class="panel tour-card"><div class="tour-image">'
            + (image ? '<img src="' + e(image) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '')
            + '</div><div class="tour-body"><div class="tour-top"><div><span class="eyebrow">' + e(platforms[detail.platform])
            + '</span><h2>' + e(detail.name) + '</h2></div><div class="tour-price">'
            + (applied ? '<small>' + (date ? e(date) : '첫 가격 기간') + ' · 성인 1인</small><strong>' + number(applied.fare.adult)
                + ' ' + e(applied.fare.currency) + '</strong><small>아동 1인 '
                + (applied.fare.child == null ? '미확인' : number(applied.fare.child) + ' ' + e(applied.fare.currency)) + '</small>' : '가격 미확인')
            + '</div></div><p class="muted">' + time(detail.departure) + '–' + time(detail.arrival)
            + ' · ' + e(modes[detail.mode]) + ' · 집합 ' + e(detail.meetingPlace) + ' · 종료 ' + e(detail.endPlace) + '</p>'
            + '<p>' + detail.stops.map(id => e(nameOf(id))).join(' → ') + '</p>' + inclusionTags(detail)
            + '<details><summary>이용 기간 및 취소 정책</summary><ul class="cost-list">'
            + detail.prices.map(price => '<li><span>' + e(price.from) + ' – ' + e(price.to) + '</span><strong>'
                + number(price.fare.adult) + ' ' + e(price.fare.currency) + '</strong></li>').join('')
            + '</ul><p>' + e(detail.cancellation || '취소 정책 미입력') + '</p></details>'
            + '<div>' + priceType(tour.source, tour.checkedAt) + '</div><div class="actions">'
            + (booking ? '<a class="soft" href="' + e(booking) + '" target="_blank" rel="noopener noreferrer">예약 페이지</a>' : '')
            + '<button type="button" class="soft" data-pin="' + e(tour.id) + '" data-date="' + e(chosenDate) + '">비교 목록에 추가</button>'
            + '<button type="button" class="primary" data-tour="' + e(tour.id) + '" data-date="' + e(chosenDate) + '">비용 비교</button>'
            + '</div></div></article>';
    }

    async function find() {
        const token = ++latestSearch;
        const params = new URLSearchParams({destination: search.elements.destination.value});
        const date = search.elements.date.value;
        if (date) params.set('date', date);
        if (search.elements.platform.value) params.set('platform', search.elements.platform.value);
        status('투어를 검색하는 중입니다.');
        const result = await api('/tours/search?' + params);
        if (token !== latestSearch) return;
        status(result.notices.join(' '));
        $('#tour-list').innerHTML = result.tours.length ? result.tours.map(tour => tourCard(tour, date)).join('')
            : empty('조건에 맞는 투어가 없습니다. 날짜 필터를 지우거나 상품을 직접 등록할 수 있습니다.');
        $$('[data-tour]').forEach(button => button.onclick = () => {
            compareTour({id: button.dataset.tour, date: button.dataset.date || dateAfter(0)});
        });
        $$('[data-pin]').forEach(button => button.onclick = () => {
            const id = button.dataset.pin;
            const tour = result.tours.find(tour => tour.id === id);
            shortlist = [...shortlist.filter(item => item.id !== id), {
                id, name: tour.details.name, date: button.dataset.date || dateAfter(0)
            }];
            write('tourShortlist', shortlist);
            renderShortlist();
            status('비교 목록에 추가했습니다 · ' + tour.details.name);
        });
    }

    search.onsubmit = event => { event.preventDefault(); find().catch(showError); };
    form.onsubmit = event => {
        event.preventDefault();
        const fields = form.elements;
        const tour = Object.fromEntries(['destination', 'platform', 'name', 'bookingUrl', 'imageUrl',
            'departure', 'arrival', 'meetingPlace', 'endPlace', 'cancellation', 'mode'].map(key => [key, fields[key].value]));
        Object.keys(inclusions).forEach(key => tour[key] = fields[key].checked);
        tour.stops = [...selectedStops];
        tour.prices = $$('.price-period').map(row => ({
            from: $('[name="from"]', row).value, to: $('[name="to"]', row).value,
            fare: readFare(row, 'price-' + row.dataset.index + '-')
        }));
        tour.requiredExtras = readFare(form, 'extras-');
        tour.excludedTransport = tour.transportIncluded ? null : readFare(form, 'transport-');
        busy(form, async () => {
            const saved = await api('/tours', tour);
            status('등록했습니다 · ' + saved.details.name, 'info', '#register-status');
            search.elements.destination.value = saved.details.destination;
            search.elements.date.value = '';
            search.elements.platform.value = '';
            await find();
        }).catch(error => status(error.message, 'error', '#register-status'));
    };
    renderShortlist();
    await find();
}
