# TravelPlanner 국내여행 플래너

TravelPlanner는 국내여행의 이동 경로, 현지 일정, 일일투어 비용을 비교하는 포트폴리오용 웹 앱입니다. 전국 17개 시와 도를 지원하며, 카탈로그에 지역별 3개씩 총 51개의 일일투어 코스를 담고 있습니다.

## 현재 구현된 기능

| 화면 | 사용 방법 |
| --- | --- |
| 여행 조건 `/` | 가운데 카드에서 출발지, 목적지, 시작일, 종료일, 성인과 아동 인원을 입력합니다. 시와 도 이름을 사용하면 지역 대표 지점으로 계산합니다. 정확한 장소가 필요하면 역, 공항, 숙소 이름이나 주소를 입력하고 **Enter**를 누른 뒤 검색 결과를 선택합니다. |
| 이동 경로 `/search` | 교통수단별 예상 비용과 소요 시간을 비교하고 추천순, 최저가, 최단 시간으로 정렬할 수 있습니다. 구간별 교통수단과 요금을 바꿀 수 있습니다. 지도에서는 조회된 경로를 그리고 대중교통 탑승과 도보를 구분합니다. |
| 현지 일정 `/itinerary` | 날짜별로 관광지나 숙소를 추가하고 방문 순서를 바꿉니다. 마우스와 터치로 끌어 옮길 수 있고, 키보드에서는 순서 변경 손잡이에 초점을 둔 뒤 위아래 화살표를 사용합니다. 구간별 교통비와 입장료를 확인하고 일정을 저장하거나 다시 열 수 있습니다. |
| 일일투어 둘러보기 `/tours` | 지역, 날짜, 플랫폼으로 카탈로그와 기존 등록 상품을 조회합니다. 방문지, 요금, 출처 또는 예약 링크를 확인하고 비교 목록에 담을 수 있습니다. 상품 직접 등록 폼은 제거했습니다. |
| 비용 비교 `/compare` | 투어와 같은 방문지를 자유여행으로 둘러보는 경우 또는 선택 날짜의 내 일정과 비교합니다. 인원, 교통수단, 추가 비용을 바꾸어 재계산할 수 있습니다. |

같은 시나 도에 속하는 서로 다른 장소도 출발지와 목적지로 선택할 수 있습니다. 새 현지 일정은 빈 상태에서 시작하며, 홈에서 세부 목적지를 골랐다면 첫날 첫 방문지로 추가됩니다. 여행 조건의 예산, 여행 유형, 선호 교통수단 입력은 제거했습니다.

### 현지 일정에서 일일투어 찾기

1. 현지 일정에서 날짜를 고르고 **장소 검색해서 추가**를 엽니다.
2. 장소명으로 검색한 뒤 결과에서 **저장된 관광지**를 추가합니다. 카카오맵의 장소명과 주소 검색 결과도 일정에 추가할 수 있습니다.
3. 해당 날짜의 관광지와 방문지가 겹치는 일일투어가 카드에 표시됩니다. 겹치는 관광지가 많은 투어가 먼저 나옵니다.
4. **지도에서 보기**를 누르면 방문 순서를 목록과 번호 핀으로 확인할 수 있습니다. **자유여행과 비용 비교**를 누르면 같은 날짜의 내 일정으로 비교 화면을 엽니다.

투어 추천은 카탈로그 관광지 ID를 기준으로 합니다. 카카오맵에서 검색한 장소에는 별도 ID가 적용되므로, 같은 관광지의 추천을 보려면 **저장된 관광지** 검색 결과를 선택하세요. 투어 지도는 저장된 방문지와 순서를 표시합니다. 투어 버스의 실제 운행 경로는 카탈로그에 포함되어 있지 않습니다.

## 로컬에서 실행하기

JDK 17이 필요합니다. `build.gradle`이 있는 프로젝트 폴더에서 다음 명령을 실행합니다. 첫 실행에는 Gradle과 의존성을 내려받을 네트워크 연결이 필요합니다.

```powershell
java -version
.\gradlew.bat bootRun
```

