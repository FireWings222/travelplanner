import {$, api, e, modes, won, minutes, distance, modeKind, PLANE_PATH, transitStepKind} from './common.js';

let sdk;

// 구간 지도나 지도 분할 화면이 처음 필요할 때 페이지의 JavaScript 키로 카카오맵 SDK를 불러옵니다.
// 공통 레이아웃의 kakao-map-key 메타 값으로 SDK를 요청합니다. 여러 카드가 지도 기능을 동시에 사용해도 로딩 Promise를 공유합니다.
// 로드가 실패하면 Promise를 비워 다음 지도 열기에서 다시 시도할 수 있게 합니다.
export function loadSdk() {
    sdk ??= new Promise((resolve, reject) => {
        const script = document.createElement('script');
        const key = $('meta[name="kakao-map-key"]').content;
        script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + encodeURIComponent(key);
        const timer = setTimeout(fail, 10000);
        // 지도 스크립트를 로드할 수 없을 때 지연 타이머와 태그를 정리하고 화면에 표시할 오류를 전달합니다.
        function fail() {
            clearTimeout(timer);
            script.remove();
            reject(new Error('카카오맵을 불러오지 못했습니다. JavaScript 키와 등록 도메인을 확인해 주세요.'));
        }
        script.onerror = fail;
        // SDK 스크립트가 내려오면 지도 모듈을 초기화한 뒤 렌더링에 전달합니다.
        script.onload = () => {
            if (!window.kakao?.maps?.load) return fail();
            window.kakao.maps.load(() => {
                clearTimeout(timer);
                resolve(window.kakao.maps);
            });
        };
        document.head.append(script);
    }).catch(error => {
        sdk = undefined;
        throw error;
    });
    return sdk;
}

// 카카오 경로는 견적 응답에 좌표를 담습니다. ODsay 구간은 상세 노선이 필요한 시점에 노선 코드로 조회합니다.
// 배열마다 독립된 선을 그려 환승이나 조회에 실패한 부분을 임의의 직선으로 이어 붙이지 않습니다.
const walkRequests = new Map();

// 도보 좌표가 생략된 연결만 별도로 조회합니다. 같은 탭에서 지도를 다시 그려도 같은 좌표의 요청은 공유합니다.
function connectionLines(step) {
    const params = new URLSearchParams({fromLat: step.walkFrom.latitude, fromLng: step.walkFrom.longitude,
        toLat: step.walkTo.latitude, toLng: step.walkTo.longitude});
    const key = params.toString();
    if (!walkRequests.has(key)) {
        if (walkRequests.size >= 64) walkRequests.delete(walkRequests.keys().next().value);
        const request = api('/routes/walking-geometry?' + key).catch(() => []);
        walkRequests.set(key, request);
        request.then(lines => {
            if (!lines.length && walkRequests.get(key) === request) walkRequests.delete(key);
        });
    }
    return walkRequests.get(key);
}

async function routeParts(segment, includeWalkConnections = true) {
    const steps = (segment.boardings ?? []).filter(step => step.path?.length >= 2);
    if (steps.length) {
        const parts = await Promise.all(segment.boardings.map(async step => {
            const kind = transitStepKind(step.mode);
            if (step.path?.length >= 2) return [{points: step.path, kind}];
            if (includeWalkConnections && kind === 'walk' && step.walkFrom && step.walkTo) {
                return (await connectionLines(step)).map(points => ({points, kind}));
            }
            return [];
        }));
        return parts.flat();
    }
    // ODsay 전체 노선의 선과 도보 단계는 좌표로 연결되어 있지 않으므로, 전체 선에 도보 스타일을 적용하지 않습니다.
    const lines = segment.geometry?.length ? segment.geometry : segment.mapObject
        ? await api('/routes/geometry?mapObject=' + encodeURIComponent(segment.mapObject)) : [];
    return lines.map(points => ({points, kind: segment.mode === 'WALK' ? 'walk' : null}));
}

/** 단계별 색과 선 모양을 정합니다. 수단을 모르는 선은 원래 지도 화면의 색을 유지합니다. */
function appearance(container, part, fallback) {
    if (part.kind === 'walk') {
        return {color: cssValue(container, '--ink-2') || cssValue(container, '--muted'), style: 'shortdot'};
    }
    return {color: part.kind ? cssValue(container, '--m-' + part.kind) : fallback, style: 'solid'};
}

