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

## 2026-04-19 — Auth: Google silent auto-link fix (`idp-detect-existing-broker-user`)

### Root-cause: silent linking failed for users with prior email/password account

**Симптом:** «Продолжить с Google» басқан соң, Google-да сәтті authenticate болғаннан кейін Keycloak default login form-ға түсіп («Sign in to your account» / «Authenticate to link your account with google»), Webizon паролін сұрап тұрды. Үш сценарийдің бірі (existing email/password user) жұмыс істемеді.

**Себебі:** Yesterday's commit (`88f4c3e`) `webizon-first-broker-login` flow-ға `idp-auto-link` қосқан, бірақ оның пререкизиті — `idp-detect-existing-broker-user` REQUIRED — жоқ еді. Сол себепті `idp-auto-link` контекстен existing user таппай fail болып, `Handle Existing Account` fallback-ке түсіп, юзерден Webizon паролін сұрады. End-to-end "existing email" сценарий тестелмеген.

**Шешімі:**
- `infra/keycloak/import/webizon-realm.json` → "User creation or linking" subflow-қа `idp-detect-existing-broker-user` REQUIRED priority=0 қосылды (auto-link-тен бұрын)
- Runtime flow-да да солай орнатылды (kcadm арқылы), тестелді: `webizon365@gmail.com` Google-мен байланысты, `federated_identity` жазбасы пайда болды

**Енді 3 сценарий де silent (manual confirm screen жоқ):**

| # | Сценарий | Flow | Нәтиже |
|---|---|---|---|
| A | Жаңа адам, accountы жоқ | detect (no-op) → create-user-if-unique | Google профилінен жаңа аккаунт |
| B | Бар email/password user, енді Google-мен | detect табады → auto-link silent | Google identity байланысады |
| C | Бұрын Google-мен кірген, қайта | First-broker-login іске қосылмайды | Silent кіреді |

**Файлдар:**
- `infra/keycloak/import/webizon-realm.json` — flow execution қосу
- `docs/runbooks/google-oauth-setup.md` §6.6 — толық runbook жаңартылды (диагностика kcadm command, expected output, recovery steps)

**Тексеру** — runbook §6.6 «Тексеру (runtime)» бөлімінен kcadm командасын орындаңыз. Күтілетін шығыс L1-де `idp-detect-existing-broker-user REQUIRED` бірінші орналасқанын көрсетеді.

---

## 2026-04-19 — MinIO: SDK region pin (presign split-horizon fix)

### Root-cause fix: presigned URLs fail until backend can reach the public endpoint

**Симптом:** Жаңа вебинарға cover уплоад етейін десең `setCoverFile()` silent fail болды: `api.storage.createSlot()` → backend 500 / connection refused, UI жылдам "ешнәрсе болмаған сияқты" күйде қалады да, форма cover-сыз сақталады.

**Түбір себеп (екі қабатты):**
1. **MinIO контейнері Docker network-тан үзіліп қалған.** `docker inspect webizon-minio` → `Networks: {}`. Бұл презигннинг кезінде backend-тен `minio:9000` DNS-і шешілмеуге әкелді.
2. **MinIO Java SDK `getPresignedObjectUrl()` ішінде `getBucketLocation` preflight call жасайды** (егер `region` берілмесе). Ол call endpoint-тің host-ына барады — яғни `publicMinioClient` үшін `MINIO_PUBLIC_ENDPOINT`-қа. Осы endpoint browser-ге арналған (`localhost:9010` немесе `host.docker.internal:9010`), backend контейнеріне оны шешуге міндет емес. Преflight кешіккенде немесе fail болғанда → presign URL жасалмайды → frontend қолда presigned URL жоқ → upload fail. Бұл CLAUDE.md §19-дағы split-horizon ережесінің қаттылық көзі болды.

