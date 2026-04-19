# Webizon — Implementation Plan

> This plan is sequenced so every phase delivers something runnable.
> Each phase has a clear exit criterion. Do not start phase N+1 until phase N's exit criterion is met.

---

## Phase 0 — Foundation (Week 0)

**Goal**: A runnable skeleton that boots, logs, and passes a health check.

### Tasks
1. Initialize monorepo structure (`backend/`, `frontend/`, `infra/`, `docs/`)
2. `docker-compose.yml` with all infrastructure services (Postgres, Redis, Kafka, Zookeeper, Centrifugo, MinIO, Keycloak)
3. `Makefile` with `dev-up`, `dev-down`, `dev-logs`, `test`, `lint`, `build`
4. Backend: Spring Boot 3.2 skeleton (`pom.xml`, `WebizonApplication.java`, `application.yml`, `/actuator/health`)
5. Frontend: Nuxt 4 skeleton with TypeScript strict mode, ESLint, Prettier, Tailwind
6. `.gitignore`, `.editorconfig`, `.env.example`
7. CI pipeline (GitLab): build backend, build frontend, run tests, build Docker images
8. ADR-0001: stack lock-in
9. ADR-0002: multi-tenancy via discriminator + RLS
10. ADR-0003: Centrifugo for real-time

### Exit criteria
- `make dev-up` starts every service cleanly
- `curl http://localhost:8080/actuator/health` returns 200
- `curl http://localhost:3000` returns the Nuxt welcome page
- CI pipeline green on `main`

---

## Phase 1 — Identity & Tenancy (Weeks 1–2)

**Goal**: A tenant can sign up, log in, and land in an empty admin dashboard.

### Backend
1. Flyway: `V001__core_tenancy.sql` — `tenants`, `users`, `tenant_users`, `plans`, `subscriptions`
2. `TenantContext` (ThreadLocal), `TenantContextFilter`, `TenantAwareRepository<T, ID>`
3. Hibernate `@Filter` for automatic tenant scoping
4. Postgres RLS policies on all tenant tables
5. Keycloak dev realm JSON (committed), auto-imported on `make dev-up`
6. JWT validation filter (Spring Security + Keycloak JWKS)
7. `POST /api/v1/public/signup` — creates Tenant + Owner User + default Subscription (Trial plan)
8. `GET /api/v1/me` — returns current user + tenant
9. `GET /api/v1/tenant` — tenant details
10. Integration tests with Testcontainers

### Frontend
1. Auth flow with Keycloak OIDC (server-side callback)
2. `useAuth()` composable + `authStore`
3. Signup page, login redirect, logout
4. Admin layout shell (empty sidebar + topbar)
5. Route middleware enforcing auth on `/admin/*`
6. Tenant switch UI (if user belongs to multiple tenants — future)

### Exit criteria
- Anonymous user can sign up → becomes tenant_owner
- After login, user lands on `/admin` with their tenant name in the header
- All DB queries include `tenant_id` (audited via SQL log inspection)
- RLS blocks cross-tenant SELECTs (verified in integration test)

---

## Phase 2 — Events & Sessions (Weeks 3–4)

**Goal**: Admin can create an Event, schedule a LIVE Session, and a participant can visit the landing page.

### Backend
1. Flyway: `V002__events_sessions.sql`
2. Entities: `Event`, `EventSession`, `EventChatSettings`, `LandingConfig`
3. Services: `EventService`, `SessionService`, `SlugGenerator`
4. Controllers:
   - `POST /api/v1/events` (admin create)
   - `GET /api/v1/events` (admin list, paginated)
   - `PUT /api/v1/events/{id}` (admin update)
   - `POST /api/v1/events/{id}/sessions` (create LIVE session)
   - `GET /api/v1/public/events/{slug}` (landing page data, unauthenticated)
5. YouTube URL validation + canonical extraction
6. Business rules: finished session cannot be reactivated, only a new session linked to the same event
7. Cover image upload via pre-signed MinIO URL

