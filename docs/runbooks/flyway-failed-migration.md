# Runbook: Flyway failed migration — backend won't start

## Симптомдар

Backend контейнер старт етпейді. Логта:

```
Migration checksum mismatch for migration version 0XX
Either revert the changes to the migration, or run repair to update the schema history.
```

Немесе:

```
Caused by: org.flywaydb.core.internal.exception.FlywayMigrateException: Script V0XX__*.sql failed
ERROR: duplicate key value violates unique constraint "..."
```

## Себебі

Flyway миграциялары `flyway_schema_history` кестесінде сақталады. Файл қайта-қайта өзгертілсе немесе бір рет сәтсіз орындалса — checksum mismatch болады немесе failed запись кіре алады. Flyway жаңа нұсқаны runtime-да іске қоспайды; қолмен араласу керек.

## Диагностика

```bash
docker exec webizon-postgres psql -U webizon -d webizon \
  -c "SELECT version, description, success, checksum FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

`success = f` деген жол бар ма, немесе қазіргі `.sql` файлдағы checksum DB-да сақталғаннан өзгеше ме — соны көріңіз.

## Fix (dev ортасы)

### 1. Failed migration-ды өшіру

```bash
docker exec webizon-postgres psql -U webizon -d webizon \
  -c "DELETE FROM flyway_schema_history WHERE version = '0XX';"
```

### 2. Кедергі жасайтын деректерді тазалау (қажет болса)

Егер миграция UNIQUE constraint-қа соғылса (мысал: `tenants_slug_key`), сол жолды табыңыз:

```bash
docker exec webizon-postgres psql -U webizon -d webizon \
  -c "SELECT * FROM tenants WHERE slug = 'X';"
```

Дев-те қаупсіз болса — DELETE. Prod-та — **ешқашан олай істеме**, алдымен миграцияны идемпотентті қылыңыз.

### 3. Backend-ті қайта іске қосу

```bash
docker restart webizon-backend
```

Flyway енді жаңартылған `.sql` файлды қайта орындайды.

## Болашақта қайталанбас үшін

Әр жаңа `V0XX__*.sql`:

1. **Идемпотентті болсын.** `ON CONFLICT DO NOTHING` (без target) `INSERT`-тер үшін барлық unique constraint-тарды қамтиды.
2. **Dev seed миграциялары тіркелген UUID-терді қолданса** — Keycloak realm JSON, конфиг, басқа миграциялар да сол UUID-ге сәйкес болсын. Tenant/user-ды slug+email бойынша таба алатын қосалқы жол бар болсын (V022-дағы `lower(u.email) = 'webizon365@gmail.com'`).
3. **Миграцияны committed етпеген кезде тест жасаңыз** — `docker compose logs -f backend` арқылы бірінші іске қосылғанын көріп алыңыз.
4. **Checksum mismatch-тан қорғану:** коммитке енген `.sql` файлды **ешқашан** өзгертпеңіз. Өзгерту қажет болса — жаңа `V0YY__*.sql` жасаңыз.

## Prod ортасы

Prod-та `flyway_schema_history`-ден жолды ешқашан DELETE етпеңіз. Орнына `flyway repair` қолданыңыз:

```bash
docker exec webizon-backend flyway -url=jdbc:postgresql://postgres:5432/webizon \
  -user=webizon -password=... repair
```

Repair: failed migrations-ты тазалайды, checksum-дарды қайта синхрондайды. Содан кейін backend restart.

## Related

- [CLAUDE.md §4 — Migrations: Flyway](../../CLAUDE.md)
- [ARCHITECTURE.md — Миграция нұсқалары](../ARCHITECTURE.md)
