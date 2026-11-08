# Handoff Report: Authoritative Backend & API Specification Survey

**Author:** `teamwork_spec_miner`  
**Working Directory:** `c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_backend_survey\`  
**Target:** Implementation of Mentor Hub / Mentor Tracker backend on Spring Boot 3.4.2 GA / Java 21 / PostgreSQL 16 / Flyway / TelegramBots 10.3 / Spring AI 1.0.0-M5 (with Next-Gen target 4.x/2.x compatibility).  
**Timestamp:** 2026-11-08T14:40:00Z  

---

## 1. Observation

Direct observations extracted from authoritative source documents and codebase files:

### 1.1. Authoritative Requirements & Documents Observed
- **`ORIGINAL_REQUEST.md` (lines 14–46):**
  - R1 specifies Spring Boot 3.4.2 GA / Java 21 / PostgreSQL with Flyway migrations.
  - Endpoints required: `/api/inbox` (review queue for content drafts BEFORE publication: `#контент`, `#рилс`, `#карусель`, `#сторителл`; mentor questions; AI-extracted reflection summary; approval/rejection), `/api/students` (student profiles, discipline calendar with interactive tooltips, sick/pause toggle «В строю 🔥 / Болеет ☕» with streak freeze, Sunday official rest day), `/api/analytics` (10 cross-cutting metrics, Instagram case generation text), `/api/tasks` (assignment rules and hashtags), `/api/system` (health and Zoom tracker).
  - Two-tier passwordless administrator access via `admin_tokens`: Ksenia (Main Mentor) and Katya (Curator).
  - Existing 73 frontend automated tests in `scripts/test-runner.mjs` must pass at 100%.
- **`docs/ARCHITECTURE_SPECIFICATION.md`:**
  - Section 2 & 9: Full DDL schema for PostgreSQL 16 including `students`, `admin_tokens`, `zoom_meetings`, `task_rules`, `daily_reports`, `reel_submissions`, `sales_entries`, `mentor_settings`, and `audit_message_logs`.
  - Section 3: Reels Anti-Fraud Engine (`ReelsAntiFraudEngine` with SHA-256 deduplication and HTTP HEAD/GET probe).
  - Section 4: Hashtag rules and daily accumulator upsert via unique constraint `uq_student_report_date (student_id, report_date)`.
  - Section 5: `@Scheduled` Daily Reminder Engine running at `0 0 20 * * MON-SAT` (Europe/Minsk).
  - Section 6: Spring AI pipeline extracting `ExtractedReportDto` (with `BURNOUT_ALERT` sentiment and 3-bullet summary).
  - Section 10: 14 OpenAPI endpoints matching the React SPA.
- **`frontend/src/types.ts` & `frontend/src/mockData.ts`:**
  - DTO types: `DailyReport`, `ContentDraft` (types: `'reel'`, `'carousel'`, `'storytell'`), `ReflectionSummary` (`emotions`, `actions`, `questionToMentor`), `StudentDiscipline`, `DayInfo` (`DayStatus`: `'COMPLETED_ON_TIME' | 'COMPLETED_LATE' | 'MISSED' | 'PENDING' | 'WEEKEND' | 'PAUSED'`), `TaskRule`.
- **`scripts/test-runner.mjs` (73 tests across 4 tiers):**
  - Verifies exact field names, brand neutrality (zero occurrences of forbidden external brands), currency formatting (`ru-RU`), division-by-zero protection in multiplier calculation (`calculateMultiplier`), Saturday as current day in cohort grid, and Sunday as `WEEKEND`.

---

## 2. Logic Chain

1. **Alignment of UI, API, and DB Models:**
   - The React frontend expects specific camelCase properties in JSON responses (`contentDrafts`, `reflectionSummary`, `weekDays`, `missedDetails`, `isOnPause`, `disciplineRate`, `nextZoomDate`, `completedZoomsCount`).
   - The PostgreSQL database uses snake_case relational columns with standard foreign keys and constraints (`student_id`, `report_date`, `is_paused`, `next_zoom_at`).
   - Therefore, Spring Boot Jackson serialization must be configured with `PropertyNamingStrategies.LOWER_CAMEL_CASE` (default in Spring Boot) and DTO mapping layers (e.g. MapStruct or records) must cleanly map entity attributes to the client contracts.

2. **Content Drafts vs. Reel Submissions:**
   - Initial specifications focused strictly on Instagram Reels (`reel_submissions`).
   - The updated business requirement (R1/R2) and frontend types expand this to three content draft categories checked *BEFORE* publication: Reels (`reel`), Carousels (`carousel`), and Storytells (`storytell`).
   - Therefore, the database and API must accommodate both:
     - `reel_submissions` remains dedicated to cryptographic SHA-256 anti-fraud and HTTP accessibility probing for video links.
     - `content_drafts` (or a unified drafts table/child entity) captures draft text, slide counts, PDF/media attachments, and draft validation states.

3. **Discipline Calculation & Sick/Pause Mechanics:**
   - Sunday is an official rest day: marked as `status: 'WEEKEND'`, tooltip: `'Воскресенье — общий выходной потока ☕'`, and excluded from the denominator in `disciplineRate` calculations.
   - When an admin toggles a student to pause (`is_paused = true`), days during the pause are assigned `status: 'PAUSED'`, tooltip: `'Режим паузы: болезнь, стрик заморожен ☕'`.
   - Days with `status = 'PAUSED'` or `'WEEKEND'` do not break the active discipline streak and are excluded from late/missed penalties.
   - Reports submitted before 21:00 Europe/Minsk get `COMPLETED_ON_TIME`; between 21:00 and 23:59 get `COMPLETED_LATE`; no submission yields `MISSED`.

