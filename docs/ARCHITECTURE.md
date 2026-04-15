# Webizon — Жүйе Архитектурасы

---

## 📎 Байланысты документация

| Мен іздеп жатқан нәрсе | Қай файл |
|------------------------|----------|
| Документация индексі (жалпы карта) | [docs/README.md](./README.md) |
| Не бар, нені жасама — толық тізім | [WHAT_EXISTS.md](./WHAT_EXISTS.md) |
| Жобаны іске қосу, Docker, порттар | [ONBOARDING.md](./ONBOARDING.md) |
| Auth жүйесі — SecurityConfig, JWT, Keycloak | [AUTH.md](./AUTH.md) |
| Барлық REST API endpoint-тар | [API.md](./API.md) |
| Өзгерістер тарихы, bug fix-тер | [CHANGELOG.md](./CHANGELOG.md) |

---

## 1. Жалпы шолу

Webizon — **multi-tenant SaaS вебинар платформасы**. Бір жүйеде бірнеше tenant (клиент компания) жұмыс жасайды, олардың деректері PostgreSQL Row-Level Security (RLS) арқылы бір-бірінен оқшауланған.

```
Браузер
  │
  ├─► Nuxt 4 Frontend (port 3001)
  │     └─► Nitro SSR Server
  │           └─► Proxy: /api/backend/* → Backend:8080/api/*
  │
  ├─► Centrifugo WebSocket (port 8000)   ← Chat, CTA, presence
  │
  └─► Keycloak (port 8180)              ← Google OAuth redirect

Backend (port 8081)
  ├─► PostgreSQL  (port 5435)
  ├─► Redis       (port 6382)
  ├─► Kafka       (port 9093)
  ├─► ClickHouse  (port 8123)
  ├─► MinIO       (port 9010)
  ├─► Keycloak    (port 8180)
  └─► Centrifugo  (port 8000)
```

---

## 2. Технологиялар стегі

| Қабат | Технология | Нұсқа |
|-------|-----------|-------|
| Backend API | Java + Spring Boot | 21 / 3.2 |
| Фронтенд | Nuxt + Vue + TypeScript | 4 / 3 |
| Негізгі база | PostgreSQL | 16 |
| Analytics база | ClickHouse | 24.3 |
| Кэш / pub-sub | Redis | 7 |
| Хабарлама шиналары | Apache Kafka | 7.5 |
| WebSocket сервер | Centrifugo | 5 |
| Объект сақтау | MinIO (S3-compatible) | latest |
| Auth | Keycloak | 24 |
| Мониторинг | Prometheus + Grafana | latest |
| Стиль | Tailwind CSS | 3 |
| ORM | Hibernate 6 | — |
| DB миграция | Flyway | — |

---

## 3. Multi-tenancy архитектурасы

### Деректер оқшаулау стратегиясы

**PostgreSQL Row-Level Security (RLS)** — бір схема, бірақ әрбір жол `tenant_id` бойынша қорғалған.

```
HTTP Request
  → TenantContextFilter (JWT-тен tenant_id оқиды)
  → TenantContext.set(tenantId)
  → SET LOCAL app.current_tenant = '<uuid>'
  → PostgreSQL RLS policy: WHERE tenant_id = current_setting('app.current_tenant')::uuid
```

### Tenant ID таралу жолы

1. **JWT claim:** `tenant_id` — Keycloak protocol mapper орнатылса
2. **X-Tenant-Id header:** JWT claim жоқ болса fallback (dev режимде)
3. **TenantContext:** ThreadLocal — request бойы тіршілік етеді

### Аннотациялар

| Аннотация | Мағынасы |
|-----------|---------|
| `@AllowCrossTenant` | Tenant scope-сыз жұмыс жасайтын сервис/метод |
| `@TenantAwareEntity` | Hibernate-тің tenant_id автоматты қосатын entity |

---

## 4. Backend модульдері

```
com.webizon/
├── ai/           Claude API арқылы lead scoring
├── analytics/    Kafka → ClickHouse pipeline
├── auth/         CurrentUser (JWT claims helper)
├── autosession/  Auto-session replay + historical chat
├── billing/      Pay-per-use есептеу
├── chat/         Real-time chat + moderation + policies
├── common/       BaseEntity, GlobalExceptionHandler
├── config/       SecurityConfig, CORS
├── cta/          CTA engine
├── events/       Event + Session lifecycle
├── notifications/ Email outbox pattern
├── platform/     Super Admin endpoints
├── realtime/     Centrifugo client + token
├── room/         Room bootstrap + capabilities
├── storage/      MinIO upload/download flow
├── tenancy/      Auth, User, Tenant, Membership, Invite, RLS
└── timeline/     Timeline action CRUD + replay
```

---

## 5. Frontend құрылымы

