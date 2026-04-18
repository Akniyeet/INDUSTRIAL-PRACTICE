# Webizon — Қазіргі Жаған Нәрсе (WHAT EXISTS)

> **БАРЛЫҚ АГЕНТ ПЕН МАМАН АЛДЫМЕН ОСЫ ФАЙЛДЫ ОҚУ КЕРЕК.**
>
> Мұнда жазылған барлығы **ЖАСАЛЫП ҚОЙҒАН**. Қайта жазба. Қайта жасама.
> Өзгерту керек болса — бар файлды редактирле, жаңасын жасама.

---

## 📎 Байланысты документация

| Мен іздеп жатқан нәрсе | Қай файл |
|------------------------|----------|
| Документация индексі (жалпы карта) | [docs/README.md](./README.md) |
| Жобаны іске қосу, Docker, URL-дер | [ONBOARDING.md](./ONBOARDING.md) |
| Жүйе архитектурасы, модульдер, DB | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Auth flow — login, Google, OTP | [AUTH.md](./AUTH.md) |
| Барлық API endpoint-тар | [API.md](./API.md) |
| Соңғы өзгерістер тарихы | [CHANGELOG.md](./CHANGELOG.md) |

---

## ⚠️ ЕШҚАШАН ҚАЙТА ЖАСАМАЙТЫН НӘРСЕЛЕР

| Не | Неге |
|----|------|
| `app/pages/auth/sign-in.vue` | Авторизация беті БАР, қара фонда, glassmorphism |
| `app/pages/auth/sign-up.vue` | Тіркелу беті БАР, OTP-мен |
| `app/pages/auth/callback.vue` | Google OAuth callback БАР |
| `app/stores/auth.ts` | Auth store БАР — token, user, login, logout, refresh |
| `app/plugins/auth.client.ts` | Auth hydrate + refresh loop БАР |
| Auth flow (Direct Grant) | Keycloak SSO redirect ЕМЕС — Custom form + Direct Grant. Өзгертпе! |
| `app/middleware/auth.ts` | Route guard БАР |
| `app/middleware/platform-auth.ts` | Platform guard БАР |
| `usePlatformTheme.ts` | Dark/Light mode composable БАР |
| `useCentrifuge.ts` | WebSocket composable БАР |
| `useApi.ts` | API client composable БАР |
| `auth.ts` store | Pinia auth БАР — жаңа auth library орнатпа |

---

## FRONTEND — Беттер (Pages)

### Auth беттері — `app/pages/auth/`
| Файл | Не | Маңызды ескерту |
|------|----|-----------------|
| `sign-in.vue` | Email/пароль кіру | `layout: false`, `bg-slate-950`, glassmorphism. SSO redirect жоқ |
| `sign-up.vue` | Тіркелу + OTP верификация | `layout: false`, сол стиль |
| `callback.vue` | Google OAuth handler | sessionStorage-дан pkce_verifier оқиды |

### Admin беттері — `app/pages/admin/`
| Файл | Не |
|------|----|
| `index.vue` | Dashboard (live stats, upcoming sessions, quick links) |
| `events/index.vue` | Event тізімі (search, filter, pagination) |
| `events/create.vue` | 3-степті wizard форма |
| `events/[id]/index.vue` | Event detail (5 tab: overview, sessions, CTA, timeline, chat settings) |
| `events/[id]/edit.vue` | Event редактирлеу |
| `sessions/index.vue` | Барлық session overview (live/upcoming/ended buckets) |
| `sessions/[id]/index.vue` | Session detail + lifecycle actions |
| `sessions/[id]/live.vue` | Live broadcast control (moderation, CTA, admin messages) |
| `sessions/[id]/analytics.vue` | Retention chart, CTA CTR, AI lead scoring |
| `sessions/[id]/timeline.vue` | Timeline action CRUD editor |
| `sessions/[id]/chat-review.vue` | Historical chat review + replay exclusion |
| `members.vue` | Team management + invite жіберу |
| `settings.vue` | Tenant info (read-only) |
| `infrastructure.vue` | Docker сервис статустары + restart |
| `wallet.vue` | Billing wallet |

### Публикалық event беттері — `app/pages/e/[tenant]/[slug]/`
| Файл | Не |
|------|----|
| `index.vue` | Event landing (state: LIVE_NOW/WAITING/SLOT_SELECTION/LANDING/UNAVAILABLE) |
| `room.vue` | Viewer room (YouTube + chat + CTA) |
| `waiting.vue` | Waiting room + countdown |

