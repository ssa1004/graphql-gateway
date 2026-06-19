# Screenshots / demo GIFs

이 폴더는 README 가 참조하는 GraphiQL 데모 GIF·스크린샷을 담는 곳입니다. 이미지는 **실제로
띄운 게이트웨이에서 직접 캡처**합니다 — 꾸며 넣지 않습니다. 아래 명령으로 누구나 같은 화면을
재현해 캡처할 수 있습니다.

This folder holds the GraphiQL demo GIF / screenshots referenced from the top-level README.
All images are **captured from a real, running gateway** — never fabricated. The commands
below reproduce the exact screens.

## 사전 준비 / Prerequisites

- Docker (게이트웨이를 컨테이너로 띄울 때) 또는 JDK 21 (`make run` 으로 호스트 실행).
- GIF 캡처 도구 (택1):
  - macOS: [Kap](https://getkap.co/) 또는 [Gifox](https://gifox.app/)
  - Linux: [Peek](https://github.com/phw/peek) 또는 `byzanz-record`
- (선택) `jq` — `integration-demo.sh` 출력 가독성용.

## 1. 게이트웨이 기동 / Start the gateway

demo 프로필은 downstream 9 service 가 없어도 됩니다 — in-memory stub 어댑터가 응답을
대신하고 JWT 인증을 끕니다.

```bash
make up                                # = docker compose up --build -d (demo 프로필)
# 또는 호스트에서 직접:
# make run                             # = ./gradlew :gateway-bootstrap:bootRun --args='--spring.profiles.active=demo'

# health 가 UP 이 될 때까지 대기
curl -sf http://localhost:8080/actuator/health/readiness && echo " — ready"
```

## 2. GraphiQL 데모 GIF (`graphiql-demo.gif`)

브라우저에서 GraphiQL playground 를 열고, 쿼리를 입력해 실행하는 화면을 녹화합니다.

```bash
open http://localhost:8080/graphiql      # macOS (Linux: xdg-open)
```

GIF 캡처 도구로 GraphiQL 창을 녹화하면서 아래 쿼리를 붙여넣고 **Run (▶)** 을 누릅니다 —
9 service 조회 + 3-service 조인이 한 endpoint 로 도는 것을 보여줍니다:

```graphql
query {
  order(id: "o-1") {
    id
    status
    totalAmount
    user { id email displayName }      # auth-service
    invoice { id status totalAmount }  # billing-platform
  }
}
```

녹화한 파일을 이 폴더에 `graphiql-demo.gif` 로 저장합니다.

## 3. 부분 실패(partial failure) 캡처 (`partial-failure.png`)

README headline 인 "한 downstream 이 죽어도 그 필드만 null" 을 실제로 보여주려면,
billing-platform 만 장애로 두고 같은 주문을 조회합니다. WireMock 기반 회귀 테스트
`DownstreamWireMockTest` 가 정확히 이 시나리오(billing 503)를 검증하므로, 캡처 화면은
그 테스트와 동일한 응답 모양이어야 합니다 (README "Partial-failure isolation — sample
response" 의 JSON 과 일치).

billing 만 죽은 응답을 직접 보는 가장 빠른 방법은 통합 테스트를 돌려 로그를 캡처하거나,
실제 downstream 환경(`gateway.downstream.stub=false`)에서 billing 만 내린 뒤 위 쿼리를
GraphiQL 에서 실행하는 것입니다. 응답의 `data.order.invoice` 가 `null`, `errors[0]` 에
`service: "billing"`, `classification: "DOWNSTREAM_UNAVAILABLE"` 가 실린 화면을
`partial-failure.png` 로 저장합니다.

## 4. 정지 / Tear down

```bash
make down        # 정지 (볼륨 유지)
# make clean     # 정지 + 볼륨 삭제
```

## 캡처 후 / After capturing

이 폴더에 이미지를 두고, 최상위 README 에서 상대경로로 참조하세요. 예:

```markdown
![GraphiQL demo](docs/screenshots/graphiql-demo.gif)
![Partial failure](docs/screenshots/partial-failure.png)
```
