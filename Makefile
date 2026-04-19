.DEFAULT_GOAL := help

COMPOSE := docker compose

# ============================================================================
# HELP
# ============================================================================

.PHONY: help
help: ## Show this help message
	@awk 'BEGIN {FS = ":.*##"; printf "\nWebizon Makefile\n\nUsage:\n  make \033[36m<target>\033[0m\n\nTargets:\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

# ============================================================================
# DEVELOPMENT (DOCKER COMPOSE)
# ============================================================================

.PHONY: dev-up
dev-up: ## Start all services (infra + app) in detached mode
	@if [ ! -f .env ]; then cp .env.example .env && echo ".env created from .env.example"; fi
	$(COMPOSE) up -d
	@echo ""
	@echo "Webizon is starting..."
	@echo "  Frontend:        http://localhost:3000"
	@echo "  API:             http://localhost:8080"
	@echo "  Swagger:         http://localhost:8080/swagger-ui.html"
	@echo "  Keycloak:        http://localhost:8180 (admin/admin)"
	@echo "  Centrifugo:      http://localhost:8000"
	@echo "  MinIO console:   http://localhost:9001 (minioadmin/minioadmin)"
	@echo "  Kafka UI:        http://localhost:8090"
	@echo "  ClickHouse:      http://localhost:8123"

.PHONY: dev-down
dev-down: ## Stop all services (keep volumes)
	$(COMPOSE) down

.PHONY: dev-nuke
dev-nuke: ## Stop all services and delete all volumes (destructive!)
	$(COMPOSE) down -v
	@echo "All volumes deleted."

.PHONY: dev-logs
dev-logs: ## Tail logs of all services
	$(COMPOSE) logs -f

.PHONY: dev-logs-backend
dev-logs-backend: ## Tail backend logs only
	$(COMPOSE) logs -f backend

.PHONY: dev-logs-frontend
dev-logs-frontend: ## Tail frontend logs only
	$(COMPOSE) logs -f frontend

.PHONY: dev-restart-backend
dev-restart-backend: ## Rebuild and restart backend
	$(COMPOSE) up -d --build backend

.PHONY: dev-restart-frontend
dev-restart-frontend: ## Rebuild and restart frontend
	$(COMPOSE) up -d --build frontend

.PHONY: dev-ps
dev-ps: ## Show status of all services
	$(COMPOSE) ps

# ============================================================================
# BACKEND (local JDK, no docker)
# ============================================================================

.PHONY: backend-build
backend-build: ## Build the backend (mvn package)
	cd backend && ./mvnw clean package -DskipTests

.PHONY: backend-test
backend-test: ## Run backend unit tests
	cd backend && ./mvnw test

.PHONY: backend-test-it
backend-test-it: ## Run backend integration tests (testcontainers)
	cd backend && ./mvnw verify -Pintegration

.PHONY: backend-lint
backend-lint: ## Run backend linters (checkstyle, pmd, spotbugs)
	cd backend && ./mvnw verify -Pquality -DskipTests

.PHONY: backend-format
backend-format: ## Auto-format backend code (Spotless)
	cd backend && ./mvnw spotless:apply

# ============================================================================
# FRONTEND
# ============================================================================

.PHONY: frontend-install
frontend-install: ## Install frontend dependencies
	cd frontend && npm install

.PHONY: frontend-dev
frontend-dev: ## Run frontend dev server (Nuxt)
	cd frontend && npm run dev

.PHONY: frontend-build
frontend-build: ## Build frontend for production
	cd frontend && npm run build

.PHONY: frontend-test
frontend-test: ## Run frontend unit tests
	cd frontend && npm test

.PHONY: frontend-lint
frontend-lint: ## Run frontend linters
	cd frontend && npm run lint

.PHONY: frontend-typecheck
frontend-typecheck: ## Run frontend type check
	cd frontend && npm run typecheck

# ============================================================================
# ALL
# ============================================================================

.PHONY: test
test: backend-test frontend-test ## Run all unit tests

.PHONY: lint
lint: backend-lint frontend-lint ## Run all linters

.PHONY: build
build: backend-build frontend-build ## Build everything

# ============================================================================
# DATABASE
# ============================================================================

.PHONY: db-psql
db-psql: ## Connect to Postgres via psql
	$(COMPOSE) exec postgres psql -U webizon -d webizon

.PHONY: db-migrate
db-migrate: ## Run Flyway migrations manually
	cd backend && ./mvnw flyway:migrate

.PHONY: db-info
db-info: ## Show Flyway migration status
	cd backend && ./mvnw flyway:info

.PHONY: clickhouse-cli
clickhouse-cli: ## Connect to ClickHouse CLI
	$(COMPOSE) exec clickhouse clickhouse-client

# ============================================================================
# UTILITIES
# ============================================================================

.PHONY: clean
clean: ## Clean build artifacts
	cd backend && ./mvnw clean || true
	rm -rf frontend/.nuxt frontend/.output frontend/node_modules || true
