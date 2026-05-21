# ADR-0002: DataLoader 로 N+1 쿼리 방지

## 상태

채택 (Accepted)

## 맥락

GraphQL 의 조인 필드는 부모 객체마다 따로 resolver 가 호출된다. 예를 들어 주문 목록을
조회하면서 각 주문의 인보이스를 함께 받는 쿼리를 생각해 보자.

```graphql
query {
  order(id: "o-1") { id invoice { id status } }
}
```

`order` 가 하나면 문제가 없지만, 목록으로 N개를 펼치면 `Order.invoice` resolver 가 N번
호출되고, 그때마다 billing-platform 으로 REST 요청이 한 번씩 나간다. 부모 1번 + 자식 N번 =
N+1 호출이다. downstream 입장에서는 한 GraphQL 쿼리가 수십 개의 REST 요청으로 증폭된다.

## 결정

조인 필드는 **DataLoader 패턴**으로 batch 호출한다. Spring for GraphQL 의
`BatchLoaderRegistry` 와 graphql-java 의 `DataLoader` 를 사용한다.

동작:

1. resolver(`@SchemaMapping`)는 downstream 을 직접 호출하지 않고 `DataLoader.load(key)` 로
   조회할 키만 등록한다. 이 호출은 즉시 값을 주지 않고, 나중에 완성될 `CompletableFuture` 를
   돌려준다.
2. graphql-java 는 한 실행 tick 동안 쌓인 키를 모은다.
3. tick 이 끝나면 등록된 batch 함수를 키 집합으로 **한 번** 호출한다.
4. batch 함수가 키별 결과 맵을 돌려주면, graphql-java 가 각 future 를 해당 값으로 완성한다.

게이트웨이에는 4개의 DataLoader 를 둔다.

| DataLoader | 조인 필드 | downstream |
|---|---|---|
| `userLoader` | `Order.user`, `GpuJob.submitter` | auth-service |
| `invoiceLoader` | `Order.invoice` | billing-platform |
| `notificationsLoader` | `User.notifications` | notification-hub |
| `feedLoader` | `Trade.feed` | realtime-feed-service |

`Order.user` 와 `GpuJob.submitter` 는 같은 `userLoader` 를 공유한다 — 한 쿼리 안에서 사용자
조회가 여러 경로로 들어와도 키가 한 곳에 모인다.

batch 함수는 application 계층의 `*ByIds` use case 를 호출한다. downstream 이 bulk 조회
endpoint 를 지원하지 않는 경우, adapter-out 어댑터가 키 수만큼의 단건 호출을 coroutine 으로
병렬 fan-out 한다. 이 경우에도 GraphQL 쿼리 레벨의 N+1(직렬 N회)은 사라지고, 게이트웨이
내부의 병렬 호출 한 묶음으로 바뀐다.

## 결과

- 조인이 있는 쿼리에서 downstream 호출이 N+1 에서 1묶음으로 준다.
- DataLoader 는 같은 키를 한 번만 조회한다 — 중복 키가 자동으로 합쳐진다.
- resolver 코드가 단순해진다. resolver 는 키만 넘기고, batch 와 캐싱은 DataLoader 가 맡는다.
- 단점: batch 함수가 모든 키를 한 번에 받으므로, 키가 매우 많은 쿼리는 downstream 에 큰
  부하를 줄 수 있다. 쿼리 페이지 크기 제한(ADR-0008)과 함께 다뤄야 한다.
- DataLoader 의 batch 함수 이름과 resolver 파라미터 이름이 일치해야 graphql-java 가 같은
  인스턴스를 주입한다. 이 결합은 코드 주석으로 명시한다.
