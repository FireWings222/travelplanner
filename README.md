# TravelPlanner

국내외 이동 경로를 비교하고, 현지 일정과 일일투어의 비용을 계산하는 로컬 웹 앱입니다. Spring Boot 4.1.2, Java 17, Gradle Groovy, Spring MVC, Thymeleaf, Bootstrap 5, Spring Data JPA, Flyway를 사용합니다.

## IntelliJ IDEA에서 실행

1. **File → Open**으로 프로젝트 폴더를 엽니다. Gradle 프로젝트 가져오기를 선택합니다.
2. **File → Project Structure**의 Project SDK와 **Settings → Build, Execution, Deployment → Build Tools → Gradle**의 Gradle JVM에 JDK 17을 지정합니다. Gradle 배포판은 Wrapper를 사용합니다.
3. Gradle 동기화가 끝나면 `src/main/java/com/example/travelplanner/TravelplannerApplication.java`의 `main`을 실행합니다. 실행 구성의 Working directory는 프로젝트 루트로 지정합니다.
4. [http://localhost:8080](http://localhost:8080)에 접속합니다. 첫 실행 시 Flyway가 기본 로컬 DB를 초기화합니다.

기본 프로필은 API 키 없이 동작하며, 저장한 일정과 직접 등록한 투어는 `data/travelplanner.mv.db`에 저장됩니다. 이 프로필은 H2를 사용합니다. Gradle의 첫 동기화에는 의존성을 받기 위한 네트워크 연결이 필요합니다. 서버는 기본적으로 루프백 주소에서 수신하며 일정 저장소는 로컬 앱 한 인스턴스가 공유합니다.

### 명령줄 실행과 테스트

Windows PowerShell에서 실행합니다.

```powershell
java -version
.\gradlew.bat bootRun
.\gradlew.bat test
.\gradlew.bat bootJar
java -jar build/libs/travelplanner-0.1.0.jar
```

macOS와 Linux에서는 `./gradlew`를 사용합니다. 테스트는 별도의 인메모리 DB를 사용하며, 결과는 `build/reports/tests/test/index.html`에서 확인할 수 있습니다.

### PostgreSQL 프로필

1. PostgreSQL에 `travelplanner` 데이터베이스와 앱 계정을 준비합니다. 첫 실행 시 해당 DB의 기본 스키마에 Flyway가 테이블과 인덱스를 생성할 권한이 필요합니다.
2. IntelliJ의 **Run → Edit Configurations → Environment variables**에 다음 변수와 본인의 DB 접속 값을 설정합니다.

| 변수 | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `TRAVELPLANNER_DB_URL` | `jdbc:postgresql://localhost:5432/travelplanner` |
| `TRAVELPLANNER_DB_USER` | PostgreSQL 앱 계정 |
| `TRAVELPLANNER_DB_PASSWORD` | PostgreSQL 앱 계정 비밀번호 |

3. 앱을 실행합니다. 시작 로그의 Flyway 연결 및 마이그레이션 메시지를 확인합니다.

연결이 거부되면 PostgreSQL 서비스와 URL의 호스트·포트를, 인증이 실패하면 계정과 비밀번호를 확인합니다. 스키마 권한 오류는 앱 계정의 스키마 사용·생성 권한을 확인합니다. 기본 H2 DB에 저장한 데이터는 별도로 유지됩니다. 포트 8080이 사용 중이면 해당 프로세스를 종료하거나 실행 구성에 `SERVER_PORT`를 설정할 수 있습니다.

## 샘플 사용 흐름

1. 홈에서 **오사카 샘플 불러오기**와 **여행 검색**을 누릅니다. 서울역에서 인천국제공항과 간사이국제공항을 거쳐 오사카역으로 가는 구간이 표시됩니다.
2. 공항철도, 공항버스, 택시를 선택하거나 각 구간에서 요금을 직접 입력합니다. 경로 목록과 추천 근거가 갱신됩니다.
3. **여행 일정 만들기**를 누르고 2일차를 선택합니다. 교토 관광지 네 곳이 들어 있으며, 방문 순서·관광지·이동수단을 수정할 수 있습니다. **일정 저장**으로 저장하고 **저장한 여행**에서 다시 엽니다.
4. **현지 일일투어 보기**에서 교토 샘플의 가격 기간과 방문지, 포함 항목을 확인합니다. 여러 후보는 **비교 목록에 추가**로 이 탭의 목록에 모을 수 있습니다. **비용 비교**를 눌러 투어와 같은 관광지를 방문하는 자유여행 비용을 조회합니다. 2일차와 비교하려면 투어 날짜를 2027-03-11로 선택하고 **선택한 날짜의 내 일정과 비교하기**를 체크합니다.
5. 자유여행 첫 구간을 택시로 바꾸면 Fetch API가 비용을 다시 계산합니다. 필요하면 숙소 도시와 집합·종료 장소 교통비를 직접 입력합니다.
6. 별도 투어는 상품 이름, HTTPS 예약 링크, 방문 관광지, 포함 항목과 가격 기간을 직접 입력해 비교합니다. 클룩, 마이리얼트립, NOL/야놀자, 기타 플랫폼으로 분류할 수 있습니다.

## 요금과 시간의 범위

- 제공 도시와 관광지, 교통비, 거리, 입장료 및 환율은 샘플입니다. 서울, 부산, 제주, 오사카, 교토, 도쿄를 지원합니다. 일정의 현지 이동 범위는 목적지이며 오사카와 교토는 양쪽 관광지를 함께 선택할 수 있습니다.
- 교통 거리와 소요 시간은 좌표에 기반한 가상 추정치입니다. 목적지 왕복 추정에는 편도 운임을 두 번 적용합니다. 현지 일정에는 목적지까지의 왕복 교통비와 숙박비·식비가 포함되지 않습니다.
- 가격은 성인·아동 개별 요금 또는 일행 공동 요금으로 입력합니다. 공동 요금은 행당 한 번 더한 뒤 인원별 비용 표시에 균등 배분합니다. 택시나 차량을 여러 대 이용하는 경우 차량 전체 요금을 공동 요금으로 입력합니다. 모든 금액 계산에는 `BigDecimal`을 사용합니다.
- 비교 결과는 입력한 필수 추가비를 반영합니다. 투어에 포함된 입장료는 다시 청구하지 않습니다. 날짜에 적용되는 투어 가격·요금·환율이 없으면 누락 항목을 표시하고 필요한 경우 비용 차이와 추천을 보류합니다. 비교 방문지가 다르면 어느 쪽에만 있는지 표시합니다.
- 숙소 위치는 도시 중심으로 가정합니다. 집합·종료 장소가 제공 도시의 중심 위치와 일치하면 왕복 접근 비용을 추정하며, 그 외 장소는 사용자 요금을 받습니다. 투어 실이동 시간은 교토 샘플에서만 추정합니다.

## 애플리케이션 구조

| 위치 | 역할 |
| --- | --- |
| `controller` / `dto` / `exception` | Thymeleaf 화면, REST 요청과 응답, 검증 및 JSON 오류 |
| `service` / `pricing` / `recommendation` | 일정, 경로, 투어, 합산과 환산, 추천 기준 |
| `integration/TravelProviders.java` | 여행지·관광지, 경로, 투어 플랫폼, 환율 Provider 계약 |
| `entity` / `repository` | 저장 일정과 사용자 등록 상품의 JPA 저장소 |
| `resources/db/migration` | PostgreSQL 및 H2 로컬 프로필에서 실행하는 Flyway 마이그레이션 |
| `resources/templates` / `resources/static` | 반응형 화면 5개, 스타일, Fetch API, 생성한 교토 풍경 이미지 |

### REST API

| 메서드 | 경로 | 입력 또는 용도 |
| --- | --- | --- |
| GET | `/api/destinations/search` | 선택 검색어 `q` |
| GET | `/api/attractions` | 선택 `destination`, `q` |
| GET | `/api/attractions/{id}` | 관광지 상세 |
| POST | `/api/routes/compare` | `trip`, 선택한 구간별 `legs` |
| POST | `/api/itineraries/generate` | 여행 조건 `Trip` |
| POST | `/api/itineraries/quote` | 여행 조건과 일자별 목록 `Plan` |
| GET / POST | `/api/travel-plans` | 저장 계획 목록 / `Plan` 저장 |
| GET / PUT / DELETE | `/api/travel-plans/{id}` | 조회 / `Plan`으로 변경 / 삭제 |
| GET | `/api/tours/search` | `destination`, 선택 `date`, `platform` |
| GET / POST | `/api/tours/{id}` / `/api/tours` | 상세 / `TourInput` 등록 |
| POST | `/api/compare`, `/api/compare/recalculate` | `tourId`, `date`, `party`, 선택 숙소 도시·방문지·구간·추가비 |

요청과 응답의 상세 구조는 `dto/Travel.java`를 참고하세요. 교통 선택의 구간 키는 서버 응답의 `sections[].key`입니다. 유효하지 않은 JSON 요청에는 HTTP 400과 `detail`을, 필드 검증 오류에는 `fields`를 추가로 반환합니다.

### 데이터 모델 확장 설계

MVP에서는 `travel_plan`과 `tour_product`에 UUID 기본키와 조회용 인덱스를 사용합니다. 일정은 날짜·방문 순서·사용자 요금 입력을 한 트랜잭션에서 저장합니다. 일정 수정 경합은 JPA 버전 열로 감지합니다. 투어에는 직접 입력한 날짜별 가격, 출처(직접 등록), 등록 시각을 보존합니다. 카탈로그와 미리 계산한 견적을 저장하기 위한 관계형 구조는 다음과 같습니다.

| 모델 | 기본키·관계와 주요 인덱스 |
| --- | --- |
| User | `id` PK, 정규화 이메일 UNIQUE |
| Destination | `id` PK, `(country, name)` 검색 인덱스 |
| TouristAttraction | `id` PK, `destination_id` FK, `(destination_id, name)` 인덱스 |
| TravelPlan | `id` PK, 회원 단계에서 `user_id` 및 `destination_id` FK, `(user_id, updated_at)` |
| TravelDay | `id` PK, `plan_id` FK, `(plan_id, visit_date)` UNIQUE |
| ItineraryStop | `id` PK, `day_id`, `attraction_id` FK, `(day_id, sequence)` UNIQUE |
| TravelRoute | `id` PK, 출발·도착 도시 FK 및 선택 `day_id` FK, 도시 쌍·날짜 인덱스 |
| RouteSegment | `id` PK, `route_id` FK, `(route_id, sequence)` UNIQUE |
| TransportationOption | `id` PK, `segment_id` FK, `(segment_id, mode)` |
| TransportationFare | `id` PK, `option_id` FK, `(option_id, valid_from, valid_to)` |
| TourProvider | `id` PK, 플랫폼 코드 UNIQUE |
| TourProduct | `id` PK, `provider_id`, `destination_id` FK, `(destination_id, checked_at)` |
| TourStop | `id` PK, `product_id`, `attraction_id` FK, `(product_id, sequence)` UNIQUE |
| TourInclusion | `(product_id, item_code)` PK, `product_id` FK |
| TourPrice | `id` PK, `product_id` FK, `(product_id, valid_from, valid_to)` |
| CostComparison | `id` PK, `day_id`, `product_id` FK, `(day_id, calculated_at)` |
| CostItem | `id` PK, `comparison_id` FK, `(comparison_id, side)`. 원통화 금액, 적용 환율·날짜, 원화 합계와 누락 이유를 저장 |
| Favorite | `id` PK, `user_id` FK와 관광지 또는 투어 FK 중 하나, 대상별 사용자 UNIQUE |

금액은 `NUMERIC`과 통화 코드로 보존하고, 제공업체 요금에는 출처와 확인 시각을 함께 기록하는 구조입니다. 관계형 확장은 기존 레코드를 보존하는 별도 Flyway 마이그레이션으로 추가합니다.

## 이후 단계

- **7단계**에서 지도 Provider로 지도를 그리고, 현재 구간의 좌표와 관광지 좌표를 연결합니다. 지도 서비스 확인 후 지도 키를 환경 변수로 설정하고, 직선 예시와 실경로를 구분합니다.
- **8단계**에서 Spring Security 회원 기능과 사용자별 일정 및 관심 관광지·투어 목록을 연결합니다.
- **9단계**에서 각 제공업체의 공식 문서, 이용 약관 및 권한을 확인한 후 Provider 구현을 연결합니다. 예약 제휴 정보와 키는 운영 환경에서 설정합니다. 투어 Provider가 실패하면 저장된 상품과 검색 안내를 계속 보여 줍니다.
