# Public Event Landing

Route: `/e/{tenantSlug}/{eventSlug}` — the single entry point visitors hit from shared links, ads, and email blasts. Resolved server-side into one of five states (`LIVE_NOW`, `WAITING`, `SLOT_SELECTION`, `LANDING`, `UNAVAILABLE`) by `PublicEventResolution`, then rendered by `frontend/app/pages/e/[tenant]/[slug]/index.vue`.

This document describes the pieces an admin can influence, how they flow from the form to the page, and the marketing-theatre layer the frontend adds on top.

---

## 1. Data flow

```
Admin edits "Лендинг Builder" (Step 5 of EventForm)
        │
        ▼
EventForm.onSubmit → buildLandingConfig()
        │              (strips empty rows, defaults icon="Sparkles")
        ▼
POST/PATCH /api/v1/events{…}  body.landingConfig = {…}
        │
        ▼
EventService.create / update
        │
        ▼
events.landing_config JSONB  ← V016 migration
        │
        ▼
PublicEventResolution → PublicEventView.landingConfig
        │
        ▼
Public landing page renders Benefits + Timeline sections
```

### JSONB payload shape

```json
{
  "benefitsTitle": "Что вы узнаете",
  "benefits": [
    { "title": "Практические знания", "desc": "...", "icon": "Lightbulb" },
    { "title": "Живое общение",       "desc": "...", "icon": "MessageCircle" }
  ],
  "timelineTitle": "Программа эфира",
  "timelineSubtitle": "Пошаговый план урока",
  "timeline": [
    { "title": "Приветствие",    "desc": "..." },
    { "title": "Основная часть", "desc": "..." }
  ]
}
```

All fields are optional. The public page omits each section when its list is empty.

---

## 2. Admin form (Step 5 — "Лендинг Builder")

File: `frontend/app/components/admin/EventForm.vue`

### Available controls

| Field              | Type                         | Used on public landing                          |
|--------------------|------------------------------|-------------------------------------------------|
| `benefitsTitle`    | text                         | H2 above the benefits grid                      |
| `benefits[]`       | array `{title, desc, icon}`  | 3-column card grid (icon-picker: 22 lucide icons)|
| `timelineTitle`    | text                         | H2 above the program steps                      |
| `timelineSubtitle` | text                         | Supporting subtitle under H2                    |
| `timeline[]`       | array `{title, desc}`        | Numbered vertical timeline                      |

The icon picker maps a short name (`"Sparkles"`, `"Lightbulb"`, …) to a lucide component, kept in sync between admin form and public landing via `benefitIconMap`. Storing the name — not the SVG — lets the icon set evolve without touching the DB.

### Edit-mode preload

When `props.initial.landingConfig` is present, the form defensively coerces each field (`readString`, `readLandingItems`) and falls back to the marketing-friendly defaults only if the loaded list is empty. UI-only fields like `_iconOpen` (picker popover state) are never round-tripped to the backend.

---

## 3. Public landing rendering

File: `frontend/app/pages/e/[tenant]/[slug]/index.vue`

### Hero card

A 1:1 replica of the marketing landing's broadcast mockup, populated from admin data:

- **State badge** — `Эфирде` (red) / `Жақын арада басталады` (amber) / `Қайта көру` (violet). No fallback badge is rendered outside those three states.
- **Title** + **first description paragraph**. The description has a set of "redundant prefix" regexes that strip leading labels like `Ашық вебинар:` — admins keep typing them out of habit, so we clean them at render time.
- **Countdown** (rendered when `nextSession` exists) + start-time chip.
- **Primary CTA** (`Эфирге орын алу` / `Күту бөлмесіне кіру` / `Эфирге кіру`).
- **Scarcity twin** — the "57 орын қалды" badge, sized to match the CTA button's frame exactly (`padding: 14px 22px; border-radius: 14px; font-size: 15px`) so they read as a pair.
- **Cover preview** with LIVE badge, viewer count, three orbiting metric chips, a `Тегін чек-листті алу → Жүктеу` CTA overlay, and a rotating info feed (see §4).

### Benefits grid (optional)

Renders when `benefitsList.length > 0`. 3-column responsive grid with hover lift, gradient glow, and a lucide icon in a pastel `bg-gradient-to-br from-accent-500/20 to-brand-500/20` tile.

### Timeline (optional)

Renders when `timelineList.length > 0`. Numbered vertical list with a gradient spine running top-to-bottom. Each step card hover-lights its violet border.

### Speaker block

Uses admin-entered `speakerName` + `speakerBio`. Avatar is the first letter of the name on a brand gradient.

### Closing CTA

Mirrors the hero CTA — same `Эфирге орын алу` / `Күту бөлмесіне кіру` / `Эфирге кіру` label, same reserved state, same countdown + spots badge pair. Sits at the bottom of the page to catch visitors who scrolled past the hero.

---

## 4. Rotating info feed

The "chat feed" visual on the hero card is not chat — it cycles through the admin-entered event facts so the card feels alive while actually teaching the visitor something.

Pool (from `infoPool` computed):

