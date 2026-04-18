# Webizon — AGENT HANDOFF

> **Бұл файл жаңа AI агентке арналған.** Жобаға алғаш кірсең — осы файлды бірінші оқы. Жоба толық картасы, нені қайдан табу керек, соңғы жұмыс қай жерде тоқтады, қандай шұғыл ережелер бар — барлығы осында.

**Соңғы жаңартылды:** 2026-04-18
**Соңғы commit:** `09e2f5c` (docs: Google OAuth runbook)
**Branch:** `master` (origin: `https://gitlab.com/webizon365/webizon.git`)

---

## 🚦 5 минуттық орнату (Mac/жаңа Windows/CI)

```bash
git clone https://gitlab.com/webizon365/webizon.git
cd webizon
cp .env.example .env

# .env-ке МІНДЕТТІ құпияларды толтыр:
#   GOOGLE_CLIENT_ID          → docs/runbooks/google-oauth-setup.md §3.1
#   GOOGLE_CLIENT_SECRET      → сол runbook
#   KEYCLOAK_ADMIN_PASSWORD   → dev: admin
#   POSTGRES_PASSWORD         → dev: postgres
#   CENTRIFUGO_TOKEN_HMAC_SECRET → dev: dev_hmac_secret_change_me_local_32x (≥32 symb)
#   ANTHROPIC_API_KEY         → AI lead scoring үшін (опционал)
#   SMTP_PASSWORD             → Gmail App Password, OTP email үшін

docker compose up -d
# Күту: ~2 минут — барлық 11 контейнер healthy болу үшін

# Browser-де тексер:
open http://localhost:3001/auth/sign-in
```

Толық нұсқаулық → [ONBOARDING.md](./ONBOARDING.md) + [runbooks/google-oauth-setup.md](./runbooks/google-oauth-setup.md).

---

## 🗺️ Документация картасы (қайда не тұр)

Оқу тәртібі маңызды. **Бұл тәртіпте оқы:**

1. **[AGENT_HANDOFF.md](./AGENT_HANDOFF.md)** *(сен қазір осы)* — жалпы картасы, шұғыл ережелер.
2. **[WHAT_EXISTS.md](./WHAT_EXISTS.md)** — қай файл бар, ешқашан қайта жасамайтын тізімі, manual setup кестесі.
3. **[ONBOARDING.md](./ONBOARDING.md)** — Docker, URL-дер, тест аккаунттар, типтік қателер.
4. **[ARCHITECTURE.md](./ARCHITECTURE.md)** — backend модульдер, multi-tenancy (RLS), real-time, analytics pipeline, миграциялар.
5. **[AUTH.md](./AUTH.md)** — auth flow диаграммалар, Keycloak, PKCE, OTP.
6. **[API.md](./API.md)** — барлық REST endpoint-тар.
7. **[CHANGELOG.md](./CHANGELOG.md)** — соңғы өзгерістер хронологиялық.

**Runbook-тар (арнайы процедуралар):**

- [runbooks/google-oauth-setup.md](./runbooks/google-oauth-setup.md) — Google Sign-in setup end-to-end.

**ADR-лар (архитектуралық шешімдер):**

- [adr/0001-technology-stack.md](./adr/0001-technology-stack.md)
- [adr/0002-multi-tenancy-strategy.md](./adr/0002-multi-tenancy-strategy.md)
- [adr/0003-centrifugo-channel-naming.md](./adr/0003-centrifugo-channel-naming.md)
- [adr/0004-billing-pipeline.md](./adr/0004-billing-pipeline.md)
- [adr/0005-authentication-keycloak.md](./adr/0005-authentication-keycloak.md)

---

## ⚠️ ҚАТАҢ ЕРЕЖЕЛЕР (не істеуге болмайды)

Осы ережелерді бұзсаң — **жобаға зиян келтіресің**. Қабылдамас алдында user-ден сұра.

1. **Қайта жасама** — `WHAT_EXISTS.md`-тегі «ЕШҚАШАН ҚАЙТА ЖАСАМАЙТЫН» тізіміне тиіспе. Auth store, auth плагин, sign-in/sign-up беттер, Centrifugo composable, Keycloak realm JSON — бәрі істелген.
2. **Session-ды қайта қолданба** — Finished session-ды «upcoming»-ке айналдыру **тыйым**. Auto session = жаңа Session записы.
3. **Profile ID ғана** — `profileId` = user identity. Phone, email, any other — identity емес.
4. **Guest viewing жоқ** — барлық room access Keycloak auth арқылы.
5. **YouTube chat жоқ** — барлық chat Webizon-ның өз Centrifugo-сы арқылы.
6. **Kafka — chat-қа емес** — Kafka тек analytics үшін. Chat = DB + Centrifugo (низкая латентность).
7. **Migration нөмірі** — қазіргі соңғы: **V021**. Келесі: **V022**.
8. **Client Secret-ті commit жасама** — Google/Stripe/SMTP құпиялары `.env`-те, git-те жоқ.
9. **`--no-verify` немесе `--amend` қолданба** — pre-commit hook пассалса, жаңа commit жаса.
10. **`git add .` қолданба** — файлдарды атпен қос (`.env`, `*.key` кездейсоқ қосылмайды).