### Frontend
1. Admin: Events list page
2. Admin: Event wizard (title, description, speaker, cover upload, slug)
3. Admin: Session scheduler (date, time, YouTube URL)
4. Public: Landing page `/event/[slug]` with countdown
5. Participant signup flow (magic-link or full account)

### Exit criteria
- Admin creates event → sees it in list
- Admin schedules a session → session appears as "upcoming"
- Visiting `/event/{slug}` unauthenticated shows landing page
- Logged-in participant sees "Join" button on landing

---

## Phase 3 — Real-time Layer (Week 5)

**Goal**: Participants connect to Centrifugo and receive broadcast system messages.

### Backend
1. Centrifugo config (`centrifugo/config.json`) with HMAC token auth, Redis engine, channel namespaces
2. `CentrifugoClient` Spring component (HTTP publisher)
3. `POST /api/v1/rt/token` — issues a short-lived Centrifugo JWT scoped to the user's tenant
4. `/api/v1/sessions/{id}/system/broadcast` (admin-only) — publishes a system message
5. Channel naming convention enforced in `ChannelNameFactory`

### Frontend
1. `useCentrifuge()` composable wrapping `centrifuge-js`
2. Reconnect with exponential backoff
3. Subscribe to `tenant.{tid}.session.{sid}.system` when entering a room
4. Display system banner when admin broadcasts
5. Token refresh on 401

### Exit criteria
- Two browser tabs in the same session receive an admin broadcast simultaneously
- Disconnecting network → auto-reconnects within 10 seconds
- Invalid token → clean error, no silent failures
- Centrifugo dashboard shows active clients tagged by tenant

---

## Phase 4 — Live Room & Chat (Weeks 6–7)

**Goal**: A real webinar can run — video, chat, moderation, presence.

### Backend
1. Flyway: `V003__chat_schema.sql` — `chat_messages`, `chat_user_status`, `moderation_actions`
2. `ChatService` with full policy chain (ban, mute, slow mode, flood, forbidden words, links)
3. `FloodProtectionService` + `SlowModeService` with Redis Lua scripts
4. `POST /api/v1/sessions/{id}/chat` — send (publishes to Kafka + Centrifugo, returns 202)
5. `GET /api/v1/sessions/{id}/chat` — history, cursor-paginated
6. `ChatPersistenceConsumer` — Kafka → PostgreSQL batch writes (500 msg or 2s)
7. Moderation endpoints: delete, mute, warn, ban, unban
8. `PresenceService` with Redis hash for active viewers
9. Viewer count broadcast every 5 seconds (with debouncing)

### Frontend
1. Room layout (mobile-first): video top, chat below, CTA overlay
2. YouTube embed component with no chat iframe
3. Chat component: messages list, send box, reply, slow mode countdown
4. Moderator panel (visible only to moderators): delete, mute, warn
5. Viewer count live-updated
6. Reconnect UX (spinner + banner, not silent)

### Exit criteria
- 100 browser tabs in one session all receive chat messages within 250ms (measured locally)
- Deleted messages disappear for everyone in under 1 second
- Slow mode is enforced per user
- Flood spam is blocked before reaching Kafka
- A participant who refreshes mid-session sees the last 50 messages

---

## Phase 5 — CTA & Timeline (Week 8)

**Goal**: Admin triggers CTAs during a live session; participants see them instantly.

### Backend
1. Flyway: `V004__cta_timeline.sql` — `event_ctas`, `event_timeline_actions`
2. `CtaService` and `TimelineService`
3. `POST /api/v1/sessions/{id}/cta/{ctaId}/show` + `hide` — publishes to Centrifugo timeline channel
4. Timeline action storage (offset-based, for later auto-session replay)
5. CTA priority and placement resolution rules

### Frontend
1. Admin: CTA manager (create, edit, assign to event)
2. Admin live control: show/hide buttons during session
3. Participant: CTA overlay component that subscribes to timeline channel
4. CTA click tracking → analytics event

### Exit criteria
- Admin clicks "Show CTA" → CTA appears on all viewers within 500ms
- CTA click is tracked as an analytics event
- Multiple CTAs respect priority rules (single placement shows highest priority)

---

## Phase 6 — Analytics Pipeline (Week 9)