4. **10 Cross-Cutting Metrics:**
   - The analytics module aggregates 10 distinct dimensions across all students:
     1. Reels verified count (`totalReelsCount`)
     2. Carousels verified count (`totalCarouselsCount`)
     3. Storytells verified count (`totalStorytellsCount`)
     4. Direct mailings/outreach total (`totalMailingsCount`)
     5. Cohort revenue / Point B income total (`totalIncome`)
     6. Cohort average ticket (`currentAvgTicket`)
     7. Point A -> Point B revenue growth factor and percentage
     8. Average ticket growth factor and percentage
     9. New clients count (`totalNewClientsCount`, collected via Saturday 18:00 poll)
     10. AI reflection summary & emotional status index (`BURNOUT_ALERT` frequency)

5. **Security & Two-Tier Admin Access:**
   - Ksenia (`MENTOR`) has complete system privileges and Ksenia's personal Telegram Business Connection (`business_connection_id`) sends replies directly on her behalf.
   - Katya (`CURATOR`) has operational privileges (pause toggles, Zoom scheduling, viewing queues, triggering sync).
   - Passwordless magic links via `admin_tokens` eliminate credential friction on mobile/iPad devices.

---

## 3. Caveats

1. **Spring AI Prototyping & Offline Stubs:**
   - External LLM / Speech-to-Text services (OpenAI Whisper / Gemini) require valid API keys and internet connectivity.
   - As mandated by R2.3, the system must incorporate an automated mock/stub fallback (`AiReportExtractor` fallback and `WhisperStub`) so that offline builds, unit tests, and CI/CD pipelines succeed with 0 external dependencies.
2. **Timezone Anchor:**
   - All deadline checks (21:00 deadline, 20:00 reminders, Saturday 18:00 polls) strictly reference `Europe/Minsk` (UTC+3, no DST changes). Database timestamps must be stored as `TIMESTAMP WITH TIME ZONE` (UTC internally) and converted to `Europe/Minsk` for day-boundary evaluations.
3. **Russian Locale Currency & Number Formatting:**
   - Financial amounts (e.g. `45 000 ₽`) must be handled as `BigDecimal` / `NUMERIC(12,2)`. When parsing incoming text, grouping separators (spaces or periods like `35.500 руб`) must not be parsed as decimal separators, avoiding 1000-fold undercounting.

---

## 4. Conclusion

The specification is fully established, complete, and mutually consistent across the documentation, frontend types, test runner assertions, and relational database schema. The backend can now be constructed cleanly according to the following exhaustive technical blueprint.

---

## 5. Verification Method

To verify that the implementation adheres to this specification:
1. **Frontend Integration & Compatibility:**
   ```powershell
   node scripts/test-runner.mjs
   ```
   Must pass all 73 assertions across 4 tiers with exit code 0.
2. **Backend Unit & Integration Tests:**
   ```powershell
   mvn clean test
   ```
   Must execute all controller, service, repository, and migration tests with 100% success.
3. **Database Migration Verification:**
   Flyway must apply all migrations (`db/migration/V1__*.sql`) to PostgreSQL on startup without validation errors.
4. **Invalidation Conditions:**
   - Any HTTP 4xx/5xx on required endpoints with valid mock data.
   - Any brand name leak violating brand neutrality.
   - Failure to freeze streak during paused days.
   - Discrepancy in the 10 cross-cutting analytics metrics.

---

# Detailed Specification Reference

## Part I: Complete REST API Controllers & Endpoints

Base Path: `/api`  
Content-Type: `application/json; charset=UTF-8`  
Standard Error Representation: RFC 7807 (Problem Details for HTTP APIs)

### 1. Controller: `InboxController` (`/api/inbox`)

#### 1.1 `GET /api/inbox`
- **Purpose:** Fetches the pending daily report queue for mentor review (Inbox Zero workflow). Contains content drafts *BEFORE* publication, recognized mentor questions, AI reflection summaries, and anti-fraud alerts.
- **Query Parameters:**
  - `date` *(optional, `LocalDate`, format: `YYYY-MM-DD`)*: Defaults to current date in `Europe/Minsk`.
  - `status` *(optional, string, default: `PENDING`)*: Filter by report status (`PENDING`, `APPROVED`, `REJECTED`, `ALL`).
- **Response Status:** `200 OK`
- **Response Schema:** `List<DailyReportResponse>`
```json
[
  {
    "id": "rep-1",
    "studentId": "st-1",
    "studentName": "Милана Горохова",
    "avatarText": "МГ",
    "tgUsername": "milana_gorohova",
    "tariff": "VIP",
    "submittedAt": "18:24 (сегодня)",
    "isOnTime": true,
    "status": "PENDING",
    "contentDrafts": [
      {
        "id": "cd-1",
        "type": "reel",
        "label": "Reels #14 — продающий хук для стилиста",
        "isDuplicate": false,
        "duplicateDate": null,
        "url": "https://instagram.com/reel/DC194kaP91m"
      },
      {
        "id": "cd-2",
        "type": "carousel",
        "label": "Карусель: 5 ошибок при подборе капсулы (PDF)",
        "isDuplicate": false,
        "duplicateDate": null,
        "url": null
      }
    ],
    "reels": [
      {
        "id": "r-1",
        "url": "https://instagram.com/reel/DC194kaP91m",
        "shortcode": "DC194kaP91m",
        "isValid": true,
        "isDuplicate": false,
        "duplicateDate": null
      }
    ],
    "salesAmount": 45000.00,
    "mailingsCount": 25,
    "newClientsCount": 2,
    "mentorQuestion": "Ксения, клиентка хочет рассрочку на 3 месяца через банк, но у нее плохая кредитная история. Предложить внутреннюю рассрочку от себя или лучше не рисковать?",
    "voiceTranscript": null,
    "reflectionSummary": {
      "emotions": "Бодрое состояние, вдохновлена первой крупной продажей за неделю",
      "actions": "Отправлено 25 рассылок по скрипту #3, смонтирован черновик Reels и карусель на 5 слайдов",
      "questionToMentor": "Как лучше оформить внутреннюю рассрочку без банка?"
    },
    "aiSummary": "Сдан черновик Reels #14 и карусель на 5 слайдов. 25 рассылок в Директ, продажа 45 000 ₽, +2 новых клиента."
  }
]
```