```
frontend/
├── app/
│   ├── pages/
│   │   ├── auth/           sign-in, sign-up, callback
│   │   ├── admin/          Admin панелі (events, sessions, analytics...)
│   │   ├── e/[tenant]/[slug]/ Публикалық event беттер
│   │   └── platform/       Super Admin беттер
│   ├── layouts/
│   │   ├── admin.vue       Admin sidebar + header
│   │   ├── platform.vue    Platform admin (dark/light mode)
│   │   ├── event.vue       Event landing layout
│   │   └── default.vue     Жалпы layout
│   ├── components/
│   │   ├── ui/             Базалық UI компоненттер
│   │   ├── admin/          Admin-спецификалық компоненттер
│   │   └── room/           Room компоненттер (chat, CTA, player)
│   ├── stores/
│   │   ├── auth.ts         Pinia auth store
│   │   └── toast.ts        Toast notifications
│   ├── composables/
│   │   ├── useApi.ts       API client
│   │   └── useCentrifuge.ts WebSocket
│   ├── middleware/
│   │   ├── auth.ts         Protected route guard
│   │   └── platform-auth.ts Platform admin guard
│   └── plugins/
│       └── auth.client.ts  Hydrate + token refresh loop
├── shared/
│   └── api/
│       ├── client.ts       HTTP client
│       └── endpoints/      Typed API endpoint classes
└── nuxt.config.ts          Nuxt конфиг + Nitro proxy
```

### API routing

```
Браузер                    Nitro (SSR сервер)         Backend
GET /api/backend/v1/*  →   proxy →   http://backend:8080/api/v1/*
```

`nuxt.config.ts` routeRules:
```ts
'/api/backend/**': {
  proxy: 'http://backend:8080/api/**'
}
```

---

## 6. Real-time архитектурасы

### Centrifugo channels

Канал атауы үлгісі: `tenant.{tenantId}.session.{sessionId}.{kind}`

| Kind | Не жіберіледі |
|------|--------------|
| `chat` | Жаңа хабарлама (USER/ADMIN/SYSTEM/HISTORICAL) |
| `cta` | CTA_SHOW / CTA_HIDE |
| `presence` | Viewer count |
| `state` | Capabilities өзгерісі, private warning |

### Chat деректер жолы

```
ChatController.send()
  → Policy chain (Ban → Mute → SlowMode → Link → WordFilter → Flood)
  → PostgreSQL-ге сақтайды
  → CentrifugoClient.publish()
  → Барлық room клиенттеріне жетеді
```

**Ескерту:** Chat Kafka-дан өтпейді — тікелей DB + Centrifugo. Kafka тек analytics pipeline үшін.

### Chat пагинация

Cursor-based (keyset) — `(created_at, id)` composite index. 60K+ хабарлама кезінде де O(1) жылдамдық.

---

## 7. Analytics pipeline

```
Іс-әрекет (room enter, CTA click, watch milestone)
  → AnalyticsTrackingController
  → AnalyticsKafkaPublisher (partition key: sessionId)
  → Kafka topic: webizon.analytics.events
  → ClickHouse writer (batch insert)
  → AnalyticsReportService (query)
```

**Ескерту:** Kafka partition key = `sessionId` (tenant_id емес). Бір tenant-тың көп сессиясы болса hot partition болмайды.

---

## 8. Файл сақтау (MinIO)

### Two-step upload flow

```
1. POST /api/v1/storage/uploads
   → MinIO presigned PUT URL + assetId қайтарады

2. Клиент тікелей MinIO-ға PUT жасайды
   (Content-Type S3 V4 signature-ға кірген, дәл сол header жіберілуі керек)

3. POST /api/v1/storage/uploads/{assetId}/confirm
   → Backend object size тексеріп, UPLOADED status орнатады
```

### Buckets

| Bucket | Не үшін |
|--------|---------|
| `webizon-covers` | Event обложкалары |
| `webizon-cta-files` | CTA файлдары (PDF, checklist) |
| `webizon-recordings` | Запись видеолар |
| `webizon-invoices` | Шот-фактуралар |

---

## 9. Database миграция тарихы

| Нұсқа | Не |
|-------|----|
| V001 | Users, tenants, memberships, invites |
| V002 | Events, sessions |
| V003 | Chat, moderation |
| V004 | CTA, timeline |
| V005 | Analytics events |
| V006 | Auto-sessions, historical chat |
| V007 | File assets (MinIO) |
| V008 | Billing: usage records, invoices |
| V009 | Notification outbox |
| V010 | Session registrations |
| V011 | Session start notifications |
| V012 | Tenant invites (SHA-256 token) |
| V013 | Chat mode column (EVERYONE/ADMINS_ONLY/DISABLED) |
| V014 | Analytics event entity fix (append-only) |
| V015 | Chat cursor-based pagination index |
| V016 | Event landing config |
| V017 | Chat settings extra fields (allow_links, etc.) |
| V018 | Platform admin seed data |
| V019 | AI lead scores table |

**Келесі миграция: V020**

---

## 10. Мониторинг

- **Prometheus:** http://localhost:9090 — метрика жинаушы
- **Grafana:** http://localhost:3002 (admin/admin) — дашбордтар
- **Kafka UI:** http://localhost:8090 — Kafka topic тексеру
- **Backend health:** http://localhost:8081/actuator/health

Spring Boot Actuator endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`
