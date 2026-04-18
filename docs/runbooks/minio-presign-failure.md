# Runbook — MinIO presign / cover upload failure

**Симптом:** Admin UI-да event wizard-те cover таңдағанда "silent fail" — форма cover-сыз сақталады, network tab-те `POST /api/v1/storage/uploads` 500 қайтарады. Немесе backend log-ында:

```
Failed to connect to localhost/[0:0:0:0:0:0:0:1]:9010
```

немесе

```
UnknownHostException: minio
```

## Диагностика (қысқа)

```bash
# 1. MinIO контейнері тірі ме және network-та ма?
docker ps --filter name=webizon-minio --format "{{.Status}}\t{{.Networks}}"
# Желімді: `Up ... (healthy)  webizon_webizon-net`
# Апатты:  `Up ...  ` (network бос) — reattach керек

# 2. Backend MinIO-ға internal hostname-мен жете ала ма?
docker exec webizon-backend nc -zv minio 9000
# Желімді: `Connection to minio 9000 port [tcp/*] succeeded!`

# 3. Buckets бар ма?
docker exec webizon-minio mc alias set local http://localhost:9000 minioadmin minioadmin >/dev/null
docker exec webizon-minio mc ls local
# webizon-covers, webizon-cta-files, webizon-recordings, webizon-invoices көріну керек

# 4. Presign call backend-тен network-қа шыға ма?
curl -s -X POST http://localhost:8081/api/v1/storage/uploads \
  -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Id: $TENANT" \
  -H "Content-Type: application/json" \
  -d '{"purpose":"COVER","contentType":"image/png","sizeBytes":100,"filename":"t.png"}'
# Желімді: { "assetId": ..., "uploadUrl": "http://localhost:9010/..." }
```

## Fix қадамдары (ең жиі → ең сирек)

### 1. MinIO желіден үзіліп қалған

```bash
docker compose up -d minio
# Контейнер қайта старт болып webizon_webizon-net-ке қосылады.
# Backend perm-permdaxic не initializer bucket-тарды автоматты re-ensure етеді.
```

### 2. Presign slow / fails → SDK `getBucketLocation` preflight call жасап жатыр

Бұл fix-і **құрылған**, бірақ оны реверт еткен жағдайда қайталанады:

- `backend/src/main/java/com/webizon/storage/config/MinioClientConfig.java` → `internalMinioClient` және `publicMinioClient` екеуіне де `.region("us-east-1")` тіркелуі керек.
- Бұл SDK-ға region-ды қамтамасыз етеді → preflight lookup skip → presign таза local HMAC operation → backend publicMinioEndpoint-қа network-пен тиюі шарт емес.

Код қалпында екенін тексеру:
```bash
grep -n "region\|FIXED_REGION" backend/src/main/java/com/webizon/storage/config/MinioClientConfig.java
```

### 3. Buckets жоқ (fresh clone / volume reset)

`MinioBucketInitializer` `ApplicationReadyEvent`-те автоматты құрады — bucket жоқ болса:

```bash
docker compose restart backend
# немесе qolmen:
docker exec webizon-minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker exec webizon-minio mc mb --ignore-existing local/webizon-covers
docker exec webizon-minio mc mb --ignore-existing local/webizon-cta-files
docker exec webizon-minio mc mb --ignore-existing local/webizon-recordings
docker exec webizon-minio mc mb --ignore-existing local/webizon-invoices
docker exec webizon-minio mc anonymous set download local/webizon-covers
```

### 4. `MINIO_PUBLIC_ENDPOINT` қате

**Дұрыс мәндер:**
- `.env` (local Docker Desktop Mac/Windows): `MINIO_PUBLIC_ENDPOINT=http://localhost:9010`
- Production: `https://storage.webizon.kz` (шын CDN домені)

**Еш уақытта `http://minio:9000`** public endpoint ретінде **қоймаңыз** — browser оны шешмейді.

### 5. Realm-дағы phantom `tenant_id` → FK violation

Егер upload сәтті, бірақ `POST /api/v1/events` немесе `/storage/uploads/{id}/confirm` `file_assets_tenant_id_fkey` FK violation берсе:

```bash
# JWT-дегі tenant_id DB-да жоқ. Realm-дан clean up:
docker exec webizon-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8180 --realm master --user admin --password admin
docker exec webizon-keycloak /opt/keycloak/bin/kcadm.sh update users/<USER_UUID> \
  -r webizon -s 'attributes={}'
# + webizon-frontend client-тен tenant_id protocol mapper жою
```

JWT енді `tenant_id` claim-сіз келеді → `TenantContextFilter` X-Tenant-Id header-ден fallback оқиды (frontend bootstrap endpoint-тан алады).

## Верификация

```bash
# Full end-to-end cycle
bash /tmp/create_webinars.sh   # 3 wb жасайды, cover уплоадпен
# Результат: 3 event-те де cover URL толық, MinIO-ға бинарлар байт-by-байт дәл сақталған.
```

## Related

- CLAUDE.md §19 — Split-horizon hostname rule (қазір region-pin әмбебап шешімі бар, hostname split talap етілмейді).
- `docs/CHANGELOG.md` — `2026-04-19 — MinIO: SDK region pin`.
- `MinioClientConfig.java` javadoc-тары — неге region-pin барлық dev-hairpin мәселесін жояды.