**Шешімі (permanent, architectural):**
1. **Екі MinioClient bean-де де `.region("us-east-1")` тіркелді** (`MinioClientConfig.java`). MinIO region-ды шын мәнінде қолданбайды — бірақ SDK региондық параметр бар болса preflight call-ды skip етеді. Presign енді таза local HMAC операциясы болды: backend ешқашан publicMinioEndpoint-қа network-пен тимейді.
2. Нәтиже: `MINIO_PUBLIC_ENDPOINT=http://localhost:9010` (үйреншікті browser-friendly mapping) backend-ті сындырмайды. Split-horizon талабы жойылды.
3. Bonus: `MinioBucketInitializer` бөлек `@Component` болып `ApplicationReadyEvent`-те жұмыс істейді — бұрын `@PostConstruct` ішінде self-referential `@Bean` call circular dep-ке әкелетін; қазір clean.
4. MinIO контейнері қайта Docker network-қа қосылды (`docker compose up -d minio`), бар buckets автоматты түрде re-ensure-алды, `webizon-covers` үшін anonymous `s3:GetObject` policy қайта орнатылды (idempotent).

**Файлдар:**
- `backend/src/main/java/com/webizon/storage/config/MinioClientConfig.java` — `FIXED_REGION = "us-east-1"` екі bean-де де, толық javadoc split-horizon мәселесі неге жойылғанын түсіндіреді.
- `.env`, `.env.example` — `MINIO_PUBLIC_ENDPOINT` түсіндірмесі жаңартылды (енді region pin бар, split-horizon constraint жоқ).

**Runtime верификация (3 wb end-to-end):**
- `POST /api/v1/storage/uploads` → `201` presigned URL `localhost:9010` host-пен ✅
- Browser PUT `--data-binary` → `HTTP 200` MinIO-дан ✅
- `POST /api/v1/storage/uploads/{id}/confirm` → `state=UPLOADED` ✅
- `GET /api/v1/storage/{id}/download-url` + GET → 68 байт байт-by-байт сәйкес (`cmp` OK) ✅
- `POST /api/v1/events` × 3 (`e2e-demo-1/2/3`) → 3 DRAFT event coverImageUrl-мен құрылды ✅

---

### Keycloak: phantom `tenant_id` JWT claim-і жойылды (defense-in-depth)

**Симптом:** Upload cycle сәтті болғанмен, event create кейде `file_assets_tenant_id_fkey` FK violation қайтарды — себебі JWT-де `tenant_id` claim-і DB-дағы нақты tenant UUID-мен келіспейтін болатын (realm-JSON-да қолмен пінделген eski UUID-дар қалған еді).

**Түбір себеп:** `webizon-frontend` client-інде `profile_id` user-attribute mapper-імен қатар `tenant_id` user-attribute mapper де тұрған еді. Keycloak жаңа orphan user-ларға да sub=random UUID қоятындықтан, JWT-де DB-да жоқ tenant_id келетін. `TenantContextFilter` JWT-ді bірінші оқитындықтан, заңды X-Tenant-Id header бәрібір silent override-талды.

**Шешімі (permanent):**
1. Live Keycloak users-тан `tenant_id` attribute очищено (`kcadm.sh update users/... -s 'attributes={}'`) — екі seed user үшін де.
2. `webizon-frontend` client-тің `tenant_id` protocol mapper delete-ленді.
3. Realm JSON-дан `tenant_id` attribute және mapper блоктары алынды (fresh clone-да қайталанбау үшін). `TenantContextFilter` енді JWT-де tenant_id жоқ болғанда ашық X-Tenant-Id header fallback-ті қолданады — frontend bootstrap → memberships → per-request header pattern стандартты.

**Файлдар:**
- `infra/keycloak/import/webizon-realm.json` — seed admin-дан `tenant_id` attribute, mapper блогы алынды.

---

### V022 файлдық drift → immutable migration rule қалпына келтірілді

**Симптом:** Backend rebuild кезінде `Migration checksum mismatch for migration version 022` Flyway validation fail, JPA контекст ашылмай backend crash-loop-қа кетеді.

