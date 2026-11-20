import {$, api, read, write, showTrip, status, showError, needsTrip, panel, e, modes, number, won, minutes,
    metric, metrics, moneyDetails, missing, legCards, connectLegs, busy} from './common.js';

export async function init(cities) {
    const trip = read('trip');
    if (!trip) return needsTrip('#route-summary');
    showTrip(trip, cities);
    const controls = $('#route-controls');
    let legs = read('legs') ?? {};
    async function update(next) {
        status('이동 경로를 계산하는 중입니다.');
        const result = await api('/routes/compare', {trip, legs: next});
        legs = next;
        write('legs', legs);
        const selected = result.selected;
        status('');
        $('#route-summary').innerHTML = panel('<h2>선택한 이동 경로</h2>'
            + metrics(metric('편도 소요 시간', minutes(selected.minutes)), metric('거리 / 환승', number(selected.kilometers)
                + 'km / ' + selected.transfers + '회'), metric('전체 인원 편도', won(selected.cost.totalKrw)),
                metric('전체 인원 왕복 추정', won(result.roundTripKrw)))
            + '<p class="muted">' + moneyDetails(selected.cost) + '</p>'
            + '<p class="muted">경로·거리·시간 출처는 TravelPlanner 샘플 데이터입니다. 왕복은 편도 요금을 2회 적용한 추정치입니다.</p>'
            + missing(selected.cost) + (selected.sections.length ? '' : '<p>출발지와 목적지가 같은 도시입니다.</p>'));
        controls.innerHTML = selected.sections.length
            ? panel('<h2>구간별 교통수단</h2>' + legCards(selected.sections, legs)) : '';
        connectLegs(controls, (key, leg) => busy(controls, () => update({...legs, [key]: leg})).catch(showError));
        const rows = result.alternatives.map(option => '<tr><th scope="row">'
            + (option.id === 'selected' ? '<span class="tag">선택</span> ' : '')
            + e(option.modes.map(mode => modes[mode]).join(' / ')) + '</th><td>' + minutes(option.journey.minutes)
            + '</td><td>' + option.journey.transfers + '회</td><td>' + won(option.journey.cost.perPersonKrw)
            + '</td><td>' + won(option.journey.cost.totalKrw) + '</td><td>' + won(option.roundTripKrw)
            + (option.journey.cost.missing.length ? ' (부분 합계)' : '') + '</td></tr>').join('');
        const picks = Object.entries(result.recommendations).map(([title, pick]) => {
            const option = result.alternatives.find(item => item.id === pick.optionId);
            return '<article><h3>' + e(title) + '</h3><strong>' + e(option.modes.map(mode => modes[mode]).join(' / '))
                + '</strong><p class="muted">' + e(pick.reason) + '</p></article>';
        }).join('');
        $('#route-results').innerHTML = selected.sections.length ? panel('<h2>교통수단별 비교</h2>'
            + '<div class="table-responsive"><table class="table align-middle"><thead><tr><th scope="col">이동 방식</th><th scope="col">편도 시간</th>'
            + '<th scope="col">환승</th><th scope="col">편도 평균 1인</th><th scope="col">편도 일행</th><th scope="col">왕복 일행</th>'
            + '</tr></thead><tbody>' + rows + '</tbody></table></div>'
            + (picks ? '<h2>샘플 데이터 기반 추천</h2><div class="picks">' + picks + '</div>' : '')) : '';
    }
    await update(legs);
}
