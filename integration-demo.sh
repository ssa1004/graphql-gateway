#!/usr/bin/env bash
# GraphQL gateway 시연 스크립트.
#
# docker-compose 로 띄운 게이트웨이(demo 프로필 — stub 어댑터)에 GraphQL 쿼리를 보내,
# 9 service 조회와 service 간 조인이 한 endpoint 로 동작하는 것을 보여준다.
#
#   docker compose up --build -d
#   ./integration-demo.sh
#
# 환경변수 GATEWAY_URL 로 대상 주소를 바꿀 수 있다 (기본 http://localhost:8080).

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
GRAPHQL="${GATEWAY_URL}/graphql"

# GraphQL 쿼리 한 건을 보내고 결과를 출력한다.
# 사용법: query "<설명>" '<graphql query>'
query() {
  local title="$1"
  local doc="$2"
  echo ""
  echo "── ${title} ──────────────────────────────────────────"
  # jq 가 있으면 보기 좋게, 없으면 raw 출력.
  local payload
  payload=$(printf '{"query":%s}' "$(printf '%s' "$doc" | jq -Rs .)")
  if command -v jq >/dev/null 2>&1; then
    curl -s -X POST "$GRAPHQL" -H 'Content-Type: application/json' -d "$payload" | jq .
  else
    curl -s -X POST "$GRAPHQL" -H 'Content-Type: application/json' -d "$payload"
    echo ""
  fi
}

echo "GraphQL gateway 시연 — ${GRAPHQL}"

# 게이트웨이가 뜰 때까지 대기.
echo -n "게이트웨이 health 대기"
for _ in $(seq 1 30); do
  if curl -s -f "${GATEWAY_URL}/actuator/health/readiness" >/dev/null 2>&1; then
    echo " — 준비됨"
    break
  fi
  echo -n "."
  sleep 2
done

# 1) auth-service — 사용자 단건 조회.
query "Query.user — auth-service" '
query {
  user(id: "u-1") { id email displayName roles status }
}'

# 2) commerce-ops + billing-platform + auth-service — 주문 조회 + 2개 조인.
#    Order.user (auth-service), Order.invoice (billing-platform) 가 DataLoader 로 합쳐진다.
query "Query.order + Order.user + Order.invoice — 3 service 조인" '
query {
  order(id: "o-1") {
    id
    status
    totalAmount
    user { id email }
    invoice { id status totalAmount }
  }
}'

# 3) bid-ask-marketplace + realtime-feed-service — 거래 조회 + Trade.feed 조인.
query "Query.trade + Trade.feed — marketplace + realtime-feed 조인" '
query {
  trade(id: "t-1") {
    id
    price
    status
    feed { id topic subscriberCount }
  }
}'

# 4) gpu-job-orchestrator + auth-service — GPU job 조회 + 제출자 조인.
query "Query.job + GpuJob.submitter — gpu-orchestrator + auth 조인" '
query {
  job(id: "job-1") {
    id
    name
    status
    gpuType
    submitter { id email }
  }
}'

# 5) auth-service + notification-hub — 사용자 + 알림 목록 조인.
query "Query.user + User.notifications — auth + notification-hub 조인" '
query {
  user(id: "u-2") {
    id
    notifications(first: 5) { id channel title read }
  }
}'

# 6) search-service — 통합 검색 (Relay cursor connection 페이징).
query "Query.search — search-service, cursor connection" '
query {
  search(q: "노트북", first: 3) {
    totalCount
    pageInfo { hasNextPage endCursor }
    edges { cursor node { id type title score } }
  }
}'

# 7) security-log-search — 테넌트 보안 알림 목록.
query "Query.securityAlerts — security-log-search" '
query {
  securityAlerts(tenantId: "tenant-1", first: 4) {
    totalCount
    edges { node { id severity ruleName message } }
  }
}'

# 8) 한 쿼리로 여러 service 동시 조회 — GraphQL 게이트웨이의 핵심.
query "여러 service 를 한 번에 — user + trade + invoice" '
query {
  user(id: "u-1") { displayName }
  trade(id: "t-2") { id price status }
  invoice(id: "inv-2") { id period totalAmount status }
}'

# 9) Mutation — 알림 읽음 처리.
query "Mutation.markNotificationRead — notification-hub" '
mutation {
  markNotificationRead(id: "ntf-1-2") { id title read }
}'

echo ""
echo "── 시연 완료 ──────────────────────────────────────────"
echo "GraphQL playground: ${GATEWAY_URL}/graphiql"