#### 1.2 `POST /api/inbox/{reportId}/approve`
- **Purpose:** Mentor approves daily report and content drafts. If `replyText` is provided, automatically dispatches feedback to student in Telegram via Ksenia's Business Connection (`business_connection_id`).
- **Path Parameter:** `reportId` (String / Long)
- **Request Body:**
```json
{
  "replyText": "Отличная работа по рассылкам! С рассрочкой лучше заключить договор с графиком платежей."
}
```
- **Response Status:** `200 OK`
- **Response Body:**
```json
{
  "success": true,
  "reportId": "rep-1",
  "status": "APPROVED",
  "tgMessageDelivered": true,
  "approvedAt": "2026-11-08T18:40:12Z"
}
```
- **Error Behavior:**
  - `404 Not Found`: Report ID does not exist.
  - `400 Bad Request`: Report is already in `APPROVED` status.
  - If Telegram message dispatch fails (e.g. user blocked bot), database status is still set to `APPROVED`, but `tgMessageDelivered: false` is returned with an audit log record.

#### 1.3 `POST /api/inbox/{reportId}/reject`
- **Purpose:** Rejects report or content draft with required feedback explanation sent to Telegram.
- **Path Parameter:** `reportId` (String / Long)
- **Request Body:**
```json
{
  "reason": "Ссылка на Reels ведет на закрытый аккаунт. Открой профиль и пришли ссылку заново."
}
```
- **Response Status:** `200 OK`
- **Response Body:**
```json
{
  "success": true,
  "reportId": "rep-2",
  "status": "REJECTED",
  "feedbackSent": true,
  "rejectedAt": "2026-11-08T18:45:00Z"
}
```

---

### 2. Controller: `StudentController` (`/api/students`)

#### 2.1 `GET /api/students`
- **Purpose:** Retrieves full student list with weekly discipline calendar (Mon–Sun), streak status, and Zoom schedules.
- **Query Parameters:**
  - `search` *(optional)*: Case-insensitive query filtering by `fullName` or `niche`.
  - `active` *(optional, default: `true`)*: Filter active/inactive students.
- **Response Status:** `200 OK`
- **Response Schema:** `List<StudentDisciplineResponse>`
```json
[
  {
    "id": "st-1",
    "fullName": "Милана Горохова",
    "avatarText": "МГ",
    "tgUsername": "milana_gorohova",
    "niche": "Стилист-имиджмейкер",
    "tariff": "VIP",
    "initialIncome": 30000.00,
    "currentIncome": 180000.00,
    "initialAvgTicket": 3000.00,
    "currentAvgTicket": 45000.00,
    "totalReels": 14,
    "totalCarousels": 8,
    "totalStorytells": 10,
    "totalMailings": 145,
    "totalNewClients": 7,
    "disciplineRate": 95,
    "isOnPause": false,
    "pauseReason": null,
    "nextZoomDate": "11.11 в 17:00",
    "completedZoomsCount": 4,
    "weekDays": [
      { "day": "Пн", "date": "02.11", "status": "COMPLETED_ON_TIME", "missedDetails": "Reels #11, 20 рассылок, +1 клиент" },
      { "day": "Вт", "date": "03.11", "status": "COMPLETED_ON_TIME", "missedDetails": "Карусель #5, 25 рассылок" },
      { "day": "Ср", "date": "04.11", "status": "COMPLETED_ON_TIME", "missedDetails": "Сторителл #3, 20 рассылок, +1 клиент" },
      { "day": "Чт", "date": "05.11", "status": "COMPLETED_ON_TIME", "missedDetails": "Reels #12, 25 рассылок" },
      { "day": "Пт", "date": "06.11", "status": "COMPLETED_LATE", "missedDetails": "Сдано в 22:40 (опоздание): Reels #13, 15 рассылок" },
      { "day": "Сб", "date": "07.11", "status": "COMPLETED_ON_TIME", "missedDetails": "Reels #14, карусель, 25 рассылок, +2 клиента" },
      { "day": "Вс", "date": "08.11", "status": "WEEKEND", "missedDetails": "Воскресенье — общий выходной потока ☕" }
    ]
  }
]
```

#### 2.2 `GET /api/students/{id}`
- **Purpose:** Full profile detail for a single student including historical reports, Zoom notes, and sales entries.
- **Path Parameter:** `id` (Long / String)
- **Response Status:** `200 OK`

#### 2.3 `POST /api/students`
- **Purpose:** Registers a new student in the cohort.
- **Request Body:**
```json
{
  "fullName": "Полина Соколова",
  "avatarText": "ПС",
  "tgUsername": "sokolova_smm",
  "tgChatId": 71050505,
  "niche": "SMM-маркетолог",
  "tariff": "VIP",
  "initialIncome": 40000.00,
  "initialAvgTicket": 10000.00,
  "startDate": "2026-11-02"
}
```
- **Response Status:** `201 Created`

#### 2.4 `PUT /api/students/{id}`
- **Purpose:** Updates student profile attributes (Point A, current niche, tariff, avatar).
- **Response Status:** `200 OK`

#### 2.5 `DELETE /api/students/{id}`
- **Purpose:** Soft deletes/archives student profile (`is_active = FALSE`).
- **Response Status:** `204 No Content`

#### 2.6 `POST /api/students/{id}/toggle-pause`
- **Purpose:** Sick/pause toggle («В строю 🔥 / Болеет ☕»). Freezes discipline streak and sets day status to `PAUSED`.
- **Request Body:**
```json
{
  "isPaused": true,
  "pauseReason": "Заболела (ОРВИ до 12.11)"
}
```
- **Response Status:** `200 OK`
- **Response Body:**
```json
{
  "studentId": "st-4",
  "fullName": "Виктория Лебедева",
  "isOnPause": true,
  "pauseReason": "Заболела (ОРВИ до 12.11)",
  "streakFrozen": true,
  "updatedAt": "2026-11-08T14:40:00Z"
}
```

#### 2.7 `GET /api/students/{id}/discipline-calendar`
- **Purpose:** Returns comprehensive month/cohort discipline calendar breakdown.
- **Response Status:** `200 OK`