브라우저에서 [TravelPlanner 열기](http://localhost:8080)를 선택합니다. IntelliJ IDEA에서는 폴더를 Gradle 프로젝트로 열고 Gradle JVM에 JDK 17을 지정한 뒤 `TravelplannerApplication`을 실행할 수 있습니다. macOS와 Linux에서는 `.\gradlew.bat` 대신 `./gradlew`를 사용합니다.

기본 DB는 H2이며 Flyway가 시작할 때 테이블을 준비합니다. 저장 일정과 기존 등록 상품은 `data/travelplanner.mv.db`에 보관합니다. `data/`와 IntelliJ의 로컬 설정 폴더인 `.idea/`는 Git 추적 대상에서 제외됩니다. 다른 PC에서 같은 소스를 실행하면 그 PC의 로컬 DB를 사용합니다. 기존 해외 일정은 저장 목록에서 **지원 종료**로 표시됩니다.

API 키 없이도 지역 대표 지점으로 검색하고, 저장된 관광지로 일정을 만들거나, 투어 카탈로그를 조회할 수 있습니다. 외부에서 받아야 하는 지도, 상세 장소, 교통 요금과 시간은 해당 API 설정과 응답에 따라 제공됩니다.

### API 키 설정

PowerShell에서는 앱을 실행할 창에서 필요한 환경변수만 설정합니다. IntelliJ에서는 Spring Boot 또는 Application 실행 구성의 **환경 변수**에 같은 이름을 추가합니다. 값을 변경한 뒤 앱을 다시 실행하세요. 키는 로컬 실행 환경에서 관리합니다.

| 환경변수 | 용도 |
| --- | --- |
| `KAKAO_MAP_JS_KEY` | 브라우저에서 카카오맵을 표시할 JavaScript 키. JavaScript SDK 도메인에 브라우저에서 접속하는 앱 주소를 등록합니다. 기본 주소는 `http://localhost:8080`입니다. |
| `KAKAO_MAP_REST_API_KEY` | 서버의 카카오 장소명과 주소 검색, 대중교통과 도보 경로 조회에 사용합니다. 변수를 설정하지 않으면 카카오모빌리티 REST 키를 참조합니다. |
| `KAKAO_MOBILITY_REST_API_KEY` | 자가용과 택시의 도로 경로, 거리, 예상 시간을 조회합니다. 이 값이 비어 있으면 카카오맵 REST 키를 참조합니다. 사용한 키에 자동차 길찾기 권한이 필요합니다. |
| `ODSAY_API_KEY` | 카카오 대중교통 경로를 얻지 못한 경우 ODsay 경로를 조회합니다. ODsay의 Server 키를 사용하고 Server IP에는 앱이 외부로 요청을 보내는 공인 IP를 등록합니다. |

설정 예시는 다음과 같습니다. 필요한 줄만 실행한 뒤 같은 창에서 앱을 시작하세요.

```powershell
$env:KAKAO_MAP_JS_KEY = Read-Host '카카오맵 JavaScript 키'
$env:KAKAO_MAP_REST_API_KEY = Read-Host '카카오맵 REST API 키'
$env:KAKAO_MOBILITY_REST_API_KEY = Read-Host '카카오모빌리티 REST API 키'
$env:ODSAY_API_KEY = Read-Host 'ODsay Server 키'
.\gradlew.bat bootRun
```

REST 키에 호출 허용 IP를 지정했다면 앱 서버에서 외부로 요청을 보내는 IP를 확인하세요. [카카오맵 REST API 문서](https://developers.kakao.com/docs/ko/kakaomap/rest-api), [카카오모빌리티 길찾기 문서](https://developers.kakaomobility.com/guide/navi-api/start)

### PostgreSQL 사용하기

PostgreSQL에 DB와 앱 계정을 만들고, Flyway가 테이블과 인덱스를 생성할 수 있도록 해당 스키마에 권한을 부여합니다.

| 환경변수 | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `TRAVELPLANNER_DB_URL` | 예시 `jdbc:postgresql://localhost:5432/travelplanner` |
| `TRAVELPLANNER_DB_USER` | PostgreSQL 앱 계정 |
| `TRAVELPLANNER_DB_PASSWORD` | PostgreSQL 앱 계정 비밀번호 |

기본 포트 8080이 사용 중이면 `SERVER_PORT`로 바꿀 수 있습니다. 지도 SDK를 사용한다면 변경한 접속 주소도 도메인 설정에 반영하세요.

## 비용과 지도 표시 기준

- 요금은 원화로 계산합니다. 성인과 아동의 1인 요금은 인원수를 곱하고, 택시처럼 일행이 함께 내는 요금은 구간당 한 번 더합니다. 확인하지 못한 항목은 **미확인**으로 표시하고 확인된 금액만 부분 합계에 넣습니다. 왕복 예상 운임은 확인된 편도 운임의 두 배입니다.
- 현지 일정은 하루의 첫 장소부터 마지막 장소까지의 교통비와 입장료를 계산합니다. 숙소에서 출발하거나 숙소로 돌아가려면 일정에 숙소를 넣고 순서를 조정합니다. 숙박비와 식비는 여행 견적에 포함되지 않습니다.
- 카탈로그 요금은 조사 기준의 공개 요금이나 성인과 아동을 합해 6~20명일 때 적용하는 단체 예상가입니다. 기존 등록 상품은 선택한 날짜의 요금을 적용합니다. 실제 예약 가능 여부와 최종 가격은 판매처에서 확인하세요. 비용 비교 화면에는 양쪽의 방문지 차이와 미확인 비용이 표시됩니다.
- 도로 경로는 카카오모빌리티, 대중교통과 도보는 카카오맵 또는 ODsay 응답에서 확인한 좌표를 사용합니다. 경로 좌표가 없는 지상 구간은 위치만 표시합니다. 대중교통 구간은 제공된 버스 번호, 지하철 노선, 승하차 위치, 도보 연결을 보여 줍니다.
- 제주와 육지를 오가는 경우 공항을 경유하는 연결을 표시합니다. 날짜별 항공권과 열차, 고속버스의 실시간 운임은 별도 확인이 필요합니다.

## 테스트와 빌드

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
java -jar build/libs/travelplanner-0.1.0.jar
```

테스트는 별도의 인메모리 H2 DB를 사용합니다. 결과는 `build/reports/tests/test/index.html`에서 확인합니다. UI 변경을 확인할 때는 1280px과 390px에서 홈 입력, Enter 장소 검색, 현지 일정과 일일투어 비용 비교 흐름을 점검합니다.

화면 변경 후 예전 스타일이 보이거나 `Cannot set properties of null (setting 'innerHTML')`가 표시되면 실행 중인 앱을 재시작하고 브라우저를 새로고침하세요. 이전 JavaScript가 새 HTML에서 제거된 요소를 찾을 때 같은 오류가 재현됩니다. 현재 공통 레이아웃은 CSS에 내용 해시를 붙이고 JavaScript 모듈을 같은 버전 경로로 요청합니다. [공통 레이아웃](src/main/resources/templates/layout.html), [정적 파일 설정](src/main/resources/application.properties)

## 기술 스택과 프로젝트 구조

Java 17, Spring Boot 4.1.2, Spring MVC, Thymeleaf, Bootstrap 5.3.8, JavaScript ES 모듈, Spring Data JPA, Flyway, H2 및 PostgreSQL을 사용합니다.

| 위치 | 역할 |
| --- | --- |
| `src/main/java/com/example/travelplanner/controller` | 화면 라우팅과 REST API |
| `src/main/java/com/example/travelplanner/service`, `pricing`, `recommendation` | 경로와 일정 계산, 가격 비교, 추천 |
| `src/main/java/com/example/travelplanner/integration` | 국내 카탈로그와 카카오, ODsay 연동 |
| `src/main/resources/catalog` | 관광지 좌표와 입장료, 지역별 일일투어 코스와 요금 |
| `src/main/resources/templates`, `src/main/resources/static` | Thymeleaf 화면, CSS, JavaScript |
| `src/main/resources/db/migration` | 일정과 등록 투어 저장을 위한 Flyway 마이그레이션 |
| `src/test/java` | 통합 테스트와 API 연동 테스트 |

### 주요 API

| 메서드와 경로 | 역할 |
| --- | --- |
| `GET /api/destinations/search`, `GET /api/places/search` | 지역 목록과 전국 출발지 또는 목적지 검색 |
| `POST /api/routes/compare` | 교통수단별 경로, 시간, 운임 비교 |
| `GET /api/attractions`, `GET /api/itineraries/places` | 저장된 관광지와 현지 카카오맵 장소 검색 |
| `POST /api/itineraries/generate`, `POST /api/itineraries/quote` | 일정 생성과 견적 재계산 |
| `GET /api/travel-plans`, `POST /api/travel-plans` | 저장 일정 목록과 새 일정 저장 |
| `GET`, `PUT`, `DELETE /api/travel-plans/{id}` | 저장 일정 조회, 갱신, 삭제 |
| `GET /api/tours/search`, `GET /api/tours/{id}` | 일일투어 목록과 상세 |
| `POST /api/tours/recommend` | 선택한 날짜, 인원, 방문지로 투어 추천 |
| `POST /api/compare`, `POST /api/compare/recalculate` | 일일투어와 자유여행의 비용 비교와 재계산 |

요청과 응답 필드는 [Travel.java](src/main/java/com/example/travelplanner/dto/Travel.java)와 [TravelApi.java](src/main/java/com/example/travelplanner/controller/TravelApi.java)를 참고합니다. 화면 규칙과 변경 이력은 [DESIGN.md](DESIGN.md)와 [디자인 기록](doc/design/README.md)에 정리합니다.
