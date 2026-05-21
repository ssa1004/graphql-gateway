# Contributing

본 저장소의 개발 흐름과 commit 규칙을 정리한 문서입니다.

## 브랜치 전략

GitHub Flow 를 따릅니다. 작은 규모의 프로젝트이므로 git-flow 와 같은 복잡한 모델은 사용하지
않습니다.

```
main (protected)
  ├── feature/trade-feed-join       ← 기능 브랜치
  ├── fix/cursor-decode-edge-case
  └── docs/update-adr
```

`main` 에서 새 브랜치를 생성하고 (`git checkout -b feature/<짧은-설명>`), 작업 후 PR 을
열어 코드 리뷰를 받고, CI 통과 후 Squash and merge 합니다. 머지 후 feature 브랜치는
삭제합니다. `main` 은 항상 배포 가능한 상태로 유지됩니다.

## Commit 메시지

Conventional Commits 형식을 따릅니다.

```
<type>(<scope>): <짧은 설명, 50자 이내>

<상세 설명, 한 줄에 72자 이내>
- 무엇이 / 왜 변경되었는지
- 영향받는 모듈
```

자주 사용하는 type 은 다음과 같습니다.

- `feat`: 새 기능 추가
- `fix`: 버그 수정
- `refactor`: 동작 변경 없는 코드 정리
- `test`: 테스트 추가 / 수정
- `docs`: 문서만 변경
- `chore`: 빌드 / 설정 / 의존성 변경
- `perf`: 성능 개선

scope 에는 모듈 이름 (`domain`, `application`, `adapter-in`, `adapter-out` 등) 이 들어갑니다.
게이트웨이 특성상 `feat(adapter-in)` (resolver) 과 `feat(adapter-out)` (downstream 어댑터) 의
빈도가 높습니다.

### 예시

```
feat(adapter-in): Trade.feed 조인을 DataLoader 로 추가

- realtime-feed-service 의 skuId 별 피드를 feedLoader 로 batch 조회
- N+1 방지 — 체결 N건을 펼쳐도 downstream 호출은 한 묶음
```

```
fix(adapter-out): downstream 404 가 회로 실패로 집계되던 문제

404 는 "조회 대상 없음" 이라는 정상 흐름인데 CircuitBreaker 의 실패율에 잡혀
회로가 불필요하게 열리던 문제. NotFoundSignal 을 ignore-exceptions 로 등록.
```

## Commit 단위

한 commit 은 한 가지 논리적 변경만 담는 것을 원칙으로 합니다. 50개 파일을 초과하는 commit
은 거의 항상 분리 가능합니다. WIP commit 은 PR 머지 전에 squash 합니다.

## 테스트

PR 전 `./gradlew check` 통과가 필수입니다. downstream 9 service 는 떠 있지 않아도 됩니다 —
WireMock 과 stub 어댑터로 모든 테스트가 자족적으로 동작합니다.

- 도메인 단위 검증: `:gateway-domain:test`
- use case 단위 검증: `:gateway-application:test`
- downstream 어댑터 (WireMock): `:gateway-adapter-out:test`
- GraphQL end-to-end (GraphQlTester + WireMock): `:gateway-bootstrap:test`

## 코드 스타일

- Kotlin: official code style (`.editorconfig` 의 4-space).
- 100% Kotlin — Java 소스를 추가하지 않습니다.
- 주석 / 문서는 자연스러운 한국어 (영어 직역체 지양).
