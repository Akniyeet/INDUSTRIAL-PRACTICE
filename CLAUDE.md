# WEBIZON — Engineering Constitution

> This document is the single source of truth for engineering decisions on Webizon.
> It is written as an instruction manual for engineers and AI assistants working on this codebase.
> Every rule here is intentional. Before violating any rule, you must write an ADR explaining why.

---

## 1. Product Identity

**Webizon** is a multi-tenant SaaS webinar / live-event platform.

It is **not**:
- A Zoom clone
- A YouTube wrapper
- A single-tenant internal tool

It **is**:
- A standalone commercial product
- A pay-per-use SaaS, billed in **Kazakhstani Tenge (KZT)**
- Designed to handle **50,000–60,000 concurrent viewers per session** without degradation
- Designed to host **multiple parallel live events** across many independent customers (tenants)

### Target customers
- **B2B**: online schools, corporate training departments, EdTech companies
- **B2C / SMB**: individual coaches, teachers, content creators, small studios

### Core value proposition
> "Run a professional webinar for thousands of participants without worrying about infrastructure,
> pay only for what you use, go from zero to live in 5 minutes."

---

## 2. Business Model (Pay-Per-Use)

Billing is **consumption-based**, denominated in **KZT**.

### Pricing units (configurable per tenant plan)
| Unit | Description | Default rate (KZT) |
|------|-------------|---------------------|
| Webinar seat | Each unique participant who joined a live/auto session | 15 ₸ / seat |
| Cloud storage | Per GB per month over free quota | 200 ₸ / GB / month |
| Payment processing | Percentage of turnover when tenant uses Webizon checkout | 3% |
| Auto-webinar replay view | Each unique viewer of an auto session | 10 ₸ / seat |

### Free trial
- 14 days
- Up to 10 participants per session
- All features unlocked
- No credit card required to start

### Billing principles
- **Usage is metered**, not declared
- Usage events flow from the live path → Kafka → billing ledger
- Billing ledger is **append-only and immutable**
- Invoices are generated monthly from the ledger
- Overages are charged automatically via saved payment method
- A tenant with an unpaid invoice older than 14 days is **soft-suspended** (existing events stay, new events blocked)

### Non-negotiable billing rules
1. **Never double-charge**. Billing events must be idempotent (deduplicated by `(tenant_id, session_id, profile_id, event_type)`).
2. **Never lose a billing event**. Every billable action is persisted before the user sees success.
3. **Never bill for infrastructure failures**. If a session crashes, affected seats are not billed.
4. **Ledger is immutable**. Corrections are new entries, never edits.

---

## 3. Scale Targets (Hard Requirements)

These are not aspirations. They are engineering constraints.

| Metric | Target |
|--------|--------|
| Concurrent viewers per session | **60,000** |
| Concurrent sessions per tenant | 50 |
| Concurrent sessions across platform | 500 |
| Chat messages per second per session | 1,000 |
| WebSocket connection establishment rate | 5,000 / second (burst) |
| API p95 latency (REST) | < 200 ms |
| Chat message delivery p95 | < 250 ms end-to-end |
| Analytics event ingestion rate | 100,000 / second |
| Session recording playback start | < 2 seconds |
| Uptime SLO (live sessions) | 99.9% monthly |

**If a design decision cannot meet these targets, it is the wrong decision. Rework it.**

---

## 4. Technology Stack (Locked)

These choices are final. Changing any of them requires an ADR approved before work begins.

### Backend API (Business logic)
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot **3.5.3** with **virtual threads enabled** (Project Loom). Stay on the latest 3.x patch — do not jump to 4.x until the whole module ecosystem (Spring Cloud, Spring Security, springdoc) is on 7.x compatible releases.
- **Build**: Maven (not Gradle — simpler for CI, better IDE tooling in our team)
- **HTTP**: Spring MVC (not WebFlux — virtual threads give us non-blocking benefits with blocking-style code)
- **Validation**: Jakarta Validation (`@Valid`)
- **API docs**: springdoc-openapi (OpenAPI 3.1)

**Why Java/Spring?** Team expertise, mature ecosystem for billing/ORM/Stripe, Spring Boot 3 + Loom solves the concurrency story, the WebSocket bottleneck is offloaded to Centrifugo so the API server only handles REST CRUD and event publishing.

### Real-time layer (WebSockets)
- **Centrifugo v5** — standalone, purpose-built real-time server written in Go
- Clients connect **directly** to Centrifugo, not to the Java API
- Java API **publishes** to Centrifugo via HTTP API (server-to-server)
- Centrifugo handles: fan-out, presence, history, connection management
- Scaling: Centrifugo cluster with **Redis engine** for pub/sub between nodes

**Why Centrifugo?** One node handles 1M+ concurrent connections. Purpose-built for our exact problem. Removes fan-out from our application code entirely.

### Persistence
- **Primary DB**: PostgreSQL 16
- **Connection pooler**: PgBouncer (transaction mode)
- **Migrations**: Flyway
- **ORM**: Spring Data JPA + Hibernate (with `@BatchSize`, query plan caching, read-only transactions where applicable)
- **Multi-tenancy**: Shared schema, `tenant_id` discriminator column on every domain table, enforced via Hibernate filter + repository base class
- **Row-level security**: PostgreSQL RLS policies as defense-in-depth against tenant leakage

### Analytics Database
- **ClickHouse** — columnar, purpose-built for high-volume analytics
- Stores: behavioral events, retention data, viewer timelines, CTA interactions
- Ingested via Kafka Connect ClickHouse sink (or Kafka consumer writing in batches)
- Queried by the Analytics API for dashboards
- **Never queried in the hot path** (no synchronous user-facing queries)

### Caching & Ephemeral State
- **Redis 7** — start as **single master + one read replica** (`redis-sentinel` for failover). Used for:
  - Rate limiting (flood protection, slow mode token buckets)
  - Session state cache
  - Centrifugo pub/sub engine (its own dedicated Redis instance, separate from app cache)
  - Distributed locks (Redlock where needed)
  - Ephemeral presence counters
- **Cluster mode is NOT the default.** Sharding adds real cost: cross-slot `MULTI` is impossible, Lua scripts can't span slots, the Lettuce client needs cluster awareness, and slot rebalancing during a hot session is operational pain. Stay on a single primary until **measured** load (not guesswork) shows memory > 70 % on an 8 GB instance OR CPU > 60 % at peak. Then introduce Cluster — and only for the cache that hits the wall, not all of them at once.
- **Centrifugo's Redis is always separate** from the app cache, regardless of Cluster decision. Mixing pub/sub traffic with key-value workload starves both.

### Message Broker
- **Apache Kafka** (3 brokers min in production). Kafka is **not** a default transport — it earns its place only when one of these is true: (a) the consumer is in a different service, (b) durability + replay is required, (c) write throughput must be decoupled from read consumers, (d) fan-out to N independent subscribers is needed.

**Use case matrix — what Kafka is for, and what it is NOT for:**

| Pipeline | Kafka? | Why |
|---|---|---|
| Analytics events → ClickHouse | ✅ yes | Decouple write firehose from CH batches; CH consumer runs separately |
| Lead signals → CRM | ✅ yes | CRM is a different service; needs replay for outage recovery |
| Billing events → invoice / dunning | ✅ yes | Durability required for financial correctness; multiple consumers |
| Integration events (tenant provisioned, session started) → external webhooks | ✅ yes | Multiple subscribers, replay on consumer failure |
| Dead-letter queue for the above | ✅ yes | Compaction-free retention for forensics |
| **Chat message persistence → Postgres** | ❌ NO | App writes directly to Postgres (batched). Kafka adds latency, ordering complexity, and a moving part for zero benefit. If analytics need chat events, emit a separate Kafka record AFTER the Postgres write succeeds |
| Moderation actions → audit log | ❌ NO | Direct Postgres insert. Audit must be transactionally consistent with the action |
| Email notifications | ❌ NO | Use a transactional outbox table + worker. Kafka is overkill |
| Cache invalidation | ❌ NO | Use Redis pub/sub or in-process events |
| Per-request RPC between services | ❌ NO | Use OpenFeign / HTTP. Kafka is async-only |

**The rule**: if you find yourself reaching for Kafka because "it's an event", stop and ask which of (a)–(d) actually applies. If none, pick a simpler tool.

### Object Storage
- **S3-compatible** (AWS S3 in production, MinIO in dev/local)
- Stores: event cover images, uploaded CTA files, session recordings, exported reports
- Direct uploads via **pre-signed URLs** (never stream large files through the API)

### Search (later phase)
- **OpenSearch** for event catalog search and chat log search (not MVP)

