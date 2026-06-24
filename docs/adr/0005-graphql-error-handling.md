# ADR-0005: GraphQL error 처리

## 상태

채택 (Accepted)

## 맥락

resolver 에서 예외가 던져졌을 때 클라이언트에게 무엇을, 어떻게 보여줄지 정해야 한다.

- 예외를 그대로 두면 graphql-java 의 기본 동작이 `INTERNAL_ERROR` 한 줄로 뭉뚱그린다.
  클라이언트는 어느 downstream 때문인지, 재시도하면 될 일인지 알 수 없다.
- 예외 메시지(스택트레이스 포함)를 그대로 노출하면 게이트웨이 내부 구조가 새어 나간다.
- downstream 장애(ADR-0003)와 클라이언트 입력 오류(잘못된 cursor 등)는 성격이 다른데,
  같은 오류로 처리하면 클라이언트가 대응을 못 한다.

## 결정

Spring for GraphQL 의 `DataFetcherExceptionResolver` 를 구현해 resolver 예외를 GraphQL
`errors` 항목으로 변환한다.

매핑 규칙:

| 예외 | errorType | extensions.classification | 의미 |
|---|---|---|---|
| `DownstreamException` | `INTERNAL_ERROR` | `DOWNSTREAM_UNAVAILABLE` | downstream 일시 장애 |
| `IllegalArgumentException` | `BAD_REQUEST` | `BAD_REQUEST` | 잘못된 cursor 등 클라이언트 입력 오류 |
| 그 외 | `INTERNAL_ERROR` | `INTERNAL_ERROR` | 게이트웨이 내부 오류 |

### downstream 장애를 INTERNAL_ERROR 로 둔 이유

downstream 장애는 의미상 503(Service Unavailable) 에 가깝지만, Spring for GraphQL 의
`ErrorType` enum 에는 503 에 해당하는 값이 없다(`BAD_REQUEST`, `UNAUTHORIZED`,
`FORBIDDEN`, `NOT_FOUND`, `INTERNAL_ERROR` 뿐). 그래서 `errorType` 은 `INTERNAL_ERROR` 로
두되, `extensions.classification` 에 `DOWNSTREAM_UNAVAILABLE` 을 실어 구분한다.

클라이언트는 `classification` 을 보고:

- `DOWNSTREAM_UNAVAILABLE` — 일시 장애. 잠시 후 재시도하면 될 수 있다.
- `INTERNAL_ERROR` — 게이트웨이 버그. 재시도해도 같다.
- `BAD_REQUEST` — 요청을 고쳐야 한다.

### extensions 에 service 식별자

`DownstreamException` 은 어느 service 가 문제인지 알고 있다. 이를 `extensions.service` 에
실어 보낸다(`billing`, `auth` 등). 클라이언트나 운영자가 어떤 downstream 때문에 어떤
필드가 비었는지 바로 안다.

### 내부 메시지 비노출

`INTERNAL_ERROR` 의 경우 예외 메시지를 그대로 노출하지 않고 "내부 오류가 발생했습니다"
같은 일반 문구로 대체한다. 원본 예외와 스택트레이스는 서버 로그에만 남긴다.

### 부분 실패 유지

이 변환은 쿼리를 죽이지 않는다. 실패한 필드만 `null` 이 되고 `errors` 에 항목이 추가될 뿐,
나머지 필드는 정상 응답에 담긴다 (ADR-0003 의 부분 실패).

## 결과

- 클라이언트가 오류의 성격을 `classification` 으로 구분해 대응(재시도 / 입력 수정 / 포기)할
  수 있다.
- 어느 downstream 때문에 필드가 비었는지 `extensions.service` 로 식별된다.
- 게이트웨이 내부 구조가 오류 응답으로 새지 않는다.
- 단점: 모든 resolver 예외가 이 한 resolver 를 거치므로, 새 예외 타입이 생기면 매핑 규칙을
  갱신해야 한다. 누락되면 `INTERNAL_ERROR` 로 떨어진다(안전한 기본값이지만 정보가 적다).

## 용어 풀이 (쉽게)

- **GraphQL의 data / errors 동시 응답** — GraphQL 응답은 성공한 부분(data)과 실패한 부분의 사정(errors)을 한 봉투에 같이 담을 수 있다. 그래서 한 칸이 실패해도 전체가 죽지 않고 "여긴 됐고 저긴 이래서 안 됐다"를 함께 알려 준다.
- **extensions(확장 필드)** — 표준 오류 메시지 옆에 우리가 원하는 추가 정보를 붙여 넣는 자유 칸. 여기에 "어느 downstream이 문제였는지(service)"나 "일시 장애인지 입력 오류인지(classification)" 같은 꼬리표를 실어 클라이언트가 대응을 정하게 한다.
- **503 / Service Unavailable** — "서버가 지금 잠깐 일을 못 받는 상태"라는 뜻의 응답 신호. 잠시 후 다시 오면 될 수 있다는 의미라, downstream 일시 장애에 어울린다.
