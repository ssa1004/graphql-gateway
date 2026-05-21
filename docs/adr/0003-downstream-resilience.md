# ADR-0003: downstream 호출의 Resilience4j 적용과 부분 실패

## 상태

채택 (Accepted)

## 맥락

게이트웨이는 한 GraphQL 쿼리를 처리하면서 여러 downstream service 를 호출한다. downstream
중 하나가 느려지거나 죽으면 다음 문제가 생긴다.

- **지연 전파** — 느린 downstream 호출이 게이트웨이 스레드를 오래 붙잡으면, 그 사이 다른
  쿼리도 처리량이 떨어진다.
- **장애 증폭** — downstream 이 죽었는데 계속 호출하면, 매번 타임아웃을 기다리느라 게이트웨이
  자원이 소진된다.
- **전체 실패** — 조인 필드 하나의 downstream 이 죽었다고 쿼리 전체가 실패하면, 멀쩡한
  나머지 데이터까지 못 받는다.

## 결정

### downstream 호출 보호 — Resilience4j

모든 downstream 호출을 Resilience4j 로 감싼다. 적용 순서는 안에서 밖으로 TimeLimiter →
Retry → CircuitBreaker 다.

- **TimeLimiter** — 한 번의 시도가 정해진 시간(기본 2.5초)을 넘으면 끊는다. WebClient
  transport 레벨에도 별도 타임아웃(2초)을 둬서 소켓이 영원히 매달리지 않게 한다.
- **Retry** — 일시적 실패(5xx, 타임아웃, 연결 오류)를 지수 backoff 로 최대 3회 재시도한다.
  4xx 와 404 는 재시도해도 결과가 같으므로 재시도 대상에서 뺀다.
- **CircuitBreaker** — 최근 호출의 실패율이 임계치(50%)를 넘으면 회로를 열어 호출 자체를
  즉시 차단한다. 일정 시간 뒤 half-open 으로 전환해 일부 호출을 흘려보고, 성공하면 닫는다.

Resilience4j instance 는 downstream service 단위(`auth`, `billing`, ...)로 분리한다.
billing-platform 이 죽어도 auth-service 호출의 회로는 영향을 받지 않는다. actuator 의
`circuitbreakers` / `retries` endpoint 로 service 별 상태를 관측한다.

404 는 "조회 대상 없음" 이라는 정상 흐름이므로 회로 실패율 집계와 재시도 대상에서 제외한다
(`NotFoundSignal` 을 `ignore-exceptions` 로 등록).

### 부분 실패 — GraphQL 의 강점 활용

GraphQL 은 응답에 `data` 와 `errors` 를 함께 담을 수 있다. 한 필드 resolver 가 실패해도
그 필드만 `null` 로 떨어지고, 나머지 데이터는 `data` 에 그대로 담긴다.

이를 활용해, downstream 장애를 쿼리 전체 실패가 아니라 **해당 필드만의 실패**로 격리한다.
예를 들어 `order { id, invoice }` 쿼리에서 billing-platform 이 죽으면, `invoice` 는 `null`
이 되고 `errors` 에 항목이 추가되지만, `order.id` 는 정상적으로 응답에 담긴다.

downstream 호출 실패는 `DownstreamException` 으로 통일해 던지고, 어느 service 가 문제인지
식별자를 담는다. 이 예외를 GraphQL error 로 변환하는 방법은 ADR-0005 에서 다룬다.

## 결과

- downstream 한 곳의 장애가 게이트웨이 전체나 다른 쿼리로 번지지 않는다.
- 회로가 열린 동안에는 호출을 기다리지 않고 즉시 실패하므로 게이트웨이 자원이 보호된다.
- 클라이언트는 일부 필드가 빈 응답이라도 받는다. 빈 필드의 원인은 `errors` 로 알 수 있다.
- 단점: 부분 실패를 받은 클라이언트가 `errors` 를 확인하지 않으면, 데이터가 비어 있는
  이유를 모른 채 화면을 그릴 수 있다. 클라이언트 쪽 처리 규약이 필요하다.
- TimeLimiter timeout 과 WebClient transport timeout, Retry 횟수의 곱이 한 쿼리의 최악
  지연이 된다. 이 값들은 실제 downstream 응답 분포를 보고 조정한다.
