# ADR-0001: GraphQL gateway 의 역할 경계

## 상태

채택 (Accepted)

## 맥락

9개의 백엔드 service (auth-service, commerce-ops, bid-ask-marketplace, billing-platform,
gpu-job-orchestrator, search-service, security-log-search, notification-hub,
realtime-feed-service) 가 각자 REST API 를 노출한다. 클라이언트(웹 / 앱)가 한 화면을
그리려면 여러 service 를 따로 호출하고 응답을 직접 조합해야 한다. 호출 수가 많고,
조합 로직이 클라이언트마다 중복된다.

이 위에 GraphQL gateway 를 두기로 했다. 다만 GraphQL 게이트웨이에는 여러 형태가 있어
역할 경계를 먼저 정해야 한다.

- **BFF (Backend For Frontend)** — 게이트웨이가 downstream REST 를 직접 호출하고 응답을
  GraphQL 타입으로 조합한다. schema 는 게이트웨이가 소유한다.
- **Apollo Federation** — 각 service 가 자기 GraphQL subgraph 를 노출하고, 게이트웨이가
  이를 supergraph 로 합성한다. service 쪽에 GraphQL 구현이 필요하다.
- **Schema stitching** — 여러 GraphQL 스키마를 게이트웨이에서 이어 붙인다. federation 의
  옛 방식.

## 결정

**BFF 형태**를 택한다. 게이트웨이가 downstream 9 service 의 REST 를 WebClient 로 직접
호출하고, 응답을 게이트웨이가 소유한 GraphQL schema 로 조합한다.

이유:

- downstream 9 service 는 이미 REST 로 구현돼 있고, 각자에 GraphQL subgraph 를 추가하는
  것은 9개 레포의 동시 변경을 요구한다. Federation 의 이점(스키마 분산 소유)보다 비용이 크다.
- 게이트웨이가 schema 를 단독 소유하면 클라이언트 요구에 맞춘 타입 설계가 자유롭다.
  `Order.invoice` 처럼 service 경계를 가로지르는 조인을 게이트웨이 schema 에서 자연스럽게
  표현할 수 있다.
- service 들은 REST 계약만 안정적으로 유지하면 되고, GraphQL 을 몰라도 된다.

게이트웨이의 범위:

- **포함** — 9 service 의 핵심 타입 조회, service 간 조인(`Order.invoice` 등), 인증 토큰
  검증과 relay, downstream 장애 격리, 쿼리 비용 제한.
- **제외** — 쓰기(mutation) 의 대부분. 게이트웨이는 조회 위주이고, 복잡한 쓰기는 각 service
  REST 를 직접 호출하는 편이 트랜잭션 경계가 명확하다. mutation 은 알림 읽음 처리 정도만 둔다.
- **제외** — 비즈니스 규칙. 게이트웨이는 조합과 전달만 하고, 도메인 규칙은 각 service 안에 있다.

## 결과

- 클라이언트는 한 endpoint(`/graphql`)로 필요한 데이터만 골라 받는다. over-fetching 이 준다.
- 게이트웨이가 9 service 의 의존 중심점이 된다 — 게이트웨이 장애가 전체 조회에 영향을 준다.
  이를 완화하려고 downstream 장애 격리(ADR-0003)와 부분 실패 모델(ADR-0005)을 둔다.
- downstream REST 계약이 바뀌면 게이트웨이의 변환 계층을 고쳐야 한다. 이 영향을 줄이는
  방법은 ADR-0009 에서 다룬다.
