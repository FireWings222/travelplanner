import {$, api, read, write, showTrip, status, showError, needsTrip, panel, e, modes, number, won, minutes,
    metric, metrics, moneyDetails, missing, legCards, connectLegs, busy} from './common.js';

// 요금 근거가 없는 0원 부분 합계는 무료로 보이지 않도록 표시합니다.
function routeFare(value, cost) {
    if (!cost.missing.length) return won(value);
    return Number(value) === 0 ? '미확인' : won(value) + ' (부분 합계)';
}

// 경로 비교 화면에서 홈 검색 조건을 불러오고 초기 이동 견적을 조회합니다.
// 홈에서 저장한 trip을 읽고 여행 조건이 없으면 홈으로 돌아갈 안내를 표시합니다. 최초 견적과 이후 변경은 update가 같은 경로 비교 API에 요청합니다.
export async function init(cities) {
    const trip = read('trip');
    if (!trip) return needsTrip('#route-summary');
    showTrip(trip, cities);
    const controls = $('#route-controls');
    let legs = read('legs') ?? {};
    // 현재 교통 선택을 서버에 보내 요약과 추천을 다시 그린 뒤 다음 변경 이벤트를 연결합니다.
    // next는 구간 ID를 키로 해서 선택 교통수단과 직접 입력한 금액을 담은 객체입니다.
    // 서버 계산이 성공한 뒤에만 선택 상태를 세션에 저장하고, 결과 표·구간 카드·추천을 새로 그립니다.
    async function update(next) {
        status('이동 경로를 계산하는 중입니다.');
        const result = await api('/routes/compare', {trip, legs: next});
        legs = next;
        write('legs', legs);
        const selected = result.selected;
        status('');
        $('#route-summary').innerHTML = panel('<h2>선택한 이동 경로</h2>'
            + metrics(metric('편도 소요 시간', minutes(selected.minutes)), metric('거리 / 환승', number(selected.kilometers)
                + 'km / ' + selected.transfers + '회'), metric('전체 인원 편도', routeFare(selected.cost.totalKrw, selected.cost)),
                metric('전체 인원 왕복 추정', routeFare(result.roundTripKrw, selected.cost)))
            + (selected.cost.missing.length && Number(selected.cost.totalKrw) === 0
                ? '' : '<p class="muted">' + moneyDetails(selected.cost) + '</p>')
            + '<p class="muted">구간별 교통수단에서 요금과 시간의 출처를 확인할 수 있습니다. 왕복은 편도 요금을 2회 적용한 추정치입니다.</p>'
            + missing(selected.cost) + (selected.sections.length ? '' : '<p>출발 지점과 도착 지점이 같습니다.</p>'));
        controls.innerHTML = selected.sections.length
            ? panel('<h2>구간별 교통수단</h2>' + legCards(selected.sections, legs)) : '';
        connectLegs(controls, (key, leg) => busy(controls, () => update({...legs, [key]: leg})).catch(showError));
        const rows = result.alternatives.map(option => '<tr><th scope="row">'
            + (option.id === 'selected' ? '<span class="tag">선택</span> ' : '')
            + e(option.modes.map(mode => modes[mode]).join(' / ')) + '</th><td>' + minutes(option.journey.minutes)
            + '</td><td>' + option.journey.transfers + '회</td><td>' + routeFare(option.journey.cost.perPersonKrw, option.journey.cost)
            + '</td><td>' + routeFare(option.journey.cost.totalKrw, option.journey.cost)
            + '</td><td>' + routeFare(option.roundTripKrw, option.journey.cost) + '</td></tr>').join('');
        const picks = Object.entries(result.recommendations).map(([title, pick]) => {
            const option = result.alternatives.find(item => item.id === pick.optionId);
            return '<article><h3>' + e(title) + '</h3><strong>' + e(option.modes.map(mode => modes[mode]).join(' / '))
                + '</strong><p class="muted">' + e(pick.reason) + '</p></article>';
        }).join('');
        $('#route-results').innerHTML = selected.sections.length ? panel('<h2>교통수단별 비교</h2>'
            + '<div class="table-responsive"><table class="table align-middle"><thead><tr><th scope="col">이동 방식</th><th scope="col">편도 시간</th>'
            + '<th scope="col">환승</th><th scope="col">편도 평균 1인</th><th scope="col">편도 일행</th><th scope="col">왕복 일행</th>'
            + '</tr></thead><tbody>' + rows + '</tbody></table></div>'
            + (picks ? '<h2>경로 추천</h2><div class="picks">' + picks + '</div>' : '')) : '';
    }
    await update(legs);
}
