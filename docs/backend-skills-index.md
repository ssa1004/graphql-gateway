# 백엔드 스킬 인덱스 — 이 레포에서 무엇을 배우나

> 이 레포(9 service 통합 GraphQL gateway)가 시연하는 백엔드 패턴을
> **"무엇 → 이 레포 어디서 → 왜(ADR) → 더 깊은 이론"** 으로 잇는 학습용 인덱스.
> "이 패턴 공부하려면 어디부터 보나"의 진입점. 설명을 다시 쓰지 않고 코드·결정·이론으로 연결만 한다.
>
> 짝 학습 레포: [dev-lab](https://github.com/ssa1004/dev-lab) (이론) ↔ 이 레포 (구현).

## API 설계 — GraphQL gateway / BFF

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **BFF 형태 게이트웨이** | `gateway-adapter-in` GraphQL controller (`@QueryMapping`) + `gateway-adapter-out` 9 service WebClient | [ADR-0001](adr/0001-graphql-gateway-role.md) | Federation 대신 downstream REST 직접 호출 + schema 단독 소유 |
| **over/under-fetching 제거** | schema 기반 필드 선택 — `gateway-bootstrap/.../schema.graphqls` | ADR-0001 | 클라이언트가 쓸 필드만, 한 화면을 한 쿼리로 |
| **service 간 조인 (schema stitching)** | `Order.user` / `Order.invoice` 등 `@SchemaMapping` resolver | ADR-0001, [ADR-0006](adr/0006-schema-design.md) | service 경계를 가로지르는 조인을 게이트웨이 schema 에서 표현 |
| **Relay cursor connection 페이징** | `CursorCodec` (domain) + `search` / `securityAlerts` connection 타입 | ADR-0006 | offset 노출 대신 불투명 cursor — 페이징 구현 바뀌어도 클라이언트 계약 유지 |

→ 이론: `dev-lab/api-design` (REST vs GraphQL, over/under-fetching, BFF), `dev-lab/system-design` (gateway 패턴, schema stitching vs federation)

## N+1 방지 — DataLoader 배칭

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **DataLoader 배칭** | `gateway-adapter-in` `@BatchMapping` + `BatchLoaderRegistry` (userLoader / invoiceLoader / notificationsLoader / feedLoader) | [ADR-0002](adr/0002-dataloader-n-plus-1.md) | resolver 는 키만 등록 → graphql-java 가 한 tick 키를 모아 batch 1회 |
| **중복 키 합치기** | 공유 `userLoader` (`Order.user` + `GpuJob.submitter`) | ADR-0002 | 한 쿼리 안 같은 키를 한 번만 조회 |
| **단건 endpoint fan-out** | adapter-out 에서 키 수만큼 단건 호출을 coroutine 병렬 | ADR-0002 | bulk endpoint 없어도 직렬 N회 → 병렬 1묶음 |

→ 이론: `dev-lab/api-design` (N+1 문제, DataLoader 배칭), `dev-lab/webflux` (DataLoader 비동기 / `CompletableFuture`)

## 비동기 · 논블로킹

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **suspend resolver + WebClient** | `gateway-adapter-in` suspend resolver, `gateway-adapter-out` non-blocking WebClient | — | downstream I/O 대기에 스레드를 묶지 않음 |
| **Reactor Context 로 토큰 전파** | `WebGraphQlInterceptor` → Reactor Context → coroutine context | [ADR-0004](adr/0004-jwt-auth-token-relay.md) | `ThreadLocal` 은 coroutine / DataLoader fan-out 스레드 경계를 못 넘음 |

→ 이론: `dev-lab/webflux` (논블로킹 / Reactor / coroutine 상호운용, context 전파)

## 회복탄력성 — downstream 9 service 호출

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **CircuitBreaker / Retry / TimeLimiter** | `gateway-adapter-out` WebClient 어댑터 (Resilience4j, service 단위 instance) | [ADR-0003](adr/0003-downstream-resilience.md) | 적용 순서 TimeLimiter→Retry→CircuitBreaker, service 별 회로 분리 |
| **지수 backoff 재시도** | 5xx / 타임아웃 / 연결오류만 재시도, 4xx·404 제외 | ADR-0003 | 의미 없는 재시도 배제 + thundering herd 완화 |
| **부분 실패 격리** | nullable 조인 필드 + `DownstreamException` → GraphQL `errors` | ADR-0003, [ADR-0005](adr/0005-graphql-error-handling.md) | downstream 한 곳이 죽어도 그 필드만 null, 쿼리 전체는 삶 |

→ 이론: `dev-lab/resilience` (circuit breaker / retry / timeout / bulkhead), `dev-lab/networking` (downstream timeout 합의 한계)

## 캐싱 — Caffeine

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **단기 로컬 캐시** | `gateway-adapter-out` Caffeine — 안정적 데이터(사용자 프로필)만 | [ADR-0007](adr/0007-caching.md) | DataLoader 는 한 요청 내 중복만 합침 → 요청 간 반복 조회는 캐시로 |
| **cache stampede 회피** | Caffeine `AsyncLoadingCache` (동시 로드 1회로 합침) | ADR-0007 | 만료 순간 같은 키 동시 요청이 downstream 을 몰아치는 것 방지 |

→ 이론: `dev-lab/api-design` (캐시 위치 / TTL trade-off), `dev-lab/system-design` (로컬 vs 공유 캐시)

## 인증 — JWT 검증 + token relay

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **OAuth2 resource server (JWT)** | `gateway-adapter-in` Spring Security — auth-service JWK Set 검증 | [ADR-0004](adr/0004-jwt-auth-token-relay.md) | 인증 안 된 요청을 게이트웨이에서 차단 → downstream 호출 절약 |
| **token relay** | 원본 JWT 를 downstream 호출에 그대로 전달 | ADR-0004 | 인증 주체를 auth-service 하나로 유지, 권한 판단은 각 service |

→ 이론: `dev-lab/api-design` (인증 / 토큰 전파), `dev-lab/system-design` (게이트웨이 인증 경계)

## 쿼리 비용 제한 (GraphQL DoS 방어)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **depth / complexity 제한** | `gateway-adapter-in` graphql-java instrumentation (`MaxQueryDepth` / `MaxQueryComplexity`) | [ADR-0008](adr/0008-query-complexity-depth-limit.md) | 실행 전 정적 검사 — 한계 초과 쿼리는 resolver 호출 0으로 거부 |
| **페이지 크기 제한** | `first` 인자 1~100 으로 절단 | ADR-0008 | 거대한 페이지로 downstream 끌어가는 것 차단 |

→ 이론: `dev-lab/api-design` (GraphQL 쿼리 비용 / rate-limit), `dev-lab/system-design` (gateway DoS 방어)

## downstream 계약 변경 흡수

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **DTO ↔ 도메인 모델 분리** | `gateway-adapter-out` DTO → `gateway-domain` 모델 → GraphQL 타입 | [ADR-0009](adr/0009-downstream-contract-change.md) | 계약 변경 영향을 adapter-out 한 곳에 가둠 |
| **느슨한 역직렬화 + 매퍼 방어** | `@JsonIgnoreProperties(ignoreUnknown)`, 알 수 없는 enum → 안전 기본값 | ADR-0009 | 필드 추가는 안 깨지고, 스펙 위반은 매퍼에서 걸러냄 |
| **WireMock contract test** | `DownstreamWireMockTest`, `WebUserAdapterTest` | ADR-0009 | downstream 9곳이 안 떠 있어도 게이트웨이 CI 자족 + 계약 회귀 검출 |

→ 이론: `dev-lab/api-design` (contract test / 스키마 진화), `dev-lab/system-design` (anti-corruption layer)

## 관측성 (Observability)

| 패턴 | 이 레포 어디서 | 한 줄 |
|------|---------------|-------|
| **Micrometer / OTLP tracing** | `gateway-bootstrap/.../application.yml` (`management.tracing`, OTLP export) | demo 프로필에선 export off, 운영에선 trace 전파 |
| **Resilience4j actuator** | `/actuator/circuitbreakers` · `/actuator/retries` | service 별 회로 / 재시도 상태 관측 |
| **liveness / readiness probe** | `/actuator/health/{liveness,readiness}` (Dockerfile · compose healthcheck) | k8s probe + compose healthcheck 분리 |

→ 이론: `dev-lab/observability` (3축 + tracing 전파), `dev-lab/networking` (커넥션 풀 sizing / downstream timeout)

## 학습 순서 제안 (이 레포 기준)

1. **README 상단 + "시스템 흐름" mermaid** → `order { id, user, invoice }` 한 쿼리가 3 service 를 조인하고 부분 실패를 흡수하는 흐름 감 잡기
2. **[docs/adr/](adr/)** → 왜 그렇게 했나 (ADR 9건) ← 이 레포의 핵심 학습 자료. 특히 [ADR-0001](adr/0001-graphql-gateway-role.md)(BFF 경계) → [ADR-0002](adr/0002-dataloader-n-plus-1.md)(N+1) → [ADR-0003](adr/0003-downstream-resilience.md)(부분 실패) 순서
3. **위 패턴 표** 에서 관심 패턴 → 코드(모듈) + 해당 ADR + dev-lab 이론
4. **`make up && make demo`** → GraphQL 쿼리가 실제로 9 service 를 조합하는 것을 눈으로 확인 (`integration-demo.sh` 9개 시나리오)
5. **[docs/security/owasp-mapping.md](security/owasp-mapping.md)** → API 보안 관점 (OWASP API Security Top 10)

> 짝 학습 레포: [dev-lab](https://github.com/ssa1004/dev-lab) (이론) ↔ 이 레포 (구현). 이론에서 "왜"를, 여기서 "실제로 어떻게"를 본다.
