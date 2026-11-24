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
export const platforms = {KLOOK: '클룩', MYREALTRIP: '마이리얼트립', NOL: 'NOL / 야놀자', KKDAY: 'KKday', OTHER: '기타'};
const inclusions = {transportIncluded: '관광지 간 이동', mealIncluded: '식사', guideIncluded: '가이드'};
// 투어 목록과 비용 비교 카드에 교통, 식사, 가이드 포함 여부를 표시합니다.
export const inclusionTags = detail => '<div class="tags">' + Object.entries(inclusions).map(([key, label]) =>
    '<span class="tag ' + (detail[key] ? 'included' : '') + '">' + label + (detail[key] ? ' 포함' : ' 불포함') + '</span>').join('') + '</div>';
export const sources = {UNCONFIRMED: '조회 미확인', CATALOG: '관광지 참고 정보', MANUAL: '사용자 입력 요금',
    PROVIDER: '제공업체 가격', ODSAY: 'ODsay 예상 요금·시간',
    KAKAO_MOBILITY: '카카오모빌리티 예상 정보', KAKAO_MAP: '카카오맵 검색 장소', KAKAO_ROUTING: '카카오맵 예상 경로',
    PUBLISHED: '공개 요금', ESTIMATED: '1인 예상가'};
// 비용과 거리를 한국어 숫자 형식으로 표시합니다.
export const number = value => new Intl.NumberFormat('ko-KR', {maximumFractionDigits: 2}).format(value);
// 합계·요금에 원 단위를 붙이고 데이터가 없으면 미확인으로 표시합니다.
export const won = value => value == null ? '미확인' : number(value) + '원';
export const distance = value => value == null ? '거리 미확인' : number(value) + 'km';
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
// 투어 검색과 비용 비교 화면에 사용할 기본 날짜를 YYYY-MM-DD 형식으로 만듭니다.
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
// 도시 목록을 여행지 선택 메뉴의 옵션으로 바꿉니다.
export function cityOptions(cities, selected) {
    return options(cities.map(city => [city.id, city.name]), selected);
}
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
export const priceType = (source, checkedAt) => '<span class="tag">'
    + e(sources[source]) + '</span>' + (checkedAt ? '<span class="meta">확인 ' + e(checked(checkedAt)) + ' KST</span>' : '');
// 일정 출처와 별도의 가격 출처가 있으면 각각 연결합니다. 직접 등록 상품의 예약 URL은 이 함수에서 처리하지 않습니다.
export const tourReferenceLinks = reference => [[reference?.sourceUrl, '출처 보기'], [reference?.priceUrl, '공개 요금 보기']]
    .map(([url, label]) => [safeUrl(url), label]).filter(([url]) => url)
    .map(([url, label]) => '<a class="soft" href="' + e(url) + '" target="_blank" rel="noopener noreferrer">' + label + '</a>').join('');
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

/** 지도 선과 이동 내역이 같은 교통수단 색을 사용하도록 단계별 분류를 공유합니다. */
export const transitStepKind = mode => ({WALK: 'walk', CITY_BUS: 'bus', SUBWAY: 'rail', EXPRESS_BUS: 'bus', INTERCITY_BUS: 'bus'}[mode]);
export const isIntercityBus = mode => mode === 'EXPRESS_BUS' || mode === 'INTERCITY_BUS';

// 공급자가 반환한 단계 순서대로 도보와 탑승을 함께 표시합니다. 역이나 정류장 이름이 없으면 미확인입니다.
export function transitSteps(segment, listClass = 'boardings') {
    if (!segment.boardings?.length) return '';
    return '<ol class="' + listClass + ' transit-steps" aria-label="도보 및 대중교통 이동 순서">'
        + segment.boardings.map(step => {
            const kind = transitStepKind(step.mode);
            const facts = [step.minutes == null ? '' : minutes(step.minutes),
                step.meters == null ? '' : (step.meters < 1000 ? number(step.meters) + 'm' : distance(step.meters / 1000))]
                .filter(Boolean).join(', ');
            const routeName = step.lines?.length ? ' ' + e(step.lines.join(', ')) : '';
            return '<li>' + (kind ? '<i class="transit-line ' + kind + '" aria-hidden="true"></i>' : '')
                + '<b>' + e(modes[step.mode] ?? step.mode) + routeName + '</b> '
                + e(step.start ?? '출발 지점 미확인') + '에서 ' + e(step.end ?? '도착 지점 미확인')
                + (facts ? ', ' + facts : '') + '</li>';
        }).join('') + '</ol>';
}

