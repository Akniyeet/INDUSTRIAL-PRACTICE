# Webizon — Авторизация Жүйесі

---

## 📎 Байланысты документация

| Мен іздеп жатқан нәрсе | Қай файл |
|------------------------|----------|
| Документация индексі (жалпы карта) | [docs/README.md](./README.md) |
| Auth файлдары қай жерде тұр (нақты path-тар) | [WHAT_EXISTS.md](./WHAT_EXISTS.md) |
| SecurityConfig, RoleEnrichmentFilter, TenantContextFilter | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Auth endpoint-тар (`/api/v1/public/auth/*`) | [API.md](./API.md) |
| Auth-қа байланысты bug fix-тер тарихы | [CHANGELOG.md](./CHANGELOG.md) |

---

> **МАҢЫЗДЫ:** Бұл Keycloak SSO redirect flow емес. Пайдаланушы Keycloak бетін көрмейді. Webizon-ның өз авторизация беті бар, бэкенд Keycloak-пен сахна артында сөйлеседі.

---

## 1. Жалпы принцип

```
Пайдаланушы    →    Webizon sign-in беті    →    Backend    →    Keycloak
                    (өз дизайны, қара фон)        (proxy)        (көрінбейді)
```

Keycloak-тың hosted login бетіне redirect **жоқ**, тек Google OAuth үшін ғана.

---

## 2. Frontend файлдары

| Файл | Орналасуы | Не жасайды |
|------|-----------|-----------|
| `sign-in.vue` | `app/pages/auth/sign-in.vue` | Email/пароль кіру формасы |
| `sign-up.vue` | `app/pages/auth/sign-up.vue` | Тіркелу + OTP верификация |
| `callback.vue` | `app/pages/auth/callback.vue` | Google OAuth callback handler |
| `auth.ts` | `app/stores/auth.ts` | Pinia store — token, user, actions |
| `auth.client.ts` | `app/plugins/auth.client.ts` | Hydrate + token refresh loop |
| `auth.ts` | `app/middleware/auth.ts` | Protected route guard |
| `platform-auth.ts` | `app/middleware/platform-auth.ts` | Platform admin guard |

---

## 3. Backend файлдары

| Файл | Орналасуы | Не жасайды |
|------|-----------|-----------|
| `AuthPublicController.java` | `tenancy/api/` | `/api/v1/public/auth/*` endpoints |
| `AuthBootstrapController.java` | `tenancy/api/` | `/api/v1/auth/bootstrap` |
| `KeycloakAuthService.java` | `tenancy/service/` | Keycloak HTTP calls |
| `OtpService.java` | `tenancy/service/` | Redis OTP + email |
| `SecurityConfig.java` | `config/` | JWT decoder, CORS, whitelist |
| `TenantContextFilter.java` | `tenancy/` | JWT-тен tenant_id |
| `RoleEnrichmentFilter.java` | `tenancy/` | DB-дан tenant roles |
| `CurrentUser.java` | `auth/` | JWT claims helper |

---

## 4. Auth Store (Pinia)

**Файл:** `app/stores/auth.ts`

### State

```ts
{
  token: string | null        // Keycloak JWT access token (15 минут)
  refreshToken: string | null // 30 күн, rotating
  expiresAt: number | null    // Date.now() + expires_in * 1000
  user: {
    id: string                // PostgreSQL users.id (UUID) ← app identity
    email: string
    fullName: string | null
    role: string              // TENANT_OWNER / TENANT_ADMIN / TENANT_MODERATOR / TENANT_PRESENTER
    tenantId: string | null   // UUID
    tenantSlug: string | null // мысалы: "webizon-365"
    avatarUrl: string | null
    isPlatformAdmin: boolean
  }
}
```

### Actions

| Action | Не жасайды |
|--------|-----------|
| `login(email, password)` | Direct Grant → tokens → bootstrap |
| `register(fullName, email, password)` | Keycloak user create → tokens → bootstrap |
| `handleOAuthCallback(code, redirectUri, codeVerifier)` | PKCE code exchange → tokens → bootstrap |
| `startGoogleLogin(callbackUrl, destination)` | PKCE генерациялап Keycloak OAuth URL-ге redirect |
| `tryRefresh()` | refresh_token → жаңа access_token |
| `hydrate()` | localStorage-дан session қалпына келтіру |
| `logout()` | Store + localStorage тазалау |

### localStorage

Key: `webizon:auth`
Value: `{ token, refreshToken, expiresAt, user }` — JSON

---

## 5. Auth Plugin

**Файл:** `app/plugins/auth.client.ts`

Тек браузерде іске қосылады (`.client.ts`).