### Frontend
- **Framework**: **Nuxt 4.4** (pure Nuxt 4, not forward-compat mode). All app code lives under `frontend/app/` — the new default srcDir convention. Root-level dirs kept outside `app/`: `shared/`, `public/`, `server/` (Nitro), plus build config (`nuxt.config.ts`, `tailwind.config.ts`, `eslint.config.mjs`, `tsconfig.json`, `Dockerfile`). Inside templates the `~` alias resolves to `app/`, and the top-level `shared/` tree is reached via the generated `#shared` alias (not `~/shared`).
- **Language**: Vue 3 + TypeScript (strict mode)
- **State**: Pinia
- **Styling**: Tailwind CSS + semantic token extensions (`brand.*`, `success.*`, `warning.*`, `danger.*`) in `tailwind.config.ts`
- **UI kit**: **Hand-rolled** component library in `components/ui/*` (Button, Input, Textarea, Select, Card, Badge, Spinner, Skeleton, Modal, Empty, Countdown, Toast) built on **Headless UI** (accessibility primitives) + **lucide-vue-next** (icons). `@nuxt/ui` was evaluated and dropped — a hand-rolled kit gives us exact control over chrome, animation budgets, and Kazakh/Russian typography without fighting a third-party theme.
- **Real-time client**: `centrifuge-js` (official Centrifugo client)
- **HTTP client**: Custom **`ApiClient`** class in `shared/api/client.ts` that wraps `$fetch` with a narrow signature. Raw `$fetch` is NOT used directly — Nuxt's global `$fetch` is typed against Nitro's compile-time route map and trying to resolve backend paths through it collapses TS inference. The wrapper also normalises Spring `ProblemDetail` responses into a stable `ApiError` shape.
- **Forms**: Hand-written reactive validation (MVP). `zod` and `vee-validate` are in the dependency list for later phases but not wired yet — current forms use inline `validate()` functions on top of `reactive()` state.
- **i18n**: **Deferred**. All admin UI strings are currently inline Kazakh literals. `@nuxtjs/i18n` wiring will land when we need RU/EN parity (not MVP).
- **Testing**: Vitest (unit) + Playwright (e2e)

### Billing / Payments
- **Kazakhstan**: CloudPayments Kazakhstan or PayBox.money (both support KZT and are PCI-compliant)
- **International**: Stripe (for customers outside KZ, later phase)
- **Tax**: KZ VAT (12%) — invoice generation must include VAT breakdown
- **Invoices**: PDF generated server-side (OpenHTMLToPDF or similar)

### Authentication & Identity
- **Provider**: Keycloak 24 (self-hosted)
- **Why Keycloak?** Multi-realm (one realm per tenant possible), OIDC, social login, MFA, account recovery, admin UI
- **Tokens**: JWT (RS256), short-lived access tokens (15 min), rotating refresh tokens (30 days)
- **Tenant isolation in tokens**: JWT includes `tenant_id` and `role` claims. The API validates both on every request.

### Observability (non-negotiable from day one)
- **Metrics**: Prometheus + Grafana
- **Tracing**: OpenTelemetry → Jaeger (or Grafana Tempo)
- **Logs**: Structured JSON logs → Loki (or ELK)
- **Alerting**: Alertmanager with PagerDuty-style escalation
- **Health checks**: Spring Actuator, liveness + readiness endpoints
- **Synthetic monitoring**: Uptime checks on public endpoints every 60s

### Deployment
- **Containers**: Docker (multi-stage builds, non-root user, distroless base where possible)
- **Orchestration**: Kubernetes (production) + Docker Compose (local dev)
- **CI/CD**: GitLab CI → image registry → ArgoCD (or plain `kubectl apply` in Helm chart form)
- **Environments**: `local` → `staging` → `production`
- **Secrets**: Kubernetes Secrets backed by External Secrets Operator → HashiCorp Vault (or AWS/GCP Secret Manager)

---

## 5. Architecture Overview

```
                    ┌──────────────────┐
                    │    Browsers      │
                    │  (Nuxt 4 SSR)    │
                    └────────┬─────────┘
                             │ HTTPS + WSS
        ┌────────────────────┼─────────────────────┐
        │                    │                     │
        ▼                    ▼                     ▼
  ┌──────────┐        ┌──────────────┐      ┌─────────────┐
  │  Nginx   │        │  Centrifugo  │      │   Keycloak  │
  │  (ingress)       │   (cluster)  │      │   (auth)    │
  └────┬─────┘        └──────┬───────┘      └─────────────┘
       │                     │
       ▼                     │ pub/sub
  ┌──────────────┐           ▼
  │ Webizon API  │──publish──┘
  │ (Spring 3)   │
  │ virtual      │
  │ threads      │
  └──┬───┬───┬───┘
     │   │   │
     │   │   └──────────────┐
     │   │                  │
     ▼   ▼                  ▼
  ┌────┐ ┌─────┐      ┌──────────┐
  │ PG │ │Redis│      │  Kafka   │
  │+Bouncer     │      │ (3 nodes)│
  └────┘ └─────┘      └────┬─────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ ClickHouse  │
                    │ (analytics) │
                    └─────────────┘
```

### Request paths

**Cold path (REST)**: Browser → Nginx → Webizon API → PostgreSQL / Kafka / S3
**Hot path (real-time)**: Browser → Centrifugo (WSS) ← publish ← Webizon API
**Chat write path**: Browser → Webizon API (validate, policy check) → Kafka → ChatPersistenceConsumer → PostgreSQL; simultaneously API publishes to Centrifugo for fan-out
**Chat read (history)**: Browser → Webizon API → PostgreSQL (with Redis cache)
**Analytics write path**: Any service → Kafka → ClickHouse consumer → ClickHouse
**Analytics read path**: Browser → Webizon API → ClickHouse (aggregation queries)

### Why this architecture scales to 60K/session

1. **The API server never holds WebSocket connections.** Centrifugo does. One API pod can drive dozens of Centrifugo channels without holding any socket state.
2. **Fan-out is O(1) from the API's perspective.** Publishing a chat message is a single HTTP POST to Centrifugo. Centrifugo delivers to all 60K subscribers.
3. **Chat persistence is async.** The API publishes to Kafka and returns 200 OK. A consumer batch-writes to PostgreSQL. DB is never the bottleneck.
4. **Analytics never hit PostgreSQL.** ClickHouse absorbs the firehose.
5. **Redis is Redis.** Flood protection and slow mode are O(1) Lua scripts.
6. **Horizontal scaling**: API is stateless. Add pods until REST latency targets are met. Centrifugo is clustered via Redis engine. Both scale independently.

---

## 6. Multi-Tenancy (CRITICAL)

Webizon is **multi-tenant from line one**. This is not a feature to add later — it's a foundational constraint that affects every query, every cache key, every log line, every metric label.

### Isolation model: **Shared database, shared schema, `tenant_id` discriminator**
- Every domain table has `tenant_id UUID NOT NULL` as the first column
- Every index that includes another column starts with `tenant_id`
- Every query is filtered by `tenant_id`, enforced at the repository layer
- PostgreSQL **Row-Level Security (RLS)** policies enforce this at the database level (defense in depth)

### Implementation rules (non-negotiable)
1. **`TenantContext`** — a `ThreadLocal<UUID>` set by a Spring `Filter` on every authenticated request, read from the `tenant_id` JWT claim. Never set manually outside the filter.
2. **`TenantAwareRepository<T, ID>`** — base repository class that automatically adds `WHERE tenant_id = :currentTenant` to every query. Never write raw queries that bypass it.
3. **Hibernate Filter** `@FilterDef(name="tenantFilter")` enabled globally on every entity with `tenant_id`.
4. **Kafka messages** include `tenant_id` in headers and payload. Consumers set `TenantContext` before processing.
5. **Cache keys** always include tenant_id: `tenant:{tenantId}:session:{sessionId}:*`
6. **Log MDC** (mapped diagnostic context) includes `tenant_id` on every log line for audit and debugging.
7. **Metrics** tagged with `tenant_id` (bounded set — aggregate beyond top-N tenants).
8. **Centrifugo channel names** prefixed with tenant: `tenant.{tenantId}.session.{sessionId}.chat`
9. **S3 object keys** prefixed with tenant: `tenants/{tenantId}/sessions/{sessionId}/recordings/...`

### Tenant lifecycle
- **Signup** → creates Tenant, Owner User, default Plan (trial), provisions a Keycloak group, seeds default CTA templates
- **Suspend** (unpaid) → block new sessions, preserve data, show banner in admin
- **Delete** (GDPR) → soft delete for 30 days, then hard delete including S3 objects and Kafka-archived data
- **Export** (GDPR portability) → generate ZIP of tenant's data on demand