// 선택한 구간의 노선과 양 끝 마커를 그립니다. 화면의 카드가 재계산으로 사라졌다면 지도를 그리지 않습니다.
// 노선 좌표 배열마다 선을 따로 그리고 양쪽 마커를 포함한 범위로 지도를 맞춥니다. 반환하는 함수는 같은 카드를 다시 열 때 지도를 맞추는 데 사용합니다.
export async function showRouteMap(panel, route) {
    const maps = await loadSdk();
    const lines = await routeParts(route);
    if (!lines.length) throw new Error('이 구간의 경로 좌표를 확인할 수 없습니다.');
    const container = $('.leg-map', panel);
    if (!container.isConnected) return;
    // API에서 받은 위도·경도를 카카오맵 객체에서 사용하는 좌표로 변환합니다.
    const position = point => new maps.LatLng(point.latitude, point.longitude);
    const bounds = new maps.LatLngBounds();
    const start = position(route.from);
    const end = position(route.to);
    container.hidden = false;
    const map = new maps.Map(container, {center: start, level: 5});

    for (const [location, title] of [[start, route.from.name], [end, route.to.name]]) {
        new maps.Marker({map, position: location, title});
        bounds.extend(location);
    }
    for (const part of lines) {
        const path = part.points.map(position);
        const {color, style} = appearance(container, part, cssValue(container, '--green'));
        path.forEach(point => bounds.extend(point));
        new maps.Polyline({map, path, strokeWeight: 5, strokeColor: color, strokeOpacity: 0.9, strokeStyle: style});
    }
    map.setBounds(bounds);
    return () => {
        map.relayout();
        map.setBounds(bounds);
    };
}

// ---------- 지도 분할 화면의 큰 지도 (DESIGN.md "카카오맵 위 요소", 결정 0008) ----------

// 지도 칸(container)에 카카오맵을 만듭니다. 칸 크기가 바뀌면 relayout 뒤 view.onResize를 불러 범위를 다시 맞춥니다.
// 반환하는 view에는 SDK(maps), 지도(map), 지도 위에 올린 선과 이름표(items)가 들어 있습니다.
export async function createMap(container) {
    const maps = await loadSdk();
    const map = new maps.Map(container, {center: new maps.LatLng(35.6, 127.6), level: 12});
    map.addControl(new maps.ZoomControl(), maps.ControlPosition.TOPRIGHT);
    const view = {maps, map, container, items: [], rules: [], onResize: null};
    let size = '';
    new ResizeObserver(() => {
        const next = container.clientWidth + 'x' + container.clientHeight;
        if (next === size || !container.clientWidth) return;
        size = next;
        map.relayout();
        view.onResize?.();
    }).observe(container);
    // 확대, 이동이 끝날 때마다 화면에서 너무 가까운 이름표를 숨깁니다.
    maps.event.addListener(map, 'idle', () => applyRules(view));
    return view;
}

// 지도 칸의 CSS 변수(교통수단 색 등)를 읽습니다. 지도 위 색도 DESIGN.md 값을 그대로 씁니다.
const cssValue = (node, name) => getComputedStyle(node).getPropertyValue(name).trim();
// 지도 위 CustomOverlay에 넣을 HTML 요소를 만듭니다.
function overlayNode(className, html) {
    const node = document.createElement('div');
    node.className = className;
    node.innerHTML = html;
    return node;
}
// 이름표: side l은 점 오른쪽, r은 점 왼쪽, b는 점 아래에 붙습니다.
const ANCHOR = {l: {x: 0, y: 0.5}, r: {x: 1, y: 0.5}, b: {x: 0.5, y: 0}, c: {x: 0.5, y: 0.5}, pin: {x: 0.5, y: 1}};
const callout = (title, sub, side) => overlayNode('kc ' + side, e(title) + (sub ? '<small>' + e(sub) + '</small>' : ''));

