SHELL := /usr/bin/env bash
COMPOSE := docker compose --env-file .env

.PHONY: help bootstrap doctor config build up up-devtools up-platform up-observability up-full down restart ps logs smoke test test-web test-backend test-mobile clean-data

help:
	@printf '%s\n' \
	  'SNAD runtime commands' \
	  '' \
	  '  make bootstrap          Create .env and generate local secrets' \
	  '  make doctor             Validate host prerequisites and Compose' \
	  '  make config             Render the effective Compose configuration' \
	  '  make build              Build backend and web images' \
	  '  make up                 Start PostgreSQL, backend, and web' \
	  '  make up-devtools        Start core plus local email capture' \
	  '  make up-platform        Start core plus Redis platform services' \
	  '  make up-observability   Start core plus Prometheus and Grafana' \
	  '  make up-full            Start all available local profiles' \
	  '  make smoke              Validate backend, OpenAPI, and web endpoints' \
	  '  make test               Run backend, web, and mobile quality checks' \
	  '  make down               Stop the local environment' \
	  '  make clean-data         Delete local Docker volumes after confirmation'

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

clean-data:
	@read -r -p 'Delete all SNAD local Docker volumes? Type DELETE: ' answer; \
	  if [[ "$$answer" != 'DELETE' ]]; then echo 'Cancelled.'; exit 1; fi
	@$(COMPOSE) down --volumes --remove-orphans