**Goal**: Every significant user action lands in ClickHouse and the admin dashboard shows meaningful charts.

### Backend
1. `AnalyticsPublisher` service emitting events to Kafka topic `webizon.analytics.events`
2. Instrument existing code paths to emit events: landing_viewed, room_entered, chat_message_sent, cta_click, heartbeat, watch_milestone, etc.
3. ClickHouse schema (`analytics_events` table with proper partitioning and sort key)
4. ClickHouse consumer (`AnalyticsConsumer`) batching inserts (up to 10k events per batch)
5. Dashboard queries:
   - `GET /api/v1/analytics/session/{id}/overview`
   - `GET /api/v1/analytics/session/{id}/retention`
   - `GET /api/v1/analytics/session/{id}/cta-funnel`
   - `GET /api/v1/analytics/tenant/overview`
6. Redis cache layer (60s TTL) for dashboard queries

### Frontend
1. Admin: Session analytics page
2. Retention line chart (viewer count over time)
3. CTA funnel bar chart
4. Top chatters, moderation stats
5. Tenant-wide overview (total sessions, total watch time)

### Exit criteria
- Dashboard reflects events within 10 seconds of occurrence
- 100k events/sec sustained ingestion in load test
- Admin can drill from tenant overview → event → specific session

---

## Phase 7 — Billing MVP (Week 10)

**Goal**: Metered usage accumulates, a test invoice is generated, and a test payment succeeds.

### Backend
1. Flyway: `V005__billing.sql` — `usage_meter`, `billing_ledger`, `invoices`, `payment_methods`, `payment_attempts`
2. `BillingEventPublisher` emitting events for: session_seat_billed, replay_seat_billed, storage_used
3. `BillingEventConsumer` (idempotent) writing to ledger + updating meter
4. `InvoiceGenerator` (monthly `@Scheduled` job) generating PDF invoices
5. CloudPayments integration (or PayBox) with webhook handler for payment status
6. `PUT /api/v1/billing/payment-method` — tokenize card via provider widget
7. `POST /api/v1/webhooks/cloudpayments` — HMAC-verified
8. Reconciliation job (nightly) comparing meter vs ledger

### Frontend
1. Admin: Billing page — current usage, plan, next invoice estimate
2. Admin: Payment method management
3. Admin: Invoice history with PDF download
4. Tenant status banner (Trial / Active / Past Due / Suspended)

### Exit criteria
- A session with 100 participants increments the seat meter by 100
- Monthly invoice PDF is generated with correct VAT breakdown in KZT
- Test payment via provider succeeds and marks invoice paid
- Idempotency verified: same event fired twice increments meter once

---

## Phase 8 — Auto Sessions & Historical Replay (Week 11)

**Goal**: A finished LIVE session can be replayed as an AUTO session at a scheduled slot, with historical chat replay.

### Backend
1. `AutoSessionScheduler` — cron-based, starts AUTO sessions at their planned time
2. `HistoricalChatReplayer` — publishes chat messages to Centrifugo at the right offsets
3. `POST /api/v1/events/{id}/sessions/auto` — create AUTO slot from a LIVE source
4. Admin chat review UI to exclude messages from replay
5. Timeline actions replayed at correct offsets
6. Billing meter increments for AUTO replay seats (different rate)

### Frontend
1. Admin: Auto session scheduler (select LIVE source, pick slots)
2. Participant: Slot selection page after missed live
3. Historical chat review UI

### Exit criteria
- AUTO session starts at scheduled time and plays historical chat in sync
- Replay seats are billed at the correct rate
- Admin can exclude specific messages from replay

---

## Phase 9 — Recordings & Downloads (Week 12)

**Goal**: LIVE sessions can be recorded to S3 and offered as on-demand recordings.

### Approach
- Recording is handled externally (YouTube unlisted archive) OR via a separate recording worker that captures the YouTube stream
- Initial approach: rely on YouTube's automatic archive, store the URL
- Future: a dedicated recording worker (FFmpeg-based) writing to S3

