# Security Policy

## 지원 버전

본 저장소는 포트폴리오 / 연구용 단일 라인이며, `main` 브랜치만 보안 패치를 적용합니다.

| 버전 | 지원 여부 |
|---|---|
| `main` | 지원 |
| 그 외 브랜치 / 태그 | 지원 안 함 |

## 취약점 보고

취약점을 발견하면 GitHub 의 [Private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
기능을 사용해 주세요.

- 저장소: <https://github.com/ssa1004/graphql-gateway>
- 경로: **Security** 탭 → **Report a vulnerability**

공개 issue 에는 취약점 상세를 적지 마세요.

## 응답 시점

- 접수 확인: 영업일 기준 7일 이내
- 영향 평가 / 패치 계획: 14일 이내

## 적용 범위

본 저장소 안의 코드 — `gateway-*` 모듈, `helm/`, `integration-demo.sh`, GitHub Actions
workflow — 만 대상입니다. 외부 의존 라이브러리의 취약점은 업스트림에 직접 보고해 주세요.

## 게이트웨이 보안 관련 메모

GraphQL gateway 라는 성격상 특히 신경 쓴 항목입니다.

- **인증** — OAuth2 resource server 로 동작하며, auth-service 의 JWK Set 으로 JWT 서명을
  검증합니다. 게이트웨이는 토큰을 발급하지 않고 들어온 JWT 를 downstream 으로 그대로
  relay 합니다 (docs/adr/0004).
- **쿼리 DoS** — GraphQL 은 클라이언트가 쿼리 모양을 정하므로, 깊게 중첩되거나 필드가
  폭증하는 쿼리로 게이트웨이와 downstream 을 끌어내릴 수 있습니다. complexity / depth 제한을
  두어 한계를 넘는 쿼리는 실행 전에 거부합니다 (docs/adr/0008).
- **error 노출** — resolver 내부 예외 메시지를 그대로 클라이언트에 노출하지 않습니다.
  downstream 식별자 정도만 GraphQL error extensions 에 싣습니다 (docs/adr/0005).