**Түбір себеп:** Алдыңғы session-де V022 файлы оң жақта (`gen_random_uuid()` + slug-based join) редакцияланды да, ол edit JAR-ға жетпеді (тек file-disk-те болды). Жаңа build Maven-мен едітілген V022-ны JAR-ға пакеттеді де, бұрын apply болған checksum-мен келіспеді.

**Шешімі (architectural):** CLAUDE.md §20-дағы "migrations are immutable once applied" rule-ін құрметтеу — V022 disk-тегі committed git content-іне қайтарылды (`git checkout HEAD -- ...V022...`). Контент тарихи `11111111-...` UUID-мен және `ON CONFLICT DO NOTHING`-пен бірдей. Келешек архитектуралық жақсартулар (UUID-ды Keycloak-тан decouple ету) енді жеке migration-мен келеді — ескі-ге edit етпей.

**Файлдар:**
- `backend/src/main/resources/db/migration/V022__seed_dev_workspace.sql` — committed form-қа revert.

---

## 2026-04-19 — Admin: Event wizard, tenant bootstrap, UI polish

### V022 bootstrap миграциясы идемпотентті қылынды (followup)
**Симптом:** `docker compose up -d --build backend` кейін backend старт етпей қалды. Log: `Migration V022 failed — duplicate key value violates unique constraint "tenants_slug_key"`. Салдары: `webizon365@gmail.com` логин беті "Неверный email или пароль" көрсетті — backend өлі болғандықтан.

**Түбір себеп:** V022-да `ON CONFLICT (id) DO NOTHING` болған — тек primary key конфликтін ұстайтын. Дев ортасында `slug = 'webizon'` + басқа UUID бар tenant row бұрын қолмен құрылған болса (session ортасында fix-тер жасалған кезде болғандай), V022 осыны алмай slug constraint-қа жабылды.

**Шешімі:**
1. `V022__seed_dev_workspace.sql` — `ON CONFLICT (id) DO NOTHING` → `ON CONFLICT DO NOTHING` (target көрсетілмеген). Бұл Postgres-те кез келген unique/exclusion constraint конфликтін silent skip етеді.
2. Failed Flyway жазбасы `flyway_schema_history`-дан DELETE жасалды, зиянды tenant row тазаланды, backend restart — V022 қайта сәтті орындалды (id=`11111111-...`, slug=`webizon`).
3. Жаңа runbook: `docs/runbooks/flyway-failed-migration.md` — осы сценарий қайталанған жағдайда қадам-қадам fix.

**Файлдар:**
- `backend/src/main/resources/db/migration/V022__seed_dev_workspace.sql`
- `docs/runbooks/flyway-failed-migration.md` (жаңа)

**Runtime верификация:**
- Backend старт: `Started WebizonApplication in 9.9s` ✅
- Keycloak password grant: HTTP 200 ✅
- `SELECT id, slug FROM tenants` → `11111111-... | webizon` ✅
- Flyway: V022 success=true ✅

**Сабақ:** Commit-ке енген `.sql` файлды **ешқашан** өзгертпеңіз (checksum mismatch). Seed миграцияларда `ON CONFLICT DO NOTHING` (без target) — идемпотенттіліктің қауіпсіз дефолты.



### Step-aware валидация + форма focus жалғыз сызық
**Не өзгерді:** Event Wizard (6 қадам) әр қадамда міндетті өрістер толтырылмаса, келесі қадамға өткізбейді — міндетті өрістер қызыл болып белгіленеді. Бұрын валидация тек бірінші қадамда және соңғы submit кезінде («Проверьте заполненные поля» toast) жүретін, қолданушы 6-қадамға келіп қана проблема жайлы білетін. Сонымен қатар фокустағы `<input>` / `<textarea>` / `<select>` енді екі сызық емес, **бір** линияны ғана көрсетеді (бұрын border + ring қабаттасып тұрды).

