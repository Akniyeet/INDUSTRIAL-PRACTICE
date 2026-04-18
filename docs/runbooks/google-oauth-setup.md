# Runbook — Google OAuth 2.0 Setup (Sign in / Sign up with Google)

> **Мақсаты:** Webizon-да "Войти с Google" / "Sign up with Google" батырмасын жұмыс істеткізу.
>
> Бұл runbook кімге арналған:
> 1. Жоба жаңа компьютерге (мысалы, Mac-ке) тартылған соң `.env` бос болса → 4-тарауды оқы.
> 2. Жаңа Google Cloud project жасау керек болса (мысалы, prod үшін) → 2-тарауды оқы.
> 3. "Google-ге басқанда қате шығып жатыр" → 6-тарауды (troubleshooting) оқы.

---

## 📎 Байланысты документация

| Мен іздеп жатқан нәрсе | Қай файл |
|------------------------|----------|
| Жалпы auth архитектурасы | [../AUTH.md](../AUTH.md) |
| Keycloak realm JSON, IDP конфиг | `infra/keycloak/import/webizon-realm.json` |
| ADR — неге Keycloak | [../adr/0005-authentication-keycloak.md](../adr/0005-authentication-keycloak.md) |
| Қандай файлдар бар | [../WHAT_EXISTS.md](../WHAT_EXISTS.md) |

---

## 1. Жалпы суреттеме

Webizon-да Google-мен кіру **екі слой арқылы** жүреді:

```
[Пайдаланушы]
     ↓ "Войти с Google" басады
[Frontend: sign-in.vue → auth.startGoogleLogin()]
     ↓ PKCE верификатор генерациялайды, sessionStorage-қа сақтайды
     ↓ window.location = Keycloak /auth URL + kc_idp_hint=google
[Keycloak: /realms/webizon/protocol/openid-connect/auth]
     ↓ kc_idp_hint=google → автоматты Google-ге redirect
[Google: accounts.google.com/o/oauth2/v2/auth]
     ↓ пайдаланушы Google аккаунтын таңдайды
     ↓ redirect: http://localhost:8180/realms/webizon/broker/google/endpoint?code=...
[Keycloak: webizon-first-broker-login flow]
     ↓ Google email бар → Keycloak user-ді автоматты байланыстырады (trustEmail=true)
     ↓ Жоқ болса — жаңа Keycloak user жасайды (email verified)
     ↓ redirect: http://localhost:3000/auth/callback?code=...
[Frontend: callback.vue]
     ↓ sessionStorage-дан PKCE verifier алады
     ↓ POST /api/v1/public/auth/callback { code, redirectUri, codeVerifier }
[Backend: KeycloakAuthService.exchangeCode()]
     ↓ POST Keycloak /token grant_type=authorization_code + code_verifier
     ↓ ← JWT (access + refresh)
     ↓ ← frontend-ке тарады
[Frontend: bootstrap → setSession → redirect destination]
```

**Маңызды:** бұл жүйеде **ҮШ сенім шекарасы** бар:
- Google Cloud Console — OAuth Client ID + Client Secret шығарады.
- Keycloak realm — Google IDP конфигурациясы (ID + Secret осында).
- Webizon frontend/backend — PKCE арқылы Keycloak-пен сөйлеседі.

Егер біреуі бос болса, flow үзіледі.

---

## 2. Google Cloud Console-да жаңа OAuth Client жасау (тек бір рет)

> **Назар аудар:** project `812805454769` («Webizon-365») — local dev үшін OAuth Client **әлдеқашан жасалған**. Қайта жасама. Credentials қайдан алу керектігі 3.1 бөлімінде. Егер prod үшін жаңа project керек болса — төмендегі қадамдарды орында.

### 2.1 Жаңа Google Cloud Project (егер керек болса)

1. https://console.cloud.google.com/ → project selector → **New Project**
2. Project name: `webizon-prod` (немесе дев үшін — `webizon-dev` — бұл жасалған)
3. **Create**

### 2.2 OAuth consent screen дайындау

1. Sidebar → **APIs & Services → OAuth consent screen**
2. User type: **External** (кез келген Google аккаунты үшін)
3. App information:
   - App name: `Webizon`
   - User support email: `aidos.zhumanazar@gmail.com` (немесе команда email)
   - Developer contact: сол email
