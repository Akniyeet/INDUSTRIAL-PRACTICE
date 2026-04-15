# Webizon — Жаңа Маман Нұсқаулығы (Onboarding Guide)

> Жобаны алғаш рет іске қосу, архитектураны түсіну және дамытуды жалғастыру үшін осы нұсқаулықты оқы.

---

## 📎 Байланысты документация

| Осыны іске қосқан соң не оқу керек | Қай файл |
|------------------------------------|----------|
| Документация индексі (жалпы карта) | [docs/README.md](./README.md) |
| Не бар, қайта жасама тізімі — **МІНДЕТТІ** | [WHAT_EXISTS.md](./WHAT_EXISTS.md) |
| Жүйе архитектурасы — модульдер, RLS, pipeline | [ARCHITECTURE.md](./ARCHITECTURE.md) |
| Auth жүйесі — login, Google OAuth, OTP | [AUTH.md](./AUTH.md) |
| Барлық API endpoint-тар | [API.md](./API.md) |
| Соңғы өзгерістер | [CHANGELOG.md](./CHANGELOG.md) |

---

## 1. Алғышарттар (Prerequisites)

Компьютерде орнатылған болуы керек:

| Құрал | Нұсқа | Тексеру |
|-------|-------|---------|
| Docker Desktop | 4.x+ | `docker --version` |
| Node.js | 20+ | `node --version` |
| Java JDK | 21+ | `java --version` |
| Git | кез келген | `git --version` |

---

## 2. Жобаны жүктеп алу

```bash
git clone <repo-url> webizon
cd webizon
```

---

## 3. Іске қосу (Docker арқылы — ең оңай тәсіл)

### 3.1 `.env` файлын дайындау

```bash
cp .env.example .env
# .env ішінде ешнәрсе өзгертпесе де жергілікті (local) режимде жұмыс жасайды
```

### 3.2 Барлық сервистерді іске қосу

```bash
docker compose up -d
```

### 3.3 Барлығы іске қосылғанын тексеру

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

Мына 13 контейнер **healthy** болуы керек:

| Контейнер | Порт | Не үшін |
|-----------|------|---------|
| `webizon-postgres` | 5435 | Негізгі база |
| `webizon-redis` | 6382 | Кэш, OTP, session state |
| `webizon-kafka` | 9093 | Analytics pipeline |
| `webizon-zookeeper` | — | Kafka-ның тәуелділігі |
| `webizon-clickhouse` | 8123 | Analytics warehouse |
| `webizon-minio` | 9010, 9011 | Файл сақтау (суреттер, материалдар) |
| `webizon-keycloak` | 8180 | OIDC auth provider |
| `webizon-centrifugo` | 8000 | WebSocket (chat, CTA, presence) |
| `webizon-backend` | 8081 | Spring Boot API |
| `webizon-frontend` | 3001 | Nuxt 4 SSR |
| `webizon-kafka-ui` | 8090 | Kafka веб интерфейс |
| `webizon-grafana` | 3002 | Мониторинг дашборд |
| `webizon-prometheus` | 9090 | Метрика жинаушы |

---

## 4. Маңызды URL-дер

| URL | Не |
|-----|----|
| http://localhost:3001 | Фронтенд (негізгі қолданба) |
| http://localhost:3001/admin | Admin панелі |
| http://localhost:3001/platform | Platform Super Admin |
| http://localhost:8081/actuator/health | Backend денсаулық тексеру |
| http://localhost:8180 | Keycloak Admin Console |
| http://localhost:9011 | MinIO Console |
| http://localhost:8090 | Kafka UI |
| http://localhost:3002 | Grafana (admin/admin) |
| http://localhost:9090 | Prometheus |

---

## 5. Тест аккаунттары

| Email | Пароль | Рөл |
|-------|--------|-----|
| `webizon365@gmail.com` | `Test1234!` | Platform Super Admin + Tenant Owner |
| `askadmass@gmail.com` | — | Tenant пайдаланушысы |

> Keycloak Admin Console: http://localhost:8180 → admin / admin

---

## 6. Жергілікті фронтенд dev режимі (Docker-сыз)