### Anti-patterns (never do this)
- ❌ Storing "shared" data without a tenant_id
- ❌ Admin tools that query across tenants without explicit `@AllowCrossTenant` audit annotation
- ❌ Caching values under tenant-agnostic keys
- ❌ Metrics that leak cardinality (e.g., one series per tenant_id forever)
- ❌ Running a migration that doesn't account for `tenant_id`

---

## 7. Domain Model (High Level)

### Core entities (top of mind)
- **Tenant** — the paying customer (organization or individual)
- **User** — a member of a Tenant with a role (Owner, Admin, Moderator, Presenter)
- **Plan** — pricing tier a Tenant is on (Trial, Flex)
- **UsageMeter** — accumulating counters per tenant per billing cycle
- **Invoice** — generated monthly from UsageMeter + BillingLedger
- **PaymentMethod** — saved card/bank reference via billing provider
- **Event** — reusable content definition (title, cover, speaker, slug, landing config)
- **Session** — a concrete occurrence of an Event (LIVE or AUTO, scheduled at a specific time)
- **Room** — runtime view of a Session (state, active CTA, chat settings)
- **Participant** — a joiner of a Session (profileId + tenant + session)
- **ChatMessage** — a single chat entry (with moderation flags)
- **TimelineAction** — offset-based action to execute during playback
- **CTA** — conversion/material element (button, file, link)
- **ModerationAction** — audit row for any moderator action
- **AnalyticsEvent** — behavioral event (writes to Kafka → ClickHouse)
- **BillingEvent** — billable action (writes to Kafka → PostgreSQL ledger)
- **Recording** — stored video asset tied to a Session

### Hard rules
- An **Event** is reusable content. A **Session** is a one-time run. **Never** reactivate a finished Session.
- A **LIVE Session** can have many **AUTO Sessions** derived from it (replay slots). Each AUTO is a new Session row.
- A **Participant** belongs to exactly one Session. Multiple Sessions for the same user = multiple Participant rows.
- A **ChatMessage** belongs to a Session. Historical chat replay copies the original message IDs by reference, not by duplication.
- **Analytics data** is partitioned by `(tenant_id, session_id, created_at)` in ClickHouse.

---

## 8. Real-Time Layer (Centrifugo)

### Channel naming convention
All Centrifugo channels are prefixed with `tenant.{tenantId}`. This ensures tenant isolation at the message broker level.

| Purpose | Channel |
|---------|---------|
| Chat messages | `tenant.{tid}.session.{sid}.chat` |
| System events | `tenant.{tid}.session.{sid}.system` |
| Timeline / CTA sync | `tenant.{tid}.session.{sid}.timeline` |
| Presence updates | `tenant.{tid}.session.{sid}.presence` |
| Private user messages | `$tenant.{tid}.user.{uid}` (Centrifugo private prefix `$`) |

### Authentication
- Client requests a **short-lived Centrifugo connection token** from the Webizon API (`POST /api/v1/rt/token`)
- The API validates the user's JWT, builds a Centrifugo JWT (HMAC with shared secret), and returns it
- Client connects to Centrifugo with that token
- Centrifugo validates the token and enforces channel-level permissions via its config

### Publishing from the API
- Spring uses a thin **CentrifugoClient** component that wraps the Centrifugo HTTP API
- All publishes are **fire-and-forget with retry**: if Centrifugo is temporarily unreachable, we enqueue to a Kafka DLQ and retry asynchronously
- Publishes are **idempotent** — each publish includes a `message_id` that Centrifugo uses for deduplication

### History & replay
- Centrifugo's built-in channel history is used for short-term (last N messages) chat scroll-back on reconnect
- Long-term history lives in PostgreSQL (ChatMessage table), fetched via REST API with pagination

### Reconnect semantics
- Clients reconnect automatically with exponential backoff (`centrifuge-js` handles this)
- On reconnect, the client requests a new token (in case the old one expired) and resubscribes to the same channels
- Missed messages are recovered from Centrifugo history (< 5 min) or REST API (older)

---

## 9. Chat Subsystem (Hot Path)

The chat is the most load-sensitive component. Every design decision here prioritizes throughput and deterministic latency.

### Write path
1. Client sends `POST /api/v1/sessions/{id}/chat` with the message payload
2. API authenticates, loads TenantContext, runs policy chain:
   - User is not banned (tenant-level OR session-level)
   - User is not muted (`chat_user_status.mute_until > now()`)
   - Chat is enabled for this session
   - Slow mode check (Redis Lua token bucket per `(session_id, user_id)`)
   - Flood protection check (Redis Lua sliding window)
   - Forbidden word filter (per-tenant rule set)
   - Link policy (per-tenant deny list)
3. **Fan-out first**: API publishes to Centrifugo channel `tenant.{tid}.session.{sid}.chat` — clients see the message in < 100 ms
4. **Persist second**: the message is appended to the in-process `ChatWriteBuffer` (per session_id), which flushes on either of two triggers:
   - 100 messages reached for any single session, OR
   - 50 ms wall clock since the oldest message in the buffer
   The flush is a single multi-row `INSERT INTO chat_messages VALUES (...), (...), (...)` against the partitioned table — see "Storage pattern" below.
5. API returns `202 Accepted` with the server-assigned `messageId` from the buffer (the buffer hands out monotonic ids before the flush so the client can correlate).

**Why fan-out before persistence?** Because users need to see messages instantly and the Postgres write — even batched — is the slowest step. Persistence failures fall through to a DLQ table (`chat_write_failures`) and an alert. We accept a ~50 ms eventual-durability window in exchange for sub-100 ms perceived delivery.

**Why NOT route through Kafka here?** Because chat persistence needs to be transactionally close to the moderation/audit write path, and Kafka adds: an extra hop, ordering risk across partitions, a consumer to operate, a DLQ to drain. None of (a) cross-service decoupling, (b) replay, (c) write/read decoupling, or (d) fan-out apply — Centrifugo already does fan-out. See the §4 Kafka use case matrix.

### Storage pattern (CRITICAL — do not deviate)

Hot session math: 10 000 viewers × ~5 messages/min/active-user × ~10 % active = ~5 000 messages/min per session, with bursts to ~500 messages/sec at peak engagement. Across 60 concurrent sessions in production peak: ~30 000 messages/sec system-wide. A naive `INSERT` per message is the bottleneck — both for latency and for index/lock contention.

**Five rules govern the chat write path:**

1. **Hash-partition `chat_messages` by `session_id`** (32 partitions). Each session's writes hit a single child table, so two hot sessions never contend on the same B-tree page. Partitions can be `DETACH`-ed cheaply for retention (see rule 5).
2. **Composite primary key `(session_id, id)`** — the `id` is per-session monotonic, generated by the application's `ChatWriteBuffer`. This avoids a global `BIGSERIAL` hot spot and lets the partition pruner skip 31/32 children on any query.
3. **Append-only — never UPDATE the row.** Moderation deletions, edits, and hides are recorded as separate rows in `chat_message_moderation` keyed by `(session_id, message_id, action_type)`. Read queries `LEFT JOIN moderation_state(session_id, message_id)` to compute the visible state. Result: **zero dead tuples** on `chat_messages`, no autovacuum pressure on the hot table.
4. **Batched multi-row INSERT** via the in-process `ChatWriteBuffer` (50 ms / 100 message flush window — see the write path above). Use Postgres `COPY` with `pgcopy` only if profiling shows extended-query INSERT is the bottleneck — the simpler path comes first.
5. **Retention by partition swap.** A nightly job marks any partition whose newest row is > 90 days old, exports it to S3 as compressed JSONL, then `DETACH PARTITION` + `DROP TABLE`. Constant time, no `DELETE` storms.

**What the schema looks like** (illustrative — actual DDL lives in the Flyway migration):

```sql
CREATE TABLE chat_messages (
    id          BIGINT       NOT NULL,                  -- per-session monotonic
    session_id  UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    text        TEXT         NOT NULL,
    reply_to_id BIGINT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, id)
) PARTITION BY HASH (session_id);

-- 32 children: chat_messages_p00 ... chat_messages_p31

CREATE TABLE chat_message_moderation (
    session_id   UUID         NOT NULL,
    message_id   BIGINT       NOT NULL,
    action_type  TEXT         NOT NULL  -- 'DELETE' | 'HIDE' | 'PIN'
        CHECK (action_type IN ('DELETE','HIDE','PIN')),
    actor_id     UUID         NOT NULL,
    reason       TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, message_id, action_type)
);
```