// 지도 위에 올린 요소를 모두 지웁니다.
function clear(view) {
    view.items.forEach(item => item.setMap(null));
    view.items = [];
    view.rules = [];
}
// 요소를 지도에 올립니다. visible 함수가 있으면 확대, 이동이 끝날 때마다 보일지 다시 정합니다.
function put(view, position, node, anchor = 'c', z = 30, visible) {
    const overlay = new view.maps.CustomOverlay({position, content: node, xAnchor: ANCHOR[anchor].x, yAnchor: ANCHOR[anchor].y, zIndex: z, clickable: false});
    view.items.push(overlay);
    if (visible) view.rules.push([overlay, visible]);
    else overlay.setMap(view.map);
}
function applyRules(view) {
    view.rules.forEach(([overlay, visible]) => overlay.setMap(visible() ? view.map : null));
}
// 두 좌표가 지금 화면에서 몇 픽셀 떨어져 있는지 계산합니다.
function pixelGap(view, a, b) {
    const projection = view.map.getProjection();
    const pa = projection.containerPointFromCoords(a);
    const pb = projection.containerPointFromCoords(b);
    return Math.hypot(pa.x - pb.x, pa.y - pb.y);
}
// 흰 테두리를 두른 선: 교통수단 색 선 아래에 5px 더 굵은 흰 선을 깝니다.
function casedLine(view, path, color, weight, z, style = 'solid') {
    const white = cssValue(view.container, '--paper') || '#ffffff';
    for (const [strokeWeight, strokeColor, zIndex] of [[weight + 5, white, z], [weight, color, z + 1]]) {
        view.items.push(new view.maps.Polyline({map: view.map, path, strokeWeight, strokeColor, strokeOpacity: 1, strokeStyle: style, zIndex}));
    }
}
// 두 공항 사이 항공 구간을 살짝 휜 곡선 점으로 근사합니다. angle은 가운데 비행기 표시의 방향입니다.
function arcPoints(a, b, count = 48) {
    const k = Math.cos((a.latitude + b.latitude) / 2 * Math.PI / 180);
    const ax = a.longitude * k, bx = b.longitude * k, dx = bx - ax, dy = b.latitude - a.latitude;
    const cx = (ax + bx) / 2 - Math.abs(dy) * 0.17, cy = (a.latitude + b.latitude) / 2 - dx * 0.17;
    const points = [];
    for (let i = 0; i <= count; i++) {
        const t = i / count, u = 1 - t;
        points.push({longitude: (u * u * ax + 2 * t * u * cx + t * t * bx) / k, latitude: u * u * a.latitude + 2 * t * u * cy + t * t * b.latitude});
    }
    return {points, angle: Math.atan2(-dy, dx) * 180 / Math.PI + 90};
}
// 두 좌표 사이 직선 거리(km)를 계산합니다. 출발지 주변, 도착지 주변 묶음을 나누는 데 씁니다.
function distanceKm(a, b) {
    const rad = Math.PI / 180;
    const h = Math.sin((b.latitude - a.latitude) * rad / 2) ** 2
        + Math.cos(a.latitude * rad) * Math.cos(b.latitude * rad) * Math.sin((b.longitude - a.longitude) * rad / 2) ** 2;
    return 12742 * Math.asin(Math.sqrt(h));
}