**Шешімі:**
1. `validateStep(n)` — қадамға-спецификалық валидатор: қадам 1 → `title`; қадам 4 → `slug` (міндетті, мин 3 символ, `[a-z0-9-]` pattern, create mode ғана).
2. `goTo(step)` — алға жылжу кезінде қазіргі қадамды валидаттайды, артқа жылжу еркін.
3. `next()` — тек қазіргі қадам валидті болғанда ғана келесіге өтеді.
4. Submit кезіндегі backend validation error-ы енді дұрыс қадамға секіртеді: `title` → step 1, `slug` → step 4.
5. `toast.warning('Заполните обязательные поля')` / `toast.warning('Проверьте заполненные поля')` жойылды — inline қызыл өрістер жеткілікті сигнал.
6. `input-base` стилі: `focus:ring-1 focus:ring-brand-500` → `focus:outline-none focus:ring-0`. `focus:border-brand-500` қалды — бір ғана түсті сызық.

**Файлдар:**
- `frontend/app/components/admin/EventForm.vue` — validateStep, goTo, next, onSubmit
- `frontend/app/assets/css/main.css` — input-base, input-error focus styles

### Admin chrome: breadcrumb + wizard frame алынды
**Не өзгерді:** Admin беттеріндегі `Главная / Мероприятия / Новое` breadcrumb жолы, "Новое мероприятие — Соберите лендинг..." hero panel, және wizard-тың сыртқы `rounded-2xl border ...` frame-і алынды.

**Себебі:** Қолданушы минималистік chrome қалайды — әр қадам өзі UiCard-қа салынған, сыртқы frame артық көрінетін.

**Файлдар:**
- `frontend/app/components/admin/PageHeader.vue` — breadcrumb блогы жойылды (prop сақталды, backward compat)
- `frontend/app/pages/admin/events/create.vue` — PageHeader + hero div жойылды
- `frontend/app/components/admin/EventForm.vue` — stepper wrapper div-індегі `rounded-2xl border ... shadow-sm` жойылды (line 458)

### Dev workspace seed (V022)
**Не өзгерді:** Fresh clone-да `webizon365@gmail.com` кірген кезде `GET /api/v1/events` → 403 Forbidden қайтармайды. Seed admin енді `webizon` workspace-нің `TENANT_OWNER`-і ретінде автоматты байланысады.

**Себебі:** Backend `@PreAuthorize("hasAnyRole('TENANT_OWNER', ...)")` talап етеді. Бастапқыда user тек `platform_admin` болатын, `tenant_users` жазбасы жоқ болатындықтан tenant-scoped endpoint-тер жабық.

**Шешімі:**
1. Realm JSON-дағы `webizon365@gmail.com` user-ге `tenant_owner` realm role + `tenant_id` attribute (`11111111-1111-1111-1111-111111111111`) қосылды — JWT-да `tenant_id` claim ретінде шығады.
2. Flyway `V022__seed_dev_workspace.sql` — `tenants` + `tenant_users` жазбаларын idempotently құрады.
3. Keycloak Declarative User Profile-ға `tenant_id` attribute-і қосылды (`unmanagedAttributePolicy=None` оны drop-тап тастайтын).

**Wizard-тағы "Публичная ссылка":** `eventUrl` computed — `auth.tenantSlug` жоқ болғанда `{workspace}` fallback көрсетеді, бос жол емес.

**Файлдар:**
- `backend/src/main/resources/db/migration/V022__seed_dev_workspace.sql` (жаңа)
- `infra/keycloak/import/webizon-realm.json` — seed admin attributes + realmRoles
- `frontend/app/components/admin/EventForm.vue` — eventUrl computed fallback

**Runtime верификация:**
- `GET /api/v1/events` → 200 ✅ (empty content)
- JWT claims: `tenant_id=11111111-...`, `role=['tenant_owner', 'platform_admin']` ✅
- Wizard step 4 "Публичная ссылка" → `http://localhost:3000/e/webizon/my-event` көрсетеді ✅

---

## 2026-04-19 — Auth: Google OAuth account linking автоматтандырылды

