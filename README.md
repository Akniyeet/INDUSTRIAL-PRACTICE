# Webizon

> Multi-tenant webinar SaaS built for scale.
> Engineered for **60,000 concurrent viewers per session** with pay-per-use billing in KZT.

---

## What is Webizon?

Webizon is a standalone, multi-tenant webinar and live-event platform. Customers sign up, create events, run live sessions with custom chat and conversion CTAs, and pay only for what they use.

**Target audience**: online schools, EdTech companies, individual coaches, and corporate training teams — anywhere from 10 to 60,000 participants per session.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend API | Java 21 + Spring Boot 3.2 (virtual threads enabled) |
| Real-time | Centrifugo v5 (dedicated WebSocket server) |
| Primary DB | PostgreSQL 16 + PgBouncer |
| Analytics DB | ClickHouse |
| Cache / pub-sub | Redis 7 (Cluster mode) |
| Message bus | Apache Kafka |
| Object storage | S3 (MinIO in dev) |
| Auth | Keycloak 24 |
| Frontend | Nuxt 4 + Vue 3 + TypeScript |
| Billing | CloudPayments Kazakhstan (KZT) |
| Deploy | Docker → Kubernetes |

📚 **Documentation:** [`docs/README.md`](./docs/README.md) — documentation index and navigation.  
📋 **What exists:** [`docs/WHAT_EXISTS.md`](./docs/WHAT_EXISTS.md) — **read this first** before making any changes.  
See [`CLAUDE.md`](./CLAUDE.md) for the full engineering constitution and rationale.

---

## 🚀 Жобаны іске қосу (How to Start)

> Толық нұсқаулық: **[docs/ONBOARDING.md](./docs/ONBOARDING.md)**

### 1. Барлық сервистерді іске қосу (Docker)

```bash
# ⚠️ МАҢЫЗДЫ: тек осы командамен қос — docker run -e қолданба!
cd E:/PROJECT/webizon
docker compose up -d
```

### 2. Барлығы іске қосылғанын тексеру

```bash
docker ps
```

13 контейнер **healthy** болуы керек. Backend 30–60 секунд жүктеледі.

### 3. URL-дер (нақты порттар)

| Сервис | URL |
|--------|-----|
| **Frontend** | http://localhost:3001 |
| **Admin панелі** | http://localhost:3001/admin |
| **Platform Super Admin** | http://localhost:3001/platform |
| Backend API | http://localhost:8081/actuator/health |
| Keycloak | http://localhost:8180 (admin/admin) |
| MinIO Console | http://localhost:9011 |
| Kafka UI | http://localhost:8090 |
| Grafana | http://localhost:3002 (admin/admin) |

### 4. Тест аккаунт

```
Email:    webizon365@gmail.com
Password: Test1234!
```

### 5. Тоқтату

```bash
docker compose down
```

### ⚠️ Жиі кездесетін қате

**Ескі контейнерлер жүгіріп тұрса** (порт 3000/8080 — біздікі 3001/8081):
```bash
# Қай compose stack-тен екенін тексер:
docker inspect webizon-frontend --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'

# Егер біздің directory емес болса — тоқтат:
cd <сол директория>
docker compose down

# Содан кейін біздікін қос:
cd E:/PROJECT/webizon
docker compose up -d
```

---

## Repository Layout

```
webizon/
├── backend/                Spring Boot 3 API server (Java 21)
│   ├── src/main/java/com/webizon/
│   │   ├── WebizonApplication.java
│   │   ├── config/         Spring config
│   │   ├── tenancy/        TenantContext, filters, RLS
│   │   ├── auth/           Keycloak integration, JWT
│   │   ├── events/         Event and Session module
│   │   ├── chat/           Chat subsystem (with Centrifugo client)
│   │   ├── cta/            CTA and timeline engine
│   │   ├── analytics/      Event publishing and dashboards
│   │   ├── billing/        Metering, invoicing, payments
│   │   └── realtime/       CentrifugoClient, token issuance
│   └── src/test/           JUnit 5 + Testcontainers
│
├── frontend/               Nuxt 4 (Vue 3 + TS)
│   ├── app/
│   │   ├── pages/          Routes
│   │   ├── components/     Reusable UI
│   │   ├── composables/    useAuth, useCentrifuge, ...
│   │   ├── stores/         Pinia
│   │   └── middleware/     auth, tenant
│   └── nuxt.config.ts
│
├── infra/                  Infrastructure as code
│   ├── docker/             Dockerfiles, docker-compose.yml
│   ├── centrifugo/         Centrifugo config
│   ├── keycloak/           Realm export JSON
│   └── k8s/                Kubernetes manifests (later)
│
├── docs/
│   ├── README.md           ← Документация индексі (навигация)
│   ├── WHAT_EXISTS.md      ← Не бар, қайта жасама тізімі (алдымен оқы!)
│   ├── ONBOARDING.md       ← Жобаны іске қосу нұсқаулығы
│   ├── ARCHITECTURE.md     ← Жүйе архитектурасы
│   ├── AUTH.md             ← Авторизация жүйесі
│   ├── API.md              ← Барлық API endpoint-тар
│   ├── CHANGELOG.md        ← Өзгерістер тарихы
│   ├── adr/                Architecture Decision Records
│   ├── specs/              Feature specs
│   └── runbooks/           Incident runbooks
│
├── CLAUDE.md               Engineering constitution
├── README.md               This file
├── Makefile                Dev commands
├── docker-compose.yml      Local dev orchestration
└── .env.example            Required environment variables
```

---

## Contribution Guide

Before contributing, read [`CLAUDE.md`](./CLAUDE.md) in full. It is the engineering constitution.

### Workflow
1. Create a feature branch from `main`
2. Write your change with tests
3. Open a PR with a clear description
4. CI must be green
5. At least one engineer reviews
6. Merge via "Rebase and merge"

### Commit style
Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `perf:`

### Architectural changes
Any change that affects more than one module or touches CLAUDE.md requires an ADR in `docs/adr/`.

---

## Scale Targets

These are commitments, not wishes.

| Metric | Target |
|--------|--------|
| Concurrent viewers per session | 60,000 |
| API p95 latency | < 200 ms |
| Chat delivery p95 (end-to-end) | < 250 ms |
| Uptime SLO | 99.9% |

See CLAUDE.md §3 for the full list.

---

## License

Proprietary. © Webizon. All rights reserved.
