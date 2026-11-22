# TravelPlanner 국내여행 플래너

서울·부산·제주 여행의 교통편, 일정, 일일투어 비용을 비교하는 웹 앱입니다. 출발지와 여행 날짜, 인원수를 입력하면 이동 경로와 예상 비용을 확인할 수 있습니다. 관광지를 날짜별로 편집하고 저장하거나, 같은 관광지를 둘러보는 자유여행과 일일투어를 비교할 수 있습니다.

## 실행 방법

JDK 17이 필요합니다. 이 README와 `build.gradle`이 있는 폴더에서 명령을 실행하세요. 첫 빌드에는 Gradle과 라이브러리를 내려받을 네트워크 연결이 필요합니다.

```powershell
java -version
.\gradlew.bat bootRun
```

브라우저에서 [TravelPlanner 열기](http://localhost:8080)를 선택하세요. IntelliJ IDEA에서는 이 폴더를 Gradle 프로젝트로 열고, Gradle JVM에 JDK 17을 지정한 뒤 `TravelplannerApplication`을 실행할 수 있습니다. macOS와 Linux에서는 `.\gradlew.bat` 대신 `./gradlew`를 사용합니다.

기본 설정은 API 키 없이 동작하며 H2 DB를 사용합니다. Flyway가 첫 실행 시 DB를 초기화합니다. 저장 일정과 사용자 등록 투어는 작업 폴더의 `data/travelplanner.mv.db`에 보관됩니다. 기존 국내여행 기록은 같은 DB에서 계속 사용할 수 있습니다. 이전 해외 일정은 저장 목록에 **지원 종료**로 표시되며, 해외 투어는 국내 검색에 표시되지 않습니다.

## ODsay 대중교통 조회 켜기

ODsay LAB에서 발급받은 **Server 키**를 실행 환경의 `ODSAY_API_KEY`로 설정합니다. ODsay의 Server IP에는 앱에서 외부로 요청을 보내는 공인 IP를 등록합니다.

API 테스트에 성공한 PowerShell 창에 키가 이미 설정되어 있다면 같은 창에서 프로젝트 폴더로 이동하고 `.\gradlew.bat bootRun`을 실행하세요. IntelliJ에서 실행할 때는 해당 실행 구성의 환경변수에 `ODSAY_API_KEY`를 추가합니다.

공항 접근 및 관광지 사이에서 ODsay가 반환한 버스·지하철 경로에 **ODsay 예상 요금·시간**과 조회 시각을 표시합니다. 버스 번호, 지하철 노선명, 승하차 정류장·역은 선택한 구간 카드에 표시합니다. 같은 응답에 버스와 지하철을 함께 타는 경로가 있으면 **버스 + 지하철** 선택지가 추가됩니다. 아동 요금은 미확인으로 표시하며 구간별로 직접 입력할 수 있습니다. 조회할 수 없는 기존 선택지는 샘플로 표시됩니다. 도시 간 열차·고속버스와 항공편은 샘플 요금입니다.

## 택시와 자가용 예상 시간 확인하기

카카오디벨로퍼스의 앱에서 **REST API 키**를 복사해 IntelliJ의 `TravelplannerApplication` 실행 구성 환경변수에 `KAKAO_MOBILITY_REST_API_KEY`로 추가한 뒤 앱을 다시 실행하세요. REST 키에 호출 허용 IP를 설정한 경우 서버의 외부 요청 IP도 확인하세요. [카카오모빌리티 키 설정 가이드](https://developers.kakaomobility.com/guide/navi-api/start)

지원되는 구간은 자동차 길찾기 응답에 따라 택시와 자가용의 거리와 현재 교통 기준 예상 시간을 표시합니다. 택시 예상 요금은 차량 한 대의 일행 공동 요금입니다. 자가용 총비용은 직접 입력할 수 있습니다. 키가 없거나 조회가 실패하면 기존 샘플 시간과 요금 정보를 표시합니다.

## 카카오맵에서 대중교통 노선 보기

카카오디벨로퍼스에서 카카오맵을 활성화하고 JavaScript SDK 도메인에 `http://localhost:8080`을 등록합니다. TravelPlanner를 시작하는 PowerShell 창에서 ODsay 키와 함께 카카오맵 **JavaScript 키**를 설정하세요.

```powershell
$env:KAKAO_MAP_JS_KEY = Read-Host '카카오맵 JavaScript 키'
.\gradlew.bat bootRun
```

경로 비교, 여행 일정, 자유여행 비용 비교에서 ODsay가 제공한 구간의 **지도에서 보기**를 누르면 카카오맵에 버스·지하철 노선과 출발·도착 위치가 표시됩니다. 노선 좌표는 지도를 열 때 조회합니다. 지도에 표시되는 선은 대중교통 탑승 구간을 나타냅니다.

샘플로 표시되는 시내버스 등의 구간도 **카카오맵에서 길찾기**로 열 수 있습니다. 이 링크는 출발·도착 지점을 카카오맵에 전달해 새로 검색하므로 카카오맵의 경로와 앱의 요금·시간이 다를 수 있습니다.

## 제주 예제로 시작하기

1. 홈에서 **제주 샘플 불러오기**와 **여행 검색**을 누릅니다. 서울역 → 김포국제공항 → 제주국제공항 → 제주시 중심의 세 구간이 표시됩니다. 공항 접근 교통편을 바꾸거나 구간별 요금을 직접 입력할 수 있습니다.
2. **여행 일정 만들기**를 누릅니다. 방문 날짜와 순서를 편집하거나 **카카오맵에서 관광지 검색**에 장소 이름을 입력해 원하는 결과를 추가합니다. **일정 저장**을 누르면 **저장한 여행**에서 다시 열 수 있습니다.
3. **현지 일일투어 보기**에서 **제주 동부 일일 버스투어**의 **비용 비교**를 누릅니다. 기본 비교는 투어와 같은 네 곳을 자유여행으로 방문할 때 드는 비용을 보여줍니다. 날짜를 바꾸거나 같은 목적지의 내 일정과 비교할 수 있습니다. 구간별 교통수단과 필수 추가비를 입력해 다시 계산할 수도 있습니다.
4. 서울 ↔ 부산의 교통편을 보려면 홈에서 출발지와 목적지를 바꾸고 검색합니다. KTX, 일반 기차, 고속·시외버스, 자가용의 편도와 왕복 예상 비용을 비교할 수 있습니다.

## 일정에 카카오맵 장소 추가하기

IntelliJ 실행 구성에 카카오맵 사용이 활성화된 앱의 **REST API 키**가 필요합니다. 기존 `KAKAO_MOBILITY_REST_API_KEY`를 사용하거나, 장소 검색에 다른 카카오 앱을 사용한다면 `KAKAO_MAP_REST_API_KEY`를 추가하세요. 키를 변경한 뒤 앱을 다시 실행합니다. [카카오맵 키워드 검색 문서](https://developers.kakao.com/docs/ko/kakaomap/rest-api#search-by-keyword)

일정에서 날짜를 고른 뒤 **장소명** 또는 **주소**로 검색하고 **추가**를 누릅니다. 숙소가 장소명으로 검색되지 않으면 건물 번호가 포함된 도로명 주소 또는 지번 주소를 입력하세요. 검색 결과는 선택한 목적지에 속한 장소를 표시합니다. 카카오맵에서 제공하지 않는 입장료와 체류 시간은 미확인으로 표시합니다. 기존 자동 생성 일정과 이전 저장 일정도 편집할 수 있습니다.

## 비용 계산 기준

- ODsay 조회 구간에는 응답의 요금, 거리, 소요 시간을 사용합니다. 샘플 구간의 거리는 좌표로 계산하며, 왕복 예상 운임은 편도 운임의 두 배입니다. 입장료와 기본 투어 상품은 샘플입니다.
- 요금 입력과 계산 단위는 원입니다. 성인·아동 1인 요금에는 각 인원수를 곱하고, 차량 등 일행 공동 요금은 구간당 한 번 더합니다. 이전 기록의 원화 요금은 그대로 읽고, 외화 요금은 미확인으로 표시합니다.
- 일정은 매일 첫 장소를 출발지로 삼아 마지막 장소까지의 교통비와 입장료를 계산합니다. 숙소에서 출발하거나 숙소로 돌아가려면 일정에 해당 위치를 넣어 순서를 조정하세요. 목적지까지의 왕복 운임, 숙박비, 식비는 별도입니다.
- 투어 가격은 선택한 날짜에 적용되는 가격 기간을 사용합니다. 투어에 포함된 입장료는 상품 가격에 이미 반영되어 있습니다. 집합 장소나 종료 장소의 교통비를 확인할 수 없으면 직접 입력할 수 있습니다. 필수 요금이 누락되거나 방문지가 다르면 비교 화면에 표시됩니다.

## 테스트 및 실행 파일

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
java -jar build/libs/travelplanner-0.1.0.jar
```

테스트는 별도의 인메모리 H2 DB를 사용합니다. 결과는 `build/reports/tests/test/index.html`에서 확인할 수 있습니다.

## PostgreSQL로 실행하기

PostgreSQL에 DB와 앱 계정을 만들고, 앱 계정이 기본 스키마에 Flyway 테이블과 인덱스를 생성할 수 있도록 설정합니다. 아래 환경변수를 설정한 뒤 앱을 실행하세요.

| 환경변수 | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `TRAVELPLANNER_DB_URL` | `jdbc:postgresql://localhost:5432/travelplanner` |
| `TRAVELPLANNER_DB_USER` | PostgreSQL 앱 계정 |
| `TRAVELPLANNER_DB_PASSWORD` | PostgreSQL 앱 계정 비밀번호 |

기본 포트가 사용 중이면 `SERVER_PORT`로 변경할 수 있습니다.

## 프로젝트 구조

Java 17, Spring Boot 4.1.2, Spring MVC, Thymeleaf, Bootstrap, Spring Data JPA, Flyway를 사용합니다.

| 위치 | 역할 |
| --- | --- |
| `controller`, `dto` | 화면 라우팅, REST API, 요청과 응답 |
| `service`, `pricing`, `recommendation` | 경로·일정 계산, 비용 합산, 추천 |
| `integration` | 국내 여행지, 관광지, 경로, 투어 샘플 |
| `entity`, `repository`, `resources/db/migration` | 일정과 등록 투어 저장 |
| `resources/templates`, `resources/static` | 화면 템플릿, 스타일, JavaScript |

주요 API는 `GET /api/destinations/search`, `GET /api/attractions`, `POST /api/routes/compare`, `POST /api/itineraries/generate`, `POST /api/itineraries/quote`, `GET /api/tours/search`, `POST /api/tours`, `POST /api/compare`입니다. 저장 일정은 `/api/travel-plans`에서 관리합니다. 요청 형식은 `dto/Travel.java`를 참고하세요.