### Platform Super Admin — `app/pages/platform/`
| Файл | Не |
|------|----|
| `index.vue` | Super admin dashboard |
| `tenants/index.vue` | Барлық tenant тізімі |
| `tenants/[id].vue` | Tenant detail |
| `users.vue` | Барлық пайдаланушылар |
| `revenue.vue` | Табыс есебі |

### Басқа беттер
| Файл | Не |
|------|----|
| `index.vue` | Marketing landing (/) |
| `invites/accept/[token].vue` | Invite қабылдау |

---

## FRONTEND — Компоненттер (Components)

### UI компоненттер — `app/components/ui/`
| Компонент | Не | Пайдалану |
|-----------|----|---------  |
| `LogoFull.vue` | Толық логотип (text + mark) | `<LogoFull :size="44" dark />` |
| `LogoMark.vue` | Тек белгі | `<LogoMark :size="32" />` |
| `LogoLoader.vue` | Loading spinner + мәтін | `<LogoLoader :size="72" text="Жүктелуде..." />` |
| `UiButton.vue` | Кнопка | `<UiButton variant="primary" size="md">` |
| `UiBadge.vue` | Badge/chip | `<UiBadge color="green">` |
| `UiCard.vue` | Карточка контейнер | `<UiCard>` |
| `UiInput.vue` | Text input | `<UiInput v-model="val" label="Email">` |
| `UiTextarea.vue` | Textarea | `<UiTextarea v-model="text">` |
| `UiSelect.vue` | Select dropdown | `<UiSelect v-model="val" :options="[]">` |
| `UiModal.vue` | Modal dialog | `<UiModal v-model="open">` |
| `UiEmpty.vue` | Empty state | `<UiEmpty title="Жоқ" description="...">` |
| `UiSkeleton.vue` | Loading skeleton | `<UiSkeleton class="h-4 w-32">` |
| `UiSpinner.vue` | Loading spinner | `<UiSpinner size="md">` |
| `UiCountdown.vue` | Countdown timer | `<UiCountdown :target-date="date">` |
| `UiToastContainer.vue` | Toast notifications | Layout-та орналасқан |

### Admin компоненттер — `app/components/admin/`
| Компонент | Не |
|-----------|-----|
| `EventForm.vue` | 3-степті event жасау/редактирлеу wizard (Step1: info, Step2: appearance, Step3: settings) |
| `SessionForm.vue` | Session жасау формасы |
| `EventStatusBadge.vue` | DRAFT/PUBLISHED/ARCHIVED badge |
| `SessionStatusBadge.vue` | SCHEDULED/LIVE/ENDED/CANCELLED badge |
| `PageHeader.vue` | Admin бет header (title + actions slot) |
| `SessionsPanel.vue` | Session тізімі панелі |
| `CtaPanel.vue` | CTA басқару панелі |
| `ChatSettingsPanel.vue` | Chat режимі + баптаулар |
| `AiLeadScorePanel.vue` | AI lead scoring панелі (Claude API) |

### Admin analytics — `app/components/admin/analytics/`
| Компонент | Не |
|-----------|-----|
| `RetentionChart.vue` | Viewer retention графигі |
| `CtaCtrTable.vue` | CTA click-through rate кестесі |
| `SummaryStrip.vue` | Session summary KPI |

### Admin live — `app/components/admin/live/`
| Компонент | Не |
|-----------|-----|
| `LiveCtaControl.vue` | Live кезінде CTA show/hide |
| `LiveModerationLog.vue` | Moderation action log |
| `LiveModeratorChat.vue` | Admin chat + moderation |

### Room компоненттер — `app/components/room/`
| Компонент | Не |
|-----------|-----|
| `RoomChat.vue` | Real-time chat (reply, slow mode, moderation, ADMINS_ONLY режимі) |
| `RoomCtaList.vue` | CTA display (inline/popup/sidebar/below_video) |
| `YoutubePlayer.vue` | YouTube embed player |

### Event компоненттер — `app/components/event/`
| Компонент | Не |
|-----------|-----|
| `EventHero.vue` | Event landing hero блогы |

---

## FRONTEND — Layouts

