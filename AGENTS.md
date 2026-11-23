# TravelPlanner 작업 지침

Codex 같은 AI 코딩 도구가 이 저장소에서 작업할 때 따르는 규칙이다. 사람이 읽어도 된다.

- 사용자와는 한국어로 대화한다. 화면 문구, 문서, 코드 주석도 한국어로 쓴다. 코드 식별자와 기술 용어는 원래 표기를 쓴다.
- `CLAUDE.md`는 지도 교사가 쓰는 Claude Code용 지침이라 이 문서와 규칙이 다르다(지도 교사는 feature 브랜치에서 프론트엔드만 고친다). 학생 작업에는 이 문서를 따른다.

## 프로젝트

국내여행(서울, 부산, 제주)의 교통편, 현지 일정, 일일투어 비용을 비교하는 Spring Boot 웹 앱이다. 폴리텍 분당융합기술원 학생의 포트폴리오다.

| 위치 | 내용 |
|---|---|
| `src/main/java/com/example/travelplanner` | `controller`(화면, REST API), `service`, `dto`(`Travel.java`에 API 요청과 응답 형식), `entity`, `repository`, `integration`(ODsay, 카카오 연동, 샘플 데이터), `pricing`, `recommendation`, `exception` |
| `src/main/resources/db/migration` | Flyway 마이그레이션(H2 기본, PostgreSQL 설정 별도) |
| `src/main/resources/templates` | Thymeleaf 템플릿. `layout.html`이 공통 head, 머리글, 바닥글 |
| `src/main/resources/static/js` | ES 모듈. `app.js`가 `body[data-page]`로 화면 모듈(`routes.js`, `plan.js`, `tours.js`, `compare.js`)을 불러옴. `common.js`에 API 호출, 세션 저장소, 공통 렌더링 함수, `route-map.js`에 카카오맵 함수 |
| `src/main/resources/static/css/site.css` | 공통 스타일(Bootstrap 5.3.8 webjar 위에 덮어씀) |
| `src/test/java` | JUnit 테스트 |

## 실행과 테스트

- 실행: `.\gradlew.bat bootRun` 후 http://localhost:8080 (JDK 17)
- 테스트: `.\gradlew.bat test`. 기능을 고치면 테스트를 실행하고, 동작을 바꾸는 수정이면 테스트도 함께 고친다.
- 카카오맵은 PowerShell 창에서 `$env:KAKAO_MAP_JS_KEY`를 넣고 같은 창에서 `bootRun`을 실행해야 화면에 나온다. 카카오 앱의 JavaScript SDK 도메인에 `http://localhost:8080`이 등록돼 있다. 키 값은 출력하거나 파일에 쓰지 않는다.
- 다른 API 키(`ODSAY_API_KEY`, `KAKAO_MOBILITY_REST_API_KEY`, `KAKAO_MAP_REST_API_KEY`)는 `README.md`를 따른다. 키가 없으면 샘플 데이터로 동작한다.
- 템플릿(`templates/*.html`)을 고치면 앱을 다시 실행해야 화면에 반영된다.

## 화면 작업 규칙

- 화면을 만들거나 고칠 때는 저장소 루트 `DESIGN.md`(색, 글꼴, 간격, 컴포넌트, 움직임, 화면 문구)를 따른다. 규칙에 없는 값이 필요하면 코드에 바로 쓰지 말고 `DESIGN.md`에 먼저 추가한다.
- 새 디자인은 화면 단위로 옮기는 중이다. 옮긴 화면의 `<body>`에는 `redesign` 클래스가 붙어 있고, 스타일은 `site.css` 맨 아래 "새 디자인" 구역에 있다. 화면을 옮기는 순서와 방법은 `DESIGN.md`의 "새 디자인으로 옮기는 방법"에 있다.
- 화면별 적용 상태와 다음 할 일은 `doc/design/README.md`에 있다. 경로 비교, 여행 일정, 공통 머리글은 옮겼고, 홈은 학생이 개편하기로 했다(결정 0011, 참고 시안 `doc/design/drafts/2026-11-23-home-drafts.html`).
- 여러 화면에서 쓰는 조각은 새로 만들지 말고 있는 함수를 쓴다: `estimateCard`, `animateValues`, `modeIcon`, `renderHeader`(`common.js`), `createMap`, `drawJourney`, `drawDays`(`route-map.js`)
- 화면 HTML 대부분은 JS 문자열로 만든다. 마크업을 바꿀 때는 템플릿과 JS를 함께 보고, 동적 값은 `e()`로 이스케이프한다.
- 화면 문구는 "~하세요", "~합니다" 말투로 쓰고, 가운데 점(`·`)과 줄표(`—`)는 쓰지 않는다. 모르는 금액은 0원이 아니라 "미확인"으로 쓴다.
- 데스크톱(1280px 정도)과 모바일(390px) 화면을 모두 확인한다.

## API와 데이터

- REST API 요청과 응답 형식은 `dto/Travel.java`에 있다. 형식을 바꾸면 그 API를 쓰는 화면 JS(`static/js`)도 같이 고친다.
- DB 구조를 바꿀 때는 기존 마이그레이션 파일을 고치지 말고 `db/migration`에 새 버전 파일을 추가한다.

## 디자인 기록

- 디자인 관련 조사와 결정은 `doc/design/`에 남긴다. 목록은 `doc/design/README.md`에서 관리한다.
  - 조사: `research/YYYY-MM-DD-주제.md`
  - 결정: `decisions/NNNN-주제.md`(`decisions/_template.md` 형식, 결정 하나에 파일 하나)
  - 시안: `drafts/YYYY-MM-DD-주제.html`
- 결정을 바꿀 때는 기존 파일을 지우지 않고 상태를 "대체됨"으로 바꾼 뒤 새 번호의 결정과 서로 링크한다.
- 파일 이름은 영문 소문자와 하이픈으로 쓴다.