// 서버의 구간별 견적을 교통수단 선택과 지도 버튼이 있는 카드로 그립니다.
// sections에는 서버가 선택한 교통수단, 전체 후보와 비용 항목이 들어 있습니다.
// 노선 코드와 지도 키가 있으면 앱 안에서 지도를 열고, 좌표가 있는 구간은 카카오맵 길찾기로도 연결합니다.
export function legCards(sections) {
    if (!sections.length) return '<p class="empty">이동 구간이 없습니다.</p>';
    return sections.map(section => {
        const chosen = section.chosen;
        const mapLink = kakaoDirectionsUrl(chosen);
        const total = section.cost.missing.length && Number(section.cost.totalKrw) === 0
            ? '요금 미확인' : '일행 ' + won(section.cost.totalKrw) + (section.cost.missing.length ? ' (부분 합계)' : '');
        return '<article class="leg"><div class="leg-head"><div><h3>' + e(chosen.from.name) + ' <span aria-hidden="true">→</span> '
            + e(chosen.to.name) + '</h3><p>' + distance(chosen.kilometers) + ', ' + minutes(chosen.minutes)
            + (chosen.transfers == null ? '' : ', 환승 ' + chosen.transfers + '회') + ', ' + total + '</p>'
            + priceType(chosen.source, chosen.checkedAt) + '</div><label>교통수단<select class="leg-mode" data-key="' + e(section.key)
            + '">' + options(section.options.map(option => [option.mode,
                legOptionLabel(option)]), chosen.mode) + '</select></label></div>'
            + transitSteps(chosen, 'transit-boardings')
            + '<ul class="cost-list">' + itemRow(section.cost) + '</ul>'
            + (mapLink ? '<a class="soft" href="' + e(mapLink)
                + '" target="_blank" rel="noopener noreferrer">카카오맵에서 길찾기</a>' : '')
            + ((chosen.mapObject || chosen.geometry?.length) && $('meta[name="kakao-map-key"]')
                ? ' <button type="button" class="soft leg-map-toggle" aria-expanded="false" data-route="'
                    + e(JSON.stringify({mode: chosen.mode, mapObject: chosen.mapObject, geometry: chosen.geometry,
                        boardings: chosen.boardings, from: chosen.from, to: chosen.to}))
                    + '">지도에서 보기</button><div class="leg-map-panel" hidden>'
                    + '<p class="muted" role="status"></p><div class="leg-map" hidden></div></div>'
                : '') + '</article>';
    }).join('');
}

// 선택한 교통수단과 출발·도착 좌표를 넘겨 카카오맵이 새 경로를 검색하도록 합니다.
export function kakaoDirectionsUrl(segment) {
    const mode = {
        CITY_BUS: 'traffic', SUBWAY: 'traffic', BUS_SUBWAY: 'traffic', AIRPORT_RAIL: 'traffic',
        AIRPORT_BUS: 'traffic', TRAIN: 'traffic', EXPRESS_BUS: 'traffic', INTERCITY_BUS: 'traffic',
        CAR: 'car', TAXI: 'car', RENTAL_CAR: 'car', WALK: 'walk'
    }[segment.mode];
    if (!mode) return '';
    const place = ({name, latitude, longitude}) => [encodeURIComponent(name), latitude, longitude].join(',');
    return 'https://map.kakao.com/link/by/' + mode + '/' + place(segment.from) + '/' + place(segment.to);
}

