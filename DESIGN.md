---
version: alpha
name: TravelPlanner
description: 국내여행 이동 경로, 현지 일정, 일일투어 비용을 한 화면에서 비교하는 웹 앱의 디자인 규칙. 왼쪽 목록과 오른쪽 카카오맵을 나란히 두는 지도 분할 배치(시안 A)와 학생 원본 초록을 기준으로 한다.
colors:
  primary: "#146748"
  on-primary: "#ffffff"
  ink: "#15171c"
  ink-2: "#454a54"
  ink-3: "#6a707c"
  surface: "#ffffff"
  field: "#f0f1f4"
  field-2: "#e2e4e9"
  rule: "#d0d3d9"
  selection: "#15171c"
  flash: "#dcefe3"
  warn: "#a14a00"
  over: "#c92a2a"
  mode-rail: "#0d6fc4"
  mode-air: "#146748"
  mode-bus: "#d43d2a"
  mode-road: "#a87900"
  day-1: "#146748"
  day-2: "#0d6fc4"
  day-3: "#c2410c"
  dark:
    primary: "#52c591"
    on-primary: "#06140d"
    ink: "#eceef2"
    ink-2: "#b6bbc5"
    ink-3: "#8d93a0"
    surface: "#1a1c21"
    field: "#101216"
    field-2: "#282b33"
    rule: "#383c46"
    selection: "#eceef2"
    flash: "#1d3a2b"
    warn: "#f0a35a"
    over: "#ff8787"
typography:
  title-lg:
    fontFamily: Pretendard
    fontSize: 21px
    fontWeight: 800
    lineHeight: 1.3
  amount-xl:
    fontFamily: Pretendard
    fontSize: 31px
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: -0.01em
    fontFeature: '"tnum"'
  amount-md:
    fontFamily: Pretendard
    fontSize: 17px
    fontWeight: 700
    lineHeight: 1.3
    fontFeature: '"tnum"'
  card-title:
    fontFamily: Pretendard
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.45
  body-md:
    fontFamily: Pretendard
    fontSize: 15px
    fontWeight: 400
    lineHeight: 1.45
  body-sm:
    fontFamily: Pretendard
    fontSize: 14.5px
    fontWeight: 400
    lineHeight: 1.45
  caption:
    fontFamily: Pretendard
    fontSize: 13.5px
    fontWeight: 400
    lineHeight: 1.4
  label-tag:
    fontFamily: Pretendard
    fontSize: 12px
    fontWeight: 700
    lineHeight: 1.3
rounded:
  tag: 4px
  control: 8px
  field: 10px
  card: 12px
  map: 14px
  sheet: 18px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
motion:
  number-roll:
    duration: 620ms
    timingFunction: cubic-bezier(0.33, 1, 0.68, 1)
  reorder:
    duration: 320ms
    timingFunction: cubic-bezier(0.2, 0.7, 0.2, 1)
  drop-slot:
    duration: 160ms
    timingFunction: ease-out
  recalculating:
    duration: 900ms
    timingFunction: linear
  row-flash:
    duration: 1200ms
    timingFunction: ease-out
  moved-highlight:
    duration: 1600ms
    timingFunction: ease-out
  delta-badge:
    duration: 1800ms
    timingFunction: ease-out
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.body-md}"
    rounded: "{rounded.field}"
    padding: 9px 15px
  button-outline:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.control}"
    padding: 9px 12px
  route-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.card-title}"
    rounded: "{rounded.card}"
    padding: 14px 16px
  tag:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.surface}"
    typography: "{typography.label-tag}"
    rounded: "{rounded.tag}"
    padding: 2px 6px
  tag-highlight:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-tag}"
    rounded: "{rounded.tag}"
    padding: 2px 6px
  estimate-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.amount-xl}"
    rounded: "{rounded.card}"
    padding: 16px 18px
  stop-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.card-title}"
    rounded: "{rounded.card}"
    padding: 13px 14px 13px 10px
---

# TravelPlanner 디자인 규칙

