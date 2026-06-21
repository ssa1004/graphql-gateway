<!--
PR 제목은 Conventional Commits 형식을 권장합니다 (예: feat(gateway): ...).
Squash and merge 시 이 제목이 commit 메시지가 됩니다. CONTRIBUTING.md 참고.
-->

## 변경 요약

<!-- 무엇을 / 왜 바꿨는지 한두 문장으로. -->

## 변경 유형

- [ ] `feat` — 새 기능
- [ ] `fix` — 버그 수정
- [ ] `refactor` — 동작 변경 없는 정리
- [ ] `test` — 테스트
- [ ] `docs` — 문서
- [ ] `ci` / `build` / `chore` — 빌드 · CI · 의존성

## 영향 범위

<!-- 영향받는 모듈/컴포넌트 (예: gateway-adapter-in, helm chart, Dockerfile). -->

## 체크리스트

- [ ] `./gradlew check` 가 로컬에서 통과한다
- [ ] Helm 차트를 건드렸다면 `helm lint` + `helm template | kubeconform` 통과
- [ ] Dockerfile 을 건드렸다면 `hadolint Dockerfile` 통과
- [ ] 워크플로를 건드렸다면 `actionlint` 통과, external action 은 SHA 핀 유지
- [ ] 관련 문서/ADR 를 갱신했다 (필요한 경우)
- [ ] Breaking change 라면 아래에 명시했다

## 관련 이슈

<!-- Closes #123 / Refs #456 -->

## 비고

<!-- 리뷰어가 알아야 할 배포 영향, 롤백 방법, 스크린샷 등. -->