---

## 🔧 Manual-setup құпиялар (git-те жоқ)

Барлығы `.env`-те, `.gitignore`-да:

| Айнымалы | Қайдан алу керек | Runbook |
|----------|-------------------|---------|
| `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET` | Google Cloud Console project `812805454769` → OAuth Client «Webizon Local Dev» | [runbooks/google-oauth-setup.md](./runbooks/google-oauth-setup.md) |
| `SMTP_PASSWORD` | Google аккаунт → Security → 2-Step Verification → App passwords → «Mail» | [ONBOARDING.md](./ONBOARDING.md) |
| `ANTHROPIC_API_KEY` | [console.anthropic.com](https://console.anthropic.com) → API Keys | — |
| `CENTRIFUGO_TOKEN_HMAC_SECRET` | Өзің кездейсоқ генерация (≥32 символ) | [runbooks/google-oauth-setup.md §4.3](./runbooks/google-oauth-setup.md) |
| `KEYCLOAK_ADMIN_PASSWORD` | Dev: `admin`, prod: күшті рандом | [ONBOARDING.md](./ONBOARDING.md) |
| `POSTGRES_PASSWORD` | Dev: `postgres`, prod: күшті рандом | [ONBOARDING.md](./ONBOARDING.md) |

---

## 🧰 Tech стек (бір парақта)

**Backend**
- Java 21, Spring Boot 3.5.3, Spring Security, Spring Data JPA
- PostgreSQL 16 (Flyway migrations — V021)
- Redis 7 (OTP, presence, chat rate-limit)
- Kafka 7.5 + ZooKeeper (analytics pipeline)
- ClickHouse 24.3 (analytics warehouse)
- MinIO (file storage: covers, materials, recordings, invoices)
- Centrifugo 5 (WebSocket real-time)
- Keycloak 24 (OIDC + Google IDP)

**Frontend**
- Nuxt 4.4, Vue 3, TypeScript
- Pinia (auth, toast stores)
- TailwindCSS (light/dark mode via `usePlatformTheme`)
- Centrifuge-js (real-time)
- Nitro proxy: `/api/backend/*` → `http://backend:8080/api/*`

**Dev контейнерлер (Docker Compose):** 11 сервис, барлығы dev `.env` арқылы байланысты. Порттары [ONBOARDING.md](./ONBOARDING.md)-та.

---

## 📍 Қазіргі жай-күй (қайда тоқтадық)

**Соңғы аяқталған жұмыс:**

1. **Google OAuth runbook** — жазылды, push-талды. Mac-тегі жаңа агент `.env`-ке credentials қоя алады.
2. **Runtime верификация** — Docker stack live. Email/password register+login+bootstrap = HTTP 200. Google OAuth redirect chain → `accounts.google.com/o/oauth2/v2/auth?client_id=812805454769-…` ✅.
3. **Performance fixes (P0)** — CentrifugoClient pooling, TimelineReplayEngine tenant cache, LeadSignalEvaluator count query, Caffeine cache, V021 hot-path indexes.
4. **GitLab CI/CD** — қосылған (backend compile + frontend build).

**Келесі потенциал жұмыс:**

- Mac-те нақты browser-де Google button арқылы login тест — user оны істейді.
- Recording pipeline (MinIO recordings bucket дайын, service қосылған жоқ).
- AI Lead Scoring UI refinement.
- GitLab CI-да test job қосу (қазір тек compile + build).

---

## 🔍 Жылдам навигация — қай кодты қайдан табу керек

**«Google OAuth button басқанда не болады?»**
- Frontend button: `frontend/app/pages/auth/sign-in.vue`, `sign-up.vue`
- PKCE + redirect: `frontend/app/stores/auth.ts` → `startGoogleLogin()`
- Callback: `frontend/app/pages/auth/callback.vue`
- Backend code exchange: `backend/.../tenancy/service/KeycloakAuthService.java` → `exchangeCode()`
- Keycloak realm конфиг: `infra/keycloak/import/webizon-realm.json` (Google IDP + `webizon-first-broker-login` flow)

**«Chat хабар қалай жіберіледі?»**
- Frontend: `frontend/app/components/room/RoomChat.vue` → `ChatApi.send()`
- Backend policy chain: `backend/.../chat/policy/*.java` (Ban, Mute, SlowMode, Link, WordFilter, FloodProtection)
- Persist: `ChatService` → PostgreSQL `chat_messages` + Centrifugo broadcast
- Real-time: Centrifugo channel `tenant.{id}.session.{id}.chat`

**«CTA қалай жұмыс істейді?»**
- Admin: `frontend/app/components/admin/CtaPanel.vue` + `admin/live/LiveCtaControl.vue`
- Backend: `CtaController` + `CtaService` + `CtaBroadcaster` (Centrifugo)
- Timeline event: `EventTimelineAction` (offset_seconds + action_type + payload_json)
- Replay engine: `TimelineReplayEngine.tick()` (1s, offset-based)

**«Analytics қайда сақталады?»**
- Realtime: Redis buffer (`analytics_buffer:*`)
- Durable: Kafka → `AnalyticsEventConsumer` → ClickHouse `analytics_events` table
- Reports: `AnalyticsReportController` (backend aggregation)

**«Multi-tenancy қалай жұмыс істейді?»**
- `TenantAwareEntity` base class + `tenant_id` column
- PostgreSQL RLS (`webizon_app` user, `SET LOCAL app.tenant_id = …`)
- `TenantContextFilter` (JWT-тен оқиды), `RoleEnrichmentFilter` (DB-дан roles)
- Public endpoint-тар (event resolve, invite accept): `SET LOCAL` pivot арқылы

---

## 📝 Жұмыс тәртібі (workflow)

1. **Бастамас бұрын:**
   - `git pull` жаса
   - `docs/CHANGELOG.md` соңғы жазбаны оқы — кейс-кейс кім не істегенін біл
   - `docs/WHAT_EXISTS.md` + `docs/ARCHITECTURE.md` сай білу керек
2. **Өзгерту жасағанда:**
   - `rtk` prefix қолдан (user CLAUDE.md-де — token optimization)
   - Жеке файлдарды атпен stage жаса: `rtk git add backend/src/main/...` — `git add .` емес
   - Commit message conventional commits стилінде: `feat(auth): …`, `fix(chat): …`
3. **Істеп болған соң:**
   - Runtime-де тексер (docker up → curl endpoint → логтарда error бар ма)
   - `docs/CHANGELOG.md`-ке жаз (міндетті)
   - Егер архитектура өзгерсе — `docs/ARCHITECTURE.md` жаңарт
   - Егер жаңа файлдар қосылса — `docs/WHAT_EXISTS.md` жаңарт
   - `rtk git push` — **әрқашан push жаса**
4. **Салақтық жасама:** user нақты айтты: «бірнәрсе жазғанда оны толық аяқтап, істеп тұрғанына көз жеткізіп, әрқашан пуш жасап докуменке жазып отыр». Бөлшек жұмыс қалдыруға **болмайды**.

---

## 🆘 Шұғыл істемей тұрса

| Симптом | Тексер |
|---------|--------|
| «Unauthorized» login-да | `GOOGLE_CLIENT_SECRET` 35 chars болуы керек. `docker exec webizon-keycloak sh -c 'echo ${#GOOGLE_CLIENT_SECRET}'` |
| Docker контейнер unhealthy | `docker logs webizon-{service}` |
| Flyway migration failed | `docker logs webizon-backend 2>&1 | grep -i flyway` |
| Frontend 500 | `docker logs webizon-frontend` |
| Centrifugo connect error | `CENTRIFUGO_TOKEN_HMAC_SECRET` ≥32 символ |
| Google redirect_uri_mismatch | Google Cloud → OAuth Client → Authorized redirect URI: `http://localhost:8180/realms/webizon/broker/google/endpoint` |

Толық: [runbooks/google-oauth-setup.md §6](./runbooks/google-oauth-setup.md) + [ONBOARDING.md](./ONBOARDING.md).

---

## 📬 Байланыс

- **Owner:** aidos.zhumanazar@gmail.com
- **GitLab:** https://gitlab.com/webizon365/webizon
- **Google Cloud project (dev):** `812805454769` (Webizon-365)

---

**Ережелердің жалғыз ерекшелігі:** user өзі нақты айтса (мысалы, «migrate V021 сын»), сосын қайта қарастырылады. Әйтпесе — бұл файлдағы ережелер міндетті.