---

### 3. Controller: `AnalyticsController` (`/api/analytics`)

#### 3.1 `GET /api/analytics/summary`
- **Purpose:** Cohort-level summary computing the **10 cross-cutting metrics**:
  1. Total Reels verified (`totalReelsCount`)
  2. Total Carousels verified (`totalCarouselsCount`)
  3. Total Storytells verified (`totalStorytellsCount`)
  4. Total Outreaches / Direct Mailings (`totalMailingsCount`)
  5. Total Cohort Revenue (`totalEarned`)
  6. Cohort Average Ticket (`avgTicket`)
  7. Income Growth Point A ➡️ Point B (`growthMultiplier`, `growthPercent`)
  8. Average Ticket Growth
  9. New Clients Total (`totalNewClientsCount`)
  10. AI Reflection & Burnout Sentiment Index
- **Response Status:** `200 OK`
- **Response Schema:**
```json
{
  "totalEarned": 895000.00,
  "revenueGrowthFactor": "+490%",
  "totalReels": 66,
  "totalCarousels": 35,
  "totalStorytells": 37,
  "totalMailings": 702,
  "totalNewClients": 31,
  "currentAvgTicket": 40600.00,
  "initialAvgTicket": 4800.00,
  "avgTicketGrowthFactor": "x8.4",
  "averageDisciplineRate": 87.8,
  "activeBurnoutAlertsCount": 1,
  "aiReflectionProcessedCount": 42
}
```

#### 3.2 `GET /api/analytics/case/{studentId}`
- **Purpose:** Calculates multiplier and generates ready-to-publish Instagram Stories promotional copy.
- **Response Status:** `200 OK`
- **Response Schema:**
```json
{
  "studentId": "st-1",
  "fullName": "Милана Горохова",
  "niche": "Стилист-имиджмейкер",
  "initialIncome": 30000.00,
  "currentIncome": 180000.00,
  "growthMultiplier": "x6.0",
  "growthPercent": "+500%",
  "initialAvgTicket": 3000.00,
  "currentAvgTicket": 45000.00,
  "totalReels": 14,
  "totalCarousels": 8,
  "totalStorytells": 10,
  "totalMailings": 145,
  "totalNewClients": 7,
  "disciplineRate": 95,
  "generatedStoryCopy": "🔥 КЕЙС ПОТОКА: Милана Горохова\nНиша: Стилист-имиджмейкер\n\n🚀 Точка А: 30 000 ₽ ➡️ Точка Б: 180 000 ₽ (РОСТ x6.0!)\n📈 Средний чек: вырос с 3 000 ₽ до 45 000 ₽\n🎬 Контент: 14 проверенных Reels за 3 недели\n✉️ Исходящий трафик: 145 рассылок по скрипту\n💎 Дисциплина сдачи отчетов: 95%\n\n«Она просто перестала сливать дедлайны и внедрила ежедневный трекинг действий»"
}
```

---

### 4. Controller: `TaskRulesController` (`/api/tasks`)

#### 4.1 `GET /api/tasks/rules`
- **Purpose:** Retrieves official cohort rules and hashtag specifications.
- **Response Status:** `200 OK`
- **Response Schema:** `List<TaskRuleResponse>`
```json
[
  {
    "id": "rule-1",
    "hashtag": "#рилс",
    "title": "Черновик видео Reels",
    "description": "Видеофайл или черновик Reels на проверку перед публикацией. Защита от сдачи повторных роликов.",
    "required": true,
    "example": "#рилс видео/ссылка на черновик"
  },
  {
    "id": "rule-2",
    "hashtag": "#карусель",
    "title": "Черновик карусели (слайды / PDF)",
    "description": "Текст слайдов, фото-подборка или PDF-файл карусели для проверки смыслов и оферты.",
    "required": false,
    "example": "#карусель 5 ошибок при подборе капсулы (слайды 1-5)"
  },
  {
    "id": "rule-3",
    "hashtag": "#сторителл",
    "title": "Сценарий сторителлинга",
    "description": "Сценарий серии Instagram Stories: вовлечение, смысловая связка и подводка к продаже.",
    "required": false,
    "example": "#сторителл прогрев на консультацию (10 историй)"
  },
  {
    "id": "rule-4",
    "hashtag": "#рассылки",
    "title": "Исходящий трафик (Direct)",
    "description": "Количество отправленных сообщений целевой аудитории по утвержденному скрипту.",
    "required": true,
    "example": "#рассылки 25 (по скрипту холодного контакта)"
  },
  {
    "id": "rule-5",
    "hashtag": "#продажи",
    "title": "Сумма оформленной продажи",
    "description": "Фиксация оплат и предоплат. Автоматически суммирует доход, обновляет средний чек и Точку Б.",
    "required": false,
    "example": "#продажи 45000 (оплата за пакет консультаций)"
  },
  {
    "id": "rule-6",
    "hashtag": "#вопрос",
    "title": "Рефлексия дня / Вопрос ментору",
    "description": "Мысли, эмоциональное состояние, затыки дня или вопрос к Ксении (текстом или голосовым). AI формирует краткую выжимку.",
    "required": false,
    "example": "#вопрос клиентка просит скидку 30%, как отработать?"
  },
  {
    "id": "rule-7",
    "hashtag": "#клиенты",
    "title": "Новые клиенты (субботний опрос)",
    "description": "Бот каждую субботу в 18:00 автоматически опрашивает учениц инлайн-кнопками: сколько новых клиентов закрыто за неделю.",
    "required": false,
    "example": "#клиенты выбор кнопки: [0] [1] [2] [3] [4] [5+] в субботу 18:00"
  }
]
```

#### 4.2 `PUT /api/tasks/rules/{id}`
- **Purpose:** Updates rule definition, example text, or required flag.
- **Response Status:** `200 OK`

---

### 5. Controller: `SystemController` (`/api/system`)

