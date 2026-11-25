# TravelPlanner 디자인 문서

TravelPlanner 화면 디자인에 관한 조사와 결정 기록이다. 화면을 만들 때 따르는 규칙은 저장소 루트의 [DESIGN.md](../../DESIGN.md)에 있다. 작성 규칙은 [0004 디자인 문서 관리](decisions/0004-design-docs.md)를 따른다.

- 조사 기록: `research/YYYY-MM-DD-주제.md` ([템플릿](research/_template.md))
- 결정 기록: `decisions/NNNN-주제.md` ([템플릿](decisions/_template.md))
- 결정 상태: 제안, 결정, 보류, 대체됨

## 결정 목록

| 번호 | 제목 | 상태 | 결정일 |
|---|---|---|---|
| 0001 | [UI 개선은 feature 브랜치에서 작업](decisions/0001-feature-branch.md) | 결정 | 2026-11-23 |
| 0002 | [수정 범위를 프론트엔드로 제한](decisions/0002-frontend-only.md) | 결정 | 2026-11-23 |
| 0003 | [시안 전에 현행 진단과 유사 서비스 조사부터 진행](decisions/0003-research-before-draft.md) | 결정 | 2026-11-23 |
| 0004 | [디자인 조사와 결정을 doc/design에 기록](decisions/0004-design-docs.md) | 결정 | 2026-11-23 |
| 0005 | [AI가 만든 것처럼 보이는 디자인을 피함](decisions/0005-avoid-generated-look.md) | 결정 | 2026-11-23 |
| 0006 | [디자인 참고 서비스와 트렌드 기준](decisions/0006-design-references.md) | 결정 | 2026-11-23 |
| 0007 | [UI 시안 선택(시안 A, 원본 초록)](decisions/0007-draft-selection.md) | 결정 | 2026-11-23 |
| 0008 | [지도는 카카오맵으로 구현](decisions/0008-kakao-map.md) | 결정 | 2026-11-23 |
| 0009 | [디자인 규칙을 DESIGN.md로 정리](decisions/0009-design-md.md) | 결정 | 2026-11-23 |
| 0010 | [새 디자인은 화면 단위로 옮김](decisions/0010-css-migration.md) | 결정 | 2026-11-23 |
| 0011 | [홈(여행 조건) 화면은 학생이 개편](decisions/0011-home-by-student.md) | 결정 | 2026-11-23 |
| 0012 | [홈 여행 조건 입력을 간소화](decisions/0012-simplify-trip-form.md) | 대체됨(0014) | 2026-11-24 |
| 0013 | [현지 일정에서 관련 일일투어 안내](decisions/0013-related-day-tours.md) | 결정 | 2026-11-24 |
| 0014 | [홈 여행 조건 폼의 폭과 입력 줄 정리](decisions/0014-compact-trip-form.md) | 대체됨(0015) | 2026-11-24 |
| 0015 | [홈 여행 조건 폼을 가운데에 배치](decisions/0015-center-trip-form.md) | 대체됨(0016) | 2026-11-24 |
| 0016 | [여행 조건을 오른쪽 상단에 확대 배치](decisions/0016-home-trip-panel.md) | 결정 | 2026-11-24 |
| 0017 | [시외버스 터미널 연결과 접근 경로 표시](decisions/0017-intercity-terminal-paths.md) | 결정 | 2026-11-24 |
| 0018 | [날짜별 의견 코스를 독립 편집](decisions/0018-daily-route-opinions.md) | 결정 | 2026-11-24 |
| 0019 | [이동 경로에 열차 후보 표시](decisions/0019-intercity-trains.md) | 결정 | 2026-11-25 |

## 시안 목록

| 작성일 | 제목 | 관련 결정 |
|---|---|---|
| 2026-11-23 | [시안 A 지도 분할, 시안 B 노선도 견적서](drafts/2026-11-23-ui-drafts-a-b.html) | 0007 |
| 2026-11-23 | [시안을 실제 카카오맵 위에 올린 확인용 페이지](drafts/2026-11-23-kakao-preview.html) | 0007, 0008 |
| 2026-11-23 | [홈(여행 조건) 시안 A 검색 상자형, B 지도 분할형(참고용, 고르지 않음)](drafts/2026-11-23-home-drafts.html) | 0011 |