| Layout | Не | Қай беттер |
|--------|----|-----------|
| `admin.vue` | Sidebar + header + toast | `admin/*` беттері |
| `platform.vue` | Platform header + dark/light toggle | `platform/*` беттері |
| `event.vue` | Event landing layout | `e/[tenant]/[slug]/*` |
| `default.vue` | Жалпы layout | `/`, marketing беттері |

---

## FRONTEND — Composables, Stores, Plugins

| Файл | Не |
|------|----|
| `composables/useApi.ts` | API client — `const api = useApi()` → `api.events.list()`, т.б. |
| `composables/useCentrifuge.ts` | Centrifugo WebSocket — channel subscribe |
| `composables/usePlatformTheme.ts` | Platform dark/light mode — `isDark`, `toggle()` |
| `composables/useAdminHeader.ts` | Admin header state |
| `stores/auth.ts` | Auth: token, user, login, logout, refresh, hydrate |
| `stores/toast.ts` | Toast notifications: success, error, info |
| `plugins/auth.client.ts` | Boot-time hydrate + 60s refresh loop |
| `middleware/auth.ts` | `definePageMeta({ middleware: 'auth' })` |
| `middleware/platform-auth.ts` | `definePageMeta({ middleware: 'platform-auth' })` |

---

## FRONTEND — API Endpoint Classes

**Барлығы `shared/api/endpoints/` ішінде. `useApi()` composable арқылы шақырылады:**

```ts
const api = useApi()
api.events.list()
api.sessions.create(eventId, data)
api.chat.send(sessionId, data)
// т.б.
```

| Файл | Class | Не |
|------|-------|----|
| `events.ts` | EventsApi | Event CRUD |
| `sessions.ts` | SessionsApi | Session CRUD + lifecycle |
| `chat.ts` | ChatApi | Хабарлама жіберу/алу |
| `cta.ts` | CtaApi | CTA CRUD + show/hide |
| `timeline.ts` | TimelineApi | Timeline action CRUD |
| `room.ts` | RoomApi | Room bootstrap |
| `analytics.ts` | AnalyticsApi | Tracking + reports |
| `storage.ts` | StorageApi | File upload (presigned URL) |
| `publicEvents.ts` | PublicEventsApi | Public event resolve |
| `auth.ts` | AuthApi | Login/register/refresh |
| `platform.ts` | PlatformApi | Super admin endpoints |
| `tenants.ts` | TenantsApi | Tenant info |
| `invites.ts` | InvitesApi | Invite жіберу/қабылдау |
| `moderation.ts` | ModerationApi | Warn/mute/ban |
| `realtime.ts` | RealtimeApi | Centrifugo tokens |
| `infrastructure.ts` | InfrastructureApi | Health + restart |
| `historicalChat.ts` | HistoricalChatApi | Historical chat review |
| `aiLeadScores.ts` | AiLeadScoresApi | AI lead scoring |

---

## BACKEND — Controllers (Endpoint-тар)

| Controller | Path | Не |
|------------|------|----|
| `AuthPublicController` | `/api/v1/public/auth/*` | login, register, callback, refresh, OTP |
| `AuthBootstrapController` | `/api/v1/auth/bootstrap` | User upsert + memberships |
| `EventController` | `/api/v1/events` | Event CRUD + publish/archive |
| `PublicEventController` | `/api/v1/public/tenants/{slug}/events/{slug}/resolve` | Public event resolution |
| `SessionController` | `/api/v1/events/{id}/sessions`, `/api/v1/sessions/{id}/*` | Session CRUD + lifecycle |
| `SessionRegistrationController` | `/api/v1/sessions/{id}/register` | Session registration |
| `ChatController` | `/api/v1/sessions/{id}/chat` | Chat CRUD (cursor pagination) |
| `ChatSettingsController` | `/api/v1/events/{id}/chat-settings` | Chat mode + settings |
| `ModerationController` | `/api/v1/sessions/{id}/moderation` | warn, mute, ban |
| `CtaController` | `/api/v1/events/{id}/cta` | CTA CRUD |
| `SessionCtaController` | `/api/v1/sessions/{id}/cta/{ctaId}` | CTA show/hide |
| `TimelineController` | `/api/v1/events/{id}/timeline` | Timeline CRUD |
| `RoomController` | `/api/v1/room/sessions/{id}/bootstrap` | Room bootstrap |
| `StorageController` | `/api/v1/storage` | MinIO upload/download |
| `AnalyticsTrackingController` | `/api/v1/analytics/track` | Event tracking |
| `AnalyticsReportController` | `/api/v1/analytics/sessions/{id}/report` | Reports |
| `AttendanceController` | `/api/v1/analytics/sessions/{id}/attendance` | Attendance |
| `AutoSessionController` | `/api/v1/sessions/{id}/auto` | Auto-session create |
| `HistoricalChatController` | `/api/v1/sessions/{id}/historical-chat` | Historical chat |
| `RealtimeController` | `/api/v1/realtime` | Centrifugo tokens |
| `TenantController` | `/api/v1/tenants` | Tenant info |
| `MembershipController` | `/api/v1/memberships` | Team members |
| `InviteController` | `/api/v1/invites` | Invite жіберу |
| `UserSearchController` | `/api/v1/users/search` | User іздеу |
| `PlatformController` | `/api/v1/platform` | Super admin |
| `InfrastructureHealthController` | `/api/v1/admin/infrastructure` | Health + restart |
| `BillingController` | `/api/v1/billing` | Billing/invoices |
| `AiLeadScoreController` | `/api/v1/ai/sessions/{id}/lead-score` | AI scoring |
| `NotificationController` | `/api/v1/notifications` | Notifications |
| `SessionExportController` | `/api/v1/analytics/sessions/{id}/export` | CSV export |