4. Scopes: `openid`, `profile`, `email` қосу
5. Test users (dev режиміне арналған): өз email-іңді қос (production-ға published болмаса, тек тест юзерлер логин жасай алады)
6. Save

### 2.3 OAuth Client ID жасау

1. Sidebar → **APIs & Services → Credentials** → **Create Credentials → OAuth client ID**
2. Application type: **Web application**
3. Name: `Webizon Local Dev` (немесе `Webizon Production`)
4. **Authorized JavaScript origins:**
   ```
   http://localhost:8180
   http://localhost:3000
   http://localhost:3001
   ```
   *(Prod үшін: `https://auth.webizon.kz`, `https://app.webizon.kz`)*
5. **Authorized redirect URIs:**
   ```
   http://localhost:8180/realms/webizon/broker/google/endpoint
   ```
   *(Prod үшін: `https://auth.webizon.kz/realms/webizon/broker/google/endpoint`)*
6. **Create** → pop-up ашылады → **Client ID** және **Client Secret** көшіріп ал.

> ⚠️ **Client Secret бір-ақ рет көрсетіледі.** Дереу `.env`-ке сақта немесе password manager-ге.

---

## 3. Credentials қай жерде сақталады

### 3.1 Windows dev машина (`E:\PROJECT\webizon\.env`)

`.env` файлда (git-ке **ешқашан** commit жасалмайды, `.gitignore`-да):

```env
GOOGLE_CLIENT_ID=812805454769-xxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxxxxxxxxxxxxxxxxxxxx
```

