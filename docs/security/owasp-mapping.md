# OWASP API Security Top 10 (2023) — graphql-gateway 매핑

본 문서는 OWASP API Security Top 10 (2023) 의 각 항목이 graphql-gateway 의 어디에 해당하고,
현재 어떻게 다루고 있는지를 정리한다. 이 레포는 9개 백엔드 service 의 REST API 를 GraphQL
한 endpoint (`/graphql`) 로 묶는 BFF 게이트웨이다 — REST 서비스와 달리 **클라이언트가 쿼리
모양을 정한다**. 그래서 자원 소비 (API4) 가 가장 큰 표면이고, 거기에 가장 많은 방어가 들어가
있다.

게이트웨이라는 성격상 인증 / 인가는 두 layer 로 나뉜다. 게이트웨이는 OAuth2 resource server
로 들어온 JWT 의 서명을 검증하고 (1차), 그 토큰을 downstream 9 service 로 그대로 relay 해
**객체 / 함수 단위 권한 판단은 각 downstream service 가 자기 토큰 클레임으로 수행한다** (2차).
본 문서는 그 구조 위에서 *게이트웨이 코드가 책임지는* 부분을 다룬다 (ADR-0001 의 역할 경계,
ADR-0004 의 token relay).

## 요약

| ID    | 항목                                  | 표면 | 상태 |
|-------|---------------------------------------|------|------|
| API1  | Broken Object Level Authorization     | 작음 | downstream 위임 ([§API1](#api1--broken-object-level-authorization)) |
| API2  | Broken Authentication                 | 중간 | 적용 ([§API2](#api2--broken-authentication)) |
| API3  | Broken Object Property Authorization  | 작음 | 적용 ([§API3](#api3--broken-object-property-level-authorization)) |
| API4  | Unrestricted Resource Consumption     | 큼   | 집중 방어 ([§API4](#api4--unrestricted-resource-consumption)) |
| API5  | Broken Function Level Authorization   | 작음 | downstream 위임 ([§API5](#api5--broken-function-level-authorization)) |
| API6  | Unrestricted Access to Sensitive Flow | 작음 | downstream 위임 ([§API6](#api6--unrestricted-access-to-sensitive-business-flows)) |
| API7  | Server Side Request Forgery           | 중간 | 적용 ([§API7](#api7--server-side-request-forgery)) |
| API8  | Security Misconfiguration             | 중간 | 적용 ([§API8](#api8--security-misconfiguration)) |
| API9  | Improper Inventory Management         | 작음 | 적용 ([§API9](#api9--improper-inventory-management)) |
| API10 | Unsafe Consumption of APIs            | 중간 | 적용 ([§API10](#api10--unsafe-consumption-of-apis)) |

GraphQL 특유의 위협 — 쿼리 depth / complexity DoS, introspection 노출, downstream SSRF —
은 각각 API4 / API9 / API7 항목 안에서 다룬다.

---

## API1 — Broken Object Level Authorization

상황 — 한 사용자가 다른 사용자의 주문 / 인보이스 / job 을 ID 로 직접 조회.

- 게이트웨이의 Query 진입점 (`user(id)`, `order(id)`, `invoice(id)`, `job(id)`,
  `trade(id)`) 과 조인 필드 (`Order.invoice`, `User.notifications` 등) 는 모두 ID 로 단건을
  가져온다. 게이트웨이 자체에는 "이 ID 가 호출자 소유인가" 를 판단하는 로직이 없다.
- 대신 게이트웨이는 들어온 JWT 를 downstream 호출에 그대로 relay 한다 (ADR-0004). 객체
  단위 인가는 그 토큰의 클레임 (사용자 id / 역할 / 테넌트) 을 보고 **각 downstream service
  가 수행한다** — 게이트웨이가 권한을 대신 해석하지 않는다 (ADR-0001 의 "비즈니스 규칙 제외").
  예: 다른 사용자의 주문 ID 를 넣어도 commerce-ops 가 자기 토큰 검증에서 거부하면 그 필드가
  null + error 로 떨어진다 (부분 실패, ADR-0003).

커버: BOLA 판단은 토큰을 받은 downstream service 의 책임. 게이트웨이는 토큰을 변형 없이
전달해 downstream 이 정확한 주체로 판단할 수 있게 보장한다 (`TokenRelayInterceptor`).

---

## API2 — Broken Authentication

상황 — JWT 검증 누락 / 우회 / 약한 검증.

- 게이트웨이는 OAuth2 resource server 로 동작한다 (`SecurityConfig.jwtSecurityFilterChain`).
  `Authorization: Bearer <jwt>` 를 auth-service 의 JWK Set 으로 서명 검증하고, 검증 실패면
  downstream 을 호출하기 전에 거부한다.
- `issuer-uri` / `jwk-set-uri` 는 `application.yml` 의 `spring.security.oauth2.resourceserver`
  로 설정 — 운영에서 IDP URL 을 환경변수로 주입한다.
- stateless — `SessionCreationPolicy.STATELESS`. 세션을 만들지 않아 세션 고정 / hijacking
  표면이 없다. `csrf.disable()` 은 stateless Bearer 기반 API 의 표준 패턴.
- endpoint 정책 — `/graphql` 은 `authenticated()`, `/graphiql` 과 `/actuator/health/**`
  만 `permitAll()`, 그 외 모든 요청은 인증 필요 (`anyRequest().authenticated()`).
- 게이트웨이는 토큰을 **발급하지 않는다** — 발급은 auth-service 의 책임. 인증 주체가 하나로
  유지되고, 게이트웨이에서 토큰 위조 표면이 늘지 않는다 (ADR-0004).
- `permit-all` 스위치 — `gateway.security.permit-all=true` 면 인증을 끈다. downstream /
  auth-service 가 안 떠 있는 로컬 데모 / 통합 테스트 전용이며, `@ConditionalOnProperty` 의
  기본값이 `false` 라 운영에서는 활성화되지 않는다. demo 프로필 (`application-demo.yml`)
  에서만 켜진다.

회귀 테스트: `QueryGuardTest` / `GatewayGraphQlTest` 가 demo 프로필 (permit-all) 로 돌고,
`DownstreamWireMockTest` 는 `permit-all=true` + OAuth2 자동설정 제외로 실제 어댑터 경로를
검증한다 — 운영 기본인 jwt 모드의 필터 체인 빈 자체는 `jwtSecurityFilterChain` 으로 분리돼
있어 설정 분기가 명확하다.

---

## API3 — Broken Object Property Level Authorization

상황 — GraphQL 응답에 마스킹되어야 할 민감 필드가 노출.

- 게이트웨이가 노출하는 필드는 `schema.graphqls` 의 SDL 이 화이트리스트로 작용한다.
  downstream REST 응답은 DTO (`DownstreamDtos`) 로 받고 게이트웨이 도메인 모델 (`Model.kt`)
  로 변환 (`DtoMappers`) 하므로, downstream JSON 에 필드가 추가돼도 schema 에 없으면 응답에
  새지 않는다 (ADR-0009 의 변환 계층).
- 게이트웨이는 over-fetching 을 줄이는 BFF 다 — 클라이언트는 SDL 에 정의된 필드 중 요청한
  것만 받는다. 게이트웨이가 새로운 민감 필드를 만들지 않으며, 필드 단위 민감도 (PII 마스킹
  등) 는 그 필드를 소유한 downstream service 의 책임이다 (예: auth-service / security-log-search
  의 PII 정책).

커버: SDL 이 응답 schema 의 화이트리스트. DTO → 도메인 변환이 downstream 필드 누출을 차단.

---

## API4 — Unrestricted Resource Consumption

게이트웨이의 가장 큰 표면. REST 와 달리 GraphQL 은 **클라이언트가 쿼리 모양을 정하므로**,
악의적이거나 실수로 만든 쿼리 하나가 게이트웨이와 그 뒤 9개 downstream 을 한꺼번에 끌어내릴
수 있다 (ADR-0008).

| 위협 | 방어 위치 | 정책 |
|------|-----------|------|
| **쿼리 depth DoS** — 조인 필드가 서로를 참조하는 구조에서 쿼리를 깊게 중첩하면 한 쿼리가 지수적으로 많은 resolver 호출로 펼쳐짐 | `QueryGuardConfig.maxQueryDepthInstrumentation` (graphql-java `MaxQueryDepthInstrumentation`) | 최대 중첩 깊이 제한. 기본 `gateway.query-guard.max-depth=15`. 한계 초과 시 **실행 전에** 거부 — downstream 호출 0. |
| **쿼리 complexity DoS** — 한 레벨에 필드를 폭증시키거나 같은 조인을 여러 alias 로 반복해 호출 수를 키움 | `QueryGuardConfig.maxQueryComplexityInstrumentation` (graphql-java `MaxQueryComplexityInstrumentation`) | 필드 수 가중 합산 비용 제한. 기본 `max-complexity=200`. 파싱 / 검증 단계에서 동작 — resolver 미호출. |
| **페이지 크기 폭증** — `search(first:)` / `securityAlerts(first:)` / `notifications(first:)` 에 거대한 값을 넣어 downstream 에서 대량 조회 | `QueryController.coerceLimit()` | `first` 를 `coerceIn(1, 100)` 으로 강제. `first: 100000` 을 보내도 100 으로 깎임 (ADR-0008 의 보조 장치). |
| **N+1 폭증** — 주문 N건을 펼치면 조인 필드마다 downstream 단건 호출이 N번 발생 | `DataLoaderConfig` + `FederationController` | 조인 필드 (`Order.invoice` 등) 는 resolver 가 downstream 을 직접 안 부르고 DataLoader 에 키만 등록. graphql-java 가 같은 tick 의 키를 모아 batch 1회로 호출 (ADR-0002). |
| **downstream 호출 폭주 / cascade** — 한 downstream 이 느려지거나 죽으면 호출이 쌓여 게이트웨이 자원 소진 | `application.yml` 의 `resilience4j` (ADR-0003) | service 별 CircuitBreaker (50% failure → 10s OPEN) + Retry 3회 (지수 backoff) + TimeLimiter (2.5s). 9개 instance 가 독립 회로 — 한 service 장애가 다른 호출로 번지지 않음. |
| **transport hang** — 소켓이 영원히 매달려 connection pool 고갈 | `DownstreamWebClientConfig.httpClient` | WebClient 에 connect / response / read 타임아웃 (`gateway.downstream.timeout-ms`, 기본 2s) — Resilience4j TimeLimiter 와 별개로 transport layer 에서도 한 번 더. |
| **batch 부하 합치기** — 인기 사용자 / 상품의 반복 조회가 매번 downstream 을 침 | Caffeine 캐시 (ADR-0007) | 안정적인 데이터 (사용자 프로필 등) 만 단기 TTL 캐시. 로딩 캐시가 cache stampede (만료 직후 동시 미스) 를 키당 1회 로드로 합침. 실시간성이 중요한 타입 (거래 / job / 보안 알림) 은 캐시 안 함. |

핵심 커밋: `QueryGuardConfig` 의 depth / complexity instrumentation, `QueryController.coerceLimit()`
의 페이지 크기 cap, `DataLoaderConfig` 의 N+1 batch.

회귀 테스트: `QueryGuardTest` — 한계값을 일부러 낮게 (`max-depth=3`, `max-complexity=8`)
잡아 한계 초과 쿼리가 거부되고 한계 안 쿼리는 통과하는지 검증. `GatewayQueryServiceTest`
— DataLoader batch 의 빈-입력 처리.

부수 — 운영 측 보조: 시간당 query 수 / IP 단위 rate limit 은 게이트웨이 외부 (API Gateway
/ WAF) 의 책임으로 둔다. 정적 complexity 계산은 실제 비용의 근사치라 (ADR-0008 의 단점),
필드별 가중치 정교화는 클라이언트 쿼리 패턴 관측 후 검토한다.

---

## API5 — Broken Function Level Authorization

상황 — 권한이 낮은 사용자가 권한이 필요한 작업을 호출.

- 게이트웨이의 mutation 표면은 의도적으로 최소다 — `markNotificationRead(id)` 하나뿐
  (ADR-0001 / ADR-0006). 복잡한 쓰기는 게이트웨이를 거치지 않고 각 service REST 를 직접
  호출하는 것이 원칙이라, 게이트웨이에 함수 단위 권한이 필요한 표면이 거의 없다.
- 그 한 mutation 도 token relay 로 notification-hub 에 위임된다 — "이 알림을 읽음 처리할
  권한이 있는가" 는 notification-hub 가 토큰 클레임으로 판단한다.
- 게이트웨이는 역할 (role) 기반 인가 로직을 자체 구현하지 않는다. 권한 판단을 게이트웨이와
  downstream 양쪽에 중복 구현하면 불일치 위험이 생기므로, 판단 지점을 downstream 하나로
  유지한다 (ADR-0004).

커버: 함수 단위 인가는 토큰을 받은 downstream service 의 책임. 게이트웨이는 mutation 표면을
최소화해 자체 권한 표면을 줄인다.

---

## API6 — Unrestricted Access to Sensitive Business Flows

상황 — 게이트웨이를 통한 자동화된 enumeration / scraping.

- 게이트웨이는 조회 (BFF) 위주이고, 민감한 비즈니스 흐름 (결제 / 정산 / job 제출 등) 의
  쓰기 경로를 노출하지 않는다 — mutation 은 알림 읽음 처리 하나뿐 (API5 참고).
- 조회 측 enumeration (예: 순차 ID 로 주문을 긁기) 은 API1 처럼 downstream 의 객체 단위
  인가가 1차로 막는다. 추가로 API4 의 페이지 크기 cap (`first ≤ 100`) 과 complexity 제한이
  한 쿼리로 대량을 끌어가는 것을 제한한다.
- 자동화 봇 / 비정상 호출 빈도 탐지는 게이트웨이 외부 (WAF / anti-bot) 의 책임으로 둔다 —
  게이트웨이 코드 범위가 아니다.

커버: 민감 쓰기 흐름 미노출 + downstream 인가 위임 + API4 의 양적 제한.

---

## API7 — Server Side Request Forgery

상황 — 게이트웨이가 외부 입력으로 결정된 URL 을 호출하게 되는가.

- 게이트웨이는 downstream 9 service 를 WebClient 로 호출한다. 각 service 의 base URL 은
  `application.yml` 의 `gateway.downstream.*.base-url` — **설정 / 환경변수로만 주입**되고
  (Helm ConfigMap 이 채움), GraphQL 쿼리 / 변수 같은 외부 입력으로 결정되지 않는다.
  WebClient 빈은 `DownstreamWebClientConfig` 가 기동 시점에 고정 base URL 로 생성한다.
- GraphQL schema 에 URL 을 인자로 받는 필드 / mutation 이 없다 — 클라이언트가 게이트웨이에
  "이 URL 을 fetch 하라" 고 시킬 표면이 없다.
- JWK Set 조회 — 게이트웨이가 JWT 검증을 위해 auth-service 의 JWK Set endpoint 를 HTTP 로
  부르지만, 그 URL (`jwk-set-uri`) 도 설정값 고정이다.
- 호출 경로의 path 변수 (`/api/v1/users/{id}` 의 `id` 등) 는 쿼리 인자에서 오지만, host /
  scheme 를 좌우하지 못하고 base URL 에 붙는 path segment 일 뿐이다.

커버: 외부 입력이 호출 host 를 결정하는 경로가 없음 — SSRF 표면이 구조적으로 닫혀 있다.
향후 클라이언트가 URL 을 넘기는 필드 (예: webhook 등록) 를 schema 에 추가한다면 그 시점에
host 화이트리스트 / RFC1918·link-local 차단 / DNS rebinding 방어를 별도 ADR 로 강제한다.

---

## API8 — Security Misconfiguration

상황 — 운영 기본값 / 노출 설정.

- **GraphQL introspection** — graphql-java 의 introspection 은 기본 활성이다. `application.yml`
  은 추가로 `spring.graphql.schema.printer.enabled=true` 로 `/graphql/schema` 에 SDL 을
  노출한다. 게이트웨이 schema 는 9개 portfolio service 의 *공개 타입 형태* 만 담고 (내부
  구현 / 비밀이 SDL 에 없음), 운영에서 GraphiQL 과 함께 schema 가시성을 의도적으로 둔
  포트폴리오 / 데모 환경 전제다. 운영 노출 환경에서 introspection 을 닫아야 한다면
  `spring.graphql.schema.introspection.enabled=false` + `printer.enabled=false` 로 끄고,
  접근 자체는 ingress / WAF 가 IP 제한한다.
- **GraphiQL** — `/graphiql` 의 개발용 playground UI 는 `spring.graphql.graphiql.enabled=true`
  로 켜져 있고 `permitAll()` 이다. 위 introspection 과 같은 데모 전제 — 운영 노출 시
  `graphiql.enabled=false` 또는 ingress 차단.
- **Actuator 노출** — `management.endpoints.web.exposure.include` 가 `health, info,
  prometheus, metrics, circuitbreakers, retries` 만 — `/actuator/env` / `/actuator/heapdump`
  / `/actuator/loggers` 같은 정보 노출 endpoint 는 비공개. `health.show-details=when-authorized`
  로 downstream 의존성 상세는 인증된 호출자만 본다.
- **error 응답** — `GatewayExceptionResolver` 가 resolver 예외를 GraphQL `errors` 로 변환
  하면서 내부 메시지 / 스택트레이스를 클라이언트에 노출하지 않는다. downstream 장애는
  `"downstream service '...' 를 일시적으로 사용할 수 없습니다"` 같은 일반 메시지 + extensions
  의 `classification` / `service` 식별자만 싣는다. 처리되지 않은 예외는 `"내부 오류가
  발생했습니다"` 로 일반화 (ADR-0005).
- **CSRF / 세션** — `csrf.disable()` + `SessionCreationPolicy.STATELESS` (stateless Bearer
  API 표준).
- **TLS** — 게이트웨이는 HTTP 만 listen. TLS termination 은 ingress / service mesh 의 책임.
- **graceful shutdown** — `server.shutdown=graceful` — 종료 시 진행 중인 쿼리를 끊지 않음.

부분 — 데모 범위: introspection / GraphiQL 이 켜져 있음 (포트폴리오 / 데모 가시성 목적,
운영 노출 시 차단 절차 위에 명시). HTTP 보안 헤더 (HSTS / CSP 등) 미설정 — ingress 가정.

---

## API9 — Improper Inventory Management

상황 — 버전 관리되지 않는 / 그림자 endpoint.

- 게이트웨이의 HTTP 표면은 좁다 — `/graphql` (쿼리 / mutation 단일 진입점), `/graphiql`
  (개발용 UI), `/graphql/schema` (SDL), `/actuator/*` (관측). v0 / 미공개 / shadow endpoint
  가 없다.
- GraphQL 의 API "inventory" 는 곧 schema 다 — Query / Mutation / 타입 / 필드 전체가
  `schema.graphqls` 한 파일에 명시되고, introspection 으로 검사 가능하다. REST 처럼 endpoint
  가 흩어져 누락될 표면이 없다.
- **schema 변경 추적** — schema 의 타입 / 필드를 바꾸면 resolver (`@QueryMapping` /
  `@SchemaMapping` / `@BatchMapping`) 와 도메인 모델도 함께 고친다 (AGENTS.md 의 repo 제약).
  schema 와 코드가 한 commit 으로 움직여, SDL 과 실제 구현이 어긋난 그림자 필드가 생기지
  않는다.
- `catalog-info.yaml` (Backstage) 가 service / API inventory 의 진실원본 — `graphql-gateway`
  Component + `graphql-gateway-api` API 를 등록하고, `dependsOn` 으로 9개 downstream
  component 의존을 명시한다. README + `docs/adr/` 가 설계 / 운영 결정을 추적한다.
- deprecated 필드 — 현재 없음. GraphQL 은 `@deprecated` 디렉티브로 필드 단위 deprecation
  을 표시할 수 있어, 도입 시 SDL 에 명시 + introspection 으로 클라이언트가 확인 가능.

커버: 단일 schema 파일 + schema-코드 동시 변경 규칙 + Backstage catalog 등록.

---

## API10 — Unsafe Consumption of APIs

상황 — 게이트웨이가 신뢰하는 외부 (downstream) API 응답을 안전하게 소비하는가.

게이트웨이는 본질적으로 9개 downstream API 의 소비자다 — 이 항목이 게이트웨이에 직접
해당한다.

- **응답 역직렬화** — downstream REST 응답을 임의 타입이 아니라 명시적 DTO (`DownstreamDtos`)
  로 받는다. Jackson 이 알 수 없는 필드를 만나도 DTO 스키마 밖이면 무시되고, polymorphic
  타입 해석 (`@JsonTypeInfo`) 같은 위험한 역직렬화 경로를 쓰지 않는다.
- **downstream 장애 / 악성 응답 격리** — downstream 이 5xx / 타임아웃 / 깨진 응답을 줘도
  Resilience4j (CircuitBreaker + Retry + TimeLimiter) 가 그 호출을 격리하고, GraphQL 의
  부분 실패 모델이 그 필드만 null + error 로 떨어뜨린다 — 한 downstream 의 불량 응답이 쿼리
  전체나 다른 service 호출을 오염시키지 않는다 (ADR-0003).
- **404 의 구분** — downstream 404 ("대상 없음") 는 장애가 아닌 정상 흐름으로 다뤄
  `NotFoundSignal` 로 변환하고, CircuitBreaker / Retry 의 `ignore-exceptions` 에 등록해
  회로 실패율 / 재시도 대상에서 제외한다. 단건 조회는 404 면 `null` 을 반환 (오류 아님).
- **타임아웃 다중화** — WebClient transport 타임아웃 (2s) 과 Resilience4j TimeLimiter (2.5s)
  를 겹쳐, 느린 downstream 이 게이트웨이 스레드 / connection 을 무한정 잡는 것을 막는다.
- **DTO → 도메인 변환 계층** — downstream 계약이 바뀌어도 변환 계층 (`DtoMappers`) 만
  고치면 GraphQL schema 와 resolver 는 그대로다. WireMock contract test 가 downstream
  계약을 회귀로 잡는다 (ADR-0009).

회귀 테스트: `WebUserAdapterTest` — WireMock 으로 WebClient + Resilience4j 의 200 / 404 /
5xx 응답 처리 검증. `DownstreamWireMockTest` — downstream JSON 을 실제로 받아 GraphQL 응답
으로 변환 (정상 경로) + downstream 5xx 의 부분 실패 격리.

커버: 명시 DTO 역직렬화 + Resilience4j 격리 + 부분 실패 모델 + 변환 계층의 계약 격리.

---

## 변경 로그

- 초안 작성 (이 commit). OWASP API Security Top 10 (2023) 매핑. GraphQL 게이트웨이 특유의
  표면 — 쿼리 depth / complexity DoS (API4), introspection / GraphiQL 노출 (API8 / API9),
  downstream SSRF 표면 (API7), token relay 기반 인증 위임 (API1 / API2 / API5) — 을 기존
  구현 (`QueryGuardConfig`, `SecurityConfig`, `TokenRelayInterceptor`, `GatewayExceptionResolver`,
  Resilience4j 설정) 과 ADR 에 연결.

## 참고

- [OWASP API Security Top 10 (2023)](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [ADR-0001 GraphQL gateway 의 역할 경계](../adr/0001-graphql-gateway-role.md)
- [ADR-0003 downstream 호출의 Resilience4j 적용과 부분 실패](../adr/0003-downstream-resilience.md)
- [ADR-0004 JWT 인증과 token relay](../adr/0004-jwt-auth-token-relay.md)
- [ADR-0005 GraphQL error 처리](../adr/0005-graphql-error-handling.md)
- [ADR-0008 쿼리 complexity / depth 제한](../adr/0008-query-complexity-depth-limit.md)
- [ADR-0009 downstream REST 계약 변경 대응](../adr/0009-downstream-contract-change.md)
