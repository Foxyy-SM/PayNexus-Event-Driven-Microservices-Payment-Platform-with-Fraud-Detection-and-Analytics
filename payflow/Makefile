.PHONY: build test backend-test frontend-test integration up down logs smoke demo \
	images compose-config k8s-render k8s-validate terraform-fmt terraform-validate infra-validate env

COMPOSE := docker compose --env-file .env
SERVICES := api-gateway user-service payment-service wallet-service fraud-service notification-service transaction-service

env:
	@test -f .env || (echo "Missing .env; copy .env.example to .env and change the placeholders." >&2; exit 1)

build:
	mvn -B -DskipTests package
	cd web && npm ci && npm run build

backend-test:
	mvn -B verify

frontend-test:
	cd web && npm ci && npm run lint && npm test && npm run build && npm run test:e2e

test: backend-test frontend-test

images: env
	@for service in $(SERVICES); do \
		docker build -f docker/Dockerfile --build-arg "SERVICE=$$service" -t "paynexus/$$service:local" . || exit 1; \
	done
	docker build -f web/Dockerfile -t paynexus/web:local web

up: env
	$(COMPOSE) up --build -d

down: env
	$(COMPOSE) down --remove-orphans

logs: env
	$(COMPOSE) logs -f

smoke:
	curl --fail --retry 10 --retry-delay 2 http://localhost:8080/actuator/health
	curl --fail --retry 10 --retry-delay 2 http://localhost/
	curl --fail --retry 10 --retry-delay 2 http://localhost:8088/realms/paynexus/.well-known/openid-configuration

integration: env
	@trap '$(COMPOSE) down --volumes' EXIT; \
	$(COMPOSE) up --build --wait -d && \
	$(MAKE) smoke

demo: env
	VITE_DEMO_MODE=true $(COMPOSE) up --build -d web keycloak api-gateway
	@echo "Demo: http://localhost (Keycloak: http://localhost:8088)"

compose-config:
	docker compose --env-file .env.example config --quiet

k8s-render:
	kubectl kustomize k8s

k8s-validate:
	kubectl kustomize k8s | kubectl apply --dry-run=client --validate=false -f -

terraform-fmt:
	terraform -chdir=terraform fmt -check -recursive

terraform-validate:
	terraform -chdir=terraform init -backend=false
	terraform -chdir=terraform validate

infra-validate: compose-config k8s-validate terraform-fmt terraform-validate