**Indexes on `chat_messages`** are intentionally minimal: only the PK. Scrollback queries are always `WHERE session_id = ? AND id < ? ORDER BY id DESC LIMIT 50`, which the PK already serves perfectly. Adding `(tenant_id, created_at)` would be tempting for cross-session reports, but those run against ClickHouse, not Postgres.

### Live vs. replay reads
- **Live tail**: clients read from Centrifugo channel history (~last 100 messages, in-memory). Postgres is **never** queried for the live tail.
- **Scrollback**: `GET /api/v1/sessions/{id}/chat?before={messageId}` runs the PK query above. The `LEFT JOIN chat_message_moderation` filters deleted/hidden messages.
- **Auto-session replay**: the source LIVE session's messages are loaded once at session start and pushed through Centrifugo at their original offsets — no per-message DB read during playback.

### Rate limiting
- **Slow mode**: configurable per session (0, 5, 10, 20, 30, 60 seconds). Enforced via Redis Lua `INCR` + `EXPIRE`.
- **Flood protection**: hard limit of 5 messages per 10 seconds regardless of slow mode. Same Lua pattern.
- **Moderators bypass** slow mode but not flood protection.

### Chat history
- Last N messages (default 50) are fetched on room entry from Centrifugo history
- Scrollback calls `GET /api/v1/sessions/{id}/chat?before={cursor}` and hits PostgreSQL directly
- The chat table is indexed on `(tenant_id, session_id, created_at DESC)` for this exact query

### Moderation actions
- Deletion: marks `is_deleted=true`, publishes `CHAT_MESSAGE_DELETED` to Centrifugo; clients hide it
- Mute: writes to `chat_user_status`, publishes private event to the user's channel
- All moderation actions are append-only rows in `moderation_actions` for audit

### Historical chat replay (auto sessions)
- When an AUTO session starts, the API loads the source LIVE session's messages with offsets
- A scheduler (server-side) publishes them to Centrifugo at the right offsets as the playback progresses
- Admins can pre-filter the replay set (exclude spam/abusive messages) via an admin UI

---

## 10. Authentication & Authorization

### Auth flow (browser)
1. User visits `webizon.kz` or `{tenant-slug}.webizon.kz`
2. Clicks "Sign in" → redirected to Keycloak OIDC flow
3. Keycloak authenticates, redirects back with an authorization code
4. Frontend exchanges code for tokens (via a server-side callback to protect the client secret)
5. Frontend stores tokens in an **HttpOnly, Secure, SameSite=Lax cookie**
6. Every subsequent API call sends the access token; refresh happens silently

### Authorization model
- **Roles**: `platform_admin`, `tenant_owner`, `tenant_admin`, `tenant_moderator`, `tenant_presenter`, `participant`
- **Role is scoped to a tenant** (except `platform_admin`)
- **JWT claims**:
  ```json
  {
    "sub": "user-uuid",
    "profile_id": "user-uuid",
    "tenant_id": "tenant-uuid",
    "role": "tenant_admin",
    "email": "...",
    "exp": ...,
    "iss": "https://auth.webizon.kz/realms/webizon"
  }
  ```
- Spring Security + `@PreAuthorize` annotations on every controller method
- A `@RequireRole("tenant_admin")` meta-annotation for common cases
- **Every controller method must have an explicit authorization annotation. `@PreAuthorize("permitAll()")` is allowed but must be justified in a comment.**

### Public endpoints (unauthenticated)
- Landing page data: `GET /api/v1/public/events/{slug}`
- Tenant signup: `POST /api/v1/public/signup`
- Keycloak callback: `GET /auth/callback`
- Webhook receivers (billing): `POST /api/v1/webhooks/{provider}` (verified by HMAC signature, not JWT)

### Participant auth
- Participants are **real users**. No anonymous viewing. Enforced consistently with the platform's principle.
- However, **signup friction is minimized**: email-only magic link signup is supported for participants (click link → instantly a Webizon account, can upgrade to full account later)
- This avoids requiring password creation to join a webinar while still getting a unique `profile_id`

---

## 11. Billing Subsystem (Critical for a SaaS)

### Data model
- **UsageMeter**: one row per `(tenant_id, billing_period, metric)`, updated via atomic increments from the BillingEventConsumer
- **BillingLedger**: append-only, one row per billable event, immutable
- **Invoice**: one row per `(tenant_id, billing_period)`, generated at period close
- **PaymentMethod**: tokenized card reference from CloudPayments/PayBox (we never store PAN)
- **Subscription**: current plan, billing period anchor, billing status

### Metering pipeline
```
live event happens → API emits billing event → Kafka topic webizon.billing.events
                                                       ↓
                                    BillingEventConsumer (idempotent by event_id)
                                                       ↓
                              INSERT INTO billing_ledger (append-only)
                                                       ↓
                              UPDATE usage_meter SET count = count + 1
```

### Invoicing
- A scheduled job (Quartz or Spring `@Scheduled`) runs on the 1st of each month
- For each active tenant, it reads UsageMeter for the closed period
- Computes charges per metric × rate
- Applies VAT (12%)
- Generates a PDF invoice
- Stores in S3 with a presigned URL that expires in 7 days
- Sends email notification
- Attempts to charge the default PaymentMethod automatically
- Records the payment attempt outcome in `payment_attempts`

### Payment failure handling
- Day 0: payment fails → retry in 3 days
- Day 3: second attempt → if fails, send dunning email, retry in 7 days
- Day 10: third attempt → if fails, send final warning
- Day 14: soft-suspend tenant (block new sessions, show banner)
- Day 30: disable all sessions
- Day 90: data retention expires (per ToS), eligible for hard delete

### Non-negotiable billing invariants
1. **Double-entry safety**: never update UsageMeter without also inserting into BillingLedger
2. **Idempotency keys**: every billing event has `(tenant_id, session_id, profile_id, event_type, unique_nonce)` — duplicates are silently ignored
3. **Event ordering**: billing events are partitioned in Kafka by `tenant_id` so ordering is preserved per tenant
4. **Reconciliation**: a nightly job compares UsageMeter totals to BillingLedger aggregates; any drift triggers an alert

---

## 12. Analytics Subsystem

### Event ingestion
- Any significant user action emits an `AnalyticsEvent` to Kafka topic `webizon.analytics.events`
- Events are small JSON payloads with: `tenant_id`, `event_id`, `session_id`, `profile_id`, `event_type`, `offset_seconds`, `metadata`, `created_at`
- A Kafka → ClickHouse connector (or a custom consumer batching into ClickHouse `INSERT`) ingests them
- **Target ingestion rate**: 100,000 events/sec across all tenants

### Event types (canonical list)
```
landing_viewed, auth_started, auth_completed,
room_entered, room_left, heartbeat,
chat_message_sent, chat_reply_sent, chat_like,
cta_impression, cta_click, cta_download,
watch_milestone (10m, 30m, 50%, 100%),
moderation_warning, moderation_mute, moderation_ban,
auto_slot_selected, notification_opted_in
```

### Query patterns
- Dashboard: aggregated counts and retention curves, cached in Redis for 60 seconds
- Retention chart: `SELECT minute, count() FROM analytics_events WHERE session_id = ? GROUP BY minute`
- CTA funnel: multi-step aggregation with window functions
- All queries are scoped by `tenant_id` (enforced at the query builder level)

### Data retention
- Raw events: 90 days in hot ClickHouse
- Aggregated rollups: 2 years in a separate ClickHouse table
- Export via REST API for compliance or customer data portability

---

## 13. Observability

### Metrics (Prometheus, via Micrometer)
- **RED** (Rate, Errors, Duration) on every HTTP endpoint
- **USE** (Utilization, Saturation, Errors) on every resource (CPU, memory, DB pool, Kafka lag, Redis connections)
- **Business metrics**: sessions started, active viewers, chat messages/sec, billing events/sec, new signups
- All metrics tagged with `env`, `service`, `tenant_id` (top-N only)

### Tracing (OpenTelemetry)
- Every request gets a trace ID
- Propagated via headers into Kafka, Redis, Centrifugo, downstream services
- Sampled at 10% in production, 100% in staging, 100% on errors always

### Logs (structured JSON)
- **Every log line has**: `timestamp`, `level`, `service`, `trace_id`, `tenant_id`, `profile_id`, `session_id` (when applicable), `message`
- No `System.out.println`. No `e.printStackTrace()`. Ever.
- Use SLF4J with placeholders, not string concatenation: `log.info("User {} joined session {}", userId, sessionId)`
- PII must not be logged at INFO level (emails, names) — use DEBUG and disable DEBUG in production

