// 화면 모듈에서 지정한 범위 안의 첫 번째 HTML 요소를 선택합니다.
export const $ = (selector, root = document) => root.querySelector(selector);
// 조건에 맞는 HTML 요소를 배열로 반환해 이벤트 연결과 반복 처리에 사용합니다.
export const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
// HTML 문자열에 들어가는 값의 특수 문자를 이스케이프합니다. 폼·표·카드의 동적 문구에 사용합니다.
export const e = value => String(value ?? '').replace(/[&<>"']/g, char =>
    ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
export const modes = {
    CAR: '자가용', EXPRESS_BUS: '고속버스', INTERCITY_BUS: '시외버스', KTX: 'KTX', SRT: 'SRT',
    TRAIN: '기차', SUBWAY: '지하철', CITY_BUS: '시내버스', TAXI: '택시', WALK: '도보',
    FLIGHT: '항공편', AIRPORT_BUS: '공항버스', AIRPORT_RAIL: '공항철도', RENTAL_CAR: '렌터카',
    BUS_SUBWAY: '버스 + 지하철'
};
export const platforms = {KLOOK: '클룩', MYREALTRIP: '마이리얼트립', NOL: 'NOL / 야놀자', OTHER: '기타'};
export const inclusions = {transportIncluded: '관광지 간 이동', admissionIncluded: '입장료', mealIncluded: '식사', guideIncluded: '가이드'};
// 투어 목록과 비용 비교 카드에 교통·입장료·식사·가이드 포함 여부를 표시합니다.
export const inclusionTags = detail => '<div class="tags">' + Object.entries(inclusions).map(([key, label]) =>
    '<span class="tag ' + (detail[key] ? 'included' : '') + '">' + label + (detail[key] ? ' 포함' : ' 불포함') + '</span>').join('') + '</div>';
export const sources = {SAMPLE: '테스트용 가상 가격', MANUAL: '사용자 입력 요금',
    PROVIDER: '제공업체 가격', ODSAY: 'ODsay 예상 요금·시간',
    KAKAO_MOBILITY: '카카오모빌리티 예상 정보', KAKAO_MAP: '카카오맵 검색 장소'};
// 비용과 거리를 한국어 숫자 형식으로 표시합니다.
export const number = value => new Intl.NumberFormat('ko-KR', {maximumFractionDigits: 2}).format(value);
// 합계·요금에 원 단위를 붙이고 데이터가 없으면 미확인으로 표시합니다.
export const won = value => value == null ? '미확인' : number(value) + '원';
// 서버가 반환한 분 단위 소요 시간을 화면의 시간·분 표기로 바꿉니다.
export function minutes(value) {
    if (value == null) return '미확인';
    return (value >= 60 ? Math.floor(value / 60) + '시간 ' : '') + (value % 60) + '분';
}
// 데이터 확인 시각을 한국 표준시로 바꿔 출처 표시에서 사용합니다.
export function checked(value) {
    return value ? new Intl.DateTimeFormat('ko-KR', {
        dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul'
    }).format(new Date(value)) : '미확인';
}
// 홈과 투어 등록 폼에 사용할 기본 날짜를 YYYY-MM-DD 형식으로 만듭니다.
export function dateAfter(days, base = new Date()) {
    const shifted = new Date(base.getTime());
    shifted.setDate(shifted.getDate() + days);
    return [shifted.getFullYear(), String(shifted.getMonth() + 1).padStart(2, '0'),
        String(shifted.getDate()).padStart(2, '0')].join('-');
}
// 값과 표시 이름 목록으로 드롭다운의 선택 항목 HTML을 만듭니다.
export function options(entries, selected) {
    return entries.map(([value, label]) =>
        '<option value="' + e(value) + '"' + (value === selected ? ' selected' : '') + '>' + e(label) + '</option>').join('');
}
// 도시 목록을 투어 검색·등록 폼에서 쓰는 드롭다운 옵션으로 바꿉니다.
export function cityOptions(cities, selected) {
    return options(cities.map(city => [city.id, city.name]), selected);
}
// 홈 검색 폼에서 사용할 교통수단 드롭다운 옵션을 만듭니다.
export const modeOptions = (value, first = []) => options([...first, ...Object.entries(modes)], value);
// 투어 카드의 이미지·예약 링크에 사용할 자격 증명 없는 HTTPS 주소인지 확인합니다.
export function safeUrl(value) {
    if (!value) return '';
    try {
        const url = new URL(value);
        return url.protocol === 'https:' && !url.username && !url.password ? url.href : '';
    } catch { return ''; }
}

// 화면의 요청을 Spring Boot의 /api로 보내고 JSON 응답 또는 서버 오류 메시지를 반환합니다.
// path는 /api 뒤에 붙일 주소입니다. body가 있는 기본 요청은 POST이고, 읽기 요청은 GET입니다. 수정과 삭제는 호출자가 메서드를 지정합니다.
// 204 응답은 null을 반환하고, 오류 응답은 서버의 detail과 필드 이름을 추출해 화면에서 표시할 Error로 전달합니다.
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
// 같은 브라우저 탭에서 이어서 쓸 여행 조건이나 편집 상태를 세션 저장소에서 읽습니다.
export function read(key) {
    try { return JSON.parse(sessionStorage.getItem('travelplanner.' + key)); }
    catch { return null; }
}
// 경로·일정·비용 비교 화면이 공유하는 현재 작업 상태를 세션 저장소에 기록합니다.
export function write(key, value) { sessionStorage.setItem('travelplanner.' + key, JSON.stringify(value)); }
// 화면의 상태 영역에 처리 중 메시지나 사용자 안내를 표시합니다.
export function status(text = '', kind = 'info', target = '#status') {
    const node = $(target);
    if (node) { node.className = 'status ' + kind; node.textContent = text; }
}
// 화면에서 잡은 오류의 메시지를 공통 상태 영역에 오류로 표시합니다.
export const showError = error => status(error.message, 'error');
// 여러 화면의 내용 블록에 공통 카드 레이아웃을 적용합니다.
export function panel(content, extra = '') { return '<section class="panel section ' + extra + '">' + content + '</section>'; }
// 검색 결과나 저장 항목이 없는 상황을 공통 카드로 표시합니다.
export const empty = message => panel('<p class="empty">' + e(message) + '</p>');
// 여행 조건 없이 경로·일정 화면을 열었을 때 홈 검색 링크를 표시합니다.
export function needsTrip(target) {
    $(target).innerHTML = panel('<h2>여행 조건을 먼저 선택해 주세요</h2><p class="muted">출발지, 목적지와 여행 날짜가 필요합니다.</p><a class="primary" href="/">여행 검색</a>');
}
// 경로·일정·비교 화면에 출발지, 목적지, 날짜, 인원, 예산 요약을 표시합니다.
export function showTrip(trip, cities) {
    const node = $('#trip-summary');
    if (!node || !trip) return;
    // 여행 조건에 저장된 도시 ID를 요약 화면에 보여줄 이름으로 바꿉니다.
    const city = id => cities.find(city => city.id === id)?.name ?? id;
    node.hidden = false;
    node.innerHTML = '<strong>' + e(trip.originPlace?.name ?? city(trip.origin))
        + ' <span aria-hidden="true">→</span> ' + e(trip.destinationPlace?.name ?? city(trip.destination))
        + '</strong><span>' + e(trip.start) + ' – ' + e(trip.end) + '</span><span>성인 ' + trip.party.adults
        + '명 · 아동 ' + trip.party.children + '명</span>' + (trip.budget == null ? '' : '<span>예산 ' + won(trip.budget) + '</span>');
}
// 구간이나 투어에 요금 출처 배지와 확인 시각을 표시합니다.
export const priceType = (source, checkedAt) => '<span class="tag ' + (source === 'SAMPLE' ? 'sample' : '') + '">'
    + e(sources[source]) + '</span>' + (checkedAt ? '<span class="meta">확인 ' + e(checked(checkedAt)) + ' KST</span>' : '');
// 서버가 합계에서 제외한 미확인 비용 항목을 목록으로 안내합니다.
export function missing(cost) {
    return cost.missing.length ? '<p class="notice">합산에서 제외된 항목 · ' + e(cost.missing.join(', ')) + '</p>' : '';
}
// 요금 합계 아래에 성인·아동·평균 1인 비용과 공동 비용 배분 정보를 표시합니다.
export function moneyDetails(cost) {
    return '평균 1인 ' + won(cost.perPersonKrw) + ' · 성인 1인 ' + won(cost.adultPerPersonKrw)
        + (cost.childPerPersonKrw == null ? '' : ' · 아동 1인 ' + won(cost.childPerPersonKrw))
        + (Number(cost.sharedKrw) ? ' · 공동 비용 ' + won(cost.sharedKrw) + '을 인원수로 균등 배분' : '');
}
// 비용 항목에 성인·아동 또는 공동 원화 요금을 표시합니다.
function fareDescription(fare) {
    if (fare.shared != null) return '공동 ' + won(fare.shared);
    return '성인 ' + won(fare.adult) + ' · 아동 ' + won(fare.child);
}
// 일정·투어·경로에서 사용할 비용 항목 한 줄을 출처, 원래 요금, 합계와 함께 그립니다.
export function itemRow(item) {
    const original = item.original ? ' · ' + e(fareDescription(item.original)) : '';
    const amount = item.missing.length && Number(item.totalKrw) === 0 ? '미확인' : won(item.totalKrw);
    return '<li><div>' + e(item.label) + '<small>' + e(sources[item.source]) + original + '</small></div><strong>'
        + amount + '</strong></li>';
}

// 구간, 투어, 비교 폼에서 공통으로 쓰는 금액 입력 필드를 만듭니다.
// prefix로 구간·가격 행마다 입력 필드 이름을 구분합니다. 투어 상품 가격 행에서는 성인 1인 요금을 필수로 받고 공동 요금 칸을 숨깁니다.
export function fareFields(fare, prefix, requiredAdult = false) {
    // 요금 입력 폼의 금액 필드 하나에 현재 금액과 필수 입력 여부를 적용합니다.
    const amount = (field, label, required = false) => '<label>' + label + '<input type="number" min="0" max="999999999.99" step="0.01" name="'
        + prefix + field + '" value="' + e(fare?.[field]) + '"' + (required ? ' required' : '') + '></label>';
    return '<div class="fare-fields">'
        + amount('adult', '성인 1인 (원)', requiredAdult) + amount('child', '아동 1인 (원)')
        + (requiredAdult ? '' : amount('shared', '일행 공동 (원)')) + '</div>';
}
// 사용자가 입력한 금액을 서버의 Fare 요청 형식으로 바꾸고 전부 비어 있으면 null을 반환합니다.
// fareFields와 같은 prefix를 사용해 성인·아동·공동 원화 요금을 읽습니다. 숫자 0은 유효한 무료 요금이고 빈 칸은 null로 남깁니다.
export function readFare(root, prefix) {
    // 해당 요금 편집 폼에서 접두사와 필드 이름으로 입력값을 찾습니다.
    const field = name => $('[name="' + prefix + name + '"]', root).value;
    // 폼 입력값이 비어 있으면 미확인으로, 입력되어 있으면 숫자 금액으로 해석합니다.
    const amount = name => field(name) === '' ? null : Number(field(name));
    const fare = {adult: amount('adult'), child: amount('child'), shared:
        $('[name="' + prefix + 'shared"]', root) ? amount('shared') : null};
    return [fare.adult, fare.child, fare.shared].some(value => value != null) ? fare : null;
}
// 서버의 구간별 견적을 교통수단·요금 편집·지도 버튼이 있는 카드로 그립니다.
// sections에는 서버가 선택한 교통수단, 전체 후보와 비용 항목이 들어 있습니다. day는 일정 화면이 수정 대상을 구분하는 날짜입니다.
// 노선 코드와 지도 키가 있으면 앱 안에서 지도를 열고, 좌표가 있는 구간은 카카오맵 길찾기로도 연결합니다.
export function legCards(sections, choices = {}, day = '') {
    if (!sections.length) return '<p class="empty">이동 구간이 없습니다.</p>';
    return sections.map(section => {
        const chosen = section.chosen;
        const selection = choices?.[section.key];
        const override = selection?.mode === chosen.mode ? selection.override : null;
        const mapLink = kakaoDirectionsUrl(chosen);
        const total = section.cost.missing.length && Number(section.cost.totalKrw) === 0
            ? '요금 미확인' : '일행 ' + won(section.cost.totalKrw) + (section.cost.missing.length ? ' (부분 합계)' : '');
        return '<article class="leg"><div class="leg-head"><div><h3>' + e(chosen.from.name) + ' <span aria-hidden="true">→</span> '
            + e(chosen.to.name) + '</h3><p>' + number(chosen.kilometers) + 'km · ' + minutes(chosen.minutes) + ' · 환승 '
            + chosen.transfers + '회 · ' + total + '</p>'
            + priceType(chosen.source, chosen.checkedAt) + '</div><label>교통수단<select class="leg-mode" data-key="' + e(section.key)
            + '" data-day="' + e(day) + '">' + options(section.options.map(option => [option.mode,
                legOptionLabel(option)]), chosen.mode) + '</select></label></div>'
            + (chosen.boardings?.length ? '<ol class="cost-list transit-boardings" aria-label="대중교통 탑승 순서">'
                + chosen.boardings.map(boarding => '<li><div><strong>' + e(modes[boarding.mode])
                    + (boarding.lines.length ? ' · ' + e(boarding.lines.join(', ')) : '') + '</strong>'
                    + (boarding.start || boarding.end ? '<small>' + e(boarding.start ?? '미확인') + ' → '
                        + e(boarding.end ?? '미확인') + '</small>' : '')
                    + '</div><span>' + e(minutes(boarding.minutes)) + '</span></li>').join('') + '</ol>' : '')
            + '<details class="fare-edit"' + (override ? ' open' : '') + '><summary>요금 직접 입력</summary>'
            + '<form class="leg-fare" data-key="' + e(section.key) + '" data-day="' + e(day) + '">'
            + '<p class="muted">성인·아동 1인 요금 또는 일행 공동 요금을 입력하세요. 값을 비우고 적용하면 기본 요금으로 돌아갑니다.</p>'
            + fareFields(override, 'leg-') + '<button type="submit" class="soft">요금 적용</button></form></details>'
            + '<ul class="cost-list">' + itemRow(section.cost) + '</ul>'
            + (mapLink ? '<a class="soft" href="' + e(mapLink)
                + '" target="_blank" rel="noopener noreferrer">카카오맵에서 길찾기</a>' : '')
            + (chosen.mapObject && $('meta[name="kakao-map-key"]')
                ? ' <button type="button" class="soft leg-map-toggle" aria-expanded="false" data-route="'
                    + e(JSON.stringify({mapObject: chosen.mapObject, from: chosen.from, to: chosen.to}))
                    + '">지도에서 보기</button><div class="leg-map-panel" hidden>'
                    + '<p class="muted" role="status"></p><div class="leg-map" hidden></div></div>'
                : '') + '</article>';
    }).join('');
}

// 선택한 교통수단과 출발·도착 좌표를 넘겨 카카오맵이 새 경로를 검색하도록 합니다.
function kakaoDirectionsUrl(segment) {
    const mode = {
        CITY_BUS: 'traffic', SUBWAY: 'traffic', BUS_SUBWAY: 'traffic', AIRPORT_RAIL: 'traffic',
        AIRPORT_BUS: 'traffic', TRAIN: 'traffic', CAR: 'car', TAXI: 'car', RENTAL_CAR: 'car', WALK: 'walk'
    }[segment.mode];
    if (!mode) return '';
    const place = ({name, latitude, longitude}) => [encodeURIComponent(name), latitude, longitude].join(',');
    return 'https://map.kakao.com/link/by/' + mode + '/' + place(segment.from) + '/' + place(segment.to);
}

// 교통수단을 바꾸기 전에 후보별 시간, 기본 요금과 샘플 여부를 확인할 수 있게 표시합니다.
function legOptionLabel(option) {
    const fare = option.fare;
    const amount = fare?.shared != null ? '일행 ' + won(fare.shared)
        : fare?.adult != null ? '성인 ' + won(fare.adult) : '요금 미확인';
    return (modes[option.mode] ?? option.mode) + ' · ' + minutes(option.minutes) + ' · ' + amount
        + (option.source === 'SAMPLE' ? ' (샘플)' : '');
}
// 구간 카드의 교통수단·요금 변경을 각 화면의 재계산 함수에 연결하고 지도 버튼의 조회를 처리합니다.
// 교통수단 변경 시 이전 수단의 직접 요금을 초기화합니다. 요금 입력은 현재 선택한 수단과 구간 ID를 콜백에 전달합니다.
// 지도는 버튼을 처음 열 때 조회하고, 같은 카드에서 다시 열면 이미 그린 지도의 범위를 다시 맞춥니다.
export function connectLegs(root, callback) {
    $$('.leg-map-toggle', root).forEach(button => {
        const panel = button.nextElementSibling;
        let fitMap;
        let loading = false;
        // 선택한 카드의 지도를 열고, 이미 그려진 지도는 화면 크기만 다시 맞춥니다.
        button.addEventListener('click', async () => {
            if (loading) return;
            panel.hidden = !panel.hidden;
            button.setAttribute('aria-expanded', String(!panel.hidden));
            button.textContent = panel.hidden ? '지도에서 보기' : '지도 닫기';
            if (panel.hidden) return;
            if (fitMap) {
                fitMap();
                return;
            }
            const message = $('[role="status"]', panel);
            loading = true;
            message.textContent = '노선 지도를 불러오는 중입니다.';
            try {
                const {showRouteMap} = await import('./route-map.js');
                fitMap = await showRouteMap(panel, JSON.parse(button.dataset.route));
                message.textContent = 'ODsay 버스·지하철 노선과 출발·도착 위치';
            } catch (error) {
                $('.leg-map', panel).hidden = true;
                message.textContent = error.message + ' 지도를 닫았다가 다시 열면 재시도할 수 있습니다.';
            } finally {
                loading = false;
            }
        });
    });
    // 선택한 교통수단을 상위 화면에 전달해 이 구간을 다시 계산합니다.
    $$('.leg-mode', root).forEach(select => select.addEventListener('change', () =>
        callback(select.dataset.key, {mode: select.value, override: null}, select.dataset.day)));
    // 구간 요금 폼의 값을 읽어 현재 교통수단과 함께 재계산 함수에 전달합니다.
    $$('.leg-fare', root).forEach(form => form.addEventListener('submit', event => {
        event.preventDefault();
        const select = $('.leg-mode', form.closest('.leg'));
        callback(form.dataset.key, {mode: select.value, override: readFare(form, 'leg-')}, form.dataset.day);
    }));
}
// 시간·거리·비용 요약에 사용할 제목과 값 한 쌍을 그립니다.
export const metric = (label, value) => '<div><span>' + e(label) + '</span><strong>' + e(value) + '</strong></div>';
// 요약 값 여러 개를 같은 행의 공통 레이아웃으로 묶습니다.
export const metrics = (...items) => '<div class="metrics">' + items.join('') + '</div>';
// 화면에서 요청이 실행되는 동안 입력을 잠시 비활성화하고 완료 후 복구합니다.
// 요청을 시작할 때 활성 상태인 입력만 찾아 잠급니다. 성공하거나 실패해도 finally에서 처음 잠갔던 요소만 다시 활성화합니다.
export function busy(node, action) {
    const items = $$('input, select, button', node).filter(element => !element.disabled);
    items.forEach(item => item.disabled = true);
    return Promise.resolve().then(action).finally(() => items.forEach(item => item.disabled = false));
}
