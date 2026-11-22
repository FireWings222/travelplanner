import {$, api} from './common.js';

let sdk;

// 구간 지도가 처음 열릴 때 페이지의 JavaScript 키로 카카오맵 SDK를 불러옵니다.
// 공통 레이아웃의 kakao-map-key 메타 값으로 SDK를 요청합니다. 여러 카드가 지도 기능을 동시에 사용해도 로딩 Promise를 공유합니다.
// 로드가 실패하면 Promise를 비워 다음 지도 열기에서 다시 시도할 수 있게 합니다.
function loadSdk() {
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

// 선택한 구간의 좌표를 서버에서 조회해 카카오맵에 노선과 양 끝 마커를 그립니다.
// route는 ODsay 노선 코드와 마커에 표시할 두 지점의 이름·좌표를 담습니다. 화면의 카드가 재계산으로 사라졌다면 지도를 그리지 않습니다.
// 노선 좌표 배열마다 선을 따로 그리고 양쪽 마커를 포함한 범위로 지도를 맞춥니다. 반환하는 함수는 같은 카드를 다시 열 때 지도를 맞추는 데 사용합니다.
export async function showRouteMap(panel, route) {
    const maps = await loadSdk();
    const lines = await api('/routes/geometry?mapObject=' + encodeURIComponent(route.mapObject));
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
    for (const line of lines) {
        const path = line.map(position);
        path.forEach(point => bounds.extend(point));
        new maps.Polyline({map, path, strokeWeight: 5, strokeColor: '#176c50', strokeOpacity: 0.9});
    }
    map.setBounds(bounds);
    return () => {
        map.relayout();
        map.setBounds(bounds);
    };
}