- 시안 A, B 파일은 브라우저로 바로 열린다. 위쪽 버튼으로 시안, 화면, 기기, 라이트와 다크, 색 조합을 바꿔 볼 수 있다. 결정된 것은 시안 A와 색 조합 "원본 초록"이다.
- 카카오맵 확인용 페이지는 파일을 그냥 열면 지도가 뜨지 않는다. 키 등록 도메인(`http://localhost:8080`)에서만 카카오맵이 동작하고, 키는 실행 중인 앱 화면에서 읽어 오기 때문이다. 여는 방법:
  1. 카카오맵 JavaScript 키를 넣은 PowerShell 창에서 앱을 실행한다(저장소 루트 README의 카카오맵 설정 참고).
  2. 이 파일을 `build/resources/main/static/drafts/kakao-preview.html`로 복사한다(`build/`는 커밋되지 않음).
  3. 브라우저에서 `http://localhost:8080/drafts/kakao-preview.html`을 연다.
- 두 파일에는 구현할 때 참고할 코드가 들어 있다: 금액 숫자 전환, 끌어서 옮기기(놓일 자리 표시, FLIP 이동), 카카오맵 `Polyline`과 `CustomOverlay`로 그린 경로 선, 이름표, 날짜 핀. 화면 문구는 시안용 반말이라 구현할 때는 DESIGN.md의 문구 규칙("~하세요", "~합니다")으로 바꾼다.

## 화면별 적용 상태

새 디자인으로 옮기는 방법은 DESIGN.md의 "새 디자인으로 옮기는 방법"에 있다.

| 화면 | 파일 | 상태 |
|---|---|---|
| 경로 비교 | `search.html`, `routes.js` | 적용(2026-11-23) |
| 여행 일정 | `itinerary.html`, `plan.js` | 적용(2026-11-23) |
| 공통 머리글 | `layout.html` | 적용(2026-11-23) |
| 홈 | `index.html`, `app.js` | 적용(2026-11-24, 0016) |
| 일일투어 | `tours.html`, `tours.js` | 남음(시안 먼저) |
| 비용 비교 | `compare.html`, `compare.js` | 남음(시안 먼저) |

## 다음 할 일

| 순서 | 할 일 | 참고 |
|---|---|---|
| 1 | ~~경로 비교 화면을 DESIGN.md대로 구현~~ 완료(2026-11-23). 공통 조각 `estimateCard`, `animateValues`, `modeIcon`(`common.js`)과 `createMap`, `drawJourney`(`route-map.js`)를 만듦 | 시안 A 이동 경로 화면, 진단 1~4번 |
| 2 | ~~여행 일정 화면 구현~~ 완료(2026-11-23). 지도 함수 `drawDays`, `dayPin`, `dayColor`(`route-map.js`) 추가. 장소 검색 추가는 서버에 카카오 REST API 키가 없어 확인하지 못함 | 시안 A 현지 일정 화면, 진단 5번 |
| 3 | ~~공통 머리글과 단계 탭, 여행 조건 묶음~~ 완료(2026-11-23). 다크 테마는 하지 않기로 함(0010). 옮기지 않은 일일투어, 비용 비교 화면의 예전 여행 요약 띠(`#trip-summary`)는 머리글과 겹치므로 그 화면을 옮길 때 지움 | 진단 1, 2번 |
| 4 | 구현하면서 DESIGN.md와 실제 코드가 다른 곳을 고치고, 결정 0007, 0008, 0009의 후속 확인 항목 처리 | 각 결정 기록 |
| 5 | 일일투어, 비용 비교 화면 시안 만들기 | 0007 영향 |

- Java 코드는 고치지 않는다(0002). 화면에 필요한 값(관광지 좌표, 구간 시간, 1인 금액)은 이미 API 응답에 있다.
- 구현 확인은 `.\gradlew.bat test`와 제주 샘플(홈의 "제주 샘플 불러오기") 흐름으로 한다.

## 조사 목록

| 조사일 | 제목 | 비고 |
|---|---|---|
| 2026-11-23 | [현행 UI 진단](research/2026-11-23-ui-diagnosis.md) | 문제 9건, 캡처 포함 공유본은 별도 HTML |
| 2026-11-23 | [유사 서비스 조사](research/2026-11-23-similar-services.md) | 서비스 16개, 적용 후보 21건 |

## 결정을 기다리는 질문

| 질문 | 관련 문서 |
|---|---|
| 개선의 1차 목표(사용 흐름 개선과 포트폴리오 첫인상 가운데 우선순위) | 현행 UI 진단 |
| 우선 대응할 화면 크기(데스크톱, 모바일) | 현행 UI 진단 8번 |
| Bootstrap 5 유지 여부 | 0002 |
| 학생 공유용 진단 HTML의 문체(지금은 지도 교사에게 쓴 반말) | 현행 UI 진단 |
| 원본 초록을 고른 구체적인 이유(0007 근거 보완) | 0007 |