> **Client ID** (пиксель-тіркеу: `812805454769-…`) — Google Cloud Console project number-дан басталады, жария ақпарат.
>
> **Client Secret** — **құпия**. Git-ке commit жасалмайды. Нақты мән:
> - Windows dev машина: `E:\PROJECT\webizon\.env` файлынан көшір
> - Google Cloud Console: [console.cloud.google.com](https://console.cloud.google.com) → project `812805454769` (Webizon-365) → APIs & Services → Credentials → «Webizon Local Dev» OAuth Client → `Reset Secret` немесе `Show Secret`
> - Password manager: @aidos-тан сұра

Бұл dev credentials — local Keycloak (http://localhost:8180) үшін жұмыс істейді.

### 3.2 Docker Compose-қа қалай жетеді

`docker-compose.yml` lines 221–247 (keycloak service):

```yaml
environment:
  GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-CONFIGURE_ME}
  GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-CONFIGURE_ME}
```

Docker Compose `.env` файлды автоматты оқиды. Осы екі айнымалы Keycloak контейнерге жіберіледі.

### 3.3 Keycloak realm-ге қалай жетеді

`infra/keycloak/import/webizon-realm.json` Google IDP-ы:

```json
{
  "alias": "google",
  "displayName": "Google",
  "providerId": "google",
  "enabled": true,
  "trustEmail": true,
  "firstBrokerLoginFlowAlias": "webizon-first-broker-login",
  "config": {
    "clientId": "${env.GOOGLE_CLIENT_ID:CONFIGURE_ME}",
    "clientSecret": "${env.GOOGLE_CLIENT_SECRET:CONFIGURE_ME}",
    "defaultScope": "openid profile email",
    "syncMode": "IMPORT"
  }
}
```

`${env.X:Y}` — Keycloak сервер айнымалысын оқиды, жоқ болса `Y` fallback-ты алады.

---

## 4. Жаңа компьютерде (Mac, жаңа Windows, CI) setup жасау

**Жағдай:** `git clone` жасалды, `.env` жоқ немесе бос, "Войти с Google" істемейді.

### Қадамдар

#### 4.1 `.env` файл жасау

```bash
cd /path/to/webizon
cp .env.example .env
```

#### 4.2 Google OAuth credentials қосу

`.env`-ті аш және келесі қатарларды тап:

```env
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
```

Толтыру үшін credentials-ты **3.1 бөлімдегі** нұсқаулықпен ал:
- бар Windows dev машинадағы `.env`-тен көшір, **немесе**
- Google Cloud Console project `812805454769` («Webizon-365») → APIs & Services → Credentials → «Webizon Local Dev» OAuth Client ашып, Client ID-ды көр + Client Secret-ті `Show`/`Reset` жаса, **немесе**
- @aidos password manager-ден сұра.

#### 4.3 Басқа міндетті айнымалыларды толтыр

| Айнымалы | Not |
|----------|-----|
| `KEYCLOAK_ADMIN_PASSWORD` | Dev үшін `admin` (production-да өзгерт) |
| `KEYCLOAK_CLIENT_SECRET` | `dev_client_secret` (realm JSON-ға сай) |
| `CENTRIFUGO_API_KEY` | `dev_api_key_change_me_local` |
| `CENTRIFUGO_TOKEN_HMAC_SECRET` | **≥32 символ** — `dev_hmac_secret_change_me_local_32x` |
| `POSTGRES_PASSWORD` | `postgres` (dev), prod-та күшті пароль |

Қосымша айнымалылар үшін → [../ONBOARDING.md](../ONBOARDING.md).

#### 4.4 Keycloak контейнерді toltyrlgan env-пен қайта бастау

Егер Keycloak бұрын CONFIGURE_ME-мен импортталған болса, realm JSON қайта импортталмайды. Сондықтан:

```bash
# Docker Desktop/Compose іске қосылған
docker compose down
docker volume rm webizon_keycloak_data  # realm-ды қайта импорттайды
docker compose up -d keycloak
```

Немесе Keycloak admin UI арқылы (http://localhost:8180, admin/admin):
1. **Identity Providers → Google** → Settings
2. **Client ID** және **Client Secret** қолмен енгіз
3. **Save**

#### 4.5 Тексеру

1. http://localhost:3000/auth/sign-in аш
2. **"Войти через Google"** бас
3. Keycloak → Google → пайдаланушы таңдайды → callback → dashboard

Қате болса → 6-тарау (troubleshooting).

---

## 5. Production-қа деплой

### 5.1 Production Google Cloud Project

Dev project-ті production-ға **қолданба**. Жаңа project жаса:
- Project: `webizon-prod`
- OAuth consent screen: **Publishing status: In production** (верификация керек болуы мүмкін)
- Client ID: `Webizon Production`
- Authorized JavaScript origin: `https://app.webizon.kz`, `https://auth.webizon.kz`
- Authorized redirect URI: `https://auth.webizon.kz/realms/webizon/broker/google/endpoint`

### 5.2 Credentials сақтау

- GitLab CI/CD variables (немесе Vault, AWS Secrets Manager) — prod құпиясы
- **Ешқашан** `.env.example`-ке, git commit-қа, немесе логқа салма

### 5.3 Deploy уақытында

Prod `docker compose up -d` немесе Kubernetes secret-тен env айнымалыларын берсе, Keycloak сол IDP-ты пайдаланады.

---

## 6. Troubleshooting

### 6.1 «redirect_uri_mismatch» қатесі

**Не болды:** Google Cloud Console-да `Authorized redirect URI` тізімінде Keycloak URL жоқ.

**Түзету:** Google Cloud Console → OAuth client → `Authorized redirect URIs`-ке қос:
```
http://localhost:8180/realms/webizon/broker/google/endpoint
```

### 6.2 «invalid_client» қатесі

**Не болды:** Keycloak-ке дұрыс `clientId` немесе `clientSecret` жетпеген.

**Диагноз:**
```bash
docker compose exec keycloak sh
echo $GOOGLE_CLIENT_ID     # бос болмауы керек
echo $GOOGLE_CLIENT_SECRET # бос болмауы керек
```

Бос болса → `.env`-ті толтыр, `docker compose down && docker compose up -d`.

### 6.3 «CONFIGURE_ME» Keycloak Identity Providers-та көрінеді

**Не болды:** Realm JSON алғашқы рет импортталған уақытта env бос еді, fallback `CONFIGURE_ME` жазылған.

**Түзету (тез):** Keycloak admin UI → Identity Providers → Google → Settings → Client ID / Secret-ке қолмен қой → Save.

**Түзету (толық):** Volume-ды өшір, realm қайта импорттал:
```bash
docker compose down
docker volume rm webizon_keycloak_data
docker compose up -d keycloak
```

### 6.4 Pop-up «ашылмайды» немесе `startGoogleLogin` қатесі

**Не болды:** PKCE `crypto.subtle.digest` бар ма тексер — HTTPS қажет. Localhost үшін `localhost` (немесе `127.0.0.1`) ерекше жағдай, жұмыс істеу керек. Егер `http://192.168.x.x` сияқты IP-ді қолдансаң — browser-де crypto.subtle қол жетімді емес.

**Түзету:** `http://localhost:3000` арқылы аш, IP емес.

### 6.5 Keycloak → Google-ге redirect жасамай тұр

**Диагноз:**
1. Keycloak admin UI (http://localhost:8180) → Realm `webizon` → Identity Providers
2. «Google» IDP бар ма?
3. Enabled: ✅

Егер жоқ болса — realm JSON импорт қатемен аяқталған. Logs:
```bash
docker compose logs keycloak | grep -i "identity-provider\|error"
```

### 6.6 Пайдаланушы Google-мен кіре алмайды, бірақ email/пароль жұмыс істейді

**Не болды:** First Broker Login flow қате конфигурацияланған (idp-auto-link немесе idp-create-user-if-unique).

**Диагноз:** Keycloak admin UI → Authentication → Flows → `webizon-first-broker-login`. Сатылар тізімі:
1. `idp-review-profile` (ALTERNATIVE)
2. `idp-create-user-if-unique` (ALTERNATIVE)
3. `Handle Existing Account` (ALTERNATIVE sub-flow)
   - `idp-confirm-link` (REQUIRED)
   - `Account verification options` (REQUIRED)

Барлығы REQUIRED/ALTERNATIVE болуы керек, бірде-бірі DISABLED болмауы керек.

### 6.7 «Your account is already linked to Google» бірақ юзер бұрын Google-мен кірмеген

**Не болды:** Email `trustEmail: false` болған кезде сақталған. Түзету — Keycloak admin UI → IDP → Google → Trust Email: ✅ ON.

---

## 7. Security

- **`.env` — gitignore-да. Ешқашан commit жасама.**
- Client Secret екі жерде тұрады: Google Cloud Console + `.env`. Үшіншісі болмауы керек.
- Rotation: production client secret 180 күнде бір рет жаңартылады. Жаңарту процесі:
  1. Google Cloud Console → Client → **Rotate Secret**
  2. Жаңа Secret `.env` / Vault-қа
  3. `docker compose restart keycloak` (немесе prod rolling restart)
- OAuth consent screen-де **App logo** және **Home page URL** болса, Google «Unverified App» ескертуі шықпайды.

---

## 8. Checklist (жаңа машинаға setup)

- [ ] `git clone https://gitlab.com/webizon365/webizon.git`
- [ ] `cp .env.example .env`
- [ ] `.env`-ке `GOOGLE_CLIENT_ID` және `GOOGLE_CLIENT_SECRET` қою (3.1 бөлімінен)
- [ ] Басқа міндетті айнымалылар (Keycloak, Centrifugo HMAC ≥32, Postgres password)
- [ ] `docker compose up -d`
- [ ] http://localhost:8180 (admin/admin) — Keycloak жұмыс істеп тұр
- [ ] http://localhost:8180 → Identity Providers → Google: Client ID `812805454769-…` көрінеді
- [ ] http://localhost:3000/auth/sign-in → «Войти через Google» бас → ағын аяқталады
- [ ] Dashboard ашылады, user session бар

---

## 9. Файлдар референсі

| Файл | Не үшін |
|------|---------|
| `.env` | Real secret-тер (gitignored) |
| `.env.example` | Шаблон (committed) |
| `docker-compose.yml` | Keycloak env vars pass |
| `infra/keycloak/import/webizon-realm.json` | Google IDP + first-broker-login flow |
| `frontend/app/stores/auth.ts` | `startGoogleLogin()`, `handleOAuthCallback()` |
| `frontend/app/pages/auth/sign-in.vue` | Google button |
| `frontend/app/pages/auth/sign-up.vue` | Google button |
| `frontend/app/pages/auth/callback.vue` | OAuth callback handler (PKCE retrieve) |
| `backend/.../AuthPublicController.java` | `/api/v1/public/auth/callback` |
| `backend/.../KeycloakAuthService.java` | `exchangeCode()` — code + verifier → JWT |
| `frontend/nuxt.config.ts` | `keycloakUrl`, `keycloakRealm`, `keycloakClientId` runtime config |

---

**Last updated:** 2026-04-18
**Maintainer:** aidos.zhumanazar@gmail.com
