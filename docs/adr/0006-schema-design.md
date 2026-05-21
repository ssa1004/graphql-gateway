# ADR-0006: GraphQL schema 설계 — nullability 와 페이징

## 상태

채택 (Accepted)

## 맥락

게이트웨이 schema 의 타입을 설계하면서 두 가지를 결정해야 한다.

1. 어떤 필드를 nullable 로 둘 것인가.
2. 목록(검색 결과, 알림 목록 등)을 어떻게 페이징할 것인가.

## 결정

### nullability — 조인 필드는 nullable, 본문 필드는 non-null

GraphQL 에서 non-null 필드(`String!`)의 resolver 가 `null` 을 돌려주거나 실패하면, 그 null
이 부모로 전파되어 부모 객체 전체가 null 이 된다. 최악의 경우 쿼리 루트까지 올라가 응답이
통째로 비워진다.

게이트웨이는 downstream 장애를 해당 필드만의 실패로 격리하려 한다(ADR-0003). 따라서
**다른 service 를 조인하는 필드는 nullable** 로 둔다.

- `Order.invoice: Invoice` (non-null 아님) — billing-platform 이 죽으면 `invoice` 만 null.
- `Order.user: User` — auth-service 가 죽으면 `user` 만 null.
- `Trade.feed`, `GpuJob.submitter` 도 같은 이유로 nullable.

반면 **한 service 의 응답 안에서 그 service 가 항상 채워 주는 본문 필드**는 non-null 로
둔다. `Order.id`, `Order.status`, `Order.totalAmount` 등. 이 필드가 비었다는 것은 곧
downstream 응답이 스펙을 어겼다는 뜻이고, 그때는 그 주문 객체 자체가 의미 없으므로 null
전파가 오히려 맞다.

`Query` 의 단건 조회(`user(id)`, `order(id)` 등)도 nullable 로 둔다 — 해당 id 의 대상이
없는 것은 정상 흐름이기 때문이다.

### 페이징 — Relay cursor connection

목록은 offset 노출 대신 Relay cursor connection 규약을 쓴다.

```graphql
type SearchConnection {
  edges: [SearchEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}
type SearchEdge { cursor: String!  node: SearchHit! }
type PageInfo { hasNextPage: Boolean!  endCursor: String }
```

조회는 `search(q: "...", first: 20, after: "<cursor>")` 형태다. `after` 는 "이 cursor
다음부터" 를 뜻한다.

cursor 는 **불투명(opaque) 문자열**이다. 클라이언트가 해석하면 안 된다. 현재 구현은
`offset:<n>` 을 Base64 로 감싼 값이다. downstream 이 모두 offset/limit 페이징이라 이 정도면
충분하고, 추후 keyset 페이징으로 바뀌어도 cursor 표현(`CursorCodec`)만 교체하면 schema 와
클라이언트 계약은 그대로다.

cursor 표현을 불투명하게 둔 이유가 바로 이것이다 — 페이징 구현을 바꿔도 클라이언트가
영향받지 않는다.

### enum

상태 값은 문자열 대신 enum 으로 둔다(`OrderStatus`, `TradeStatus`, `JobStatus` 등). schema
가 자체 문서가 되고, 잘못된 값이 컴파일 / 검증 단계에서 걸린다. downstream 이 게이트웨이가
모르는 새 enum 값을 보내면, 변환 계층이 안전한 기본값으로 떨어뜨린다(ADR-0009).

## 결과

- downstream 한 곳의 장애가 그 조인 필드만 null 로 만들고, 쿼리 전체를 비우지 않는다.
- 페이징 구현(offset → keyset 등)을 바꿔도 cursor 가 불투명하므로 클라이언트 계약이 안 깨진다.
- non-null 본문 필드 덕에, downstream 이 스펙을 어긴 응답은 그 객체 단위로 걸러진다.
- 단점: nullable 이 많으면 클라이언트가 모든 조인 필드에 null 체크를 해야 한다. 이는 부분
  실패를 다루는 GraphQL 게이트웨이의 본질적 비용으로 받아들인다.