---

## BACKEND — Services

| Service | Не |
|---------|----|
| `KeycloakAuthService` | Keycloak Direct Grant, code exchange, refresh, user create |
| `OtpService` | Redis OTP + Gmail SMTP |
| `UserService` | bootstrapFromJwt(), requireByKeycloakId() |
| `TenantService` | Tenant create (SET LOCAL арқылы pivot) |
| `MembershipService` | Tenant member management |
| `InviteService` | Invite token generate + accept |
| `EventService` | Event CRUD + status transitions |
| `SessionService` | Session CRUD + lifecycle state machine |
| `PublicEventService` | Public event resolution (SET LOCAL pivot) |
| `ChatService` | Policy chain → DB → Centrifugo |
| `ChatSettingsService` | Chat mode + settings |
| `ModerationService` | warn, mute, ban, delete |
| `CtaService` | CTA CRUD |
| `CtaBroadcaster` | CTA_SHOW/HIDE → Centrifugo |
| `TimelineService` | Timeline CRUD |
| `TimelineReplayEngine` | 1s tick, offset-based replay |
| `RoomService` | Room bootstrap + capabilities |
| `StorageService` | MinIO presigned URL + confirm |
| `AnalyticsRecorder` | Event tracking → Kafka |
| `AttendanceTracker` | Room enter/leave tracking |
| `LeadSignalEvaluator` | Lead scoring signals |
| `AiLeadScoringService` | Claude API integration |
| `AutoSessionService` | Auto-session create from live |
| `HistoricalChatCurationService` | Chat replay review |
| `CentrifugoClient` | HTTP publish to Centrifugo |
| `UsageMeteringService` | Billing usage tracking |
| `InvoiceService` | Invoice generate |
| `NotificationService` | Email outbox |

---

## BACKEND — Filters (filter chain)

| Filter | Order | Не |
|--------|-------|----|
| `TenantContextFilter` | BearerTokenAuthFilter кейін | JWT / X-Tenant-Id header-дан tenant_id |
| `RoleEnrichmentFilter` | @Order(20) | DB-дан tenant roles → SecurityContext |

---

## BACKEND — Database (PostgreSQL tables)

| Кесте | Migration | Не |
|-------|----------|----|
| `users` | V001 | Пайдаланушылар (keycloak_id, email, platform_admin) |
| `tenants` | V001 | Tenant компаниялар (slug, display_name, status) |
| `tenant_users` | V001 | Membership (user_id, tenant_id, role, status) |
| `tenant_invites` | V012 | Invite токендер (SHA-256) |
| `plans` | V001 | Subscription тарифтер |
| `subscriptions` | V001 | Tenant subscription |
| `events` | V002 | Event (slug, title, status: DRAFT/PUBLISHED/ARCHIVED) |
| `sessions` | V002 | Session (type: LIVE/AUTO, status: SCHEDULED/LIVE/ENDED...) |
| `session_registrations` | V010 | Қатысу тіркелу |
| `chat_messages` | V003 | Хабарламалар (offset_seconds, is_deleted, reply_to_id) |
| `event_chat_settings` | V003/V013/V017 | Chat settings (chatMode: EVERYONE/ADMINS_ONLY/DISABLED) |
| `chat_user_statuses` | V003 | User mute/ban status |
| `moderation_actions` | V003 | Moderation audit trail |
| `event_ctas` | V004 | CTA (type, placement, priority) |
| `event_timeline_actions` | V004 | Timeline (offset_seconds, action_type, payload_json) |
| `analytics_events` | V005 | Behavioral events (append-only) |
| `session_attendances` | V005 | Attendance records |
| `lead_signals` | V005 | CRM lead signals |
| `auto_sessions` | V006 | Auto-session metadata |
| `file_assets` | V007 | MinIO file metadata |
| `billing_usage_records` | V008 | Usage metering |
| `billing_invoices` | V008 | Invoices |
| `billing_invoice_lines` | V008 | Invoice line items |
| `notification_outbox` | V009 | Email outbox (outbox pattern) |
| `ai_lead_scores` | V019 | AI scoring results |

