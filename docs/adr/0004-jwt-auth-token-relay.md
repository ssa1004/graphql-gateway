# ADR-0004: JWT 인증과 token relay

## 상태

채택 (Accepted)

## 맥락

게이트웨이는 클라이언트와 9개 downstream service 사이에 선다. 인증을 어디서 어떻게 처리할지
정해야 한다.

- downstream 9 service 는 이미 각자 JWT 를 검증한다. auth-service 가 토큰을 발급하고,
  나머지 service 는 auth-service 의 JWK Set 으로 서명을 검증하는 resource server 다.
- 게이트웨이가 인증을 아예 안 하면, 인증 안 된 요청이 게이트웨이를 거쳐 downstream 까지
  내려간 뒤에야 거부된다 — 불필요한 호출이 발생한다.
- 게이트웨이가 새 토큰을 발급하면, 토큰 발급 주체가 둘이 되어 키 관리가 복잡해진다.

## 결정

### 게이트웨이는 resource server

게이트웨이도 OAuth2 resource server 로 동작한다. 클라이언트가 보낸 `Authorization: Bearer
<jwt>` 를 auth-service 의 JWK Set 으로 서명 검증한다. 검증 실패면 게이트웨이에서 바로
거부하고 downstream 을 호출하지 않는다.

게이트웨이는 토큰을 **발급하지 않는다**. 발급은 auth-service 의 책임으로 둔다.

### token relay

검증을 통과한 요청의 원본 JWT 를 downstream 호출에 그대로 실어 보낸다(relay). downstream
9 service 는 같은 토큰을 각자 다시 검증한다.

이렇게 하면:

- 인증 주체가 auth-service 하나로 유지된다.
- downstream 이 토큰의 클레임(사용자 id, 역할, 테넌트)을 그대로 보고 자기 권한 판단을 한다.
  게이트웨이가 권한을 대신 해석해 downstream 에 넘기지 않는다 — 권한 판단은 각 service 안에.
- 게이트웨이는 토큰을 새로 만들거나 변형하지 않으므로, 토큰 위조 표면이 늘지 않는다.

### 전파 경로 — Reactor Context

토큰을 들어온 요청에서 downstream 호출 시점까지 옮기는 데 `ThreadLocal` 을 쓰지 않는다.
게이트웨이의 resolver 는 Kotlin coroutine 으로 도는 suspend 함수이고, DataLoader 의 batch
fan-out 은 또 다른 스레드로 갈라진다. `ThreadLocal` 은 이 스레드 경계를 못 넘는다.

대신 Reactor Context 를 쓴다. 요청 진입점(`WebGraphQlInterceptor`)에서 토큰을 Reactor
Context 에 넣으면, Spring for GraphQL 의 요청 처리 전체에 전파되고, `kotlinx-coroutines-
reactor` 가 이를 coroutine context 로 이어준다. downstream 어댑터는 suspend 함수 안에서
이 컨텍스트를 읽어 `Authorization` 헤더에 다시 싣는다.

### endpoint 정책

- `/graphql` — 인증 필요.
- `/graphiql` — 개발용 GraphQL playground UI. 인증 불필요.
- `/actuator/health/*` — k8s probe. 인증 불필요.
- 나머지 `/actuator/*` — 인증 필요.

데모 / 로컬 검증을 위해 인증을 끄는 스위치(`gateway.security.permit-all`)를 둔다. 기본값은
false 이고, downstream / auth-service 가 안 떠 있는 환경에서만 켠다. 운영에서는 켜지 않는다.

## 결과

- 인증 안 된 요청은 게이트웨이에서 차단되어 downstream 호출을 아낀다.
- 인증 주체가 auth-service 하나로 유지된다.
- downstream 이 토큰을 그대로 받으므로 권한 판단을 각자 한다 — 게이트웨이가 권한 로직을
  중복 구현하지 않는다.
- 단점: 게이트웨이와 downstream 9곳이 모두 같은 JWK Set 을 검증하므로, 토큰 검증이 10번
  일어난다. JWK 는 캐시되므로 네트워크 비용은 작지만, 서명 검증 연산은 중복된다.
- 토큰 만료가 게이트웨이 처리 도중 발생하면 downstream 에서 거부될 수 있다. 게이트웨이는
  부분 실패(ADR-0003)로 이를 흡수한다.
