import {$, $$, api, read, write, e, panel, busy, options, status, showError, showTrip, needsTrip, nearby,
    metrics, metric, won, minutes, missing, legCards, connectLegs, itemRow} from './common.js';

export async function init(cities) {
    const root = $('#plan-days');
    const save = $('#save-plan');
    let plan = read('plan');
    let id = read('planId');
    let date;
    let quote;
    let sights = [];

    async function open(stored) {
        plan = stored.plan;
        id = stored.id;
        date = plan.trip.start;
        write('trip', plan.trip);
        write('legs', null);
        write('comparison', null);
        await load(plan.trip);
    }

    async function listSaved() {
        const records = await api('/travel-plans');
        $('#saved-plans').innerHTML = records.length ? records.map(record => {
            const city = cities.find(item => item.id === record.trip.destination)?.name ?? record.trip.destination;
            return '<div class="saved-row"><div><strong>' + e(city) + '</strong><p class="muted">' + e(record.trip.start) + ' – '
                + e(record.trip.end) + '</p></div><div class="actions"><button type="button" class="soft" data-open="' + e(record.id)
                + '">열기</button><button type="button" class="danger" data-delete="' + e(record.id) + '">삭제</button></div></div>';
        }).join('') : '<p class="empty">저장한 여행이 없습니다.</p>';
        $$('[data-open]').forEach(button => button.onclick = () => busy($('#saved-plans'), async () =>
            open(await api('/travel-plans/' + button.dataset.open))).catch(showError));
        $$('[data-delete]').forEach(button => button.onclick = () => {
            if (!window.confirm('저장한 여행을 삭제할까요?')) return;
            busy($('#saved-plans'), async () => {
                await api('/travel-plans/' + button.dataset.delete, undefined, 'DELETE');
                if (id === button.dataset.delete) {
                    id = null;
                    write('planId', null);
                    save.textContent = '일정 저장';
                }
                await listSaved();
                status('저장한 여행을 삭제했습니다.');
            }).catch(showError);
        });
    }

    async function load(trip) {
        status('일정을 계산하는 중입니다.');
        const [catalog, result] = await Promise.all([api('/attractions'),
            plan ? api('/itineraries/quote', plan) : api('/itineraries/generate', trip)]);
        sights = catalog.filter(sight => nearby(sight.destinationId, result.plan.trip.destination));
        accept(result);
    }

    function accept(result) {
        quote = result;
        plan = result.plan;
        date ??= plan.trip.start;
        write('plan', plan);
        write('planId', id);
        showTrip(plan.trip, cities);
        save.hidden = false;
        save.textContent = id ? '저장한 일정 갱신' : '일정 저장';
        const travel = result.days.reduce((sum, day) => sum + day.journey.minutes, 0);
        $('#plan-summary').innerHTML = panel('<h2>현지 일정 예상 합계</h2>'
            + metrics(metric('일차', plan.days.length + '일'), metric('현지 이동 시간', minutes(travel)),
                metric('전체 입장료 + 이동비', won(result.total.totalKrw)))
            + '<p class="muted">숙소는 목적지의 중심 위치로 가정합니다. 목적지 왕복, 숙박과 식비는 별도입니다.</p>' + missing(result.total));
        status('');
        renderDay();
    }

    async function change(day) {
        await busy(root, async () => {
            status('일정을 다시 계산하는 중입니다.');
            const next = {...plan, days: plan.days.map(item => item.date === day.date ? day : item)};
            accept(await api('/itineraries/quote', next));
        }).catch(showError);
    }

    function renderDay() {
        const day = plan.days.find(item => item.date === date);
        const summary = quote.days.find(item => item.date === date);
        const canAdd = sights.filter(sight => !day.stops.includes(sight.id));
        root.innerHTML = panel('<div class="panel-heading"><div><h2>방문 일정</h2><p>추가, 삭제하고 위아래로 순서를 옮길 수 있습니다.</p></div>'
            + '<label>일자<select id="pick-day">' + options(plan.days.map((item, index) => [item.date,
                (index + 1) + '일차 · ' + item.date]), date) + '</select></label></div>'
            + metrics(metric('관광지 체류', minutes(summary.visitMinutes)), metric('이동 시간', minutes(summary.journey.minutes)),
                metric('일행 예상 지출', won(summary.total.totalKrw))) + missing(summary.total)
            + (summary.stops.length ? '<ol class="stop-list">' + summary.stops.map((sight, index) => {
                const cost = summary.admissions[index];
                return '<li><div><strong>' + e(sight.name) + '</strong><p class="muted">' + e(sight.description)
                    + ' · 체류 ' + minutes(sight.visitMinutes) + ' · 입장료 일행 ' + won(cost.totalKrw) + '</p></div>'
                    + '<div class="actions"><button class="soft" type="button" aria-label="' + e(sight.name) + ' 위로" data-index="' + index
                    + '" data-move="-1"' + (index === 0 ? ' disabled' : '') + '>↑</button>'
                    + '<button class="soft" type="button" aria-label="' + e(sight.name) + ' 아래로" data-index="' + index
                    + '" data-move="1"' + (index + 1 === summary.stops.length ? ' disabled' : '') + '>↓</button>'
                    + '<button class="danger" type="button" data-index="' + index + '" data-remove>삭제</button></div></li>';
            }).join('') + '</ol>' : '<p class="empty">추가된 관광지가 없습니다.</p>')
            + (canAdd.length && day.stops.length < 15 ? '<form id="add-stop" class="actions"><label>관광지 검색<input name="filter" type="search" placeholder="관광지 이름"></label>'
                + '<label>관광지 추가<select name="sight" required>'
                + options(canAdd.map(sight => [sight.id, sight.name])) + '</select></label><button type="submit" class="primary">추가</button></form>' : '')
            + '<details><summary>입장료와 환율 내역</summary><ul class="cost-list">' + summary.admissions.map(itemRow).join('') + '</ul></details>')
            + panel('<h2>관광지 간 이동</h2>' + legCards(summary.journey.sections, day.legs, date));
        $('#pick-day').onchange = event => { date = event.target.value; renderDay(); };
        function changeStops(stops) {
            const visit = [plan.trip.destination, ...stops, plan.trip.destination];
            const valid = visit.slice(1).map((next, index) => visit[index] + '~' + next);
            const legs = Object.fromEntries(Object.entries(day.legs ?? {}).filter(([key]) => valid.includes(key)));
            change({...day, stops, legs});
        }
        const add = $('#add-stop');
        if (add) {
            add.elements.filter.oninput = () => {
                const term = add.elements.filter.value.trim().toLowerCase();
                const matches = canAdd.filter(sight => sight.name.toLowerCase().includes(term));
                add.elements.sight.innerHTML = matches.length ? options(matches.map(sight => [sight.id, sight.name]))
                    : '<option value="">검색 결과 없음</option>';
                $('button', add).disabled = !matches.length;
            };
            add.onsubmit = event => { event.preventDefault(); changeStops([...day.stops, add.elements.sight.value]); };
        }
        $$('[data-move]', root).forEach(button => button.onclick = () => {
            const index = +button.dataset.index;
            const target = index + +button.dataset.move;
            const stops = [...day.stops];
            [stops[index], stops[target]] = [stops[target], stops[index]];
            changeStops(stops);
        });
        $$('[data-remove]', root).forEach(button => button.onclick = () =>
            changeStops(day.stops.filter((_, index) => index !== +button.dataset.index)));
        connectLegs(root, (key, leg) => change({...day, legs: {...day.legs, [key]: leg}}));
    }

    save.onclick = async () => {
        save.disabled = true;
        try {
            const stored = await api(id ? '/travel-plans/' + id : '/travel-plans', plan, id ? 'PUT' : 'POST');
            id = stored.id;
            write('planId', id);
            save.textContent = '저장한 일정 갱신';
            await listSaved();
            status('여행 일정을 저장했습니다.');
        } catch (error) { showError(error); }
        finally { save.disabled = false; }
    };

    const trip = read('trip');
    if (!trip) needsTrip('#plan-summary');
    const outcomes = await Promise.allSettled([listSaved(), ...(trip ? [load(trip)] : [])]);
    outcomes.filter(outcome => outcome.status === 'rejected').forEach(outcome => showError(outcome.reason));
}