// 경로 비교 응답의 구간(sections)을 교통수단 색 선, 환승 점, 도착 핀, 이름표로 그립니다.
// 항공 구간의 곡선은 공항 간 연결 안내입니다. 지상 이동은 조회한 경로만 그리며, 좌표를 얻지 못하면 출발·도착 위치만 남깁니다.
// 반환하는 fit(focus)는 전체(all), 출발지 주변(start), 도착지 주변(end) 범위로 지도를 맞춥니다. focuses는 고를 수 있는 범위 목록입니다.
// rightPad는 지도 오른쪽을 가리는 견적 카드 폭을 돌려주는 함수입니다.
export async function drawJourney(view, sections, rightPad = () => 0) {
    // 노선 좌표 조회가 늦게 끝나도 같은 지도의 최신 요청만 요소를 바꿉니다.
    const token = (view.drawing ?? 0) + 1;
    view.drawing = token;
    const {maps} = view;
    const position = point => new maps.LatLng(point.latitude, point.longitude);
    const segments = sections.map(section => section.chosen);
    const shapes = await Promise.all(segments.map(async segment => {
        if (segment.mode === 'FLIGHT') return {arc: arcPoints(segment.from, segment.to)};
        return {lines: await routeParts(segment).catch(() => [])};
    }));
    if (token !== view.drawing) return null;
    // 좌표 조회가 끝날 때까지 이전 선을 유지한 뒤, 최신 결과의 선과 마커를 한 번에 교체합니다.
    clear(view);
    // 경로 좌표가 없어도 지도 범위에 양쪽 마커가 포함되도록 출발점과 도착점을 먼저 넣습니다.
    const stops = [segments[0].from, ...segments.map(segment => segment.to)];
    const all = [...stops];
    segments.forEach((segment, i) => {
        const color = cssValue(view.container, '--m-' + modeKind(segment.mode));
        const shape = shapes[i];
        const lines = shape.arc ? [{points: shape.arc.points}] : shape.lines;
        lines.forEach(part => {
            all.push(...part.points);
            const {color: lineColor, style} = appearance(view.container, part, color);
            casedLine(view, part.points.map(position), lineColor, 5, shape.arc ? 10 : 12, style);
        });
        if (!lines.length) return;
        const middleLine = lines[Math.floor(lines.length / 2)].points;
        const middle = position(middleLine[Math.floor(middleLine.length / 2)]);
        if (shape.arc) {
            put(view, middle, overlayNode('kplane', '<svg viewBox="0 0 20 20" width="18" height="18" fill="currentColor" aria-hidden="true" style="color:'
                + color + ';transform:rotate(' + shape.arc.angle.toFixed(1) + 'deg)"><path d="' + PLANE_PATH + '"/></svg>'), 'c', 31);
        }
        // 구간 이름표는 선이 화면에서 140px 이상일 때만 보여 줍니다.
        const from = position(segment.from), to = position(segment.to);
        const cost = sections[i].cost;
        const amount = cost.missing.length && Number(cost.totalKrw) === 0
            ? '요금 미확인' : won(cost.totalKrw) + (cost.missing.length ? ' (부분 합계)' : '');
        put(view, middle, callout((modes[segment.mode] ?? segment.mode) + ' ' + minutes(segment.minutes),
            distance(segment.kilometers) + ', ' + amount, shape.arc ? 'r' : 'b'),
            shape.arc ? 'r' : 'b', 41, () => pixelGap(view, from, to) >= 140);
    });
    // 출발점, 환승점, 도착 핀
    const total = segments.some(segment => segment.minutes == null) ? null
        : segments.reduce((sum, segment) => sum + segment.minutes, 0);
    stops.forEach((stop, i) => {
        const at = position(stop);
        if (i === stops.length - 1) {
            put(view, at, overlayNode('kpin', '<svg width="22" height="29" viewBox="0 0 26 34" aria-hidden="true"><path d="M13 33.5C13 33.5 2.5 20.6 2.5 12.5a10.5 10.5 0 0 1 21 0c0 8.1-10.5 21-10.5 21z" fill="'
                + cssValue(view.container, '--brand') + '" stroke="#ffffff" stroke-width="1.5"/><circle cx="13" cy="12" r="3.6" fill="#ffffff"/></svg>'), 'pin', 33);
            put(view, at, callout(stop.name, '도착, 편도 ' + minutes(total), 'l'), 'l', 40);
            return;
        }
        put(view, at, overlayNode(i === 0 ? 'knode' : 'knode sm', ''), 'c', 32);
        if (i === 0) { put(view, at, callout(stop.name, '출발', 'l'), 'l', 40); return; }
        // 환승점 이름표는 앞뒤 지점과 화면에서 70px 이상 떨어져 있을 때만 보여 줍니다.
        const before = position(stops[i - 1]), after = position(stops[i + 1]);
        put(view, at, callout(stop.name, '환승', 'r'), 'r', 40, () => pixelGap(view, at, before) >= 70 && pixelGap(view, at, after) >= 70);
    });
    // 출발지, 도착지에서 60km 안에 있는 지점을 묶어 "주변" 범위로 씁니다.
    const origin = stops[0], destination = stops[stops.length - 1];
    const near = (center, points) => points.filter(point => distanceKm(center, point) <= 60);
    const ranges = {all, start: near(origin, all), end: near(destination, all)};
    const far = distanceKm(origin, destination) > 100;
    const focuses = far ? ['all', 'start', 'end'] : [];
    // 지도 범위를 맞춥니다. 오른쪽은 견적 카드가 가리는 만큼 더 비웁니다.
    const fit = (focus = 'all') => {
        const points = ranges[focus]?.length ? ranges[focus] : all;
        const bounds = new maps.LatLngBounds();
        points.forEach(point => bounds.extend(position(point)));
        const pad = view.container.clientWidth < 430 ? 28 : focus === 'all' ? 70 : 110;
        view.map.setBounds(bounds, pad, Math.max(pad, rightPad()), pad, pad);
        applyRules(view);
    };
    return {fit, focuses, hasMissingGeometry: shapes.some(shape => shape.lines?.length === 0)};
}

