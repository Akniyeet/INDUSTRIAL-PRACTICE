# Webizon — Өзгерістер Тарихы (Changelog)

> Барлық маңызды архитектуралық өзгерістер, bug fix-тер және жаңа мүмкіндіктер осында жазылады.
> Формат: `[Күн] — Не өзгерді | Себебі | Файлдар`

---

## 📎 Байланысты документация

| Мен іздеп жатқан нәрсе | Қай файл |
|------------------------|----------|
| Документация индексі (жалпы карта) | [docs/README.md](./README.md) |
| Өзгерген файлдар қай жерде тұр | [WHAT_EXISTS.md](./WHAT_EXISTS.md) |
| Миграция нұсқалары (V001–V019, келесі V020) | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Auth-қа байланысты өзгерістер контексі | [AUTH.md](./AUTH.md) |
| Endpoint өзгерістері контексі | [API.md](./API.md) |

---

## 2026-04-18

### Public Event Landing — Broadcast-Universe Polish + End-to-End Landing Builder
**Не өзгерді:** `/e/{tenant}/{slug}` беті негізгі маркетинг лендингімен көрнекі тепе-теңдікке келді. Админдік "Лендинг Builder" (Step 5) енді шынымен жұмыс істейді — әуелі DB-ден UI-ға дейін барлық қабат қосылды.

**Себебі:**
- Қолданушы хабарлады: админдік Step 5-те енгізілген Benefits/Timeline блоктары публикалық бетте ешқашан шықпаған (бар болғаны DB колонкасы мен entity өрісі — DTO/service/form/page бәрі өткізбеген).
- Эфир брондау UX-і алдамшы болған: «Тіркелу» батырмасы басылған сайын есептегіш минус 1 болып кете беретін, пайдаланушы бір эфирді бірнеше рет брондайтындай әсер қалдыратын.
- Хардкод fake chat жазулары (сайт «тірі» көрінсін деп) шынайы эфир туралы ештеңе түсіндірмейтін.

**Лендинг өзгертулері (`frontend/app/pages/e/[tenant]/[slug]/index.vue`):**
- **Hero cover card** маркетинг лендингімен 1:1: LIVE badge, viewer counter, 3 orbit metric chip, CTA banner (`Тегін чек-листті алу → Жүктеу`), chat-like feed, LogoMark бар footer bar.
- **Rotating info feed** — айналмалы жазулар hardcode емес, `data.event` + `nextSession`-тан есептеледі: Басталуы, Ұзақтығы, Спикер, Орын саны, Тақырыбы, Форматы (LIVE/AUTO), Не туралы (description 1-абзацы). 2.5s сайын жаңарып, 3 жол көрініп тұрады.
- **«Эфирге орын алу» button + reservation**: «Тіркелу» → «Эфирге орын алу». `hasReserved` localStorage-қа (`webizon:reserved:{tenant}:{slug}`) жазылады, бір рет басылғанда ғана есептегіш `−1` болады; кейіннен батырма emerald «Орын сізге броньдалды ✓» болып қайта басылмайды.
- **«57 орын қалды» badge** енді button-мен егіз (`padding: 14px 22px; border-radius: 14px; font-size: 15px`).
- **Benefits grid** — админ Step 5 толтырған кезде 3-бағанды responsive grid, әр картада lucide icon (22 icon picker), hover lift, gradient glow. Step 5 бос болса — рендерленбейді.
- **Timeline** — numbered vertical list, violet-brand gradient spine, әр қадам карточкасы hover-да violet border-мен жанады.
- **Closing CTA** («Эфирге қосылуға дайынсыз ба?») hero-мен бірдей: таймер + 37 орын badge + reservation state.
- **Алынды:** «Жаңа эфир — жақын арада» fallback badge, «Тіркеліп, эфир басталғанда автоматты ескертпе…» абзацы, «Эфир туралы ақпарат» тақырыпша, fake chat pool.