이 문서는 TravelPlanner 화면을 만들거나 고칠 때 따르는 규칙이다. 사람도 읽고 AI 코딩 도구(Claude Code, Cursor 등)도 읽는다. 형식은 Google이 공개한 DESIGN.md 명세(`version: alpha`)를 따른다. 맨 위 YAML은 값, 아래 본문은 그 값을 쓰는 방법이다.

- 기준 시안: 시안 A 지도 분할, 색 조합 원본 초록(`doc/design/decisions/0007-draft-selection.md`)
- 결정 배경: `doc/design/README.md`의 결정 목록
- 적용 상태: 이 문서는 목표 디자인이다. 경로 비교와 여행 일정 화면부터 `site.css`와 화면 JS에 적용하고, 적용하면서 이 문서와 실제 코드가 다른 곳을 고친다.

## Overview

국내여행의 "가는 길, 현지 일정, 일일투어"를 비용과 시간으로 비교하는 도구다. 화면은 계산 결과를 빨리 읽고 바로 바꿔 볼 수 있어야 한다.

- 배치: 왼쪽은 고르고 고치는 목록, 오른쪽은 결과가 보이는 카카오맵. 여행 견적(합계와 예산)은 항상 화면에 떠 있다.
- 세련된 인상은 장식이 아니라 배치와 동작에서 낸다. 지도와 목록을 나란히 두기, 끌어서 옮기기, 바뀐 값이 움직여서 보이기 같은 것이다(결정 0006).
- AI가 만든 화면처럼 보이는 흔한 장식을 쓰지 않는다(결정 0005). 구체적인 목록은 Do's and Don'ts에 있다.
- 참고 서비스: Rome2Rio(경로 카드와 큰 지도), Wanderlog(일정과 지도를 한 화면에, 끌어서 순서 바꾸기)

## Colors