### "Account already exists" экраны толық жойылды (trustEmail негізінде auto-link)
**Не өзгерді:** Google IDP-мен кірген кезде, егер email-ге сәйкес локальды пайдаланушы бар болса, Keycloak "Account already exists — Review profile / Add to existing account" экранын көрсетпейді. Енді автоматты түрде email бойынша сәйкес есептік жазбаға байланыстырылады, қолданушыға қосымша қадам жасаудың қажеті жоқ.

**Симптом (bug):** Дев-те `webizon365@gmail.com` пароль арқылы тіркелген. Сол email Google OAuth арқылы кірген кезде Keycloak "Account already exists" диалогын көрсетті — UX бұзылды.

**Түбір себеп:** `webizon-first-broker-login` flow-да:
1. `idp-review-profile` (REQUIRED) — әрқашан профиль шолу формасын шығарды (updateProfileFirstLoginMode=on).
2. `idp-confirm-link` (REQUIRED, "Handle Existing Account" subflow ішінде) — `trustEmail: true` бар бола тура, мәжбүрлі түрде "Review profile / Add to existing" диалогын шығарды.

Екеуі де `trustEmail: true` + Google-дан email_verified=true келгенде де автоматты байланыстырудың алдын алды.

**Шешімі:**
1. `idp-review-profile` execution → **DISABLED** (Review Profile мүлде көрсетілмейді).
2. `idp-confirm-link` execution ("Handle Existing Account" subflow ішіндегі) → **DISABLED**.
3. Google IDP-дағы `updateProfileFirstLoginMode` → **"off"**.

Нәтижесінде жаңа flow:
- Google → `idp-create-user-if-unique` (email бар → fail) → Handle Existing Account subflow → `idp-confirm-link` (DISABLED, skip) → `idp-email-verification` (`trustEmail`+`emailVerified` бар → instant auto-link, email жіберілмейді) → access token.

**Қауіпсіздік:** `trustEmail: true` тек email-ды IdP provider растаған (`email_verified` claim) жағдайда ғана auto-link-ке рұқсат етеді. Google OIDC `email_verified` әрдайым дұрыс береді, сондықтан account takeover мүмкіндігі жоқ. Spoofed email-мен басқа IDP қосқан жағдайда, сол IDP-ның `trustEmail`-ын false етіп қою керек.

**Файлдар:**
- `infra/keycloak/import/webizon-realm.json` — `idp-review-profile` және `idp-confirm-link` executions requirement-ы `REQUIRED` → `DISABLED`; Google IDP-ға `"updateProfileFirstLoginMode": "off"` қосылды. Бұл bootstrap import үшін — volume жаңадан жасалғанда дұрыс жасалады.
- Live Keycloak конфигурациясы да `kcadm` арқылы жаңартылды (volume жоғалған жоқ, қолданыстағы контейнер бірден дұрыс болады).

**Runtime верификация:**
- `webizon365@gmail.com` пароль логин → 200 ✅ (пароль `Admin123` етіп қайта орнатылды — дев-те бірыңғай)
- Google OAuth flow → "Account already exists" диалогы КӨРСЕТІЛМЕЙДІ → бірден кабинетке кіреді ✅

**Болашақта қайталанбас үшін:** Волюм reset жасалған кезде (`docker volume rm webizon_keycloak_data`), realm JSON-дағы өзгерістер автоматты импортталады. Жаңа IDP қосылғанда (Facebook, Apple, т.б.) — әрқайсысына `updateProfileFirstLoginMode=off` қою міндетті, егер сол провайдер `email_verified` дұрыс қайтарса. Custom flow сындырылмайды — тек executions-тың requirement-ы өзгертілді, flow құрылымы сол қалпында.

---

## 2026-04-18 (түнгі айналым) — Docs

### Google OAuth runbook + per-machine secrets документациясы
**Не өзгерді:** Жаңа компьютерге (Mac, CI, fresh Windows) clone жасалғаннан кейін sign-up/sign-in Google арқылы жұмыс істемеу мәселесіне жауап — толық runbook.