#### 5.1 `GET /api/system/status`
- **Purpose:** Diagnostic health check of Telegram bots, Business API connection, Virtual Threads, and AI availability.
- **Response Status:** `200 OK`
```json
{
  "botActive": true,
  "botUsername": "MentorHubBot",
  "businessConnectionStatus": "CONNECTED",
  "canReplyOnBehalf": true,
  "activeStudentsCount": 5,
  "pendingReportsToday": 3,
  "springBootVersion": "3.4.2",
  "virtualThreadsEnabled": true,
  "springAiStatus": "AVAILABLE",
  "zoomTracker": {
    "nextCallAt": "2026-11-11T14:00:00Z",
    "completedCallsTotal": 19
  }
}
```

#### 5.2 `POST /api/system/sync-telegram`
- **Purpose:** Manually triggers update consumer queue flush and verifies Telegram connection.
- **Response Status:** `200 OK`

#### 5.3 `GET /api/system/zoom-tracker`
- **Purpose:** Retrieves Zoom tracker overview (next call date, completed meetings count per student and across the cohort).
- **Response Status:** `200 OK`

#### 5.4 `POST /api/system/zoom-tracker`
- **Purpose:** Schedules or logs a Zoom meeting for a student.
- **Request Body:**
```json
{
  "studentId": 1,
  "zoomUrl": "https://us02web.zoom.us/j/123456789",
  "scheduledAt": "2026-11-11T14:00:00Z",
  "meetingNotes": "Разбор скрипта продаж на высокий чек"
}
```
- **Response Status:** `201 Created`

---

## Part II: Data Model & Flyway Migrations (PostgreSQL 16)

Flyway Location: `src/main/resources/db/migration/V1__init_schema.sql`