- 기본은 흰 면(`surface`)과 중성 회색 바닥(`field`)이다. 초록 기가 도는 회색이나 옅은 초록 바탕을 쓰지 않는다.
- `primary`(학생 원본 초록 #146748)는 금액, 주 버튼, 현재 단계 표시, 로고에만 쓴다. 넓은 면을 초록으로 칠하지 않는다.
- 선택 상태는 색이 아니라 `selection`(거의 검정) 2px 테두리로 보여 준다. 옅은 초록 바탕으로 선택을 표시하지 않는다.
- 글자는 `ink`(본문), `ink-2`(보조 설명), `ink-3`(비활성, 자리 표시)의 3단계만 쓴다. 회색 글자를 본문에 쓰지 않는다.
- 선(`rule`)은 같은 카드 안에서 내용을 나눌 때만 쓴다. 구역과 구역은 선이 아니라 면(`surface`와 `field`의 차이)으로 나눈다.
- `flash`는 값이 바뀐 줄을 잠깐 칠하는 데만 쓴다. `warn`은 확인이 필요한 값, `over`는 예산 초과에 쓴다.
- 교통수단 색(`mode-*`)은 아이콘, 지도 선, 구간 표시에 쓴다. 철도는 파랑, 항공은 브랜드 초록, 버스는 빨강, 택시와 자가용은 황토색이다.
- 일정 날짜 색(`day-1`부터 `day-3`)은 날짜 버튼의 점, 지도 핀, 날짜 선에 쓴다. 4일 이상이면 같은 계열에서 색을 더 정하고 이 문서에 추가한다.
- 다크 테마 값은 `colors.dark`에 있다. 카카오맵 바탕은 다크 테마에서도 밝은 지도라서, 지도 위에 올리는 선과 핀은 다크 테마에서도 라이트 값을 쓴다.
- CSS에서는 `:root`에 변수로 정의한다. 이름은 시안 코드와 맞춘다: `--brand`(primary), `--ink`, `--ink-2`, `--ink-3`, `--paper`(surface), `--field`, `--field-2`, `--rule`, `--sel`(selection), `--flash`, `--warn`, `--over`, `--m-rail`, `--m-air`, `--m-bus`, `--m-road`, `--d1`, `--d2`, `--d3`.

## Typography

- 글꼴은 Pretendard 하나만 쓴다(학생 원본 `site.css`와 같음). 대체 글꼴 순서: `"Pretendard Variable", Pretendard, Inter, "Noto Sans KR", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif`
- 숫자(금액, 시간, 거리)는 같은 글꼴에 고정폭 숫자(`font-variant-numeric: tabular-nums`)를 쓴다. 숫자만 다른 고정폭 글꼴로 바꾸지 않는다.
- 크기 단계는 YAML의 typography 토큰만 쓴다. 새 크기가 필요하면 먼저 이 문서에 토큰을 추가한다.
  - `title-lg`: 목록 맨 위 제목(예: "서울에서 제주까지 12가지 방법")
  - `amount-xl`: 여행 견적 합계. 모바일 하단 막대에서는 23px
  - `amount-md`: 경로 카드 금액
  - `card-title`: 경로 카드 제목, 장소 이름. 선택된 카드는 굵기 800
  - `body-md`: 기본 본문, `body-sm`: 구간 내역, 견적 줄, `caption`: 보조 설명, `label-tag`: 꼬리표
- 한글 제목에 좁은 글자 간격(예: `-0.085em`)을 주지 않는다. 합계와 로고에만 `-0.01em`, `-0.02em`까지 쓴다.
- 한글 줄바꿈은 `word-break: keep-all`로 단어 단위로 한다.

## Layout

- 데스크톱(틀 폭 861px 이상)
  - 머리글: 로고, 여행 조건 묶음(출발, 도착, 일정, 인원, 수정), 오른쪽 끝에 단계 탭. 높이 약 62px
  - 본문: `field` 바닥 위에 왼쪽 목록(폭 380~440px, 세로 스크롤)과 오른쪽 지도(나머지 폭, 모서리 `map`, 바깥 여백 12px)
  - 여행 견적 카드는 지도 오른쪽 아래에 떠 있다(폭 약 292px, 바깥 여백 14px).
- 모바일(860px 이하)
  - 위쪽에 지도(높이 약 300px), 그 아래에 끌어 올리는 목록(bottom sheet, 위 모서리 `sheet`, 손잡이 막대)
  - 여행 견적은 목록 맨 아래에 붙는 막대(합계와 예산 막대만, 누르면 내역이 펼쳐짐)
  - 단계 탭은 번호만 보이고 현재 단계만 이름을 보인다.
  - 여행 조건 묶음은 출발, 도착, 일정만 보이고 인원은 숨긴다.
- 간격은 spacing 토큰(4, 8, 12, 16, 20px)만 쓴다. 목록 카드 사이는 8px, 카드 안쪽은 14~16px이다.
- 머리글 아래부터 화면 끝까지 한 화면에 들어오게 하고, 스크롤은 목록 안에서만 생기게 한다.

## Elevation & Depth

- 그림자는 세 종류만 쓴다.
  - 떠 있는 카드(여행 견적, 지도 위 버튼): `0 2px 10px rgb(18 24 38 / 18%), 0 1px 2px rgb(18 24 38 / 12%)`
  - 지도 이름표: `0 1px 3px rgb(18 24 38 / 30%)`
  - 정렬 버튼의 눌린 칸, 모바일 시트 위쪽 경계: `0 1px 2px rgb(18 24 38 / 16%)`, `0 -4px 14px rgb(18 24 38 / 16%)`
- 목록 카드(경로 카드, 장소 카드)에는 그림자를 주지 않는다. `field` 바닥 위의 흰 면으로 구분한다.

## Shapes

- 모서리는 rounded 토큰만 쓴다: 꼬리표 4px, 버튼과 입력 8px, 묶음 상자와 날짜 버튼 10px, 카드 12px, 지도 칸 14px, 모바일 시트 18px
- 알약 모양(완전히 둥근 끝)은 쓰지 않는다. 예외는 지도 핀과 날짜 색 점처럼 원이어야 하는 표시뿐이다.

## Motion

움직임은 "무엇이 바뀌었는지" 알려 주려고 쓴다. 튀는 효과(바운스), 회전, 반짝임은 쓰지 않는다. 이동, 투명도, 배경색만 쓴다.

- 금액이 바뀔 때(`number-roll`): 이전 값에서 새 값으로 숫자가 굴러가듯 바뀐다. 예산 막대 폭도 같이 바뀐다. 합계 옆에 차이("+130,000원", "-800원")를 `delta-badge` 동안 띄웠다가 없앤다. 바뀐 줄은 `row-flash` 동안 `flash` 색에서 투명으로 빠진다.
- 끌어서 옮길 때: 끄는 동안 원래 카드는 흐리게(투명도 0.35), 놓일 자리에 점선 칸("여기에 놓기")을 `drop-slot` 시간에 벌린다. 놓으면 카드들이 FLIP 방식으로 새 자리로 미끄러진다(`reorder`). 옮긴 카드는 `moved-highlight` 동안 테두리와 배경이 빠진다.
- 다시 계산할 때(`recalculating`): 바뀐 이동 구간에 "다시 계산 중"과 진행 선을 보여 준 뒤 값을 보여 준다.
- 움직임 줄이기 설정(`prefers-reduced-motion: reduce`)에서는 모든 애니메이션을 끄고 최종 값을 바로 보여 준다.

## Voice & Content Strategy

- 화면 문구는 학생이 쓴 기존 앱 말투를 따른다: 안내는 "~하세요", 상태는 "~합니다", "~중입니다". 반말을 쓰지 않는다.
- 금액은 `134,900원`처럼 천 단위 쉼표와 "원", 시간은 "2시간 13분", 거리는 "16km"로 쓴다.
- 값을 모르면 0원으로 보이게 하지 않고 "미확인"이라고 쓴다. 합계 일부만 있으면 "(부분 합계)"를 붙인다.
- 가정이 들어간 값은 가정을 밝힌다(예: "09:00 출발로 가정한 도착 시각", "왕복은 편도 요금의 두 배로 추정").
- 샘플 데이터 표시("테스트용 가상 가격")는 구간마다 반복하지 않고 화면에 한 번만 보여 준다.
- 문구에 가운데 점(`·`), 줄표(`—`)를 쓰지 않는다. 쉼표나 괄호로 쓴다.

## Components

컴포넌트 값은 YAML의 `components`에 있다. 여기서는 언제 쓰는지 적는다.

- **단계 탭**: 여행 조건, 이동 경로, 현지 일정, 일일투어 비교. 지난 단계는 체크 표시, 현재 단계는 3px 밑줄(`primary`)과 굵은 글자. 알약 모양 칩으로 만들지 않는다.
- **여행 조건 묶음**: `field` 바탕의 칸 묶음에 작은 이름(11.5px, 굵게)과 값(15px, 굵게)을 위아래로 쓴다. 맨 오른쪽에 "수정" 글자 버튼.
- **정렬 묶음 버튼**: 추천순, 최저가, 최단 시간. `field-2` 바탕 안에서 눌린 칸만 흰 면과 그림자.
- **경로 카드**(`route-card`): 제목(교통수단을 쉼표로 이음), 오른쪽 금액(`amount-md`, `primary`), 아래 줄에 교통수단 아이콘과 소요 시간과 꼬리표, 오른쪽에 1인 금액. 선택하면 2px `selection` 테두리와 함께 구간 내역이 카드 안에 펼쳐진다. 누르면 그 경로가 바로 적용된다.
- **꼬리표**(`tag`, `tag-highlight`): 경로 하나에 하나까지만 단다. 최저가와 균형처럼 여러 개면 "최저가, 균형"으로 한 꼬리표에 합친다. 최저가는 `tag-highlight`, 그 밖은 `tag`
- **여행 견적 카드**(`estimate-card`): 이름("여행 견적"), 예산 비율, 합계(`amount-xl`), 예산 막대(높이 8px, 모서리 2px), 남은 예산, 내역 줄(왕복 교통, 현지 일정, 일일투어), "일일투어 더해 보기" 테두리 버튼, "숙박비와 식비는 포함하지 않음" 안내
- **일정 날짜 버튼**: 3칸 나란히, 날짜 색 점과 "1일차", 아래 작은 글씨로 날짜. 선택은 2px `selection` 테두리
- **장소 카드**(`stop-card`): 왼쪽 끌기 손잡이, 날짜 색 번호 핀, 예상 도착 시각(굵게), 장소 이름, 설명과 머무는 시간, 오른쪽 입장료("무료" 또는 금액)
- **이동 구간 줄**: 장소 카드 사이에 점선 세로줄과 교통수단 아이콘, 교통수단 선택 상자, 시간과 거리, 금액. 구간 카드를 따로 만들지 않는다.
- **주 버튼**(`button-primary`): 한 화면에 하나만 둔다(예: 일정 저장). 나머지는 테두리 버튼(`button-outline`)이나 글자 버튼. 시안에는 주 버튼이 없어서 값은 학생 원본 `site.css`의 `.primary`(모서리 10px, 안쪽 여백 9px 15px)를 따랐다.
- **카카오맵 위 요소**(결정 0008)
  - 경로 선: 교통수단 색 선(굵기 5px, 작은 구간 3px) 아래에 5px 더 굵은 흰 선을 깔아 테두리를 만든다(`Polyline` 두 번).
  - 항공 구간: 두 공항 사이를 곡선 점으로 이어 그리고 가운데에 비행기 표시
  - 이름표: 흰 바탕, 모서리 7px, 안쪽 여백 5px 10px 6px, 지도 이름표 그림자, 굵은 이름(13px, 700)과 작은 보조 줄(12px, 소요 시간 등). `CustomOverlay`로 올린다.
  - 날짜 핀: 날짜 색 물방울 핀 안에 흰 번호. 고르지 않은 날짜의 핀과 선은 흐리게(회색 점과 점선)
  - 지도 범위: 경로나 날짜가 바뀌면 `setBounds`로 다시 맞추고, 지도 크기가 바뀌면 `relayout()` 뒤 다시 맞춘다. 견적 카드가 가리는 만큼 오른쪽 여백을 둔다.

## Do's and Don'ts

| 하지 말 것 | 대신 할 것 |
|---|---|
| 그라데이션, 유리 효과, 이모지 장식 | 흰 면과 회색 바닥, 실제 데이터(지도, 금액, 시간) |
| 모든 구역을 같은 둥근 카드와 그림자로 감싸기 | 구역은 면 차이로, 그림자는 떠 있는 요소에만 |
| 알약 모양 칩을 여러 곳에 쓰기 | 단계는 밑줄 탭, 정렬은 묶음 버튼, 꼬리표는 경로당 하나 |
| 흐린 1px 선으로 모든 것을 나누기 | 선은 카드 안 내용 구분에만 |
| 옅은 초록 바탕으로 선택 표시 | 2px 진한 테두리 |
| 작은 회색 글자로 본문 쓰기 | `ink` 본문, 금액과 합계는 크고 굵게 |
| 눌러도 반응 없는 장식 카드 | 보이는 카드는 모두 누를 수 있거나 정보를 담음 |
| 화면마다 아주 큰 제목과 설명 문장 | 작업 화면은 제목을 작게, 결과를 위로 |
| 값이 바뀔 때 화면이 "짠" 하고 바뀌기 | Motion의 숫자 전환, 끌기 자리 표시, 이동 애니메이션 |
| 0원으로 보이는 미확인 값 | "미확인", "(부분 합계)" |

## 새 화면이나 버튼을 만들 때 점검할 것

1. 이미 있는 컴포넌트(Components)로 만들 수 있는지 먼저 본다. 새 컴포넌트가 필요하면 이 문서의 YAML `components`와 Components 설명에 먼저 추가한다.
2. 색, 글자 크기, 모서리, 간격은 토큰 값만 쓴다. 코드에 새 색 값을 직접 쓰지 않는다.
3. 주 버튼은 화면에 하나인지 확인한다.
4. 값이 바뀌는 곳에는 Motion 규칙을 적용하고, 움직임 줄이기 설정에서 바로 바뀌는지 확인한다.
5. 라이트와 다크 테마, 데스크톱(1280px 정도)과 모바일(390px) 화면을 모두 확인한다.
6. Do's and Don'ts 표로 한 번 더 확인한다.
7. 화면 문구가 Voice & Content Strategy를 따르는지 확인한다.
8. 디자인 결정을 새로 했다면 `doc/design/decisions/`에 결정 기록을 남긴다.
