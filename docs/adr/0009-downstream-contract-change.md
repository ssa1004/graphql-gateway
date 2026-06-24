# ADR-0009: downstream REST 계약 변경 대응

## 상태

채택 (Accepted)

## 맥락

게이트웨이는 9개 downstream service 의 REST 응답에 의존한다. 이 service 들은 각자 따로
개발 / 배포되므로, 게이트웨이가 모르는 사이에 REST 응답이 바뀔 수 있다.

- 필드 추가 — 보통 호환되지만, 역직렬화가 엄격하면 깨진다.
- 필드 이름 변경 / 제거 — 게이트웨이의 매핑이 깨진다.
- enum 값 추가 — 게이트웨이가 모르는 값이 들어온다.
- 응답 형태 변경 — 가장 큰 영향.

게이트웨이는 이런 변경을 가능한 한 흔들리지 않고 흡수해야 한다.

## 결정

### DTO 와 도메인 모델 분리

downstream 응답을 GraphQL 타입에 직접 묶지 않는다. 계층을 둔다.

```
downstream JSON  ->  DTO  ->  도메인 모델  ->  GraphQL 타입
                  (adapter-out)         (adapter-in)
```

- **DTO** (`adapter-out`) — downstream JSON 모양 그대로 받는 전송 객체.
- **도메인 모델** (`gateway-domain`) — 게이트웨이 내부 표현. GraphQL schema 가 이것에 매핑된다.

downstream 스펙이 바뀌면 DTO 와 DTO→도메인 매퍼만 고치면 된다. GraphQL schema 와 resolver,
클라이언트 계약은 그대로다. 변경의 영향 범위가 adapter-out 한 곳에 갇힌다.

### 느슨한 역직렬화

DTO 는 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 붙인다. downstream 이 필드를
추가해도 역직렬화가 깨지지 않는다.

DTO 의 필드는 모두 nullable 로 받는다. downstream 이 필드를 누락해도 역직렬화 자체는
성공하고, 매퍼 단계에서 검증한다.

### 매퍼에서의 방어

DTO→도메인 매퍼가 계약 위반을 걸러낸다.

- **필수 필드 누락** — 도메인 모델에 반드시 있어야 하는 필드(id 등)가 DTO 에서 null 이면,
  `DownstreamException` 을 던진다. 스펙을 어긴 응답을 게이트웨이 안쪽으로 흘려보내지 않는다.
- **알 수 없는 enum** — downstream 이 게이트웨이가 모르는 enum 값을 보내면, 안전한 기본값
  으로 떨어뜨린다(역직렬화 / 쿼리 전체를 깨뜨리지 않는다).
- **선택 필드 누락** — nullable 한 도메인 필드는 null 또는 합리적 기본값으로 채운다.

### contract test

downstream 계약을 게이트웨이 테스트로 고정한다. WireMock 으로 각 downstream 의 응답을
재현하고, 그 JSON 이 GraphQL 응답으로 올바르게 변환되는지 검증한다(`DownstreamWireMockTest`).

이 테스트는 "게이트웨이가 기대하는 downstream 응답 모양" 의 실행 가능한 명세다. downstream
계약이 바뀌었는데 게이트웨이가 안 따라갔다면, 이 테스트가 깨져서 알려 준다. downstream 9
service 가 실제로 떠 있지 않아도 게이트웨이 CI 에서 자족적으로 돈다.

## 결과

- downstream 스펙 변경의 영향이 adapter-out 의 DTO / 매퍼로 국한된다. schema 와 resolver 는
  대체로 그대로다.
- downstream 의 필드 추가는 게이트웨이를 깨뜨리지 않는다(느슨한 역직렬화).
- 스펙을 어긴 downstream 응답은 매퍼에서 걸러져, 깨진 데이터가 클라이언트까지 가지 않는다.
- WireMock contract test 가 downstream 계약을 회귀로 잡아 준다.
- 단점: downstream 의 필드 이름 변경 / 제거처럼 큰 변경은 여전히 매퍼 수정이 필요하다.
  계층 분리는 변경을 한 곳에 모을 뿐, 변경 자체를 없애지는 못한다.
- 단점: WireMock 의 stub 은 downstream 의 실제 응답을 흉내 낸 것이라, downstream 이 stub
  과 다르게 바뀌면 게이트웨이 테스트는 통과해도 운영에서 깨질 수 있다. stub 을 downstream
  의 실제 응답과 주기적으로 맞춰야 이 간극이 작아진다.

## 용어 풀이 (쉽게)

- **역직렬화 (deserialization)** — 서버가 보낸 글자 덩어리(JSON)를 우리 코드가 다룰 수 있는 객체로 풀어 옮겨 담는 일. 외국어 편지를 우리말 양식 서류로 옮겨 적는 셈이라, 칸이 안 맞으면 옮기다 깨진다.
- **DTO vs 도메인 모델 (계층 분리)** — DTO는 downstream이 준 JSON을 생긴 그대로 받는 임시 그릇, 도메인 모델은 게이트웨이가 내부에서 쓰는 정돈된 표현. 둘을 나누면 바깥 형식이 바뀌어도 DTO와 변환기만 고치고 안쪽은 안 건드린다.
- **느슨한 역직렬화 (ignoreUnknown)** — "모르는 칸이 와도 무시하고 아는 것만 채워라"라고 설정해, downstream이 필드를 새로 추가해도 옮기다 깨지지 않게 하는 것.
- **contract test(계약 테스트) / WireMock** — "downstream이 이런 모양으로 답하면 게이트웨이가 이렇게 변환해야 한다"를 못 박아 둔 자동 검사. WireMock은 진짜 downstream 대신 미리 짜둔 가짜 응답을 돌려주는 모형 서버라, 9개 서버를 안 띄워도 이 검사를 돌릴 수 있다.