**Landing Builder end-to-end wiring (V016 негізінде толықтыру):**
- `EventCreateRequest`, `EventUpdateRequest`, `EventResponse`, `PublicEventView` — `Map<String, Object> landingConfig` өрісі қосылды.
- `EventService.create` — null-safe `landingConfig` entity-ге жазады (JSONB `NOT NULL` болғандықтан `null` ешқашан берілмейді).
- `EventService.update` — PATCH семантикасы: `null` → «өзгертпе», empty map → «тазала».
- `frontend/shared/api/types.ts` — `LandingBenefit`, `LandingTimelineItem`, `LandingConfig` type-тар; `EventResponse`/`EventCreateRequest`/`PublicEventView` кеңейтілді.
- `frontend/app/components/admin/EventForm.vue` — `buildLandingConfig()` submit-те жібереді, edit режимінде `props.initial.landingConfig`-тан қайта жүктейді (`_iconOpen` сияқты UI-only өрістерді алып тастайды).

**Файлдар (backend):**
- `backend/.../events/api/dto/EventCreateRequest.java` — `Map<String, Object> landingConfig`
- `backend/.../events/api/dto/EventUpdateRequest.java` — `Map<String, Object> landingConfig`
- `backend/.../events/api/dto/EventResponse.java` — `landingConfig` + default `{}`
- `backend/.../events/api/dto/PublicEventView.java` — `landingConfig` + default `{}`
- `backend/.../events/service/EventService.java` — create/update null-safe mapping

**Файлдар (frontend):**
- `frontend/shared/api/types.ts` — 3 жаңа type + 2 interface кеңейту
- `frontend/app/components/admin/EventForm.vue` — Step 5 preload + submit
- `frontend/app/pages/e/[tenant]/[slug]/index.vue` — hero, info rotation, reservation, benefits, timeline, closing CTA

**Документация:** `docs/specs/public-landing.md` — толық сипаттама: data flow, JSON shape, admin form, public rendering, reservation flow, backend contract, file map.

---

## 2026-04-16

### Password Reset Flow — Email OTP
**Не өзгерді:** Пайдаланушылар тіркелген email арқылы паролін қалпына келтіре алады.
**Себебі:** "Забыли пароль?" мүмкіндігі жоқ болды. Тіркелген пайдаланушылар кіре алмай қалса тығырыққа тірелді.
**Архитектура:** Email-ге 6 цифрлық OTP жіберіледі (бөлек Redis key namespace — `otp:reset:*`). OTP дұрыс болса Keycloak Admin API арқылы пароль өзгертіледі, автоматты JWT жауаппен қайтарылады.
**Файлдар:**
- `backend/.../tenancy/api/dto/PasswordResetRequestDto.java` — **ЖАҢА** (`{ email }`)
- `backend/.../tenancy/api/dto/PasswordResetConfirmDto.java` — **ЖАҢА** (`{ email, code, newPassword }`)
- `backend/.../tenancy/service/KeycloakAuthService.java` — `resetPassword(email, newPassword)` + `getUserIdByEmail()` Admin API
- `backend/.../tenancy/service/OtpService.java` — `generateAndSendPasswordReset()` + `verifyPasswordResetOtp()` — бөлек namespace
- `backend/.../tenancy/api/AuthPublicController.java` — `POST /api/v1/public/auth/password-reset/request` + `/confirm`
- `frontend/app/pages/auth/forgot-password.vue` — **ЖАҢА** — 4 step wizard: email → OTP → жаңа пароль → done
- `frontend/app/pages/auth/sign-in.vue` — "Забыли пароль?" сілтемесі қосылды (пароль жолы жанына)
- `frontend/app/pages/auth/sign-up.vue` — 409 Conflict болса "уже зарегистрирован → войти / восстановить" хабар

**Endpoint-тар:**
```
POST /api/v1/public/auth/password-reset/request   { email }              → 200 (email enumeration жоқ)
POST /api/v1/public/auth/password-reset/confirm   { email, code, newPassword } → TokenResponse
```

---

## 2026-04-15