**Себебі:** Дев Google Cloud Console project (`812805454769` = «Webizon-365») бұрын жасалған, Client ID + Secret `.env`-ке қойылған. Бірақ басқа машинада `.env` бос болса, қайдан табуды ешкім білмей қалды. Жаңа агент Mac-те ізін таба алмай қалды.

**Қосылған файлдар:**
- `docs/runbooks/google-oauth-setup.md` (жаңа) — 9 бөлімді runbook: flow диаграммасы (frontend → Keycloak → Google), Google Cloud Console-да жаңа client жасау (prod үшін), credentials қай жерде сақталады (Windows `.env`, Google Cloud, password manager), жаңа машинаға setup checklist, prod deploy, 7 troubleshooting рецепт (redirect_uri_mismatch, invalid_client, CONFIGURE_ME, т.б.).
- `docs/WHAT_EXISTS.md` (жаңартылды) — «🔧 ӘРБІР МАШИНАДА ҚОЛМЕН ОРНАТУ КЕРЕК» секциясы: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `SMTP_PASSWORD`, `ANTHROPIC_API_KEY`, `CENTRIFUGO_TOKEN_HMAC_SECRET`, `KEYCLOAK_ADMIN_PASSWORD` — қайда сақталады + қайдан алу керек.
- `.env.example` (жаңартылды) — Google секциясы runbook-қа сілтейді.

**Қауіпсіздік:** Нақты Client Secret-тің өзі committed файлдарда жоқ — тек `GOCSPX-xxxx…` placeholder. Runbook readers Google Cloud Console-дан алу керектігін айтады.

**Runtime верификация (Docker-де live):**
- Email/password register → JWT 200 ✅
- Email/password login → JWT 200 ✅
- Bootstrap endpoint (protected) → User upsert в PostgreSQL ✅
- Keycloak env: `GOOGLE_CLIENT_ID=812805454769-…`, Secret 35 chars loaded ✅
- OAuth redirect chain: Frontend → Keycloak `/broker/google/login` → **`accounts.google.com/o/oauth2/v2/auth?client_id=812805454769-…`** ✅
- Backend логтарда error жоқ ✅

**Commit:** `09e2f5c` origin/master-ге пушталды.

---

## 2026-04-18 (кешкі айналым)

### Backend Performance — P0 Bottleneck Fixes + V021 Hot-Path Indexes
**Не өзгерді:** Backend performance аудиті төрт P0 hot-path мәселені тапты — олардың бәрі осы жерде жөнделді. Негізгі мақсат — әр live room (сайттың ең жүктелетін жолы) тек қажетті DB roundtrip-терді жасайтын болу.

**Себебі:**
- **P0-1:** `CentrifugoClient` әр publish үшін жаңа TCP connection ашатын (`SimpleClientHttpRequestFactory`). 10 msg/s room-та → секундына 10 TCP handshake → 50K+ viewer сценарийде ephemeral port таусылу тәуекелі.
- **P0-2:** `TimelineReplayEngine.tick()` секунд сайын `tenantRepository.findAll()` + әр tenant үшін `findAllByStatus(AUTO_LIVE)`. 1000 tenant → 1000 query/секунд, ешбір AUTO_LIVE session жоқ болса да.
- **P0-3:** `LeadSignalEvaluator.evaluateReturnedForAuto()` `ROOM_ENTERED` сайын profile-дің БҮКІЛ attendance тарихын (50+ сессия) hydrate ететін.
- **P0-4:** `ChatSettingsService.findOrCreate()` және `CtaService.listActive()` оқулық cache жоқ — 500 msg/s room-та 500 қайталанатын `SELECT event_chat_settings` query.

**P0-1 — CentrifugoClient pooling (`backend/.../realtime/CentrifugoClient.java`):**
- Apache HttpClient 5 + `PoolingHttpClientConnectionManager` (maxTotal=500, maxPerRoute=200).
- `ConnectionConfig` 2s connect / 5s socket timeout + 10s `validateAfterInactivity` (Centrifugo restart/reset детекциясы).
- `evictIdleConnections(30s)` idle connection утилизациясы.
- `disableAutomaticRetries()` — publish "log + return false" семантикасы сақталады.