```sql
-- =============================================================================
-- MENTOR HUB / MENTOR TRACKER — AUTHORITATIVE POSTGRESQL 16 SCHEMA
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- -----------------------------------------------------------------------------
-- 1. ТАБЛИЦА ПРОФИЛЕЙ УЧЕНИЦ (students)
-- -----------------------------------------------------------------------------
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    avatar_text VARCHAR(10) NOT NULL,
    tg_username VARCHAR(100),
    tg_chat_id BIGINT UNIQUE,
    niche VARCHAR(255) NOT NULL,
    initial_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    current_income NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    initial_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,
    current_avg_ticket NUMERIC(12, 2) DEFAULT 0.00,
    tariff VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    start_date DATE NOT NULL DEFAULT CURRENT_DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    pause_reason VARCHAR(255),
    next_zoom_at TIMESTAMP WITH TIME ZONE,
    completed_zooms_count INT DEFAULT 0,
    total_reels INT DEFAULT 0,
    total_carousels INT DEFAULT 0,
    total_storytells INT DEFAULT 0,
    total_mailings INT DEFAULT 0,
    total_new_clients INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_students_tg_chat_id ON students(tg_chat_id);
CREATE INDEX idx_students_tg_username ON students(tg_username);
CREATE INDEX idx_students_active ON students(is_active);

-- -----------------------------------------------------------------------------
-- 2. ТАБЛИЦА ТОКЕНОВ БЕСПАРОЛЬНОГО ВХОДА АДМИНИСТРАТОРОВ (admin_tokens)
-- -----------------------------------------------------------------------------
CREATE TABLE admin_tokens (
    id BIGSERIAL PRIMARY KEY,
    admin_name VARCHAR(100) NOT NULL,                     -- 'Ксения', 'Катя'
    role VARCHAR(50) NOT NULL,                            -- 'MENTOR', 'CURATOR'
    token VARCHAR(255) NOT NULL UNIQUE,                   -- Секретный токен (UUIDv4)
    tg_user_id BIGINT,                                    -- Telegram ID для пушей и отправки ссылки
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_tokens_token ON admin_tokens(token);
CREATE INDEX idx_admin_tokens_tg_user ON admin_tokens(tg_user_id);

-- -----------------------------------------------------------------------------
-- 3. ТАБЛИЦА ZOOM-СОЗВОНОВ И РАСПИСАНИЯ ВСТРЕЧ (zoom_meetings)
-- -----------------------------------------------------------------------------
CREATE TABLE zoom_meetings (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    zoom_url TEXT,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    is_completed BOOLEAN DEFAULT FALSE,
    meeting_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_zoom_meetings_student ON zoom_meetings(student_id);
CREATE INDEX idx_zoom_meetings_scheduled ON zoom_meetings(scheduled_at);

-- -----------------------------------------------------------------------------
-- 4. ТАБЛИЦА РЕГЛАМЕНТНЫХ ПРАВИЛ И ХЭШТЕГОВ (task_rules)
-- -----------------------------------------------------------------------------
CREATE TABLE task_rules (
    id BIGSERIAL PRIMARY KEY,
    hashtag VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    example TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 5. ТАБЛИЦА ЕЖЕДНЕВНЫХ СУТОЧНЫХ ОТЧЕТОВ (daily_reports)
-- -----------------------------------------------------------------------------
CREATE TABLE daily_reports (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    report_date DATE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_on_time BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',        -- 'PENDING', 'APPROVED', 'REJECTED'
    mailings_count INT DEFAULT 0,
    sales_total NUMERIC(12, 2) DEFAULT 0.00,
    new_clients_count INT DEFAULT 0,
    mentor_question TEXT,
    mentor_reply TEXT,
    reply_sent_at TIMESTAMP WITH TIME ZONE,
    voice_transcript TEXT,
    ai_summary TEXT,
    reflection_emotions TEXT,
    reflection_actions TEXT,
    reflection_question TEXT,
    emotional_status VARCHAR(50) DEFAULT 'NORMAL',        -- 'NORMAL', 'BURNOUT_ALERT', 'STRESSED'
    raw_message_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_report_date UNIQUE (student_id, report_date)
);

CREATE INDEX idx_daily_reports_date_status ON daily_reports(report_date, status);
CREATE INDEX idx_daily_reports_student ON daily_reports(student_id);

-- -----------------------------------------------------------------------------
-- 6. ТАБЛИЦА ЧЕРНОВИКОВ КОНТЕНТА ДО ПУБЛИКАЦИИ (content_drafts)
-- -----------------------------------------------------------------------------
CREATE TABLE content_drafts (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    draft_type VARCHAR(50) NOT NULL,                     -- 'REEL', 'CAROUSEL', 'STORYTELL'
    label VARCHAR(255) NOT NULL,
    url TEXT,
    file_tg_id VARCHAR(255),
    is_duplicate BOOLEAN DEFAULT FALSE,
    duplicate_date VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_content_drafts_report ON content_drafts(report_id);

-- -----------------------------------------------------------------------------
-- 7. ТАБЛИЦА СДАННЫХ РОЛИКОВ REELS С АНТИФРОДОМ (reel_submissions)
-- -----------------------------------------------------------------------------
CREATE TABLE reel_submissions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    reel_url TEXT NOT NULL,
    canonical_url TEXT NOT NULL,
    shortcode VARCHAR(100) NOT NULL,
    reel_hash VARCHAR(64) NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_date VARCHAR(50),
    duplicate_of_id BIGINT REFERENCES reel_submissions(id) ON DELETE SET NULL,
    http_status_code INT DEFAULT 200,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reel_submissions_hash ON reel_submissions(reel_hash);
CREATE INDEX idx_reel_submissions_report ON reel_submissions(report_id);

-- -----------------------------------------------------------------------------
-- 8. ТАБЛИЦА ДЕТАЛЬНЫХ ПРОВОДОК ПРОДАЖ (sales_entries)
-- -----------------------------------------------------------------------------
CREATE TABLE sales_entries (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    amount NUMERIC(12, 2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sales_entries_student ON sales_entries(student_id);
CREATE INDEX idx_sales_entries_report ON sales_entries(report_id);

-- -----------------------------------------------------------------------------
-- 9. ТАБЛИЦА КОНФИГУРАЦИИ НАСТАВНИКА (mentor_settings)
-- -----------------------------------------------------------------------------
CREATE TABLE mentor_settings (
    id BIGSERIAL PRIMARY KEY,
    mentor_tg_id BIGINT NOT NULL UNIQUE,
    mentor_name VARCHAR(255) NOT NULL DEFAULT 'Ксения',
    business_connection_id VARCHAR(255),
    is_business_connected BOOLEAN DEFAULT FALSE,
    reminder_cron VARCHAR(50) DEFAULT '0 0 20 * * MON-SAT',
    deadline_time TIME DEFAULT '21:00:00',
    auto_reminders_enabled BOOLEAN DEFAULT TRUE,
    saturday_poll_cron VARCHAR(50) DEFAULT '0 0 18 * * SAT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 10. ЖУРНАЛ АУДИТА СЫРЫХ СООБЩЕНИЙ TELEGRAM (audit_message_logs)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_message_logs (
    id BIGSERIAL PRIMARY KEY,
    update_id BIGINT NOT NULL,
    tg_chat_id BIGINT,
    tg_user_id BIGINT,
    source_type VARCHAR(50) NOT NULL,                     -- 'BUSINESS_1ON1', 'MINI_GROUP', 'DIRECT_BOT', 'BUSINESS_1ON1_EDITED'
    raw_payload JSONB,
    processing_status VARCHAR(50) NOT NULL,               -- 'PROCESSED', 'UNMATCHED_USER', 'ERROR', 'IGNORED_MENTOR_ECHO'
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user_date ON audit_message_logs(tg_user_id, created_at);
CREATE INDEX idx_audit_logs_status ON audit_message_logs(processing_status);

-- =============================================================================
-- SEED DATA (Начальные данные для боевого запуска)
-- =============================================================================

INSERT INTO admin_tokens (admin_name, role, token, tg_user_id) VALUES
('Ксения', 'MENTOR', 'magic-ksenia-mentor-token-998877', 11223344),
('Катя', 'CURATOR', 'magic-katya-curator-token-112233', 22334455);

INSERT INTO mentor_settings (mentor_tg_id, mentor_name, is_business_connected) VALUES
(11223344, 'Ксения', TRUE);

INSERT INTO task_rules (hashtag, title, description, is_required, example) VALUES
('#рилс', 'Черновик видео Reels', 'Видеофайл или черновик Reels на проверку перед публикацией. Защита от сдачи повторных роликов.', TRUE, '#рилс видео/ссылка на черновик'),
('#карусель', 'Черновик карусели (слайды / PDF)', 'Текст слайдов, фото-подборка или PDF-файл карусели для проверки смыслов и оферты.', FALSE, '#карусель 5 ошибок при подборе капсулы (слайды 1-5)'),
('#сторителл', 'Сценарий сторителлинга', 'Сценарий серии Instagram Stories: вовлечение, смысловая связка и подводка к продаже.', FALSE, '#сторителл прогрев на консультацию (10 историй)'),
('#рассылки', 'Исходящий трафик (Direct)', 'Количество отправленных сообщений целевой аудитории по утвержденному скрипту.', TRUE, '#рассылки 25 (по скрипту холодного контакта)'),
('#продажи', 'Сумма оформленной продажи', 'Фиксация оплат и предоплат. Автоматически суммирует доход, обновляет средний чек и Точку Б.', FALSE, '#продажи 45000 (оплата за пакет консультаций)'),
('#вопрос', 'Рефлексия дня / Вопрос ментору', 'Мысли, эмоциональное состояние, затыки дня или вопрос к Ксении (текстом или голосовым). AI формирует краткую выжимку.', FALSE, '#вопрос клиентка просит скидку 30%, как отработать?'),
('#клиенты', 'Новые клиенты (субботний опрос)', 'Бот каждую субботу в 18:00 автоматически опрашивает учениц инлайн-кнопками: сколько новых клиентов закрыто за неделю.', FALSE, '#клиенты выбор кнопки: [0] [1] [2] [3] [4] [5+] в субботу 18:00');

INSERT INTO students (id, full_name, avatar_text, tg_username, tg_chat_id, niche, initial_income, current_income, initial_avg_ticket, current_avg_ticket, tariff, total_reels, total_carousels, total_storytells, total_mailings, total_new_clients, is_active, is_paused, pause_reason, completed_zooms_count) VALUES
(1, 'Милана Горохова', 'МГ', 'milana_gorohova', 71010101, 'Стилист-имиджмейкер', 30000.00, 180000.00, 3000.00, 45000.00, 'VIP', 14, 8, 10, 145, 7, TRUE, FALSE, NULL, 4),
(2, 'Аня Петрова', 'АП', 'anya_petrova_style', 71020202, 'Психолог для экспертов', 25000.00, 45000.00, 2500.00, 15000.00, 'STANDARD', 7, 3, 4, 72, 2, TRUE, FALSE, NULL, 3),
(3, 'Карина Волкова', 'КВ', 'volkova_coach', 71030303, 'Наставник бьюти-мастеров', 50000.00, 310000.00, 5000.00, 60000.00, 'VIP', 18, 12, 14, 210, 11, TRUE, FALSE, NULL, 5),
(4, 'Виктория Лебедева', 'ВЛ', 'v_lebedeva_fit', 71040404, 'Фитнес-тренер онлайн', 35000.00, 140000.00, 3500.00, 28000.00, 'STANDARD', 12, 6, 8, 115, 5, TRUE, TRUE, 'Заболела (ОРВИ до 12.11)', 3),
(5, 'Полина Соколова', 'ПС', 'sokolova_smm', 71050505, 'SMM-маркетолог', 40000.00, 220000.00, 10000.00, 55000.00, 'VIP', 15, 9, 11, 160, 8, TRUE, FALSE, NULL, 4);

SELECT setval('students_id_seq', 10);
```

