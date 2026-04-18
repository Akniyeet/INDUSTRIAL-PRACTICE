# Webizon — Документация Индексі

> **Жаңа агент немесе маман болсаң — алдымен осы файлды оқы, содан кейін сілтемелер бойынша жүр.**

---

## 🗺️ Қай жерде не бар — жылдам навигация

| Сен іздеп жатқан нәрсе | Қай файлда |
|------------------------|-----------|
| **Жаңа AI агент немесе маман — бірінші осыны оқы** | → **[AGENT_HANDOFF.md](./AGENT_HANDOFF.md)** |
| Жобаны іске қосу, Docker, URL-дер, тест аккаунттар | → **[ONBOARDING.md](./ONBOARDING.md)** |
| Не бар, не жасалған, қайта жасама тізімі | → **[WHAT_EXISTS.md](./WHAT_EXISTS.md)** |
| Жүйе архитектурасы, сервистер, DB миграциялар | → **[ARCHITECTURE.md](./ARCHITECTURE.md)** |
| Авторизация — login, Google OAuth, token refresh | → **[AUTH.md](./AUTH.md)** |
| Google OAuth-ты дөңгелектеуге арналған runbook | → **[runbooks/google-oauth-setup.md](./runbooks/google-oauth-setup.md)** |
| Барлық API endpoint-тар | → **[API.md](./API.md)** |
| Соңғы өзгерістер, bug fix-тер, не өзгерді | → **[CHANGELOG.md](./CHANGELOG.md)** |

---

## 📚 Файл-файл сипаттамасы

### [WHAT_EXISTS.md](./WHAT_EXISTS.md)
**Бірінші оқылатын файл.** Жобада не жасалғаны — frontend беттер, компоненттер, backend контроллерлер, сервистер, DB кестелері, infra файлдары — бәрі тізімделген.
- ❌ ЕШҚАШАН ЖАСАМА тізімі (қайта жазбайтын дүниелер)
- ✅ МІНДЕТТІ ЖАСА тізімі (өзгерістен кейін жаңартылатын файлдар)
- **Байланысты:** [ARCHITECTURE.md](./ARCHITECTURE.md) (архитектура деталдары), [AUTH.md](./AUTH.md) (auth flow), [API.md](./API.md) (endpoint деталдары)

---

### [ONBOARDING.md](./ONBOARDING.md)
**Жобаны алғаш рет іске қосу нұсқаулығы.** Docker команды, барлық сервис порттары, тест аккаунттар, жиі кездесетін қателер мен шешімдері.
- **Байланысты:** [WHAT_EXISTS.md](./WHAT_EXISTS.md) (не бар — алдымен оқы), [ARCHITECTURE.md](./ARCHITECTURE.md) (жүйені түсіну үшін)

---

### [ARCHITECTURE.md](./ARCHITECTURE.md)
**Жүйе архитектурасы.** Multi-tenancy (RLS), backend модульдері, frontend құрылымы, real-time (Centrifugo), analytics pipeline (Kafka→ClickHouse), MinIO, мониторинг.
- DB миграция тарихы: V001–V019, **келесі: V020**
- **Байланысты:** [WHAT_EXISTS.md](./WHAT_EXISTS.md) (нақты файлдар), [AUTH.md](./AUTH.md) (SecurityConfig, JWT decoder), [API.md](./API.md) (endpoint тізімі)

---

### [AUTH.md](./AUTH.md)
**Авторизация жүйесі.** Email+пароль, Google OAuth PKCE, OTP верификация, token refresh — барлық flow-лар қадам-қадам диаграммалармен. Frontend/backend файл локациялары, Keycloak конфигурациясы.
- ⚠️ Keycloak SSO redirect **жоқ** — Custom form + Direct Grant
- **Байланысты:** [WHAT_EXISTS.md](./WHAT_EXISTS.md) (frontend/backend файлдар), [API.md](./API.md) (`/api/v1/public/auth/*` endpoints), [ARCHITECTURE.md](./ARCHITECTURE.md) (SecurityConfig, RoleEnrichmentFilter)

---

### [API.md](./API.md)
**Барлық REST API endpoint-тары.** Base URL, auth headers, барлық route-тар методтармен, параметрлермен, жауап форматымен.
- **Байланысты:** [AUTH.md](./AUTH.md) (токен алу), [WHAT_EXISTS.md](./WHAT_EXISTS.md) (контроллер локациялары), [ARCHITECTURE.md](./ARCHITECTURE.md) (Nitro proxy арқылы қалай жетеді)

---

### [CHANGELOG.md](./CHANGELOG.md)
**Өзгерістер тарихы.** Барлық маңызды өзгерістер, bug fix-тер, архитектуралық шешімдер — хронологиялық тәртіппен.
- **Ереже:** Кез келген маңызды өзгерістен кейін жазылуы **міндетті**
- **Байланысты:** [WHAT_EXISTS.md](./WHAT_EXISTS.md) (нені өзгерттің соны тексер), [ARCHITECTURE.md](./ARCHITECTURE.md) (миграция нұсқасын жаңарт)

---

## 🔄 Бір-бірімен байланысы

```
ONBOARDING.md ──────────────────────────────────────────────────────────┐
(Жобаны іске қосу)                                                      │
         │                                                               │
         ▼                                                               │
  WHAT_EXISTS.md ◄──────────────────────────────────────────────────────┘
  (Не бар — алдымен оқы)
         │
         ├──► ARCHITECTURE.md    ◄──► AUTH.md
         │    (Жүйе архитектурасы)     (Auth flow)
         │           │                    │
         │           └────────────────────┤
         │                                │
         └──► API.md ◄───────────────────┘
              (Endpoint-тар)
         │
         └──► CHANGELOG.md
              (Өзгерістер тарихы)
```

---

## ⚡ Жылдам анықтама

```
Жаңа feature жасаймын:
  1. WHAT_EXISTS.md → не бар екенін тексер
  2. ARCHITECTURE.md → қай модульге кіреді
  3. API.md → endpoint бар ма
  4. Жасап болған соң → CHANGELOG.md жаңарт

Bug fix жасаймын:
  1. CHANGELOG.md → бұрын болды ма тексер
  2. AUTH.md / ARCHITECTURE.md → байланысты систем
  3. Fix жасаған соң → CHANGELOG.md жаз

Жаңа маман / агент болсаң:
  1. ONBOARDING.md → Docker-ді іске қос
  2. WHAT_EXISTS.md → не бар екенін біл (міндетті!)
  3. ARCHITECTURE.md → жүйені түсін
```
