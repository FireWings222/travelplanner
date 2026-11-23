# TravelPlanner 작업 지침

국내여행(서울, 부산, 제주) 교통편, 일정, 일일투어 비용을 비교하는 Spring Boot 웹 앱이다. 폴리텍 분당융합기술원 학생의 포트폴리오 프로젝트다.

## 브랜치 규칙

- `main`은 학생이 작업한다. 지도 목적의 작업은 항상 `feature/*` 브랜치를 새로 만들어서 한다. `main`에 직접 커밋하지 않는다.
- push와 병합은 사용자가 한다.

## 수정 범위

- UI 개선 작업은 프론트엔드만 고친다. Java 코드(`src/main/java`), DB 마이그레이션, 설정 파일은 건드리지 않는다. Java 수정이 필요해 보이면 먼저 사용자에게 묻는다.
- 프론트엔드 파일
  - `src/main/resources/templates/*.html`: Thymeleaf 템플릿(`layout.html`이 공통 head, header, footer)
  - `src/main/resources/static/css/site.css`: 공통 스타일(Bootstrap 5.3.8 webjar 위에 덮어씀)
  - `src/main/resources/static/js/*.js`: ES 모듈. `app.js`가 `body[data-page]`로 화면 모듈을 불러오고, `common.js`에 API 호출, 세션 저장소, 공통 렌더링 함수가 있다.
- 화면 HTML 대부분은 JS 문자열로 만들어진다. 마크업을 바꿀 때는 템플릿과 JS를 함께 본다. 동적 값은 `e()`로 이스케이프한다.
- REST API(`/api/...`) 요청과 응답 형식은 바꾸지 않는다. 형식은 `dto/Travel.java`를 읽어서 확인만 한다.

## 실행과 테스트

- 실행: `.\gradlew.bat bootRun` 후 http://localhost:8080 (JDK 17). 앱 실행은 사용자에게 먼저 묻는다.
- 테스트: `.\gradlew.bat test`

## 문서화

- UI 개선 조사 결과와 의사결정은 `doc/ui-redesign/`에 마크다운으로 남긴다. 결정은 결정 기록 문서에 날짜, 선택지, 결정, 근거로 추가한다.

## 사용자와 일하는 방식

- 인터뷰나 확인 질문은 선택지 도구 대신 대화로 묻는다. 열린 질문을 한 번에 하나씩 하고, 답을 듣고 다음 질문으로 넘어간다. 추천안이 있으면 질문 뒤에 짧게 덧붙인다.
- 결정할 일이 생기면 혼자 정하지 않고 먼저 묻는다.
- 지시를 받으면 바로 도구를 실행하지 않는다. 무엇을 어떻게 할지 먼저 답한 뒤 작업을 시작한다.