| Row          | Source                                   |
|--------------|------------------------------------------|
| 📅 Басталуы  | `nextSession.startTime` (ru-RU formatted)|
| ⏱ Ұзақтығы   | `nextSession.plannedDurationSeconds / 60`|
| 👤 Спикер    | `event.speakerName`                       |
| 🔥 Орын саны | Static "Шектеулі — тезірек тіркеліңіз"   |
| 🎯 Тақырыбы  | `event.title`                             |
| 📡 Форматы   | `LIVE_NOW` / `LIVE` → "Тікелей эфир", else "Автотрансляция" |
| 💡 Не туралы | `descriptionParagraphs[0]`                |

A new row enters every 2.5s via `TransitionGroup`; the feed caps at 3 visible rows. Viewer count jitters ±2 on each tick for authenticity.

---

## 5. Reservation flow

File: `pages/e/[tenant]/[slug]/index.vue` — `reserveSpot()`.

### Why not a real API

No backend seat cap exists. The counter is marketing theatre — but we don't want it to visibly decrement every time the same visitor clicks. So:

1. On first click: `hasReserved = true` persists to `localStorage[webizon:reserved:{tenant}:{slug}]`, the counter decrements once (pulse animation), persisted to `localStorage[webizon:spots:{tenant}:{slug}]`.
2. Subsequent clicks: no-op. The button flips to an emerald **"Орын сізге броньдалды"** with a `CheckCircle2` icon, pointer-events disabled.
3. After sign-in / sign-up redirect: the flag survives because it's in `localStorage`, not Pinia. The user lands back on the page already reserved.

The floor is `SPOTS_FLOOR = 3` — the counter never dips below that, so late arrivals still see urgency without hitting "0 orын қалды".

### Button copy rules

| State             | Default label              | Reserved label             |
|-------------------|----------------------------|----------------------------|
| `LIVE_NOW`        | `Эфирге кіру`              | _(no reserved state — live already)_ |
| `WAITING`         | `Күту бөлмесіне кіру`      | `Орын сізге броньдалды`    |
| `LANDING` (session) | `Эфирге орын алу`        | `Орын сізге броньдалды`    |
| `LANDING` (no session) | `Эфирге орын алу`     | `Орын сізге броньдалды`    |

The closing CTA follows the same rules. `LIVE_NOW` deliberately skips the reserved branch — once the stream is live, the button must always say `Эфирге қазір кіру`.

---

## 6. Backend contract

### Migration (V016)

```sql
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS landing_config JSONB NOT NULL DEFAULT '{}'::jsonb;
```

`NOT NULL DEFAULT '{}'` means every existing row was backfilled with an empty map at deploy time, so the DTO can safely assume the column is never null.

### Entity (`Event.java`)

```java
@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
@Column(name = "landing_config", nullable = false, columnDefinition = "jsonb")
private Map<String, Object> landingConfig = new HashMap<>();
```

### DTOs

| DTO                  | Field semantics                                               |
|----------------------|---------------------------------------------------------------|
| `EventCreateRequest` | Optional; `null` collapses to `{}` in `EventService.create`.  |
| `EventUpdateRequest` | Optional; `null` means "no change", empty map explicitly clears.|
| `EventResponse`      | Always present; falls back to `{}` when the entity row is null.|
| `PublicEventView`    | Always present; same defensive fallback.                      |

### Service rules

- **Create** — `request.landingConfig != null ? request.landingConfig : new HashMap<>()`. The JSONB column is `NOT NULL`, so we never pass `null`.
- **Update** — only overwrite when `request.landingConfig != null`. A caller that wants to clear the config passes an empty map.

---

## 7. Sections removed in this pass

| Removed                                                   | Why                                       |
|-----------------------------------------------------------|-------------------------------------------|
| "Жаңа эфир — жақын арада" fallback badge                 | User request — cluttered the hero.        |
| "Тіркеліп, эфир басталғанда автоматты ескертпе…" copy    | User request — implied a feature we don't have. |
| Hardcoded fake chat messages with likes                  | Replaced by the rotating event info feed (§4). |
| "Эфир туралы ақпарат" heading (earlier pass)              | Chat feed now visually fills the gap.     |

---

## 8. File map

| File                                                                      | Role                                   |
|---------------------------------------------------------------------------|----------------------------------------|
| `backend/.../db/migration/V016__event_landing_config.sql`                 | JSONB column migration                 |
| `backend/.../events/model/Event.java`                                     | Hibernate entity with JSON column      |
| `backend/.../events/api/dto/EventCreateRequest.java`                      | Admin create payload                   |
| `backend/.../events/api/dto/EventUpdateRequest.java`                      | Admin PATCH payload                    |
| `backend/.../events/api/dto/EventResponse.java`                           | Admin API response                     |
| `backend/.../events/api/dto/PublicEventView.java`                         | Public resolver response               |
| `backend/.../events/service/EventService.java`                            | Null-safe persistence                  |
| `frontend/shared/api/types.ts`                                            | `LandingBenefit`, `LandingTimelineItem`, `LandingConfig` |
| `frontend/app/components/admin/EventForm.vue`                             | Step 5 editor + submit wiring          |
| `frontend/app/pages/e/[tenant]/[slug]/index.vue`                          | Public rendering, info rotation, reservation |