### Alerts (Alertmanager)
- P1: live session down, billing pipeline stuck, authentication broken → page on-call
- P2: API p95 > 500ms, Kafka lag > 10k, DB connection saturation → Slack + email
- P3: disk > 80%, certificate expiring in 30d → daily digest

### Runbooks
- Every alert links to a runbook in `docs/runbooks/`
- Runbooks follow a template: Symptoms, Likely Causes, Diagnosis Steps, Mitigation, Escalation

---

## 14. Security (Non-Negotiable)

1. **All traffic is HTTPS**. HSTS with `max-age=31536000; includeSubDomains; preload`.
2. **JWTs are RS256**, never HS256 for user-facing tokens. Keys rotated every 90 days.
3. **CSRF**: not applicable for pure API (JWT in Authorization header), but enabled for cookie-authenticated endpoints.
4. **CORS**: explicit allow-list per environment. No `*` in production.
5. **Rate limiting**: per IP + per user on authentication endpoints. Per tenant on API-wide level (fair usage).
6. **Input validation**: every DTO annotated with Jakarta Validation. Rejected with 400 before reaching business logic.
7. **SQL injection**: prevented by JPA parameter binding. No `String.format` in queries. Ever.
8. **XSS**: chat messages are stored raw but rendered with DOMPurify on the client. HTML is never injected into the DOM from chat.
9. **CSRF on webhooks**: HMAC signature verification on every webhook endpoint.
10. **Secrets**: never in code, never in git, never in logs. Environment variables in dev, Vault in production.
11. **Dependency scanning**: OWASP Dependency-Check in CI. PRs with vulnerable deps are blocked.
12. **Container scanning**: Trivy on every image before push.
13. **Audit log**: every admin action (moderation, tenant suspension, settings change) writes an audit row with actor, action, target, timestamp, IP.
14. **PII encryption at rest**: PostgreSQL TDE or column-level encryption for email and phone.
15. **Data in transit**: TLS 1.3 required; Redis, Kafka, PostgreSQL all require TLS in production.

---

## 15. Code Quality Standards

### Java (backend)
- **Formatting**: Google Java Format (enforced by Spotless in Maven)
- **Linting**: Checkstyle + PMD + SpotBugs, all errors fail the build
- **Imports**: no wildcards, no unused
- **Null safety**: `@NonNull` and `@Nullable` annotations on every public method parameter and return type. Use `Optional<T>` for optional returns.
- **Immutability**: prefer `record` for DTOs, `final` for fields and local variables when not reassigned
- **Exception handling**: never catch `Exception` broadly. Catch specific types. Translate to domain exceptions at layer boundaries.
- **Logging**: SLF4J, parameterized messages, appropriate levels (ERROR only for things humans need to act on)
- **Comments**: explain *why*, not *what*. Self-documenting code first.
- **Package structure**:
  ```
  com.webizon.{module}/
    api/           ← controllers, request/response DTOs
    domain/        ← entities, value objects, domain services
    application/   ← use cases (application services)
    infrastructure/← JPA repos, Kafka, Redis, external clients
    config/        ← Spring config specific to the module
  ```
- **Dependency direction**: `api → application → domain`. `infrastructure` implements interfaces defined in `domain` or `application`.

### TypeScript (frontend)
- **Strict mode**: `strict: true`, `noUncheckedIndexedAccess: true`
- **ESLint**: `@nuxt/eslint` preset, no `any` without justification
- **Formatting**: Prettier
- **No logic in templates**: computed properties or composables
- **Composition API only**: no Options API
- **Type definitions for API**: shared types generated from OpenAPI via `openapi-typescript`

### Testing
- **Unit tests**: JUnit 5 + Mockito. Focus on domain logic and use cases. Target: critical paths 90%+ coverage.
- **Integration tests**: Testcontainers (real PostgreSQL, Redis, Kafka). Slower but honest.
- **E2E tests**: Playwright against a running staging environment. Run on PRs touching frontend.
- **Load tests**: k6 scripts in `tests/load/`. Must be run before any performance-impacting PR.
- **No mocks for things we own**. Mock external services only.

### CI pipeline
```
build → unit test → integration test → lint → security scan → build docker → push → deploy to staging → e2e → [manual approval] → deploy to prod
```

---

## 16. Development Workflow

### Branching
- `main` — deployable at all times
- Feature branches — short-lived, PR to `main`
- `release/*` — for hotfix coordination if needed
- **No direct pushes to `main`**. Enforced by GitLab protected branches.

### Commits
- Conventional Commits: `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `perf:`
- Small, atomic. Each commit should compile and pass tests.
- Body explains *why*, not *what*.

### Pull requests
- Linked to an issue or ADR
- Description explains the change, the reasoning, and the testing performed
- Screenshots for UI changes
- Reviewed by at least one other engineer before merge
- Code review follows `code-review:code-review` skill

### ADRs (Architecture Decision Records)
- Any decision that affects more than one module or changes a rule in this document requires an ADR
- Stored in `docs/adr/NNNN-title.md`
- Template: Context, Decision, Consequences, Alternatives Considered
- ADRs are **never edited**. If a decision is reversed, a new ADR supersedes the old one.

---

## 17. Forbidden Practices (Fail the PR)

1. ❌ Queries without `tenant_id` filter on multi-tenant tables
2. ❌ `SELECT *` in production code (always explicit columns)
3. ❌ Storing secrets in environment files committed to git
4. ❌ Catching `Exception` or `Throwable` without logging and rethrowing
5. ❌ `new RestTemplate()` — use the configured `WebClient` or `RestClient` bean
6. ❌ Logging PII at INFO level
7. ❌ `println`, `printStackTrace`, `e.printStackTrace()`
8. ❌ Raw SQL string interpolation (`"SELECT ... WHERE id = " + id`)
9. ❌ Business logic in controllers
10. ❌ N+1 queries (use `@EntityGraph`, `JOIN FETCH`, or projection DTOs)
11. ❌ Unbounded collections in responses (always paginate)
12. ❌ `@Transactional` on read endpoints without `readOnly = true`
13. ❌ Blocking calls on hot paths without justification
14. ❌ Frontend fetching directly from Centrifugo without going through our token endpoint
15. ❌ Storing passwords or PANs (delegated to Keycloak and the payment provider respectively)
16. ❌ Implementing new features without a test
17. ❌ Merging on a red pipeline

---

## 18. Performance Budgets

| Component | Budget | How measured |
|-----------|--------|--------------|
| REST API p95 | 200 ms | Prometheus histogram |
| REST API p99 | 500 ms | Prometheus histogram |
| Chat publish → receive | 250 ms end-to-end | Client-side timing |
| Page load (Nuxt SSR, first byte) | 500 ms | Lighthouse CI |
| Largest Contentful Paint | 2.0 s | Lighthouse CI |
| DB query p95 | 50 ms | `pg_stat_statements` |
| Redis op p95 | 5 ms | Lettuce metrics |
| Kafka publish p95 | 20 ms | Producer metrics |
| ClickHouse dashboard query p95 | 1.0 s | Query log |

**If a PR regresses any of these by more than 10%, it is blocked until explained and justified.**

---

## 19. Local Development

### Prerequisites
- Docker Desktop (or equivalent)
- JDK 21 (Temurin recommended)
- Maven 3.9+
- Node.js 20 LTS
- Make (for `make` targets)

### One-command startup
```bash
make dev-up
```

This spins up:
- PostgreSQL (seeded with dev data)
- Redis
- Kafka + Zookeeper
- Centrifugo
- MinIO (S3 stand-in)
- Keycloak (with dev realm auto-imported)
- The API (with live reload)
- The frontend (Nuxt dev server)

### Conventions
- All services run in Docker Compose via `docker-compose.yml` at repo root
- Dev data is seeded via Flyway migrations with `profile = dev`
- Environment variables live in `.env` (committed as `.env.example`, ignored as `.env`)

### MinIO Presigned URL — Split-Horizon Hostname Rule (CRITICAL)

S3 V4 signatures bind the `Host` header into the canonical request at signing time.
The **same hostname** must therefore be reachable from two different contexts:

| Context | Must reach | Why |
|---------|-----------|-----|
| Backend Docker container | `MINIO_PUBLIC_ENDPOINT` host | SDK calls `getBucketRegion()` before signing |
| Browser (user's machine) | `MINIO_PUBLIC_ENDPOINT` host | Actual file PUT uses the presigned URL |

**The rule: `MINIO_PUBLIC_ENDPOINT` must resolve to MinIO from BOTH contexts.**

| Environment | Correct value | Why |
|-------------|--------------|-----|
| Local Docker Desktop (Win/Mac) | `http://host.docker.internal:9010` | Docker Desktop adds this to OS hosts; containers resolve it via `extra_hosts: host-gateway` |
| Local IDE (no Docker) | `http://localhost:9010` | Direct access, no container boundary |
| Production / Staging | `https://storage.webizon.kz` | Real domain, resolvable everywhere |