### Tasks
1. `recordings` table
2. Admin UI to attach a recording URL to a session
3. Participant UI to watch on-demand (paywalled via billing)

### Exit criteria
- Tenant can offer a recording after a session ends
- Access gated by session ownership / plan

---

## Phase 10 — Load Testing & Hardening (Week 13)

**Goal**: Prove we hit our scale targets under realistic load.

### Tasks
1. k6 script simulating **60,000 concurrent viewers** in one session
2. k6 script simulating **50 concurrent sessions across tenants**
3. Chaos testing: kill one Centrifugo node, one API pod, one Kafka broker — verify recovery
4. DB index review + slow query log analysis
5. Kafka partition count tuning
6. Redis memory tuning
7. Optimize image assets and CDN caching headers
8. Lighthouse CI green on all public pages

### Exit criteria
- 60k viewers per session → p95 chat delivery < 250ms, no errors
- Chaos test passes (graceful degradation, automatic recovery)
- All performance budgets in CLAUDE.md §18 met

---

## Phase 11 — Public Launch Prep (Week 14)

### Tasks
1. Marketing landing page (webizon.kz)
2. Pricing calculator
3. Terms of Service, Privacy Policy, DPA
4. GDPR export and delete endpoints
5. Support email + basic ticketing
6. Status page (statuspage.webizon.kz)
7. Incident runbooks for top 10 scenarios
8. On-call rotation setup
9. Customer onboarding documentation

### Exit criteria
- Public signup flow works for a brand-new user in under 5 minutes
- Legal pages published
- Support channel responsive

---

## Cross-Cutting Concerns (Ongoing Throughout All Phases)

These tasks are not in a specific phase — they happen continuously.

### Security
- Dependency scanning (OWASP) in CI from day one
- Penetration test before public launch
- Key rotation runbook
- Backup and restore drill (quarterly)

### Observability
- Metrics, logs, traces instrumented as features are built (not retrofitted)
- Dashboards added for every new module
- Alerts configured with runbook links

### Testing
- Unit + integration tests for every new feature
- E2E tests for every critical user flow
- Load test scenarios updated as new endpoints are added

### Documentation
- README updated with new modules
- ADRs written for every major decision
- OpenAPI spec regenerated on every release
- CLAUDE.md amended when rules change

---

## Definition of Done (Every Task)

A task is not done until:
1. Code is written and reviewed
2. Tests are written and passing (unit + integration where applicable)
3. Documentation is updated (code comments, README, OpenAPI)
4. Metrics and logs are instrumented
5. Error paths are handled (not "happy path only")
6. Multi-tenancy is respected (tenant_id filtering verified)
7. Security is considered (input validation, authorization)
8. Performance budget is respected
9. PR is merged to `main` via green pipeline
10. Deployed to staging and smoke-tested

---

## Risk Register

| Risk | Impact | Mitigation |
|------|--------|------------|
| Centrifugo learning curve slows early phases | Medium | Build a PoC in Phase 0, not Phase 3 |
| Tenant data leakage bug | **Critical** | RLS policies, integration tests, code review checklist |
| Billing calculation error | **Critical** | Reconciliation job, idempotency keys, extensive tests |
| Kafka outage loses chat persistence | High | DLQ, alerting, accept eventual durability tradeoff |
| Load test reveals surprise bottleneck late | High | Load test after each major phase, not only at the end |
| Vendor lock (CloudPayments) | Medium | Abstraction layer `PaymentProvider` interface |
| Scope creep | High | ADR required for any scope addition; strict phase gates |

---

## Decision Log

Decisions are tracked in `docs/adr/`. Key initial ADRs:
- **ADR-0001**: Stack selection (Java/Spring + Centrifugo + ClickHouse)
- **ADR-0002**: Multi-tenancy via shared schema + discriminator + RLS
- **ADR-0003**: Centrifugo channel naming convention
- **ADR-0004**: Billing is pay-per-use, metered via Kafka pipeline
- **ADR-0005**: Authentication via Keycloak self-hosted
- **ADR-0006**: Frontend framework: Nuxt 4 + Vue 3 + TypeScript

More ADRs will be added as decisions are made.