Docker-ді тоқтатпай, тек фронтендтің dev серверін іске қосу:

```bash
cd frontend
npm install
npm run dev
# → http://localhost:3000 (немесе 3005)
```

**Маңызды:** dev режимде Keycloak жоқ. `auth.client.ts` plugin автоматты mock session орнатады:
```
email: dev@webizon.local
role: TENANT_OWNER
tenantSlug: dev-workspace
```
Backend-тен мәлімет алу үшін Docker-де бэкенд іске қосылып тұруы керек.

---

## 7. Backend-ті жергілікті іске қосу

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`application-local.yml` — Docker ішіне емес, `localhost`-қа қарайды.

---

## 8. Жиі кездесетін мәселелер

### Backend іске қосылмайды — Flyway checksum error
```sql
-- postgres контейнеріне кіріп:
docker exec -it webizon-postgres psql -U webizon webizon
UPDATE flyway_schema_history SET checksum = NULL WHERE version = 'XXX';
\q
# Backend-ті қайта іске қос
docker compose restart backend
```

### Frontend 500 қатесі — `useRuntimeConfig() inside useHead()`
`useRuntimeConfig()` `useHead()` callback ішінде шақырылмауы керек. Компонент `setup()` бастауында шақыр:
```ts
// ❌ Қате
useHead(() => ({ title: useRuntimeConfig().public.appName }))

// ✅ Дұрыс
const config = useRuntimeConfig()
useHead(() => ({ title: config.public.appName }))
```

### API URL `C:/Program Files/Git/api/backend/...` болып шығады
`docker run` командасын Git Bash-те іске қосқанда `/api/backend` → `C:/Program Files/Git/api/backend` болады.
**Шешімі:** тек `docker compose up -d` пайдалан, ешқашан `docker run -e NUXT_PUBLIC_API_BASE=/api/backend` емес.

### TenantContext not set — 500 Internal Error
JWT-те `tenant_id` claim жоқ және `X-Tenant-Id` header берілмеген. API шақырғанда:
```
X-Tenant-Id: 00000000-0000-0000-0000-000000000001
```

### `Invalid UTF-8 middle byte` — curl арқылы кирилл мәтін
curl командасымен кирилл мәтінді JSON-ға тікелей кірістірме. Temp файл пайдалан:
```bash
cat > /tmp/body.json << 'EOF'
{ "title": "Мәтін" }
EOF
curl -X POST ... --data-binary @/tmp/body.json
```

---

## 9. Деректер базасына тікелей кіру

```bash
docker exec -it webizon-postgres psql -U webizon webizon
```

Пайдалы сұраулар:
```sql
-- Tenant тізімі
SELECT id, slug, display_name, status FROM tenants;

-- Пайдаланушылар
SELECT id, email, full_name, platform_admin FROM users;

-- Event тізімі
SELECT id, slug, title, status FROM events;

-- Session тізімі
SELECT id, type, status, start_time FROM sessions ORDER BY created_at DESC;
```

---

## 10. Жоба құрылымы

```
webizon/
├── backend/          Java 21 + Spring Boot 3.2
├── frontend/         Nuxt 4 + Vue 3 + TypeScript
├── infra/            Keycloak, ClickHouse, Prometheus, Grafana конфигтары
├── docs/             Документация (осы файл осында)
│   ├── adr/          Architecture Decision Records
│   ├── ONBOARDING.md   ← осы файл
│   ├── ARCHITECTURE.md ← жүйе архитектурасы
│   ├── AUTH.md         ← авторизация жүйесі
│   ├── API.md          ← API endpoint-тар
│   └── CHANGELOG.md    ← өзгерістер тарихы
├── .env              Жергілікті env variables (git-ке кірмейді)
├── .env.example      .env үлгісі
├── docker-compose.yml
├── Makefile          Пайдалы командалар
└── README.md         Жоба шолу
```

---

## 11. Makefile командалары

```bash
make dev-up      # Docker сервистерін іске қосу
make dev-down    # Барлығын тоқтату
make logs        # Барлық логтар
make be-logs     # Тек backend логтары
make fe-logs     # Тек frontend логтары
make migrate     # Flyway миграция
make test        # Тесттерді іске қосу
```
