# Webizon — API Endpoint-тар

Base URL (Docker): `http://localhost:8081/api`  
Браузерден: `/api/backend` (Nitro proxy арқылы)

---

## 📎 Байланысты документация

| Мен іздеп жатқан нәрсе | Қай файл |
|------------------------|----------|
| Документация индексі (жалпы карта) | [docs/README.md](./README.md) |
| Токен алу — login, register, refresh | [AUTH.md](./AUTH.md) |
| Контроллерлер қай Java пакетінде тұр | [WHAT_EXISTS.md](./WHAT_EXISTS.md) |
| Nitro proxy — браузер → backend маршрут | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Жаңа endpoint қосылса тарихы | [CHANGELOG.md](./CHANGELOG.md) |

---

## Аутентификация

Барлық protected endpoint-тарда:
```
Authorization: Bearer <access_token>
X-Tenant-Id: <tenant-uuid>   ← JWT-те tenant_id claim жоқ болса
```

---

## Public Auth — `/api/v1/public/auth` (JWT талап етілмейді)

| Метод | Путь | Параметрлер | Жауап |
|-------|------|-------------|-------|
| POST | `/login` | `{ email, password }` | `{ access_token, refresh_token, expires_in }` |
| POST | `/register` | `{ fullName, email, password }` | `{ access_token, refresh_token, expires_in }` |
| POST | `/callback` | `{ code, redirectUri, codeVerifier }` | `{ access_token, refresh_token, expires_in }` |
| POST | `/refresh` | `{ refreshToken }` | `{ access_token, refresh_token, expires_in }` |
| POST | `/otp/request` | `{ email, password }` | `{ status: "sent", email }` |
| POST | `/otp/verify` | `{ email, code }` | `{ access_token, refresh_token, expires_in }` |

---

## Bootstrap — `/api/v1/auth` (JWT керек)

| Метод | Путь | Жауап |
|-------|------|-------|
| POST | `/bootstrap` | `{ user: {...}, memberships: [...] }` |

---

## Events — `/api/v1/events`

| Метод | Путь | Рөл | Не |
|-------|------|-----|-----|
| GET | `/` | OWNER/ADMIN | Event тізімі (pageable) |
| POST | `/` | OWNER/ADMIN | Жаңа event жасау |
| GET | `/{id}` | OWNER/ADMIN | Event мәліметі |
| PUT | `/{id}` | OWNER/ADMIN | Event жаңарту |
| POST | `/{id}/publish` | OWNER/ADMIN | DRAFT → PUBLISHED |
| POST | `/{id}/archive` | OWNER/ADMIN | → ARCHIVED |

---

## Sessions — `/api/v1/events/{eventId}/sessions`

| Метод | Путь | Рөл | Не |
|-------|------|-----|-----|
| GET | `/api/v1/events/{eventId}/sessions` | OWNER/ADMIN | Session тізімі |
| POST | `/api/v1/events/{eventId}/sessions` | OWNER/ADMIN | Жаңа session |
| GET | `/api/v1/sessions/{id}` | OWNER/ADMIN | Session мәліметі |
| PATCH | `/api/v1/sessions/{id}` | OWNER/ADMIN | Session жаңарту |
| POST | `/api/v1/sessions/{id}/start-live` | OWNER/ADMIN | SCHEDULED → LIVE |
| POST | `/api/v1/sessions/{id}/end-live` | OWNER/ADMIN | LIVE → ENDED |
| POST | `/api/v1/sessions/{id}/start-auto` | OWNER/ADMIN | AUTO_SCHEDULED → AUTO_LIVE |
| POST | `/api/v1/sessions/{id}/end-auto` | OWNER/ADMIN | AUTO_LIVE → AUTO_ENDED |
| POST | `/api/v1/sessions/{id}/cancel` | OWNER/ADMIN | → CANCELLED |

---

## Public Events — `/api/v1/public/tenants/{tenantSlug}/events/{eventSlug}` (JWT талап етілмейді)

| Метод | Путь | Жауап |
|-------|------|-------|
| GET | `/resolve` | `{ state, event, activeSession, nextSession, autoSlots }` |