// 날짜 색 물방울 핀 안에 흰 번호를 넣은 SVG입니다. 장소 카드와 지도 핀이 같은 모양을 씁니다.
export function dayPin(number, color, size = 26) {
    return '<svg class="pinsvg" width="' + size + '" height="' + Math.round(size * 1.3) + '" viewBox="0 0 26 34" aria-hidden="true">'
        + '<path d="M13 33.5C13 33.5 2.5 20.6 2.5 12.5a10.5 10.5 0 0 1 21 0c0 8.1-10.5 21-10.5 21z" fill="' + color + '" stroke="#ffffff" stroke-width="1.5"/>'
        + '<text x="13" y="16.8" text-anchor="middle" font-size="13" font-weight="800" fill="#ffffff">' + number + '</text></svg>';
}
// 일정 날짜 순서(0부터)에 맞는 날짜 색 변수 이름입니다. DESIGN.md의 day-1부터 day-3을 차례로 씁니다.
export const dayColor = index => '--d' + (index % 3 + 1);

// 여행 일정 화면의 지도: 고른 날짜(focus)는 날짜 색 동선과 번호 핀, 다른 날짜는 흐린 회색 점선과 점으로 그립니다.
// days는 /api/itineraries/quote 응답의 days입니다. 경로를 얻지 못한 구간은 번호 핀만 표시합니다.
// 반환하는 fit()에는 장소뿐 아니라 조회한 선의 좌표도 포함해 굽은 경로가 화면 밖으로 잘리지 않게 합니다.
export async function drawDays(view, days, focus, rightPad = () => 0) {
    // 현지 일정 지도와 투어 미리보기 지도의 요청 번호는 각각 관리합니다.
    const token = (view.drawing ?? 0) + 1;
    view.drawing = token;
    const {maps} = view;
    const position = point => new maps.LatLng(point.latitude, point.longitude);
    // 배경으로 보이는 다른 날짜는 기존 좌표만 그립니다. 새 도보 조회는 선택한 날짜의 구간에만 요청합니다.
    const shapes = await Promise.all(days.map((day, index) => Promise.all(day.journey.sections.map(section =>
        routeParts(section.chosen, index === focus).catch(() => [])))));
    if (token !== view.drawing) return null;
    clear(view);
    const ink2 = cssValue(view.container, '--ink-2');
    days.forEach((day, i) => {
        const on = i === focus;
        const color = cssValue(view.container, dayColor(i));
        shapes[i].flat().forEach(part => {
            const path = part.points.map(position);
            const style = appearance(view.container, part, color);
            if (on) casedLine(view, path, style.color, 4, 20, style.style);
            else view.items.push(new maps.Polyline({map: view.map, path, strokeWeight: 2.5, strokeColor: ink2, strokeOpacity: 0.8, strokeStyle: 'shortdash', zIndex: 5}));
        });
        day.stops.forEach((stop, n) => {
            const at = position(stop);
            if (!on) { put(view, at, overlayNode('kdim', ''), 'c', 20); return; }
            put(view, at, overlayNode('kpin', dayPin(n + 1, color, 28)), 'pin', 35 + n);
            put(view, at, overlayNode('kpinlabel', e(stop.name)), 'b', 34);
        });
    });
    const fit = () => {
        const points = days[focus]?.stops.length
            ? [...days[focus].stops, ...shapes[focus].flat().flatMap(part => part.points)]
            : [...days.flatMap(day => day.stops), ...shapes.flat(2).flatMap(part => part.points)];
        if (!points.length) return;
        const bounds = new maps.LatLngBounds();
        points.forEach(point => bounds.extend(position(point)));
        const pad = view.container.clientWidth < 430 ? 36 : 80;
        view.map.setBounds(bounds, pad, Math.max(pad, rightPad()), pad, pad);
        // 장소가 한 곳이거나 서로 가까워도 지도가 지나치게 확대되지 않도록 조정합니다.
        if (view.map.getLevel() < 6) view.map.setLevel(6);
    };
    return {fit, hasMissingGeometry: shapes[focus]?.some(lines => !lines.length)};
}