1. `auth.hydrate()` — localStorage-дан session оқиды
2. **Dev режимде** (`import.meta.dev = true`): Keycloak жоқ, автоматты mock session:
   ```ts
   token: 'dev-local-token'
   user: {
     email: 'dev@webizon.local',
     role: 'TENANT_OWNER',
     tenantSlug: 'dev-workspace'
   }
   ```
3. **Production:** 60 секунд сайын тексеріп, 2 минут қалса `tryRefresh()` шақырады

---

## 6. Auth Flows (Толық диаграмма)

### 6.1 Email + Пароль кіру

```
sign-in.vue (форма submit)
  ↓
auth.login(email, password)
  ↓
POST /api/backend/v1/public/auth/login
  ↓ [Nitro proxy]
POST /api/v1/public/auth/login         ← Backend-ке жетеді
  ↓
KeycloakAuthService.loginWithPassword()
  ↓
POST http://keycloak:8180/realms/webizon/protocol/openid-connect/token
    grant_type=password
    client_id=webizon-frontend
    client_secret=dev_client_secret
    username=email
    password=password
  ↓
← { access_token, refresh_token, expires_in }
  ↓
_applyTokens(tokens)
  ↓
POST /api/backend/v1/auth/bootstrap    (Authorization: Bearer <access_token>)
  ↓
AuthBootstrapController.bootstrap()
  ↓
UserService.bootstrapFromJwt()         ← PostgreSQL-де upsert жасайды
  ↓
← { user: {...}, memberships: [...] }
  ↓
setSession() → localStorage['webizon:auth']
  ↓
router.push('/admin')
```

### 6.2 Google OAuth (PKCE)

```
"Продолжить с Google" кнопкасы
  ↓
auth.startGoogleLogin()
  ↓
1. code_verifier  = random 32 bytes → base64url
2. code_challenge = SHA-256(verifier) → base64url
3. sessionStorage сақталады:
   - webizon:pkce_verifier
   - webizon:pkce_redirect_uri
   - webizon:auth_destination
  ↓
window.location.href = http://localhost:8180/realms/webizon/protocol/openid-connect/auth
    ?client_id=webizon-frontend
    &redirect_uri=http://localhost:3001/auth/callback
    &response_type=code
    &scope=openid profile email
    &kc_idp_hint=google         ← тікелей Google-ге жіберді
    &code_challenge=<S256>
    &code_challenge_method=S256
  ↓
[Keycloak → Google → пайдаланушы рұқсат береді]
  ↓
Keycloak redirect → /auth/callback?code=<code>&session_state=...
  ↓
callback.vue (onMounted)
  ↓
sessionStorage-дан pkce_verifier оқиды
  ↓
auth.handleOAuthCallback(code, redirectUri, codeVerifier)
  ↓
POST /api/backend/v1/public/auth/callback
  ↓
KeycloakAuthService.exchangeCode()
  ↓
POST keycloak: grant_type=authorization_code + code_verifier
  ↓
← tokens → _applyTokens() → bootstrap → setSession()
  ↓
router.replace(destination)  ← бастапқы бетке
```

### 6.3 Тіркелу (Registration)

```
sign-up.vue (форма submit)
  ↓
auth.register(fullName, email, password)
  ↓
POST /api/v1/public/auth/register
  ↓
KeycloakAuthService.register()
  ↓
1. getAdminToken()
   POST http://keycloak:8180/realms/master/.../token
       grant_type=password, client_id=admin-cli
   ← adminToken

2. createKeycloakUser(adminToken, fullName, email, password)
   POST http://keycloak:8180/admin/realms/webizon/users
       { username, email, firstName, lastName, credentials... }

3. loginWithPassword(email, password)
   ← tokens
  ↓
← tokens → _applyTokens() → bootstrap → setSession()
```

### 6.4 Token Refresh (автоматты)

```
auth.client.ts plugin
  ↓ (60 сек сайын)
(auth.expiresAt - Date.now()) < 2 минут?
  ↓ иә
auth.tryRefresh()
  ↓
POST /api/v1/public/auth/refresh
    { refreshToken: <current_refresh_token> }
  ↓
KeycloakAuthService.refreshToken()
  ↓
POST keycloak: grant_type=refresh_token
  ↓
← жаңа { access_token, refresh_token, expires_in }
  ↓
store + localStorage жаңартады
```

### 6.5 OTP Email Верификация