// 교통수단을 고르기 전에 후보별 시간과 조회 가능한 요금을 표시합니다.
function legOptionLabel(option) {
    const fare = option.fare;
    const amount = fare?.shared != null ? '일행 ' + won(fare.shared)
        : fare?.adult != null ? '성인 ' + won(fare.adult) : '요금 미확인';
    return (modes[option.mode] ?? option.mode) + ', ' + minutes(option.minutes) + ', ' + amount;
}
// 구간 카드의 교통수단 변경을 각 화면의 재계산 함수에 연결하고 지도 버튼의 조회를 처리합니다.
// 교통수단 변경 시 이전 수단에 저장된 요금을 초기화합니다.
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
        callback(select.dataset.key, {mode: select.value, override: null})));

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

// ---------- 새 디자인 공통 조각 (DESIGN.md, 결정 0010) ----------

// 교통수단을 DESIGN.md의 교통수단 색 4가지(철도, 항공, 버스, 도로) 가운데 하나로 묶습니다.
export function modeKind(mode) {
    if (['KTX', 'SRT', 'TRAIN', 'SUBWAY', 'AIRPORT_RAIL'].includes(mode)) return 'rail';
    if (mode === 'FLIGHT') return 'air';
    if (['EXPRESS_BUS', 'INTERCITY_BUS', 'CITY_BUS', 'AIRPORT_BUS', 'BUS_SUBWAY'].includes(mode)) return 'bus';
    return 'road';
}
export const PLANE_PATH = 'M10 1.5c.7 0 1.2.9 1.2 2v3.6l6.3 3.9v1.7l-6.3-2v3.7l1.8 1.4v1.3L10 16.3l-3 .8v-1.3l1.8-1.4v-3.7l-6.3 2V11l6.3-3.9V3.5c0-1.1.5-2 1.2-2z';
const MODE_ICON = {
    rail: '<path fill-rule="evenodd" d="M7 2h6a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V5a3 3 0 0 1 3-3zm-.5 3v3.5h7V5zM7.3 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2zm5.4 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2z"/><path d="M7 15.4 5.6 18M13 15.4l1.4 2.6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>',
    bus: '<path fill-rule="evenodd" d="M5.5 3h9A2.5 2.5 0 0 1 17 5.5v8a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 3 13.5v-8A2.5 2.5 0 0 1 5.5 3zM5 5.5V9h10V5.5zM6.3 11.2a1 1 0 1 0 0 2 1 1 0 0 0 0-2zm7.4 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2z"/><path d="M6 15.4v1.8M14 15.4v1.8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
    road: '<path fill-rule="evenodd" d="M6.2 4.5h7.6a1.5 1.5 0 0 1 1.4 1l1.4 3.8a2 2 0 0 1 1.4 1.9V14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-2.8a2 2 0 0 1 1.4-1.9l1.4-3.8a1.5 1.5 0 0 1 1.4-1zm.4 1.8L5.6 9h8.8l-1-2.7zM5.5 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2zm9 0a1 1 0 1 0 0 2 1 1 0 0 0 0-2z"/><path d="M4.5 15.4v1.4M15.5 15.4v1.4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>',
    air: '<path transform="rotate(45 10 10)" d="' + PLANE_PATH + '"/>'
};
// 경로 카드와 구간 줄에 쓰는 교통수단 아이콘(SVG)을 교통수단 색으로 그립니다.
export function modeIcon(mode, size = 19) {
    const kind = modeKind(mode);
    return '<svg class="mi" viewBox="0 0 20 20" width="' + size + '" height="' + size + '" fill="currentColor" style="color:var(--m-'
        + kind + ')" role="img" aria-label="' + e(modes[mode] ?? mode) + '">' + MODE_ICON[kind] + '</svg>';
}

// 숫자 전환에 쓰는 표시 형식입니다. data-fmt 값으로 고릅니다.
const ROLL_FORMAT = {
    won: value => won(Math.round(value)),
    pct: value => '예산의 ' + Math.round(value) + '%',
    left: value => value >= 0 ? won(Math.round(value)) + ' 남음' : won(Math.round(-value)) + ' 초과',
    bar: () => ''
};
// 값이 바뀌면 숫자가 굴러가며 바뀌는 칸을 만듭니다. key가 같은 칸끼리 이전 값을 기억합니다.
export const rollValue = (key, value, format = 'won', tag = 'span', className = 'num') => '<' + tag + ' class="' + className
    + '" data-roll="' + e(key) + '" data-val="' + value + '" data-fmt="' + format + '">' + ROLL_FORMAT[format](value) + '</' + tag + '>';

