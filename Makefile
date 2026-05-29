# graphql-gateway — 자주 쓰는 명령 단일 진입점
#
#   make up        게이트웨이 기동 (demo 프로필 — stub 어댑터 + 인증 off)
#   make ps        컨테이너 상태
#   make logs      게이트웨이 로그 follow
#   make demo      GraphQL 쿼리 시연 (9 service 조회 + 조인)
#   make down      정지 (볼륨 유지)
#   make clean     정지 + 볼륨 삭제
#   make build     gradle 빌드 (테스트 제외)
#   make test      전체 테스트
#   make run       호스트에서 게이트웨이 실행 (demo 프로필)
#
# 게이트웨이는 stateless 다 — DB/Redis/Kafka 없이 게이트웨이 컨테이너 하나만 뜬다.
# demo 프로필에서 downstream 9 service 는 in-memory stub 어댑터가 대신하므로,
# downstream 을 따로 띄우지 않아도 GraphQL 쿼리 시연이 된다. 자세한 건 README "Quick Start".

COMPOSE := docker compose -f docker-compose.yml
GRADLE  := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help up ps logs demo down clean build test run

help: ## 이 도움말
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## 게이트웨이 기동 (demo 프로필 — stub 어댑터, downstream 불필요)
	$(COMPOSE) up --build -d
	@echo "→ GraphiQL http://localhost:8080/graphiql · GraphQL http://localhost:8080/graphql"

ps: ## 컨테이너 상태
	$(COMPOSE) ps

logs: ## 게이트웨이 로그 follow
	$(COMPOSE) logs -f --tail=100

demo: ## GraphQL 쿼리 시연 (게이트웨이가 떠 있어야 함)
	./integration-demo.sh

down: ## 정지 (볼륨 유지)
	$(COMPOSE) down

clean: ## 정지 + 볼륨 삭제 (다음 기동 시 깨끗한 상태)
	$(COMPOSE) down -v

build: ## gradle 빌드 (테스트 제외)
	$(GRADLE) build -x test

test: ## 전체 테스트
	$(GRADLE) check

run: ## 호스트에서 게이트웨이 실행 (demo 프로필, :8080)
	$(GRADLE) :gateway-bootstrap:bootRun --args='--spring.profiles.active=demo'