### Worktree Cleanup + Docker Healthcheck Fixes
**Не өзгерді:** Басқа агент worktree-сінен іске қосылған контейнерлер тоқтатылды, артық branch-тар жойылды, healthcheck-тер түзетілді.
**Себебі:** `claude/vigorous-mcclintock` агенті `E:\PROJECT\webizon\.claude\worktrees\vigorous-mcclintock\` директориясынан Docker контейнерлерін іске қосқан. Ол контейнерлер ескі код нұсқасынан (5995567 commit) жасалған, порттары да дұрыс емес (3000/8080 — бізде 3001/8081).
**Шешімі:**
1. `claude/vigorous-mcclintock` worktree-сінен `docker compose down` жасалды
2. `claude/vigorous-mcclintock`, `claude/admiring-heyrovsky`, `claude/lucid-ride` branch-тары жойылды
3. `docker-compose.yml` zookeeper healthcheck түзетілді: `bash /dev/tcp` → `nc -z 127.0.0.1 2181`
4. `README.md`-ке дұрыс іске қосу нұсқаулары жазылды (порттар, worktree мәселесі)
**Файлдар:**
- `docker-compose.yml` — zookeeper healthcheck fix
- `README.md` — іске қосу нұсқаулары жаңартылды

---

### ⚠️ ЕРЕЖЕ: Басқа агентпен жұмыс
**Маңызды:** Жобада тек БІР агент жұмыс жасауы керек — осы сессиядағы агент.
- Параллель агент іске қоспа (worktree жасалады, ескі код пайда болады)
- Агент бірнәрсе жазған соң `rtk git commit` + `rtk git push` жасалуы керек
- Basқа агенттің жазғанын өзгертпе — тек `feat/full-platform-upgrade` branch-та жұмыс жас

---

### Documentation Cross-References
**Не өзгерді:** Барлық docs файлдары бір-бірімен байланыстырылды.
**Себебі:** Агенттер мен жаңа мамандар бір файлдан екіншісіне оңай өте алсын деп.
**Файлдар:**
- `docs/README.md` — **ЖАҢА** — орталық навигация индексі, барлық файлдың сілтемесі мен сипаттамасы
- `docs/WHAT_EXISTS.md` — 📎 Байланысты документация блогы + соңына навигация қосылды
- `docs/ARCHITECTURE.md` — 📎 Байланысты документация блогы қосылды
- `docs/AUTH.md` — 📎 Байланысты документация блогы қосылды
- `docs/API.md` — 📎 Байланысты документация блогы қосылды
- `docs/ONBOARDING.md` — 📎 Байланысты документация блогы қосылды
- `docs/CHANGELOG.md` — 📎 Байланысты документация блогы қосылды
- `README.md` (root) — docs секциясы нақты файлдармен жаңартылды, WHAT_EXISTS.md айрықша белгіленді

---

### Platform Admin — Light/Dark Mode Toggle
**Не өзгерді:** Platform Super Admin беттері тек dark mode болды, енді light/dark toggle қосылды.
**Себебі:** UX жақсарту — пайдаланушы таңдауы болуы керек.
**Файлдар:**
- `frontend/tailwind.config.ts` — `darkMode: 'class'` қосылды
- `frontend/app/composables/usePlatformTheme.ts` — **ЖАҢА** — `isDark` ref, localStorage persistence (`webizon:platform-theme`), `toggle()`, `hydrate()`
- `frontend/app/layouts/platform.vue` — Sun/Moon toggle кнопка, dual-mode classes
- `frontend/app/pages/platform/*.vue` (5 файл) — `text-white` → `text-slate-900 dark:text-white`, `bg-slate-900` → `bg-white dark:bg-slate-900`, т.б.
- Residual dark-only colors fixed: `text-slate-300` → `text-slate-600 dark:text-slate-300`, `hover:bg-slate-700` → `hover:bg-slate-200 dark:hover:bg-slate-700`, avatar bg, status badge

---

### Admin Sidebar — "Новое мероприятие" кнопкасын алу
**Не өзгерді:** Admin sidebar-дан "Новое мероприятие" жылдам кнопкасы алынды.
**Себебі:** Events беттерінде арнайы create форма бар, sidebar-дан артық.
**Файлдар:**
- `frontend/app/layouts/admin.vue`

---

### Storage Upload 500 Fix
**Не өзгерді:** Cover/файл жүктегенде 500 Internal Server Error шығып тұрды.
**Себебі:** `CurrentUser.profileId()` JWT-те `profile_id` claim жоқ болса exception лақтырды.
**Шешімі:**
1. `StorageController` → `userService.requireByKeycloakId(CurrentUser.keycloakId())` пайдаланады
2. `CurrentUser.profileId()` → `profile_id` жоқ болса `keycloakId()` қайтаратын fallback
**Файлдар:**
- `backend/.../storage/api/StorageController.java`
- `backend/.../auth/CurrentUser.java`

---

### Event Creation 500 Fix — RoleEnrichmentFilter
**Не өзгерді:** Event жасағанда 500 шықты — `@PreAuthorize("hasAnyRole('TENANT_OWNER',...)")` жұмыс жасамады.
**Себебі:** Keycloak JWT-те tenant-specific рольдер жоқ (protocol mapper орнатылмаған).
**Шешімі:** `RoleEnrichmentFilter.java` жасалды — request кезінде DB-дан `tenant_users` оқып SecurityContext-ке `ROLE_TENANT_*` рольдерін қосады.
**Файлдар:**
- `backend/.../tenancy/RoleEnrichmentFilter.java` — **ЖАҢА**

---

### EventForm Wizard — Step regression fix
**Не өзгерді:** Event жасау wizard-ында кез келген қате болса Step 1-ге қайтып кетті.
**Себебі:** catch блогы қателерді ажыратпады — field validation да, server error да Step 1-ге redirect жасады.
**Шешімі:** `apiErr.errors` (field errors) болса ғана Step 1-ге, server error болса toast ғана шығады.
**Файлдар:**
- `frontend/app/components/admin/EventForm.vue`

---

### TenantContextFilter — X-Tenant-Id header fix
**Не өзгерді:** `X-Tenant-Id` header жіберілсе де TenantContext орнатылмады.
**Себебі:** Header тексерісі `if (auth instanceof JwtAuthenticationToken)` блогының ішінде болды. Filter `BearerTokenAuthenticationFilter`-ден бұрын іске қосылғандықтан JWT әлі auth болмаған.
**Шешімі:** Header тексерісі JWT блогынан тыс шығарылды — header болса JWT-сіз де орнатылады.
**Файлдар:**
- `backend/.../tenancy/TenantContextFilter.java`

---

### PublicEventService — SET LOCAL syntax fix
**Не өзгерді:** Event landing беті 500 қайтарды.
**Себебі:** `SET LOCAL app.current_tenant = :tid` — PostgreSQL bind parameter синтаксисін `SET LOCAL` командасында қабылдамайды.
**Шешімі:** UUID тікелей жолға кірістірілді: `"SET LOCAL app.current_tenant = '" + tenantId + "'"` (UUID validated, SQL injection қаупі жоқ).
**Файлдар:**
- `backend/.../events/service/PublicEventService.java`
- `backend/.../tenancy/service/TenantService.java`
- `backend/.../tenancy/service/InviteService.java`

---

### Frontend — useRuntimeConfig inside useHead() fix
**Не өзгерді:** Event landing беті SSR кезінде 500 қайтарды: `[nuxt] instance unavailable`.
**Себебі:** `useRuntimeConfig()` `useHead(() => ...)` callback ішінде шақырылды. SSR кезінде Nuxt instance қолжетімді емес.
**Шешімі:** `const config = useRuntimeConfig()` `setup()` деңгейінде шақырылды.
**Файлдар:**
- `frontend/app/pages/e/[tenant]/[slug]/index.vue`

---

### Frontend Docker — NUXT_PUBLIC_API_BASE wrong path fix
**Не өзгерді:** Frontend беттері `C:/Program Files/Git/api/backend/...` деген URL пайдаланды.
**Себебі:** `docker run -e NUXT_PUBLIC_API_BASE=/api/backend` командасы Git Bash-те іске қосылды. Git Bash Unix slash-ті Windows path-ке айналдырды.
**Шешімі:** `docker compose up -d frontend` пайдаланылды — compose .env файлынан дұрыс мән алады.
**Ескерту:** Ешқашан Git Bash-те `/` басталатын path-ті docker run env var ретінде берме.

---

### Test Webinar Creation via API
**Не жасалды:** Программалық түрде test event + session жасалды.
- Event slug: `besplatny-urok-konversiya`
- Tenant: `webizon-365`
- Session: LIVE статуста (YouTube: dQw4w9WgXcQ)
- URL: `http://localhost:3001/e/webizon-365/besplatny-urok-konversiya`

---

## 2026-04-14

### Grafana + Prometheus Мониторинг
**Не өзгерді:** Мониторинг сервистері қосылды.
**Файлдар:**
- `docker-compose.yml` — prometheus, grafana сервистері
- `infra/grafana/provisioning/` — dashboard, datasource конфиг
- `infra/prometheus/prometheus.yml` — scrape config

---

### Infrastructure Restart Button
**Не өзгерді:** Admin → Infrastructure бетіне Docker сервистерін қайта іске қосу батырмасы қосылды.
**Файлдар:**
- `frontend/app/pages/admin/infrastructure.vue`
- `backend/.../config/InfrastructureHealthController.java`

---

### AI Lead Scoring (Claude API)
**Не өзгерді:** Session analytics бетіне AI-негізделген lead scoring қосылды. Claude claude-sonnet-4-6-20250514 арқылы session деректерін талдайды.
**Файлдар:**
- `backend/.../ai/` — AnthropicClient, AiLeadScoringService, AiLeadScoreController
- `backend/src/main/resources/db/migration/V019__ai_lead_scores.sql`
- `frontend/app/components/admin/AiLeadScorePanel.vue`
- `frontend/shared/api/endpoints/aiLeadScores.ts`

---

### Keycloak — webizon365@gmail.com пароль reset
**Не өзгерді:** webizon365@gmail.com Keycloak паролі `Test1234!` деп орнатылды.
**Себебі:** Programmatic API тестілеу үшін белгілі пароль қажет болды.

---

## 2026-04-12 (Жоба негізі)

### V001-V018 Flyway миграциялары
Негізгі деректер базасы схемасы жасалды:
- Multi-tenant users, tenants, memberships
- Events + sessions lifecycle
- Chat + moderation + policies
- CTA + timeline engine
- Analytics event tracking
- Billing usage + invoices
- Notification outbox
- Session registrations
- Platform admin seed

### Keycloak Realm Import
**Файл:** `infra/keycloak/import/webizon-realm.json`
- `webizon` realm
- `webizon-frontend` client (confidential, Direct Grant enabled)
- Google IDP (`trustEmail: true`)
- Protocol mappers

### SecurityConfig — dual issuer JWT decoder
**Себебі:** Docker ішінде Direct Grant токендері `http://keycloak:8180` issuer-ін алады. Google OAuth токендері `http://localhost:8180` алады (браузер hostname). Екеуін де қабылдау керек.
**Файл:** `backend/.../config/SecurityConfig.java`

---

## Ереже: Документацияны жаңарту

**Кез келген маңызды өзгерістен кейін `docs/CHANGELOG.md`-ке жазу керек:**
- Жаңа feature
- Bug fix (себебі + шешімі)
- Архитектура өзгерісі
- Config өзгерісі
- Database migration

Формат:
```
### [Тақырып]
**Не өзгерді:** ...
**Себебі:** ...
**Шешімі:** (bug fix болса)
**Файлдар:** ...
```