**Келесі миграция нұсқасы: V020**

---

## INFRA — Config файлдары

| Файл | Не |
|------|----|
| `infra/keycloak/import/webizon-realm.json` | Keycloak realm (client, IDP, mappers) |
| `infra/centrifugo/config.json` | Centrifugo channels, namespaces, tokens |
| `infra/clickhouse/config/listen.xml` | `0.0.0.0` listen (WSL2 fix) |
| `infra/clickhouse/init/` | ClickHouse schema init |
| `infra/grafana/provisioning/` | Dashboard + datasource |
| `infra/prometheus/prometheus.yml` | Scrape config |
| `infra/postgres/init/` | PostgreSQL init |

---

## Docker Compose — Сервистер және порттар

| Сервис | Host порт | Не |
|--------|-----------|----|
| `webizon-postgres` | **5435**:5432 | PostgreSQL 16 |
| `webizon-redis` | **6382**:6379 | Redis 7 |
| `webizon-kafka` | **9093**:9092 | Kafka |
| `webizon-zookeeper` | 2181 | Kafka тәуелділік |
| `webizon-clickhouse` | **8123**, **19000** | ClickHouse |
| `webizon-minio` | **9010**, **9011** | MinIO (API, Console) |
| `webizon-keycloak` | **8180** | Keycloak |
| `webizon-centrifugo` | **8000** | WebSocket |
| `webizon-backend` | **8081**:8080 | Spring Boot |
| `webizon-frontend` | **3001**:3000 | Nuxt 4 |
| `webizon-kafka-ui` | **8090** | Kafka UI |
| `webizon-grafana` | **3002** | Grafana |
| `webizon-prometheus` | **9090** | Prometheus |

---

## Маңызды ережелер (Жаңа агентке)

### ❌ ЕШҚАШАН ЖАСАМА

1. **Auth бетін қайта жазба** — sign-in, sign-up БАР, жұмыс жасайды
2. **Keycloak SSO redirect қосуға тырыспа** — Direct Grant + custom form — саналы шешім
3. **Жаңа auth library орнатпа** — nuxt-oidc-auth, @sidebase/nuxt-auth т.б. керек емес
4. **Бар компонентті жаңасымен ауыстырма** — алдымен бар компонентті тауып, жаңарт
5. **Flyway миграция нұсқасын қайталама** — қазіргі соңғысы V019, келесісі V020
6. **`docker run -e` пайдаланба** — тек `docker compose up -d`
7. **`/api/backend` путіне тікелей кірме** — браузер тек Nitro proxy арқылы жетеді
8. **`useRuntimeConfig()` → `useHead()` ішінде шақырма** — setup() деңгейінде шақыр
9. **`SET LOCAL` → bind parameter пайдаланба** — `"SET LOCAL app.current_tenant = '" + uuid + "'"` деп жаз

### ✅ МІНДЕТТІ ЖАСА

1. Кез келген өзгерістен кейін [`docs/CHANGELOG.md`](./CHANGELOG.md)-ке жаз
2. Жаңа endpoint жасасаң [`docs/API.md`](./API.md)-ті жаңарт
3. Жаңа Flyway migration жасасаң [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md)-тегі нұсқа санын жаңарт
4. Auth flow өзгертсең [`docs/AUTH.md`](./AUTH.md)-ті жаңарт
5. Бұл файлды жаңарт — жаңа файл/кесте/компонент қосылса осында жаз

---

## 🗺️ Толық документация картасы

→ **[docs/README.md](./README.md)** — барлық документацияның индексі мен навигациясы