```
sign-up.vue — credentials дұрыс болса:
  ↓
POST /api/v1/public/auth/otp/request
    { email, password }
  ↓
KeycloakAuthService.loginWithPassword() ← credentials тексеру
  ↓ (дұрыс болса)
OtpService.generateAndSend(email, tokens)
  1. 6 цифрлық код генерациялайды
  2. Redis-ке сақтайды (5 минут TTL):
     Key:   "otp:{email}"
     Value: { code, tokens }
  3. Gmail SMTP арқылы email жібереді
  ↓
← { status: "sent", email: "..." }

Пайдаланушы кодты енгізеді:
  ↓
POST /api/v1/public/auth/otp/verify
    { email, code }
  ↓
OtpService.verifyAndGetTokens()
  → Redis-тен tokens оқиды
  ← tokens → _applyTokens() → bootstrap → setSession()
```

---

## 7. Backend Security Config

**Файл:** `config/SecurityConfig.java`

### Whitelist (JWT талап етілмейді)

```
/actuator/health/**
/actuator/info
/actuator/prometheus
/api/v1/public/**          ← auth, event landing
/api/v1/webhooks/**
/swagger-ui/**
/v3/api-docs/**
/error
```

### JWT Decoder

Екі issuer-ды қабылдайды:
- **Internal:** `http://keycloak:8180/realms/webizon` — Docker network ішіндегі Direct Grant токендері
- **External:** `http://localhost:8180/realms/webizon` — Google OAuth токендері (браузер hostname-ін қолданады)

### Role extraction

JWT-тен екі жерден рольдер алынады:
1. `realm_access.roles[]` — Keycloak realm roles
2. `role` claim — біздің custom mapper

Барлығы `ROLE_` префиксімен Spring Security-ге қосылады.

### RoleEnrichmentFilter

JWT-те tenant-специфик рольдер жоқ болса (Keycloak mapper орнатылмаса), бэкенд DB-дан алады:
```
JWT-ті оқиды → keycloakId → PostgreSQL: tenant_users WHERE user_id = ?
→ ROLE_TENANT_OWNER / TENANT_ADMIN / ... → SecurityContext-ке қосады
```

---

## 8. Keycloak конфигурациясы

**Файл:** `infra/keycloak/import/webizon-realm.json`

| Параметр | Мән |
|----------|-----|
| Realm | `webizon` |
| Client ID | `webizon-frontend` |
| Client Secret | `dev_client_secret` |
| Access Token Lifespan | 15 минут |
| Refresh Token Lifespan | 30 күн (rotating) |
| Google IDP | `trustEmail: true` (сол email → auto-link) |

**Docker-compose env (backend):**
```
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: http://keycloak:8180/realms/webizon
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: http://keycloak:8180/realms/webizon/protocol/openid-connect/certs
```

---

## 9. Дизайн талаптары

Sign-in / Sign-up беттері міндетті түрде:
- `definePageMeta({ layout: false })` — ешқандай layout жоқ, тәуелсіз бет
- **Қара фон:** `bg-slate-950`
- **Glassmorphism карточка:** `bg-white/[0.04]`, `backdrop-blur-md`, `border border-white/[0.08]`
- **Фон blob-тар:** brand/accent/violet gradient шеңберлер + grid pattern
- **Input:** `border-white/[0.08] bg-white/[0.04] text-white`
- **Submit кнопка:** `bg-brand-600 hover:bg-brand-500`

---

## 10. Жиі кездесетін қателер

### ❌ `Keycloak SSO-ға бейімдеймін` деп redirect жасау

**Дұрыс емес.** Webizon-ның өз UI-ы бар. Пайдаланушы Keycloak hosted login бетін көрмеуі керек.

### ❌ Bootstrap шақырмау

Keycloak token алғаннан кейін `bootstrap` шақырылуы **міндетті**. Bootstrap-сыз:
- `tenantId`, `tenantSlug`, `role` — null
- Барлық protected endpoint 403 береді

### ❌ SSR-де auth тексеру

Token localStorage-да, SSR оқи алмайды. Middleware SSR-де `return` (skip) жасайды.

### ❌ nuxt-oidc-auth немесе @sidebase/nuxt-auth орнату

Бұл library-лар стандартты OIDC redirect flow пайдаланады. Webizon-ға қажет емес.

### ❌ Dev mode-та backend шақыру

Dev mode-та `dev-local-token` жіберіледі, backend оны валид JWT деп қабылдамайды. Backend-тен мәлімет алу үшін Docker-де іске қосылған backend + Keycloak арқылы реалды токен керек.

---

## 11. Идентификаторлар иерархиясы

```
Keycloak User ID (sub claim) — UUID
    ↓ UserService.bootstrapFromJwt()
PostgreSQL users.id — UUID ← canonical app identity
    ↓
tenant_users.user_id → tenant_id, role
```

- `CurrentUser.keycloakId()` → JWT `sub` claim
- `CurrentUser.profileId()` → JWT `profile_id` claim (жоқ болса → keycloakId fallback)
- `UserService.requireByKeycloakId()` → PostgreSQL users.id
