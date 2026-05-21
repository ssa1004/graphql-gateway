# AI assistant 작업 시 메타 규칙

이 문서는 Codex / Claude / 다른 AI assistant 가 이 repo (graphql-gateway) 에서 작업할 때
지켜야 할 규칙을 정리합니다. 과거에 실제로 어겼던 항목들을 모았습니다.

> 이 디렉토리(`.codex/`, `.claude/`) 는 `.gitignore` 에 포함되어 GitHub 에 올라가지 않습니다.

---

## 1. Commit author / 이메일

- **회사 이메일 절대 금지**. 개인 또는 GitHub noreply 만.
- 권장: `wittyahn@users.noreply.github.com`
- `git config --local user.email "wittyahn@users.noreply.github.com"` 으로 repo 마다 명시.

## 2. Commit 메시지

- **AI co-author trailer 금지**:
  ```
  Co-Authored-By: ... <noreply@anthropic.com>   ← 절대 추가하지 말 것
  ```
- **금지 표현** (이력서 / 포트폴리오 톤):
  - "이력서 매칭", "면접에서", "면접 talking point", "면접 마이너스"
  - "차별 포인트", "핵심 차별", "ROI"
  - "한 컷", "한 페이지"
- **회사명 / 이전 직장 정보 금지** — OSS / 표준 명칭 인용은 가능
  (GraphQL, Relay, DataLoader, Resilience4j, Spring for GraphQL 등).
- **Conventional Commits 따르기**: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
  커밋 메시지 본문은 한국어.

## 3. 코드 / 문서 안 표현

- 도메인 코드와 문서는 사실 위주, 자연스러운 한국어.
- 영어 직역체 금지: "Trade-off" → "장단점", "When to revisit" → "다시 검토할 시점".
- 이모지 (★, ⭐, 🔴, 🟡, 🥇 등) 과다 사용 금지.
- 자기홍보 톤 ("이걸 보여주면 어필") 금지.
- 어색한 번역티 표현 금지 — "보안 위생", "박제" 같은 표현 사용하지 말 것.
- italics(`*강조*`) 를 문장마다 남발하지 말 것. 정말 필요한 곳에만.

## 4. 파일 / 디렉토리

- **빈 placeholder 패키지 금지** — 실제 클래스가 0개인 패키지를 미리 만들지 말 것 (YAGNI).
- **작업 메모 파일 commit 금지** — `PLAN.md`, `TODO.md`, `notes.md` 같은 것은 `.gitignore` 에.
- 빌드 산출물 (`build/`, `out/`) 은 무조건 `.gitignore`. 단 `**/out/` 처럼 광범위한 패턴은
  hexagonal 패키지 (`adapter/out/`) 도 잡아버리니 `/out/` + `*/out/` + `!**/src/**/out/`
  패턴 사용.

## 5. 작업 절차

- 코드를 새로 만들기 전에 기존 패턴 확인 (다른 모듈 / resolver 가 어떻게 했는지).
- 큰 변경 전에는 설계 검토 → 동의 → 구현 순서.
- 변경 후 항상 `./gradlew check` 통과 확인. downstream 9 service 는 안 떠 있어도 되며,
  WireMock / stub 어댑터로 모든 테스트가 자족적으로 돈다.
- 한 commit 은 하나의 논리적 변경만 (5-15개 파일 정도).

## 6. 이 repo 특유의 제약

- **100% Kotlin** — Java 소스 파일 0개를 유지한다. `find . -name '*.java'` 결과가 0이어야 한다.
- **GraphQL schema 는 코드와 함께 움직인다** — `schema.graphqls` 의 타입 / 필드를 바꾸면
  resolver(`@QueryMapping` / `@SchemaMapping` / `@BatchMapping`) 와 도메인 모델도 같이 고친다.
- **N+1 방지** — 조인 필드(`Order.invoice` 등)는 반드시 DataLoader 를 거친다. resolver 안에서
  downstream 을 직접 단건 호출하지 말 것.
- **downstream 변경 흡수** — downstream REST 응답은 DTO 로 받고 도메인 모델로 변환한다.
  GraphQL schema 가 downstream JSON 모양에 직접 묶이지 않게 한다.

## 7. 도메인 / 기술 용어

- **BFF** = Backend For Frontend. 클라이언트 화면에 맞춰 여러 백엔드를 조합하는 게이트웨이.
- **DataLoader** = 같은 tick 의 키를 모아 batch 로 한 번에 조회하는 N+1 방지 패턴.
- **Relay cursor connection** = `edges` / `node` / `pageInfo` / `cursor` 로 페이징하는 GraphQL 규약.
- **token relay** = 들어온 JWT 를 downstream 호출에 그대로 전달하는 것.
- 기술 용어 옆에는 짧은 한국어 풀이를 함께 둘 것 (독자가 즉시 이해할 수 있게).