---

## Part III: Security & Access Control

### 1. Magic Link Authentication Architecture
- **Mechanism:** Passwordless authentication tailored for mobile and iPad environments. Administrators access the platform via a URL containing a pre-issued or dynamically requested token:
  `https://mentorhub.domain/?token=<MAGIC_TOKEN>` or via HTTP Header: `X-Admin-Token: <MAGIC_TOKEN>` / `Authorization: Bearer <MAGIC_TOKEN>`.
- **Spring Security Filter:** An `AdminTokenAuthenticationFilter` intercepts requests to `/api/**`:
  1. Inspects `X-Admin-Token` header or `?token=` query parameter or session cookie.
  2. Queries `admin_tokens` for an active matching token (`is_active = TRUE` and `expires_at > NOW()`).
  3. Updates `last_used_at` timestamp.
  4. Populates Spring Security context with `PreAuthenticatedAuthenticationToken` containing authorities `ROLE_MENTOR` or `ROLE_CURATOR`.

### 2. Administrator Roles & Permissions Matrix

| Privilege / Action | Ksenia (`ROLE_MENTOR`) | Katya (`ROLE_CURATOR`) | Public / Student |
|:---|:---:|:---:|:---:|
| Review & Approve / Reject content drafts (`/api/inbox/**`) | ✅ Yes | ✅ Yes | ❌ Forbidden |
| Reply to student on behalf of Ksenia (`business_connection_id`) | ✅ Yes | ✅ Yes (system route) | ❌ Forbidden |
| Toggle student sick/pause mode («Болеет ☕» / «В строю 🔥») | ✅ Yes | ✅ Yes | ❌ Forbidden |
| Schedule / Log Zoom meetings (`/api/system/zoom-tracker`) | ✅ Yes | ✅ Yes | ❌ Forbidden |
| View discipline calendar & 10 analytics metrics | ✅ Yes | ✅ Yes | ❌ Forbidden |
| Generate Instagram Story case copy | ✅ Yes | ✅ Yes | ❌ Forbidden |
| Modify task hashtag rules (`/api/tasks/rules/**`) | ✅ Yes | ❌ View Only | ❌ Forbidden |
| Modify mentor connection settings (`mentor_settings`) | ✅ Yes | ❌ Forbidden | ❌ Forbidden |
| Telegram Webhook / Long Polling ingestion | Internal | Internal | Authorized TG ID |

