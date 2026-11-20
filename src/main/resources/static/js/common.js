export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
export const e = value => String(value ?? '').replace(/[&<>"']/g, char =>
    ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
export const modes = {
    CAR: '자가용', EXPRESS_BUS: '고속버스', INTERCITY_BUS: '시외버스', KTX: 'KTX', SRT: 'SRT',
    TRAIN: '기차', SUBWAY: '지하철', CITY_BUS: '시내버스', TAXI: '택시', WALK: '도보',
    FLIGHT: '항공편', AIRPORT_BUS: '공항버스', AIRPORT_RAIL: '공항철도', RENTAL_CAR: '렌터카'
};
export const platforms = {KLOOK: '클룩', MYREALTRIP: '마이리얼트립', NOL: 'NOL / 야놀자', OTHER: '기타'};
export const inclusions = {transportIncluded: '관광지 간 이동', admissionIncluded: '입장료', mealIncluded: '식사', guideIncluded: '가이드'};
export const inclusionTags = detail => '<div class="tags">' + Object.entries(inclusions).map(([key, label]) =>
    '<span class="tag ' + (detail[key] ? 'included' : '') + '">' + label + (detail[key] ? ' 포함' : ' 불포함') + '</span>').join('') + '</div>';
export const sources = {SAMPLE: '테스트용 가상 가격', MANUAL: '사용자 입력 요금', PROVIDER: '제공업체 가격'};
export const currencies = ['KRW', 'JPY', 'USD', 'EUR'];
export const number = value => new Intl.NumberFormat('ko-KR', {maximumFractionDigits: 2}).format(value);
export const won = value => value == null ? '미확인' : number(value) + '원';
export function minutes(value) {
    if (value == null) return '미확인';
    return (value >= 60 ? Math.floor(value / 60) + '시간 ' : '') + (value % 60) + '분';
}
export function checked(value) {
    return value ? new Intl.DateTimeFormat('ko-KR', {
        dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul'
    }).format(new Date(value)) : '미확인';
}
export function dateAfter(days, base = new Date()) {
    const shifted = new Date(base.getTime());
    shifted.setDate(shifted.getDate() + days);
    return [shifted.getFullYear(), String(shifted.getMonth() + 1).padStart(2, '0'),
        String(shifted.getDate()).padStart(2, '0')].join('-');
}
export function options(entries, selected) {
    return entries.map(([value, label]) =>
        '<option value="' + e(value) + '"' + (value === selected ? ' selected' : '') + '>' + e(label) + '</option>').join('');
}
export function cityOptions(cities, selected) {
    return options(cities.map(city => [city.id, city.name]), selected);
}
export function nearby(city, destination) {
    const kansai = ['osaka', 'kyoto'];
    return city === destination || (kansai.includes(city) && kansai.includes(destination));
}
export const modeOptions = (value, first = []) => options([...first, ...Object.entries(modes)], value);
export function safeUrl(value) {
    if (!value) return '';
    try {
        const url = new URL(value);
        return url.protocol === 'https:' && !url.username && !url.password ? url.href : '';
    } catch { return ''; }
}

export async function api(path, body, method = body === undefined ? 'GET' : 'POST') {
    const response = await fetch('/api' + path, {
        method, headers: body === undefined ? {} : {'Content-Type': 'application/json'},
        body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        const fields = data?.fields ? ' (' + Object.keys(data.fields).join(', ') + ')' : '';
        throw new Error((data?.detail ?? '요청을 완료하지 못했습니다. 다시 시도해 주세요.') + fields);
    }
    return data;
}
export function read(key) {
    try { return JSON.parse(sessionStorage.getItem('travelplanner.' + key)); }
    catch { return null; }
}
export function write(key, value) { sessionStorage.setItem('travelplanner.' + key, JSON.stringify(value)); }
export function status(text = '', kind = 'info', target = '#status') {
    const node = $(target);
    if (node) { node.className = 'status ' + kind; node.textContent = text; }
}
export const showError = error => status(error.message, 'error');
export function panel(content, extra = '') { return '<section class="panel section ' + extra + '">' + content + '</section>'; }
export const empty = message => panel('<p class="empty">' + e(message) + '</p>');
export function needsTrip(target) {
    $(target).innerHTML = panel('<h2>여행 조건을 먼저 선택해 주세요</h2><p class="muted">출발지, 목적지와 여행 날짜가 필요합니다.</p><a class="primary" href="/">여행 검색</a>');
}
export function showTrip(trip, cities) {
    const node = $('#trip-summary');
    if (!node || !trip) return;
    const city = id => cities.find(city => city.id === id)?.name ?? id;
    node.hidden = false;
    node.innerHTML = '<strong>' + e(city(trip.origin)) + ' <span aria-hidden="true">→</span> ' + e(city(trip.destination))
        + '</strong><span>' + e(trip.start) + ' – ' + e(trip.end) + '</span><span>성인 ' + trip.party.adults
        + '명 · 아동 ' + trip.party.children + '명</span>' + (trip.budget == null ? '' : '<span>예산 ' + won(trip.budget) + '</span>');
}
export const priceType = (source, checkedAt) => '<span class="tag ' + (source === 'SAMPLE' ? 'sample' : '') + '">'
    + e(sources[source]) + '</span>' + (checkedAt ? '<span class="meta">확인 ' + e(checked(checkedAt)) + ' KST</span>' : '');
export function missing(cost) {
    return cost.missing.length ? '<p class="notice">합산에서 제외된 항목 · ' + e(cost.missing.join(', ')) + '</p>' : '';
}
export function moneyDetails(cost) {
    return '평균 1인 ' + won(cost.perPersonKrw) + ' · 성인 1인 ' + won(cost.adultPerPersonKrw)
        + (cost.childPerPersonKrw == null ? '' : ' · 아동 1인 ' + won(cost.childPerPersonKrw))
        + (Number(cost.sharedKrw) ? ' · 공동 비용 ' + won(cost.sharedKrw) + '을 인원수로 균등 배분' : '');
}
function currencyOriginal(fare) {
    if (fare.shared != null) return '공동 ' + number(fare.shared) + ' ' + fare.currency;
    return '성인 ' + (fare.adult == null ? '미확인' : number(fare.adult) + ' ' + fare.currency)
        + ' · 아동 ' + (fare.child == null ? '미확인' : number(fare.child) + ' ' + fare.currency);
}
export function itemRow(item) {
    const fx = item.rate && item.original.currency !== 'KRW'
        ? ' · 1 ' + e(item.original.currency) + ' = ' + won(item.rate.krwPerUnit)
            + ' (' + e(item.rate.date) + ', ' + e(sources[item.rate.source]) + ')' : '';
    const original = item.original ? ' · ' + e(currencyOriginal(item.original)) : '';
    const amount = item.missing.length && Number(item.totalKrw) === 0 ? '미확인' : won(item.totalKrw);
    return '<li><div>' + e(item.label) + '<small>' + e(sources[item.source]) + original + fx + '</small></div><strong>'
        + amount + '</strong></li>';
}

export function fareFields(fare, prefix, requiredAdult = false) {
    const amount = (field, label, required = false) => '<label>' + label + '<input type="number" min="0" max="999999999.99" step="0.01" name="'
        + prefix + field + '" value="' + e(fare?.[field]) + '"' + (required ? ' required' : '') + '></label>';
    return '<div class="fare-fields"><label>통화<select name="' + prefix + 'currency">'
        + options(currencies.map(value => [value, value]), fare?.currency ?? 'KRW') + '</select></label>'
        + amount('adult', '성인 1인', requiredAdult) + amount('child', '아동 1인')
        + (requiredAdult ? '' : amount('shared', '일행 공동')) + '</div>';
}
export function readFare(root, prefix) {
    const field = name => $('[name="' + prefix + name + '"]', root).value;
    const amount = name => field(name) === '' ? null : Number(field(name));
    const fare = {currency: field('currency'), adult: amount('adult'), child: amount('child'), shared:
        $('[name="' + prefix + 'shared"]', root) ? amount('shared') : null};
    return [fare.adult, fare.child, fare.shared].some(value => value != null) ? fare : null;
}
export function legCards(sections, choices = {}, day = '') {
    if (!sections.length) return '<p class="empty">이동 구간이 없습니다.</p>';
    return sections.map(section => {
        const chosen = section.chosen;
        const override = choices?.[section.key]?.override;
        return '<article class="leg"><div class="leg-head"><div><h3>' + e(chosen.from.name) + ' <span aria-hidden="true">→</span> '
            + e(chosen.to.name) + '</h3><p>' + number(chosen.kilometers) + 'km · ' + minutes(chosen.minutes) + ' · 환승 '
            + chosen.transfers + '회 · 일행 ' + won(section.cost.totalKrw) + '</p>'
            + priceType(chosen.source, chosen.checkedAt) + '</div><label>교통수단<select class="leg-mode" data-key="' + e(section.key)
            + '" data-day="' + e(day) + '">' + options(section.options.map(option => [option.mode,
                (modes[option.mode] ?? option.mode) + ' · ' + minutes(option.minutes)]), chosen.mode) + '</select></label></div>'
            + '<details class="fare-edit"' + (override ? ' open' : '') + '><summary>요금 직접 입력</summary>'
            + '<form class="leg-fare" data-key="' + e(section.key) + '" data-day="' + e(day) + '">'
            + '<p class="muted">성인·아동 1인 요금 또는 일행 공동 요금을 입력하세요. 값을 비우고 적용하면 샘플 요금으로 돌아갑니다.</p>'
            + fareFields(override, 'leg-') + '<button type="submit" class="soft">요금 적용</button></form></details>'
            + '<ul class="cost-list">' + itemRow(section.cost) + '</ul></article>';
    }).join('');
}
export function connectLegs(root, callback) {
    $$('.leg-mode', root).forEach(select => select.addEventListener('change', () =>
        callback(select.dataset.key, {mode: select.value, override: null}, select.dataset.day)));
    $$('.leg-fare', root).forEach(form => form.addEventListener('submit', event => {
        event.preventDefault();
        const select = $('.leg-mode', form.closest('.leg'));
        callback(form.dataset.key, {mode: select.value, override: readFare(form, 'leg-')}, form.dataset.day);
    }));
}
export const metric = (label, value) => '<div><span>' + e(label) + '</span><strong>' + e(value) + '</strong></div>';
export const metrics = (...items) => '<div class="metrics">' + items.join('') + '</div>';
export function busy(node, action) {
    const items = $$('input, select, button', node).filter(element => !element.disabled);
    items.forEach(item => item.disabled = true);
    return Promise.resolve().then(action).finally(() => items.forEach(item => item.disabled = false));
}
