SHELL := /usr/bin/env bash
COMPOSE := docker compose --env-file .env
CRM_COMPOSE := docker compose --env-file .env -f compose.yaml -f compose.crm.yaml

.PHONY: help bootstrap doctor config build up up-devtools up-platform up-observability up-full down restart ps logs smoke test test-web test-backend test-mobile clean-data crm-config crm-build crm-up crm-up-scale crm-up-search crm-up-observability crm-up-full crm-down crm-ps crm-logs crm-readiness crm-db-seed crm-db-validate crm-load crm-backup crm-restore-verify

help:
	@printf '%s\n' \
	  'SNAD runtime commands' \
	  '' \
	  'General:' \
	  '  make bootstrap             Create .env and generate local secrets' \
	  '  make doctor                Validate host prerequisites and Compose' \
	  '  make test                  Run backend, web, and mobile quality checks' \
	  '' \
	  'CRM runtime:' \
	  '  make crm-config            Validate merged CRM Compose configuration' \
	  '  make crm-build             Build CRM backend and web images' \
	  '  make crm-up                Start PostgreSQL 18, backend, and web' \
	  '  make crm-up-scale          Start core plus Valkey' \
	  '  make crm-up-search         Start core plus OpenSearch and Dashboards' \
	  '  make crm-up-observability  Start core plus Prometheus and Grafana' \
	  '  make crm-up-full           Start all CRM local profiles' \
	  '  make crm-db-seed           Generate scale dataset' \
	  '  make crm-db-validate       Validate tenant isolation, indexes, queues, vectors' \
	  '  make crm-load              Run k6 CRM readiness load test' \
	  '  make crm-backup            Create timestamped PostgreSQL backup' \
	  '  make crm-restore-verify    Restore latest backup into temporary database' \
	  '  make crm-readiness         Run complete runtime readiness checks' \
	  '  make crm-down              Stop CRM runtime' \
	  '  make clean-data            Delete local Docker volumes after confirmation'

bootstrap:
	@bash scripts/dev/bootstrap.sh

doctor:
	@bash scripts/dev/doctor.sh

config:
	@$(COMPOSE) config

build:
	@$(COMPOSE) build --pull

up:
	@$(COMPOSE) up -d --build postgres backend web
	@$(MAKE) smoke

up-devtools:
	@$(COMPOSE) --profile devtools up -d --build
	@$(MAKE) smoke

up-platform:
	@$(COMPOSE) --profile platform up -d --build
	@$(MAKE) smoke

up-observability:
	@$(COMPOSE) --profile observability up -d --build
	@$(MAKE) smoke

up-full:
	@$(COMPOSE) --profile devtools --profile platform --profile observability up -d --build
	@$(MAKE) smoke

down:
	@$(COMPOSE) down --remove-orphans

restart:
	@$(MAKE) down
	@$(MAKE) up

ps:
	@$(COMPOSE) ps

logs:
	@$(COMPOSE) logs --follow --tail=200

smoke:
	@bash scripts/dev/smoke.sh

test: test-backend test-web test-mobile

test-backend:
	@cd apps/sanad-platform && mvn -B -ntp test

test-web:
	@cd apps/web && npm ci && npm run lint && npm test && npm run build

test-mobile:
	@cd apps/mobile && npm ci && npm run verify:env && npx expo-doctor@1.20.0 && npm run typecheck && npm run lint && npm test -- --passWithNoTests && npm run export:web

crm-config:
	@$(CRM_COMPOSE) config --quiet
	@echo 'CRM Compose configuration is valid.'

crm-build:
	@$(CRM_COMPOSE) build --pull backend web

crm-up:
	@$(CRM_COMPOSE) up -d --build postgres backend web
	@$(MAKE) crm-readiness

crm-up-scale:
	@$(CRM_COMPOSE) --profile crm-scale up -d --build postgres valkey backend web
	@$(MAKE) crm-readiness

crm-up-search:
	@$(CRM_COMPOSE) --profile crm-search up -d --build postgres opensearch opensearch-dashboards crm-search-init backend web
	@$(MAKE) crm-readiness

crm-up-observability:
	@$(CRM_COMPOSE) --profile observability up -d --build postgres backend web prometheus grafana
	@$(MAKE) crm-readiness

crm-up-full:
	@$(CRM_COMPOSE) --profile devtools --profile crm-scale --profile crm-search --profile observability up -d --build
	@$(MAKE) crm-readiness

crm-down:
	@$(CRM_COMPOSE) --profile devtools --profile crm-scale --profile crm-search --profile observability --profile crm-load down --remove-orphans

crm-ps:
	@$(CRM_COMPOSE) --profile devtools --profile crm-scale --profile crm-search --profile observability ps

crm-logs:
	@$(CRM_COMPOSE) --profile devtools --profile crm-scale --profile crm-search --profile observability logs --follow --tail=200

crm-readiness:
	@bash scripts/crm/readiness.sh

crm-db-seed:
	@bash scripts/crm/seed-scale.sh

crm-db-validate:
	@bash scripts/crm/validate-scale.sh

crm-load:
	@mkdir -p artifacts/performance
	@$(CRM_COMPOSE) --profile crm-load run --rm crm-load-test

crm-backup:
	@bash scripts/crm/backup.sh

crm-restore-verify:
	@bash scripts/crm/restore-verify.sh

clean-data:
	@read -r -p 'Delete all SNAD local Docker volumes? Type DELETE: ' answer; \
	  if [[ "$$answer" != 'DELETE' ]]; then echo 'Cancelled.'; exit 1; fi
	@$(CRM_COMPOSE) --profile devtools --profile crm-scale --profile crm-search --profile observability down --volumes --remove-orphans