`state` мәндері:
- `LIVE_NOW` — сессия қазір жүріп тұр
- `WAITING` — жақын арада басталады (30 минут ішінде)
- `SLOT_SELECTION` — бірнеше auto slot бар
- `LANDING` — тек landing page
- `UNAVAILABLE` — жоқ немесе draft

---

## Chat — `/api/v1/sessions/{sessionId}/chat`

| Метод | Путь | Не |
|-------|------|-----|
| GET | `/` | Хабарламалар (cursor pagination: `?before=<id>&limit=50`) |
| POST | `/` | Жаңа хабарлама `{ text, replyToMessageId? }` |
| DELETE | `/{messageId}` | Хабарлама жою (MODERATOR+) |

---

## Moderation — `/api/v1/sessions/{sessionId}/moderation`

| Метод | Путь | Не |
|-------|------|-----|
| POST | `/warn` | `{ targetProfileId, reason }` — private warning |
| POST | `/mute` | `{ targetProfileId, durationSeconds }` |
| POST | `/unmute` | `{ targetProfileId }` |
| POST | `/ban` | `{ targetProfileId }` |
| POST | `/unban` | `{ targetProfileId }` |

---

## CTA — `/api/v1/events/{eventId}/cta`

| Метод | Путь | Не |
|-------|------|-----|
| GET | `/` | CTA тізімі |
| POST | `/` | Жаңа CTA `{ title, description, buttonText, actionUrl, type, placement, priority }` |
| PUT | `/{ctaId}` | CTA жаңарту |
| DELETE | `/{ctaId}` | CTA жою |
| POST | `/sessions/{sessionId}/cta/{ctaId}/show` | CTA көрсету |
| POST | `/sessions/{sessionId}/cta/{ctaId}/hide` | CTA жасыру |

---

## Timeline — `/api/v1/events/{eventId}/timeline`

| Метод | Путь | Не |
|-------|------|-----|
| GET | `/` | Timeline action тізімі |
| POST | `/` | `{ offsetSeconds, actionType, payloadJson }` |
| PUT | `/{actionId}` | Жаңарту |
| DELETE | `/{actionId}` | Жою |

---

## Room — `/api/v1/room`

| Метод | Путь | Не |
|-------|------|-----|
| GET | `/sessions/{sessionId}/bootstrap` | Room bootstrap (event, session, capabilities, channels) |

---

## Storage — `/api/v1/storage`

| Метод | Путь | Рөл | Не |
|-------|------|-----|-----|
| POST | `/uploads` | PRESENTER+ | `{ purpose, contentType }` → presigned PUT URL |
| POST | `/uploads/{assetId}/confirm` | PRESENTER+ | Upload растау |
| GET | `/{assetId}` | authenticated | Metadata |
| GET | `/{assetId}/download-url` | authenticated | Presigned GET URL |
| DELETE | `/{assetId}` | OWNER/ADMIN | Жою |

`purpose` мәндері: `COVER`, `CTA_FILE`, `RECORDING`, `INVOICE`

---

## Analytics — `/api/v1/analytics`

| Метод | Путь | Не |
|-------|------|-----|
| POST | `/track` | Іс-әрекет тіркеу `{ sessionId, eventType, offsetSeconds?, metadataJson? }` |
| GET | `/sessions/{sessionId}/report` | Session analytics есеп |
| GET | `/sessions/{sessionId}/retention` | Retention chart data |

---

## Platform Admin — `/api/v1/platform` (isPlatformAdmin керек)

| Метод | Путь | Не |
|-------|------|-----|
| GET | `/dashboard` | Жалпы статистика |
| GET | `/tenants` | Барлық tenant тізімі |
| GET | `/tenants/{id}` | Tenant мәліметі |
| GET | `/users` | Барлық пайдаланушылар |
| GET | `/revenue` | Табыс есебі |

---

## Infrastructure — `/api/v1/admin/infrastructure`

| Метод | Путь | Не |
|-------|------|-----|
| GET | `/health` | Сервис статустары |
| POST | `/restart/{service}` | Сервисті қайта іске қосу |

---

## Real-time Tokens — `/api/v1/realtime`

| Метод | Путь | Не |
|-------|------|-----|
| POST | `/connect-token` | Centrifugo connection token |
| POST | `/subscribe-token` | Channel subscription token |