**Never use `http://localhost:PORT` as `MINIO_PUBLIC_ENDPOINT` inside Docker** — `localhost` inside a container is the container itself, not the host machine.

The `internalMinioClient` (for `statObject`, `removeObject`) always uses `http://minio:9000` (Docker-internal). This never changes.

---

## 20. Documentation Discipline

- This **CLAUDE.md** is the engineering constitution. Update it when a rule changes.
- **README.md** at repo root is the quickstart for new engineers.
- **docs/adr/** holds every architecture decision.
- **docs/runbooks/** holds one file per alert, one per known incident pattern.
- **docs/specs/** holds feature specs (brainstorming skill output goes here).
- **OpenAPI spec** is generated from the code, not written by hand.
- **Internal wiki** is banned. If it's worth writing down, it goes in the repo.

### MANDATORY: Update docs/CHANGELOG.md after every meaningful change

Triggers: new feature, bug fix, architecture change, config change, new migration, new API endpoint, auth flow change.

Format:
```
### [Title]
**Не өзгерді:** ...
**Себебі:** ...
**Шешімі:** (if bug fix)
**Файлдар:** ...
```

Key doc files in `docs/`:
- `CHANGELOG.md` — all changes history
- `ARCHITECTURE.md` — system design, ports, modules
- `AUTH.md` — full auth flow (NOT Keycloak SSO redirect)
- `API.md` — all API endpoints
- `ONBOARDING.md` — new developer setup guide

---

## 21. North Star Questions (Every Design Must Answer)

Before merging any non-trivial change, the author must be able to answer:

1. Does this work correctly when there are **60,000 viewers in one session**?
2. Does this work correctly when there are **100 tenants running sessions simultaneously**?
3. What happens when **Kafka is down for 10 minutes**?
4. What happens when **Redis is down for 10 seconds**?
5. What happens when **PostgreSQL is degraded** (slow, not dead)?
6. What happens when **Centrifugo cluster loses one node**?
7. Can a **malicious tenant** degrade another tenant's experience?
8. Is this change **observable** (metrics, logs, traces)?
9. Is this change **reversible** (feature flag, blue-green, rollback plan)?
10. Does this change respect the **billing invariants** (idempotency, append-only, reconciliation)?

If the answer to any of these is "I don't know", the design is not done.

---

## 22. Immediate Build Direction

The first working milestone (**MVP**, defined in `docs/PLAN.md`) targets:
- Multi-tenant signup + login
- Event creation (admin)
- LIVE session with YouTube embed + custom chat via Centrifugo
- Basic CTA
- Basic analytics (attendance + retention chart)
- Trial billing (no real payments yet)
- Deploy to staging environment

Everything else is deferred until MVP is provably stable under load testing.

---

## 23. Frontend Implementation Conventions

These are the concrete rules that crystallised during F1 (foundation) and F2 (admin events) and now apply to every subsequent frontend phase. If a new phase needs to violate one of them, write a note here first.

### Directory layout (Nuxt 4 `app/` convention)
```
frontend/
├── app/                        ← srcDir — everything Vue/Nuxt autoimports lives here
│   ├── app.vue
│   ├── assets/css/main.css     ← Tailwind base + utility classes (input-base, field-label, card, ...)
│   ├── components/
│   │   ├── ui/                 ← Reusable, brandless primitives (UiButton, UiCard, UiModal, ...)
│   │   └── admin/              ← Admin-chrome pieces (PageHeader, EventStatusBadge, EventForm, ...)
│   ├── composables/            ← useApi, useCentrifuge, ... (auto-imported)
│   ├── layouts/                ← default.vue, admin.vue
│   ├── middleware/             ← auth.ts, ...
│   ├── pages/                  ← file-based routing (see Route grouping below)
│   ├── plugins/                ← auth.client.ts, ...
│   └── stores/                 ← Pinia stores (auth, toast, ...) auto-imported
├── shared/api/                 ← Typed API facade — framework-agnostic, outside app/
│   ├── client.ts               ← ApiClient class
│   ├── types.ts                ← Hand-written TS mirrors of backend DTOs
│   ├── endpoints/              ← One module per backend controller
│   └── index.ts                ← createApi() factory
├── public/                     ← Static assets served verbatim
├── server/                     ← Nitro routes (reserved, currently unused)
├── nuxt.config.ts
├── tailwind.config.ts
├── eslint.config.mjs
├── tsconfig.json
└── Dockerfile
```

Two aliases are relevant inside templates and scripts:
- `~` and `@` → `frontend/app/` (srcDir). Use for components, pages, composables, stores.
- `#shared` → `frontend/shared/`. Use for the API facade: `import type { EventResponse } from '#shared/api/types'`. **Never** write `~/shared/...` — that path does not exist under Nuxt 4 because `~` now points at `app/`, not the frontend root.

### Component naming and auto-imports
- `nuxt.config.ts` uses `components: [{ path: '~/components', pathPrefix: false }]`.
- **`pathPrefix: false` drops the directory segment from the component name**. This is the key rule that every new component must respect:
  - `components/ui/UiButton.vue` → `<UiButton>` ✅
  - `components/admin/PageHeader.vue` → `<PageHeader>` (NOT `<AdminPageHeader>`) ✅
  - `components/admin/EventStatusBadge.vue` → `<EventStatusBadge>` ✅
- Because the directory segment is dropped, **file names must be globally unique**. Use a `Ui*` prefix for primitives and a domain-meaningful name for admin pieces (`EventForm`, `SessionTimeline`) so there are no collisions.

### Shared API layer (`shared/api/`)
- One `*Api` class per backend controller. Each class takes an `ApiClient` in its constructor and exposes typed methods.
- `shared/api/types.ts` is **hand-written**, not codegen. It mirrors backend DTOs exactly (same field names, UPPER_SNAKE enum values). When a backend DTO changes, update this file in the same PR.
- `shared/api/client.ts` defines `ApiClient`, `RequestOptions`, `ApiError` normalisation. **It has NO Nuxt imports** — keep it framework-agnostic so it is testable in isolation and cannot accidentally pick up SSR-only state.
- `composables/useApi.ts` is the Nuxt-aware wrapper. It caches the `Api` facade on the Nuxt app instance (`nuxtApp.$api`) so every component gets the same instance, and wires `onError` into the toast store.
- **NEVER call `$fetch` directly** from a component or page. Go through `useApi().<module>.<method>()`.

### Pinia stores
- `stores/auth.ts` is the **single source of truth** for auth state in the frontend. The route middleware MUST use `useAuthStore()`, never a localStorage-backed composable — we burned time on a redirect loop when middleware read stale state from a composable that initialised after it ran.
- `stores/toast.ts` is the central error surface. `useApi` routes backend errors into `toast.error()` automatically; components only need to push their own UX-level messages (`toast.success('Saved')`).

### Route grouping for dynamic segments
Nuxt file-based routing does NOT allow `pages/foo/[id].vue` and `pages/foo/[id]/bar.vue` to coexist. When a dynamic segment needs children, **always use the directory form from the start**:
```
pages/admin/events/[id]/index.vue   ← detail page
pages/admin/events/[id]/edit.vue    ← edit page
pages/admin/events/[id]/sessions.vue ← (future)
```
Do not start with `[id].vue` and migrate later — the migration breaks existing links and deploy caches.

### Dev-mode auth injection
- `plugins/auth.client.ts` injects a **synthetic session** when `import.meta.dev && !auth.isAuthenticated`. This lets admin pages render without a live Keycloak instance.
- The route `middleware/auth.ts` must short-circuit when `import.meta.dev` is true — otherwise it will try to redirect to the Keycloak OIDC endpoint that doesn't exist locally.
- **Never** leave the synthetic session active in production builds. The plugin check is `import.meta.dev`, which is false in production. Do not widen that check.

### Tailwind tokens
- Use semantic tokens (`bg-brand-600`, `text-danger-700`), never raw hex. The palette lives in `tailwind.config.ts`.
- Shadow tokens: `shadow-soft` for cards, `shadow-pop` for elevated surfaces, `shadow-overlay` for modals and popovers.
- Legacy utility classes in `main.css` (`btn-primary`, `card`, `input-base`, `field-label`, `field-hint`, `field-error`) are preserved for backward compatibility with pre-F1 pages. New code should prefer `UiButton`, `UiCard`, `UiInput`, etc. instead of the raw classes — but the classes still exist because the login/pricing pages depend on them.

### UI primitives contract
Every `Ui*` component follows the same contract:
- Props are typed with an interface; defaults go through `withDefaults`.
- Forms use `modelValue` + `update:modelValue` (v-model compatible).
- Errors are surfaced via an `error?: string` prop — never via thrown exceptions or a parent store.
- Icons come from `lucide-vue-next`. Do NOT mix icon libraries.
- Accessibility primitives (modal focus trap, menu, listbox) come from `@headlessui/vue`. Do NOT hand-roll focus management.

### Error surfacing
- Backend errors flow: `ApiClient.request` → `normaliseError` → `useApi.onError` → `toast.error(...)`.
- 401 errors are deliberately swallowed at `useApi.onError` (they trigger a redirect to login instead).
- 400 errors with a `errors: Record<string, string>` validation map are also swallowed globally — forms MUST catch them locally and map them onto field-level errors (`EventForm.vue` is the reference implementation).

### YouTube embed rules (applies to F5 and beyond)
- Always use the `youtube-nocookie.com/embed/{videoId}` domain, never `youtube.com/embed`.
- Standard query string for every embed: `rel=0&modestbranding=1&showinfo=0&iv_load_policy=3&disablekb=1&cc_load_policy=0&playsinline=1&enablejsapi=1`.
- The visible chrome (hover title bar, YouTube watermark area) is covered by a `pointer-events-none` gradient overlay inside a dedicated `components/room/YoutubePlayer.vue` wrapper. **Do not inline YouTube iframes anywhere else** — the wrapper is the single place the overlay logic, URL parameters, and JS API hooks are maintained.
- The backend generates `youtubeEmbedUrl` on `SessionResponse`. The frontend wrapper is allowed to append additional query parameters but must never strip `modestbranding` or `rel=0` (both are required for ToS compliance + UX consistency).

### Commit message shape (frontend slices)
Frontend slices are committed as `feat(frontend): <slice summary>` with a short body listing the pages/components added. F1 and F2 are reference examples in the git log.

---

## 24. Moderation, Bans & Throttling Data Model

Moderation is not a polish feature — it is the difference between a usable live room and a hostile one. The data model and the throttling layers are pinned here so that F5 and F6 cannot drift.

### Tables (all tenant-scoped, all in PostgreSQL)

**`moderation_events`** — append-only audit log. Every action by a moderator OR by the auto-moderator lands here. Never `UPDATE`, never `DELETE` (retained 2 years for legal review).

```sql
CREATE TABLE moderation_events (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    event_id      UUID         NOT NULL,
    session_id    UUID,                              -- nullable for tenant-wide actions
    actor_id      UUID         NOT NULL,             -- moderator profile id (or system uuid)
    actor_kind    TEXT         NOT NULL              -- 'HUMAN' | 'SYSTEM' | 'AUTO_RULE'
        CHECK (actor_kind IN ('HUMAN','SYSTEM','AUTO_RULE')),
    target_user_id     UUID,                         -- nullable for non-user actions (slow mode)
    target_message_id  BIGINT,                       -- nullable
    action_type   TEXT         NOT NULL              -- see enum below
        CHECK (action_type IN (
            'WARN','MUTE','UNMUTE','CHAT_BAN','UNCHAT_BAN',
            'ROOM_REMOVE','TENANT_BAN','UNTENANT_BAN',
            'MESSAGE_DELETE','MESSAGE_HIDE','MESSAGE_PIN',
            'SLOW_MODE_CHANGE','CHAT_DISABLED','CHAT_ENABLED',
            'WORD_RULE_HIT','LINK_RULE_HIT'
        )),
    reason            TEXT,
    duration_seconds  INTEGER,                       -- mute/ban duration; null = permanent
    payload_json      JSONB,                         -- action-specific extras
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX moderation_events_tenant_recent
    ON moderation_events (tenant_id, created_at DESC);
CREATE INDEX moderation_events_target
    ON moderation_events (target_user_id, created_at DESC)
    WHERE target_user_id IS NOT NULL;
```

**`chat_user_status`** — current effective state for a user in a session. This is a fast lookup table that the chat policy chain reads on every message ingress. Updated by the moderation pipeline atomically with the corresponding `moderation_events` row (in the same transaction).

```sql
CREATE TABLE chat_user_status (
    tenant_id      UUID         NOT NULL,
    session_id     UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    is_muted       BOOLEAN      NOT NULL DEFAULT false,
    mute_until     TIMESTAMPTZ,
    is_chat_banned BOOLEAN      NOT NULL DEFAULT false,
    is_room_banned BOOLEAN      NOT NULL DEFAULT false,
    warning_count  INTEGER      NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, user_id)
);
```

This table is also mirrored into Redis as `chat:status:{session_id}:{user_id}` with a 1-hour TTL so the policy chain doesn't hit Postgres on every message. Cache invalidation happens via Centrifugo personal channel: when a user is muted, the moderation pipeline publishes a `ChatStatusChanged` event the user's client receives AND the local Redis cache is `DEL`-ed.

**`tenant_bans`** — bans that follow a user across **every** event in the tenant. Loaded once on Centrifugo connect; reject the connect entirely if banned.

```sql
CREATE TABLE tenant_bans (
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    reason      TEXT,
    banned_by   UUID         NOT NULL,
    banned_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ,                         -- null = permanent
    PRIMARY KEY (tenant_id, user_id)
);
```

**`moderation_rules`** — per-tenant auto-moderation rules. Evaluated by the policy chain in the order specified (lower `priority` first).

```sql
CREATE TABLE moderation_rules (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    kind        TEXT         NOT NULL                -- 'WORD' | 'REGEX' | 'LINK_DOMAIN'
        CHECK (kind IN ('WORD','REGEX','LINK_DOMAIN')),
    pattern     TEXT         NOT NULL,
    action      TEXT         NOT NULL                -- 'BLOCK' | 'MASK' | 'FLAG'
        CHECK (action IN ('BLOCK','MASK','FLAG')),
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX moderation_rules_tenant ON moderation_rules (tenant_id, enabled, priority);
```

### Throttling layers — defense in depth

A misbehaving client must hit a wall **before** it touches the chat policy chain. The order matters: cheap checks first, expensive checks last.

| Layer | Where | Key | Default limit | Storage |
|---|---|---|---|---|
| **Per-IP Centrifugo connect** | Centrifugo config | client IP | 5 connects / sec / IP | Centrifugo built-in |
| **Per-IP login attempts** | Spring filter | client IP | 10 attempts / min / IP | Redis sliding window |
| **Per-tenant API rate limit** | Spring filter | `tenant_id` from JWT | 1000 req / min / tenant | Redis token bucket |
| **Per-user message slow mode** | Chat policy chain | `(session_id, user_id)` | session-configured (0/5/10/20/30/60 s) | Redis Lua INCR+EXPIRE |
| **Per-user flood protection** | Chat policy chain | `(session_id, user_id)` | 5 messages / 10 sec (hard) | Redis Lua sliding window |
| **Per-session global throttle** | Chat policy chain | `session_id` | 1000 messages / sec / session | Redis Lua, optional |

**Rules for the throttling layers:**

1. **All Redis throttling is Lua** — round-trip per check is single RTT. Never two-step (`GET` then `SET`).
2. **Moderators bypass slow mode but never flood protection.** A compromised moderator account cannot DOS the room.
3. **Throttling rejection returns `429` + a `Retry-After` header** with the wait in seconds. The frontend must show a polite "wait N s" hint, not an error toast.
4. **Throttling counters do NOT persist to Postgres.** They live in Redis only. If Redis is unavailable, the throttling layer fails OPEN (allows the message) and emits a `ChatThrottlingDegraded` metric. Failing closed would deny chat to everyone.
5. **Tenant ban enforcement is at Centrifugo connect time.** When the API issues a Centrifugo token, it queries `tenant_bans` and refuses to issue a token for banned users. Already-connected sessions are kicked via a Centrifugo `disconnect` API call when a new ban lands.

### Bans must survive reconnect, refresh, and new sessions

This is the rule that the naive design always gets wrong. Three checkpoints:

1. **JWT issuance** (Keycloak callback): Webizon's IdP-side hook checks `tenant_bans` and refuses to mint a JWT for a banned user. They get bounced to a "your account is restricted" page.
2. **Centrifugo connect** (per WebSocket open): Webizon's `connect_proxy` endpoint re-checks `tenant_bans` and `chat_user_status` for the session being joined. Refuses with `disconnect_code=4403`.
3. **Per-message ingress**: The chat policy chain re-checks `chat_user_status` from Redis (with Postgres fallback). A ban that lands while the user is mid-typing rejects the next message with `429` + `code=BANNED`.

A user banned in the middle of a session **must be kicked within 1 second**. This is a non-negotiable UX/safety budget.

---

## 25. Load Test Discipline — No Premature Tuning

Right now Webizon serves zero traffic. Every performance number we have — `work_mem=64MB`, `max_connections=200`, Centrifugo `client_concurrent_messages`, Kafka `linger.ms=10`, Redis `maxmemory` — is a **guess**, not a measurement. Tuning guesses against more guesses produces a system that is brittle in unpredictable ways.

### The discipline

1. **Reasonable defaults ship first.** Don't pre-optimize. Use Spring Boot, Postgres, Redis, Centrifugo, and Kafka with vendor defaults plus the bare minimum we need to run (auth, multi-tenancy, the chat hot path). Resist the urge to set tunables until something measurably hurts.
2. **Build the load test harness before the second feature ships.** k6 (preferred — simple JS scripts, good metrics, native Prometheus output) or Locust. Repo path: `loadtest/`. Scenarios live alongside the harness.
3. **Run staged load tests against staging — never against local.** Staging must run on infra at the **shape** of production (right CPU/RAM ratio), even if smaller. Numbers from a developer laptop are not load tests; they are noise.
4. **Standard ramp**: 1k → 5k → 10k → 30k → 60k concurrent. Each step is its own report. The first step that fails the Performance Budgets in §18 is the bottleneck.
5. **Tune ONE knob per round.** After each round, change at most one configuration value, document why in `loadtest/adr/`, re-run the same scenario, and compare. If the change didn't help, revert it. Multi-knob tuning rounds make causality un-recoverable.
6. **Promote tuning to CLAUDE.md only after a load test confirms it.** Setting numbers in §18 or §4 without a corresponding `loadtest/adr/NNNN-*.md` is forbidden.

### What scenarios to run (in order)

| # | Scenario | Goal | Pass criterion |
|---|---|---|---|
| 1 | **Auth flood**: 1000 logins/s for 60 s | Catch Keycloak / JWT validation issues early | p95 token issuance < 250 ms |
| 2 | **Cold join**: 10 000 users join a single session over 30 s | Centrifugo connect throughput, JWT validation | Zero connect errors, p95 connect < 500 ms |
| 3 | **Steady chat**: 10 000 connected, ~5 msg/min/active-10 % | Postgres write batching, Centrifugo fan-out | p95 message delivery < 200 ms, zero dropped writes |
| 4 | **Burst chat**: same as above, but a 30-second window of 500 msg/s | `ChatWriteBuffer` saturation, Redis throttling | Zero data loss, slow mode kicks in correctly |
| 5 | **Multi-session**: 30 sessions × 5 000 viewers each = 150 000 concurrent | Tenant isolation under load, Centrifugo node memory | No cross-session bleed, no Centrifugo node OOMs |
| 6 | **Failure injection**: kill one Postgres replica during scenario 3 | Failover behavior | Reads degrade gracefully, writes pause < 5 s |
| 7 | **Sustained 60k**: 60 000 concurrent for 30 minutes | The actual scale target | All §18 budgets hold throughout |

### What is forbidden

- Setting a Postgres tunable (`shared_buffers`, `work_mem`, `effective_cache_size`) without a matching `loadtest/adr/` entry.
- Bumping a Centrifugo or Kafka throughput knob "to be safe" before a load test has shown it bottlenecks.
- Quoting performance numbers (RPS, p95, concurrent capacity) in the README, marketing copy, or sales conversations until scenario 7 has been run on staging-shaped infra.
- Using a load test result from a previous Spring Boot / Postgres / kernel version. Re-run after any major dependency bump.

The point of this section is not bureaucracy. It is to make sure the **first** time we discover a bottleneck is in staging on a Tuesday morning, not in production at 8 PM during a customer's flagship webinar.

---

**This document is law. If you disagree with a rule, write an ADR. Do not silently violate it.**

<!-- rtk-instructions v2 -->
# RTK (Rust Token Killer) - Token-Optimized Commands

## Golden Rule

**Always prefix commands with `rtk`**. If RTK has a dedicated filter, it uses it. If not, it passes through unchanged. This means RTK is always safe to use.

**Important**: Even in command chains with `&&`, use `rtk`:
```bash
# ❌ Wrong
git add . && git commit -m "msg" && git push

# ✅ Correct
rtk git add . && rtk git commit -m "msg" && rtk git push
```

## RTK Commands by Workflow

### Build & Compile (80-90% savings)
```bash
rtk cargo build         # Cargo build output
rtk cargo check         # Cargo check output
rtk cargo clippy        # Clippy warnings grouped by file (80%)
rtk tsc                 # TypeScript errors grouped by file/code (83%)
rtk lint                # ESLint/Biome violations grouped (84%)
rtk prettier --check    # Files needing format only (70%)
rtk next build          # Next.js build with route metrics (87%)
```

### Test (90-99% savings)
```bash
rtk cargo test          # Cargo test failures only (90%)
rtk vitest run          # Vitest failures only (99.5%)
rtk playwright test     # Playwright failures only (94%)
rtk test <cmd>          # Generic test wrapper - failures only
```

### Git (59-80% savings)
```bash
rtk git status          # Compact status
rtk git log             # Compact log (works with all git flags)
rtk git diff            # Compact diff (80%)
rtk git show            # Compact show (80%)
rtk git add             # Ultra-compact confirmations (59%)
rtk git commit          # Ultra-compact confirmations (59%)
rtk git push            # Ultra-compact confirmations
rtk git pull            # Ultra-compact confirmations
rtk git branch          # Compact branch list
rtk git fetch           # Compact fetch
rtk git stash           # Compact stash
rtk git worktree        # Compact worktree
```

Note: Git passthrough works for ALL subcommands, even those not explicitly listed.

### GitHub (26-87% savings)
```bash
rtk gh pr view <num>    # Compact PR view (87%)
rtk gh pr checks        # Compact PR checks (79%)
rtk gh run list         # Compact workflow runs (82%)
rtk gh issue list       # Compact issue list (80%)
rtk gh api              # Compact API responses (26%)
```

### JavaScript/TypeScript Tooling (70-90% savings)
```bash
rtk pnpm list           # Compact dependency tree (70%)
rtk pnpm outdated       # Compact outdated packages (80%)
rtk pnpm install        # Compact install output (90%)
rtk npm run <script>    # Compact npm script output
rtk npx <cmd>           # Compact npx command output
rtk prisma              # Prisma without ASCII art (88%)
```

### Files & Search (60-75% savings)
```bash
rtk ls <path>           # Tree format, compact (65%)
rtk read <file>         # Code reading with filtering (60%)
rtk grep <pattern>      # Search grouped by file (75%)
rtk find <pattern>      # Find grouped by directory (70%)
```

### Analysis & Debug (70-90% savings)
```bash
rtk err <cmd>           # Filter errors only from any command
rtk log <file>          # Deduplicated logs with counts
rtk json <file>         # JSON structure without values
rtk deps                # Dependency overview
rtk env                 # Environment variables compact
rtk summary <cmd>       # Smart summary of command output
rtk diff                # Ultra-compact diffs
```

### Infrastructure (85% savings)
```bash
rtk docker ps           # Compact container list
rtk docker images       # Compact image list
rtk docker logs <c>     # Deduplicated logs
rtk kubectl get         # Compact resource list
rtk kubectl logs        # Deduplicated pod logs
```

### Network (65-70% savings)
```bash
rtk curl <url>          # Compact HTTP responses (70%)
rtk wget <url>          # Compact download output (65%)
```

### Meta Commands
```bash
rtk gain                # View token savings statistics
rtk gain --history      # View command history with savings
rtk discover            # Analyze Claude Code sessions for missed RTK usage
rtk proxy <cmd>         # Run command without filtering (for debugging)
rtk init                # Add RTK instructions to CLAUDE.md
rtk init --global       # Add RTK to ~/.claude/CLAUDE.md
```

## Token Savings Overview

| Category | Commands | Typical Savings |
|----------|----------|-----------------|
| Tests | vitest, playwright, cargo test | 90-99% |
| Build | next, tsc, lint, prettier | 70-87% |
| Git | status, log, diff, add, commit | 59-80% |
| GitHub | gh pr, gh run, gh issue | 26-87% |
| Package Managers | pnpm, npm, npx | 70-90% |
| Files | ls, read, grep, find | 60-75% |
| Infrastructure | docker, kubectl | 85% |
| Network | curl, wget | 65-70% |

Overall average: **60-90% token reduction** on common development operations.
<!-- /rtk-instructions -->