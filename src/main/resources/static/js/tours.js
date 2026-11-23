import {$, $$, api, read, write, e, panel, empty, cityOptions, modeOptions, modes, platforms, inclusions, inclusionTags,
    won, priceType, status, showError, showTrip, safeUrl, fareFields, readFare, dateAfter, busy, minutes,
    tourReferenceLinks} from './common.js';

// 투어 출발·도착 시각을 카드에 표시할 시·분 형식으로 바꿉니다.
const time = value => e(value?.slice(0, 5));

// 투어 화면에서 검색, 직접 등록, 비교 목록의 초기 상태와 이벤트를 설정합니다.
// 도시·관광지 목록으로 투어 검색과 등록 폼을 준비합니다. 선택한 투어를 비용 비교로 넘길 때는 현재 탭의 여행 날짜와 인원을 사용합니다.
export async function init(cities) {
    const trip = read('trip');
    showTrip(trip, cities);
    const search = $('#tour-search');
    const form = $('#tour-form');
    const selected = trip?.destination ?? 'jeju';
    search.elements.destination.innerHTML = cityOptions(cities, selected);
    search.elements.date.value = trip?.start ?? '';
    if (trip) {
        search.elements.date.min = trip.start;
        search.elements.date.max = trip.end;
    }
    form.elements.destination.innerHTML = cityOptions(cities, selected);
    form.elements.mode.innerHTML = modeOptions('CITY_BUS');
    const sights = await api('/attractions');
    // 카드와 비교 목록에 표시할 관광지 이름을 ID로 찾습니다.
    const nameOf = id => sights.find(sight => sight.id === id)?.name ?? id;
    const selectedStops = [];
    let shortlist = (read('tourShortlist') ?? []).filter(tour => tour.id !== 'jeju-day');
    let nextPrice = 0;
    let latestSearch = 0;

    // 직접 등록 폼의 선택 도시에 맞는 관광지 목록을 그리고 선택 순서를 관리합니다.
    // 등록 폼에서 선택한 도시의 관광지만 보여줍니다. 체크 상태에서 구성한 순서를 등록 요청의 stops 배열로 사용합니다.
    function renderStops() {
        selectedStops.length = 0;
        const city = form.elements.destination.value;
        $('#tour-stops').innerHTML = sights.filter(sight => sight.destinationId === city)
            .map(sight => '<label><input type="checkbox" value="' + e(sight.id) + '"> ' + e(sight.name) + '</label>').join('');
        $('#tour-stop-order').textContent = '체크한 순서로 방문 장소를 등록합니다.';
        // 투어에 포함할 관광지를 체크한 순서대로 보관합니다.
        $$('#tour-stops input').forEach(input => input.onchange = () => {
            if (input.checked) selectedStops.push(input.value);
            else selectedStops.splice(selectedStops.indexOf(input.value), 1);
            $('#tour-stop-order').textContent = selectedStops.map(nameOf).join(' → ') || '체크한 순서로 방문 장소를 등록합니다.';
        });
    }

    // 투어 등록 폼에 적용 시작일·종료일과 성인·아동 요금을 입력할 행을 추가합니다.
    // 동적으로 추가한 각 가격 행에 고유한 입력 이름 접두사를 붙입니다. 제출할 때 각 행의 가격을 readFare가 읽습니다.
    function addPrice(from, to) {
        const index = nextPrice++;
        const node = document.createElement('div');
        node.className = 'price-period';
        node.dataset.index = index;
        node.innerHTML = '<div class="date-pair"><label>시작일<input type="date" name="from" value="' + e(from)
            + '" required></label><label>종료일<input type="date" name="to" value="' + e(to)
            + '" required></label></div>' + fareFields(null, 'price-' + index + '-', true)
            + '<button type="button" class="danger">삭제</button>';
        // 등록 폼에서 삭제할 가격 행을 제거하되 최소 한 행을 유지합니다.
        $('button', node).onclick = () => {
            if ($$('.price-period').length > 1) node.remove();
        };
        $('#tour-prices').append(node);
    }
    renderStops();
    form.elements.destination.onchange = renderStops;
    addPrice(trip?.start ?? dateAfter(0), trip?.end ?? dateAfter(30));
    // 사용자가 기간별 요금을 입력할 빈 행을 추가합니다.
    $('#add-price').onclick = () => addPrice('', '');
    $('#tour-extras').innerHTML = '<fieldset><legend>기타 필수 비용</legend><p class="muted">없으면 0원을 입력하세요. 빈 가격은 미확인으로 표시됩니다.</p>'
        + fareFields(null, 'extras-') + '</fieldset><fieldset id="excluded-transport" hidden><legend>불포함 관광지 이동비</legend>'
        + fareFields(null, 'transport-') + '</fieldset>';
    // 투어 교통비 포함 여부에 따라 별도 교통비 입력란을 표시합니다.
    form.elements.transportIncluded.onchange = () => {
        $('#excluded-transport').hidden = form.elements.transportIncluded.checked;
    };

    // 선택한 투어와 여행 인원을 세션에 기록하고 비용 비교 화면을 엽니다.
    // 투어 ID와 날짜를 세션의 comparison에 저장해 compare.js가 비교 API 요청을 만들 수 있게 합니다.
    function compareTour(tour) {
        write('comparison', {tourId: tour.id, date: tour.date,
            party: trip?.party ?? {adults: tour.estimated ? 10 : 1, children: 0}});
        location.assign('/compare?tour=' + encodeURIComponent(tour.id));
    }

    // 현재 탭에 담아 둔 투어 목록을 그려 비용 비교와 목록 삭제 버튼을 연결합니다.
    function renderShortlist() {
        const root = $('#tour-shortlist');
        root.innerHTML = shortlist.length ? panel('<h2>비교할 투어</h2>' + shortlist.map(tour =>
            '<div class="saved-row"><div><strong>' + e(tour.name) + '</strong><p class="muted">' + e(tour.date)
            + '</p></div><div class="actions"><button class="primary" type="button" data-compare="' + e(tour.id)
            + '">비교</button><button class="soft" type="button" data-unpin="' + e(tour.id) + '">목록에서 제거</button></div></div>')
            .join('')) : '';
        // 비교 목록에서 선택한 투어의 비용 비교 화면을 엽니다.
        $$('[data-compare]', root).forEach(button => button.onclick = () =>
            compareTour(shortlist.find(tour => tour.id === button.dataset.compare)));
        // 비교 목록에서만 투어를 빼고 현재 탭의 목록을 갱신합니다.
        $$('[data-unpin]', root).forEach(button => button.onclick = () => {
            shortlist = shortlist.filter(tour => tour.id !== button.dataset.unpin);
            write('tourShortlist', shortlist);
            renderShortlist();
        });
    }

    // 검색 결과 상품에 날짜별 요금, 방문 장소, 포함 항목, 비교 버튼을 표시합니다.
    function tourCard(tour, date) {
        const detail = tour.details;
        const reference = tour.reference;
        if (reference) return catalogCard(tour, date || trip?.start || dateAfter(0));
        const applied = date ? detail.prices.find(price => price.from <= date && date <= price.to) : detail.prices[0];
        const chosenDate = date || (trip?.start >= applied?.from && trip?.start <= applied?.to ? trip.start : applied?.from);
        const image = safeUrl(detail.imageUrl);
        const booking = safeUrl(detail.bookingUrl);
        return '<article class="panel tour-card"><div class="tour-image">'
            + (image ? '<img src="' + e(image) + '" alt="" loading="lazy" referrerpolicy="no-referrer">' : '')
            + '</div><div class="tour-body"><div class="tour-top"><div><span class="eyebrow">' + e(platforms[detail.platform])
            + '</span><h2>' + e(detail.name) + '</h2></div><div class="tour-price">'
            + (applied ? '<small>' + (date ? e(date) : '첫 가격 기간') + ' · 성인 1인</small><strong>' + won(applied.fare.adult) + '</strong><small>아동 1인 '
                + won(applied.fare.child) + '</small>' : '가격 미확인')
            + '</div></div><p class="muted">' + time(detail.departure) + '–' + time(detail.arrival)
            + ' · ' + e(modes[detail.mode]) + ' · 집합 ' + e(detail.meetingPlace) + ' · 종료 ' + e(detail.endPlace) + '</p>'
            + '<p>' + detail.stops.map(id => e(nameOf(id))).join(' → ') + '</p>' + inclusionTags(detail)
            + '<details><summary>이용 기간 및 취소 정책</summary><ul class="cost-list">'
            + detail.prices.map(price => '<li><span>' + e(price.from) + ' – ' + e(price.to) + '</span><strong>'
                + won(price.fare.adult) + '</strong></li>').join('')
            + '</ul><p>' + e(detail.cancellation || '취소 정책 미입력') + '</p></details>'
            + '<div>' + priceType(tour.source, tour.checkedAt) + '</div><div class="actions">'
            + (booking ? '<a class="soft" href="' + e(booking) + '" target="_blank" rel="noopener noreferrer">예약 페이지</a>' : '')
            + '<button type="button" class="soft" data-pin="' + e(tour.id) + '" data-date="' + e(chosenDate) + '">비교 목록에 추가</button>'
            + '<button type="button" class="primary" data-tour="' + e(tour.id) + '" data-date="' + e(chosenDate) + '">비용 비교</button>'
            + '</div></div></article>';
    }

    // 카탈로그는 날짜별 판매 여부 대신 확인한 공개가 또는 10인 기준 추정가를 보여 줍니다.
    // date는 비교 화면으로 넘길 여행일이고, 6인과 20인 금액은 서버가 계산한 값을 그대로 표시합니다.
    function catalogCard(tour, date) {
        const {details, reference} = tour;
        const {estimate, discount} = reference;
        const amount = details.prices[0].fare.adult;
        const label = estimate ? '10인 기준 · 1인' : '성인 1인';
        const groupPrices = estimate ? '<p class="muted">6인 기준 1인 ' + won(estimate.minimumPartyPrice)
            + ' · 20인 기준 1인 ' + won(estimate.maximumPartyPrice) + '</p>' : '';
        const transport = details.mode === 'WALK' ? '도보' : '투어버스';
        const schedule = reference.durationMinutes == null ? transport : transport + ' · 약 ' + minutes(reference.durationMinutes);
        return '<article class="panel tour-card catalog-tour"><div class="tour-body"><div class="tour-top"><div>'
            + (details.platform === 'OTHER' ? '' : '<span class="eyebrow">' + e(platforms[details.platform]) + '</span>')
            + '<h2>' + e(details.name) + '</h2></div><div class="tour-price"><small>' + label + '</small><strong>'
            + (amount === 0 ? '무료' : won(amount)) + (reference.fromPrice ? '부터' : '') + '</strong></div></div>'
            + '<p class="muted">' + e(schedule) + '</p><p>' + details.stops.map(id => e(nameOf(id))).join(' → ') + '</p>'
            + groupPrices + (discount ? '<p class="muted">' + e(discount.minimumPeople) + '인 이상 성인 1인 '
                + won(discount.adultKrw) + '</p>' : '')
            + (reference.notes ? '<p>' + e(reference.notes) + '</p>' : '')
            + '<div>' + priceType(tour.source, tour.checkedAt) + '</div><div class="actions">'
            + tourReferenceLinks(reference)
            + '<button type="button" class="soft" data-pin="' + e(tour.id) + '" data-date="' + e(date) + '">비교 목록에 추가</button>'
            + '<button type="button" class="primary" data-tour="' + e(tour.id) + '" data-date="' + e(date) + '">비용 비교</button>'
            + '</div></div></article>';
    }

    // 도시·날짜·플랫폼으로 투어 API를 조회하고 결과 카드와 비교 동작을 연결합니다.
    // 검색 폼의 필수 도시와 선택 날짜·플랫폼을 쿼리로 전달합니다. 서버가 보내는 notices도 화면에 표시해 일부 제공자 실패를 알립니다.
    async function find() {
        const token = ++latestSearch;
        const params = new URLSearchParams({destination: search.elements.destination.value});
        const date = search.elements.date.value;
        if (date) params.set('date', date);
        if (search.elements.platform.value) params.set('platform', search.elements.platform.value);
        status('투어를 검색하는 중입니다.');
        const result = await api('/tours/search?' + params);
        // 사용자가 검색 조건을 다시 제출하면 늦게 도착한 이전 요청의 목록과 안내를 버립니다.
        if (token !== latestSearch) return;
        status(result.notices.join(' '));
        $('#tour-list').innerHTML = result.tours.length ? result.tours.map(tour => tourCard(tour, date)).join('')
            : empty('조건에 맞는 투어가 없습니다. 날짜 필터를 지우거나 상품을 직접 등록할 수 있습니다.');
        // 검색 카드에서 선택한 상품과 날짜를 비용 비교 화면으로 넘깁니다.
        $$('[data-tour]').forEach(button => button.onclick = () => {
            const id = button.dataset.tour;
            compareTour({id, date: button.dataset.date || dateAfter(0),
                estimated: !!result.tours.find(tour => tour.id === id)?.reference?.estimate});
        });
        // 검색 카드의 상품과 날짜를 비교 목록에 추가합니다.
        $$('[data-pin]').forEach(button => button.onclick = () => {
            const id = button.dataset.pin;
            const tour = result.tours.find(tour => tour.id === id);
            shortlist = [...shortlist.filter(item => item.id !== id), {
                id, name: tour.details.name, date: button.dataset.date || dateAfter(0), estimated: !!tour.reference?.estimate
            }];
            write('tourShortlist', shortlist);
            renderShortlist();
            status('비교 목록에 추가했습니다 · ' + tour.details.name);
        });
    }

    // 검색 폼의 브라우저 이동을 막고 투어 목록을 다시 조회합니다.
    search.onsubmit = event => { event.preventDefault(); find().catch(showError); };
    // 직접 등록 폼의 관광지와 가격 정보를 모아 투어 등록 API에 전달합니다.
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