### 3. Token Generation & Revocation
- **Generation:** On deployment, initial tokens are generated in Flyway seed data. Admins can generate a new token via Telegram bot command `/login` from their verified `tg_user_id`.
- **Revocation:** An administrator can immediately revoke any compromised token by setting `is_active = FALSE` in `admin_tokens`.
- **Sliding Expiration:** Default token lifetime is 30 days, renewed on each active API call.

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | REST API | `GET /api/inbox` | Retrieves unreviewed daily reports and content drafts queue | Optional `date`, `status` | `List<DailyReportResponse>` | Returns empty list when zero pending | `ARCHITECTURE_SPECIFICATION.md` §10 |
| 2 | REST API | `POST /api/inbox/{id}/approve` | Approves report and sends Telegram reply from Ksenia | `reportId`, `{ replyText }` | `{ success: true, status: 'APPROVED' }` | 404 if not found; graceful status update if TG delivery fails | `ARCHITECTURE_SPECIFICATION.md` §10 |
| 3 | REST API | `POST /api/inbox/{id}/reject` | Rejects report/draft with required feedback sent to Telegram | `reportId`, `{ reason }` | `{ success: true, status: 'REJECTED' }` | 400 if reason empty; 404 if not found | `ARCHITECTURE_SPECIFICATION.md` §10 |
| 4 | REST API | `GET /api/students` | Lists students with discipline grid and Zoom tracker data | Optional `search`, `active` | `List<StudentDisciplineResponse>` | 200 with empty array on no matches | `frontend/src/types.ts` & `test-runner.mjs` |
| 5 | REST API | `POST /api/students/{id}/toggle-pause` | Toggles sick/pause state, freezing discipline streak | `studentId`, `{ isPaused, reason }` | `{ studentId, isOnPause, streakFrozen }` | 404 if student not found | `ORIGINAL_REQUEST.md` R1 |
| 6 | REST API | `GET /api/analytics/summary` | Aggregates the 10 cross-cutting performance metrics | None | Summary JSON with 10 KPIs | Returns zeroes if cohort has no entries | `ORIGINAL_REQUEST.md` R1 & `test-runner.mjs` |
| 7 | REST API | `GET /api/analytics/case/{id}` | Computes Point A->B multiplier and Story copy | `studentId` | Story text and growth ratios | Div-by-zero protection defaults to x1.0 | `ARCHITECTURE_SPECIFICATION.md` §10 |
| 8 | REST API | `GET /api/tasks/rules` | Retrieves active hashtag regulations | None | `List<TaskRuleResponse>` | Returns active rules | `frontend/src/mockData.ts` & doc §10 |
| 9 | REST API | `GET /api/system/status` | System health check and Zoom tracker status | None | Health diagnostics JSON | Reports component status | `ARCHITECTURE_SPECIFICATION.md` §10 |
| 10 | REST API | `POST /api/system/zoom-tracker` | Schedules or records completed Zoom call | `{ studentId, zoomUrl, scheduledAt, notes }` | 201 Created meeting DTO | 400 on invalid timestamp or studentId | `ORIGINAL_REQUEST.md` R1 & doc §9 |
| 11 | Data Model | Dual Content Storage | Stores video Reels (with hash) and drafts (carousels, storytells) | Ingested telegram updates / API | `reel_submissions` & `content_drafts` records | Invalidates corrupted links | `frontend/src/types.ts` & doc §9 |
| 12 | Data Model | Passwordless Magic Links | Persistent token credentials in `admin_tokens` | HTTP headers / params | Authenticated `SecurityContext` | 401 Unauthorized if token invalid/inactive | `ARCHITECTURE_SPECIFICATION.md` §9 |
| 13 | Anti-Fraud | SHA-256 Reels Deduplication | Generates hash of canonical shortcode to catch duplicates | Reel URL / shortcode | Duplicate flag & original date | Yellow badge if same user; red alert if cross-user plagiarism | `ARCHITECTURE_SPECIFICATION.md` §3 |
| 14 | Anti-Fraud | Async HTTP HEAD Accessibility Probe | Non-blocking HTTP check verifying reel is accessible | Canonical Reel URL | HTTP status code, validity | 404 flags invalid; 429/timeout flags `TIMEOUT_UNVERIFIED` | `ARCHITECTURE_SPECIFICATION.md` §3.3 |
| 15 | Parser | Russian Locale Grouping Separator Normalization | Interprets `35.500 руб` and `45 000` as thousands separator | Raw message string | Normalized `BigDecimal` | Defaults to 0.00 on unrecognized string | `ARCHITECTURE_SPECIFICATION.md` §4.1 |
| 16 | AI Pipeline | 3-Bullet Reflection Summary | Whisper transcription + LLM extraction into 3 structured bullets | Audio `.oga` or free text | `ReflectionSummary` (emotions, actions, question) | Automatic mock/stub fallback if API offline | `ORIGINAL_REQUEST.md` R2.3 & doc §6 |
| 17 | Scheduler | Daily 20:00 Reminder Engine | Pings students missing reports 1 hr before 21:00 deadline | Cron `0 0 20 * * MON-SAT` (Minsk) | Telegram ping to debtors | Skips students who submitted before 20:00 | `ARCHITECTURE_SPECIFICATION.md` §5 |
| 18 | Scheduler | Saturday 18:00 New Clients Auto-Poll | Polls students for new clients acquired during the week | Cron `0 0 18 * * SAT` (Minsk) | Inline button poll in TG | Aggregates responses to `total_new_clients` | `ORIGINAL_REQUEST.md` R2.4 & `mockData.ts` |

---

## Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---------|-------|-------------------|
| 1 | Growth Multiplier Calculator | `initialIncome = 0.00`, `currentIncome = 150000.00` | Multiplier returns `"1.0"` (protected against division by zero or NaN). |
| 2 | Reels Shortcode Normalization | `https://instagram.com/reel/DC194kaP91m?igsh=MTZkMTZk&utm_source=qr` | Strips query string completely, isolates shortcode `DC194kaP91m`, creates SHA-256 hash. |
| 3 | Cross-Student Reel Plagiarism | Student B submits Reel previously submitted by Student A | SHA-256 match found with different `student_id`. Sets `is_duplicate = true`, emits red security alert to mentor in Inbox. |
| 4 | Disconnected Telegram Business API | Mentor approves report when `is_business_connected = false` | Report status in DB changes to `APPROVED`; API returns `tgMessageDelivered: false`; UI receives warning notification without crash. |
| 5 | Sunday Discipline Handling | Date falls on Sunday (`08.11`) | Marked as `status: 'WEEKEND'`, tooltip: `"Воскресенье — общий выходной потока ☕"`. Excluded from mandatory discipline penalties. |
| 6 | Sick/Pause Mode Active | Student is marked `is_paused = true` on Friday and Saturday | Friday and Saturday marked as `status: 'PAUSED'`, tooltip: `"Режим паузы: болезнь, стрик заморожен ☕"`. Streak is preserved. |
| 7 | Partial Incremental Submissions | Student submits `#рассылки 15` at 14:00, then `#рассылки 10 #рилс ...` at 19:30 | Upsert logic sums mailings (`25`), attaches reel, updates `submitted_at`, keeps `is_on_time = true`. |
| 8 | Submission at 20:59 vs 21:01 | Report submitted at 20:59:50 vs 21:01:10 (Europe/Minsk) | 20:59 is marked `is_on_time = true` (`COMPLETED_ON_TIME`). 21:01 is marked `is_on_time = false` (`COMPLETED_LATE`, amber indicator). |
| 9 | Unregistered Telegram User Message | Unknown user messages bot | Update logged in `audit_message_logs` with status `UNMATCHED_USER`. Polite refusal message dispatched. |
| 10 | AI Service Outage / Rate Limit 429 | OpenAI/Gemini times out (>3000ms) or returns 429/503 | Fallback parser activates regex extraction, raw message saved to `raw_message_text`, mentor sees raw text with offline marker. Zero report loss. |