const rolled = {};
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)');
// 화면을 다시 그린 뒤 호출해 바뀐 금액에 숫자 전환, 차이 표시, 바뀐 줄 강조를 적용합니다(DESIGN.md Motion).
// data-roll 칸은 이전 값에서 새 값으로 620ms 동안 바뀝니다. data-flash 줄은 같은 key 값이 바뀌었을 때 1200ms 동안 칠합니다.
// key가 sum인 칸이 바뀌면 같은 영역의 .delta에 차이를 1800ms 동안 보여 줍니다. 움직임 줄이기 설정이면 최종 값만 보여 줍니다.
export function animateValues(root) {
    const changed = new Set();
    $$('[data-roll]', root).forEach(node => {
        const key = node.dataset.roll;
        const to = Number(node.dataset.val);
        const from = rolled[key];
        // 새 DOM의 표시값은 최종값으로 먼저 저장합니다. 다음 렌더링은 일시적인 애니메이션 중간값을 이어받지 않습니다.
        rolled[key] = to;
        if (from === undefined || from === to) return;
        changed.add(key);
        if (reduceMotion.matches) return;
        if (key === 'sum') {
            const delta = $('.delta', root);
            if (delta) {
                delta.textContent = (to > from ? '+' : '-') + won(Math.abs(Math.round(to - from)));
                delta.classList.remove('show');
                void delta.offsetWidth;
                delta.classList.add('show');
            }
        }
        const format = ROLL_FORMAT[node.dataset.fmt] ?? ROLL_FORMAT.won;
        const start = performance.now();
        // 전환 중인 값을 칸에 씁니다. 예산 막대는 글자 대신 폭을 바꿉니다.
        const paint = value => {
            if (node.dataset.fmt === 'bar') node.style.width = value.toFixed(2) + '%';
            else node.textContent = format(value);
        };
        paint(from);
        // 다음 화면 갱신마다 easeOutCubic 곡선으로 값을 옮깁니다.
        const step = now => {
            if (!node.isConnected) return;
            const t = Math.min(1, (now - start) / 620);
            paint(from + (to - from) * (1 - Math.pow(1 - t, 3)));
            if (t < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    });
    if (reduceMotion.matches) return;
    $$('[data-flash]', root).forEach(row => {
        if (!changed.has(row.dataset.flash)) return;
        row.classList.remove('flash');
        void row.offsetWidth;
        row.classList.add('flash');
    });
}

// 여행 견적 카드(DESIGN.md estimate-card)를 그립니다.
// rows는 {key, label, value, text, partial} 목록입니다. value가 null이면 text를 보여 주고 합계에서 뺍니다.
// partial이 true인 줄이 있으면 합계에 "(부분 합계)"를 붙입니다. budget이 없으면 예산 막대를 그리지 않습니다.
// action은 내역 아래에 넣을 버튼이나 링크 HTML입니다. 모바일에서는 open이 true일 때만 내역이 펼쳐집니다.
export function estimateCard({rows, budget, action = '', open = false}) {
    const hasTotal = rows.some(row => row.value != null);
    const sum = rows.reduce((total, row) => total + (row.value == null ? 0 : Number(row.value)), 0);
    const partial = rows.some(row => row.partial);
    const hasBudget = budget != null && Number(budget) > 0;
    // 막대의 폭만 100%에서 멈춥니다. 예산 초과분은 실제 비율과 남은 금액에서 확인할 수 있습니다.
    const ratio = hasBudget ? Math.min(100, sum / Number(budget) * 100) : 0;
    const left = hasBudget ? Number(budget) - sum : 0;
    return '<div class="est-main"><span class="est-label">여행 견적</span>'
        + (hasBudget && hasTotal ? rollValue('pct', sum / Number(budget) * 100, 'pct', 'span', 'est-pct num') : '')
        + '<strong class="est-sum num">' + (hasTotal ? rollValue('sum', sum) + (partial ? '<small>(부분 합계)</small>' : '') : '미확인') + '</strong>'
        + '<button type="button" class="est-toggle" aria-expanded="' + open + '">' + (open ? '접기' : '내역') + '</button>'
        + '<span class="delta" aria-hidden="true"></span>'
        + (hasBudget && hasTotal ? '<div class="budgetbar' + (left < 0 ? ' over' : '') + '"><i data-roll="bar" data-val="' + ratio.toFixed(2)
            + '" data-fmt="bar" style="width:' + ratio.toFixed(2) + '%"></i></div>'
            + '<p class="est-left num' + (left < 0 ? ' over' : '') + '">예산 ' + won(budget) + ' 중 ' + rollValue('left', left, 'left', 'b', '') + '</p>' : '')
        + '</div><div class="detail">'
        + rows.map(row => '<div class="est-row" data-flash="' + e(row.key) + '"><span>' + e(row.label) + '</span>'
            + (row.value == null ? '<span>' + e(row.text ?? '미확인') + '</span>' : rollValue(row.key, Number(row.value)))
            + '</div>').join('')
        + action + '<small class="fine">숙박비와 식비는 포함하지 않습니다.</small></div>';
}
// 모바일 견적 막대의 "내역" 버튼으로 내역을 펼치고 접습니다. 펼친 상태는 카드 바깥 요소의 open 클래스로 남아 다시 그려도 유지됩니다.
export function connectEstimate(node) {
    const toggle = $('.est-toggle', node);
    if (!toggle) return;
    toggle.addEventListener('click', () => {
        const open = node.classList.toggle('open');
        toggle.setAttribute('aria-expanded', String(open));
        toggle.textContent = open ? '접기' : '내역';
    });
}

// 공통 머리글의 여행 조건 묶음과 단계 탭 완료 표시를 세션 상태로 채웁니다(DESIGN.md 단계 탭, 여행 조건 묶음).
// 여행 조건이 있으면 출발, 도착, 일정, 인원과 "수정" 링크를 보여 줍니다. 홈에서는 검색 폼이 조건을 보여 주므로 묶음을 숨깁니다.
// 완료 표시: 1단계는 여행 조건, 2단계는 계산한 경로 선택(legs), 3단계는 만든 일정(plan)이 세션에 있을 때 ✓로 바꿉니다.
// 경로 비교, 여행 일정 화면처럼 세션 상태를 바꾸는 화면은 바꾼 뒤 이 함수를 다시 부릅니다.
export function renderHeader(cities) {
    const box = $('#tripbox');
    if (!box) return;
    const trip = read('trip');
    const city = id => cities.find(item => item.id === id)?.name ?? id;
    const day = value => Number(value.slice(5, 7)) + '.' + Number(value.slice(8, 10));
    const field = (label, value, extra = '') => '<span class="tf' + extra + '"><small>' + label + '</small><b>' + value + '</b></span>';
    box.hidden = !trip || document.body.dataset.page === 'home';
    box.innerHTML = trip ? field('출발', e(city(trip.origin))) + field('도착', e(city(trip.destination)))
        + field('일정', '<span class="num">' + e(day(trip.start)) + ' ~ ' + e(day(trip.end)) + '</span>')
        + field('인원', '성인 ' + trip.party.adults + (trip.party.children ? ', 아동 ' + trip.party.children : ''), ' extra')
        + '<a class="tedit" href="/">수정</a>' : '';
    // 현재 단계의 aria-current는 서버 템플릿이 붙이고, 완료 표시는 브라우저 탭에 저장된 자료를 기준으로 보완합니다.
    const done = {trip: Boolean(trip), legs: Boolean(read('legs')), plan: Boolean(read('plan')), tour: false};
    $$('.steps [data-step]').forEach((step, index) => {
        const finished = done[step.dataset.step] && !step.hasAttribute('aria-current');
        step.classList.toggle('done', finished);
        $('i', step).textContent = finished ? '✓' : String(index + 1);
        $('i', step).setAttribute('aria-label', finished ? (index + 1) + '단계 완료' : (index + 1) + '단계');
    });
}
