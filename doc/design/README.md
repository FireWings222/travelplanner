# TravelPlanner 디자인 문서

TravelPlanner 화면 디자인에 관한 조사와 결정 기록이다. 작성 규칙은 [0004 디자인 문서 관리](decisions/0004-design-docs.md)를 따른다.

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

## 시안 목록

| 작성일 | 제목 | 관련 결정 |
|---|---|---|
| 2026-11-23 | [시안 A 지도 분할, 시안 B 노선도 견적서](drafts/2026-11-23-ui-drafts-a-b.html) | 0007 |

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
| 학생 공유용 진단 HTML의 저장소 포함 여부와 문체 | 현행 UI 진단 |