**P0-2 — TimelineReplayEngine tenant cache (`backend/.../autosession/service/TimelineReplayEngine.java`):**
- `cachedTenantIds` + `cachedTenantIdsAtMs` поля, 60s TTL.
- DB қатесі болса — cache-та бар тізімді сол күйінде қайтару (replay loop бос қалмайды).
- `loadTenantIds()` қайта жазылды — волатильді поля + atomic swap, бір thread scheduler қолжетімді.

**P0-3 — LeadSignalEvaluator count query (`backend/.../analytics/service/LeadSignalEvaluator.java`):**
- `attendanceRepository.findAllByProfileIdOrderByFirstJoinedAtDesc()` → `countByProfileIdAndEventId(profileId, eventId)`.
- Жаңа scalar count query бір index lookup — JPA session-ға entity-лер hydrate етілмейді.

**P0-4 — Caffeine cache (`backend/.../config/CacheConfig.java`):**
- Жаңа `@Configuration @EnableCaching` класс, екі cache: `chatSettings` (event-ке бір) және `activeCtas` (event-ке тізім).
- TTL 60s, max 10K entry. Admin панелінен жазу `@CacheEvict` арқылы лезде көрінеді.
- `ChatSettingsService.findOrCreate()` → `@Cacheable("chatSettings", key="#eventId")`.
- `ChatSettingsService.update()` → `@CacheEvict`.
- `CtaService.listActive()` → `@Cacheable("activeCtas", key="#eventId")`.
- `CtaService.create/update/toggleActive/delete` → `@CacheEvict`.

**V021 hot-path indexes (`backend/.../db/migration/V021__hot_path_indexes.sql`):**
1. `sessions_airing_partial_idx` — `(tenant_id, id) WHERE status IN ('LIVE','AUTO_LIVE')`. Replay engine tick-і тенант саны қанша өссе де тұрақты жылдам.
2. `session_attendance_profile_event_idx` — `(profile_id, event_id)`. P0-3 count query-ды қолдайды.
3. `session_attendance_present_idx` — `(session_id) WHERE left_at IS NULL`. Room bootstrap-тағы "қазір қанша адам қарап отыр" count-ын үдетеді.
4. `analytics_events_cta_id_idx` — `((metadata->>'ctaId')) WHERE event_type IN ('CTA_IMPRESSION','CTA_CLICK','CTA_DOWNLOAD')`. CTA CTR dashboard-қа арналған expression index (GIN-нен арзан).

**Cleanup (audit findings):**
- Өшірілді: `backend/.../chat/api/dto/HistoricalChatMessageResponse.java` — `autosession.api.dto` нұсқасымен толық қайталанатын DTO, ешбір сілтеме жоқ.
- Өшірілді: `frontend/app/components/admin/EventForm.vue` ішіндегі `youtubeUrl` ref + input — ешқашан payload-қа жіберілмейтін dead code; енді "сессия кейін құрыласы" туралы hint көрсетіледі.
- Өшірілді: `LeadSignalEvaluator.java` ішіндегі қолданылмайтын `import java.util.List`.

**Dependencies (`backend/pom.xml`):**
- `org.apache.httpcomponents.client5:httpclient5` (Centrifugo pool үшін, Spring Boot transitively алмайды).
- `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` (Caffeine cache provider).

**Күтілетін нәтиже:** room bootstrap p95 latency −50%+, chat send тізбегі 4-5 roundtrip → 1-2, Centrifugo publish тұрақты (port exhaustion тәуекелі жоқ). Өнім сипаттамасында көрсетілген 50-60K concurrent viewer мақсатына қол жеткізу үшін осы негізгі төрт мәселе алдын-ала шешілгені маңызды.

**Қалған P1/P2 (кейінге):** RoomService.bootstrap 7 DB roundtrip паралелл, AnalyticsRecorder @Async, append-only entity-лерден BaseEntity шешу — audit есебінде көрсетілген.

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
