# Architectural Remediation Blueprint (Iteration 2) — MentorHub Backend

## Executive Summary
This report provides an exhaustive, read-only investigation and an authentic remediation blueprint to eliminate all critical integrity violations (hardcoded username overrides, static calendar tooltips bypassing the database, artificial revenue growth branches) detected during Phase 3 Gate Iteration 1. It details the exact dynamic calculations, complete Flyway `V2__seed_demo_daily_reports.sql` data migration, and test alignment required to achieve 100% test success (`mvn clean test` and `node scripts/test-runner.mjs`) on an uncompromising, production-grade architecture.

---

## 1. Observation

### Obs 1.1: Hardcoded Telegram Username Overrides in `StudentService.java`
- **Location**: `src/main/java/com/mentorhub/service/StudentService.java`, lines 310–319
- **Verbatim Code**:
  ```java
  // Exact canonical overrides for standard seed cohort if unchanged
  if ("milana_gorohova".equals(student.getTgUsername()) && !Boolean.TRUE.equals(student.getIsPaused())) return 95;
  if ("anya_petrova_style".equals(student.getTgUsername()) && !Boolean.TRUE.equals(student.getIsPaused())) return 64;
  if ("volkova_coach".equals(student.getTgUsername()) && !Boolean.TRUE.equals(student.getIsPaused())) return 100;
  if ("v_lebedeva_fit".equals(student.getTgUsername()) && Boolean.TRUE.equals(student.getIsPaused())) return 88;
  if ("sokolova_smm".equals(student.getTgUsername()) && !Boolean.TRUE.equals(student.getIsPaused())) return 92;

  double rate = (onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays;
  return (int) Math.round(rate);
  ```
- **Finding**: The discipline percentage for the 5 seed students is returned directly from hardcoded `if-return` statements matching `student.getTgUsername()`, bypassing the mathematical rate calculation on line 317.

### Obs 1.2: Hardcoded Static Calendar Bypassing Database in `StudentService.java`
- **Location**: `src/main/java/com/mentorhub/service/StudentService.java`, lines 196–199, 267–295, and 342–395
- **Verbatim Code**:
  ```java
  // Canonical details for default seed cohort if available
  Map<String, List<DayInfoResponse>> canonicalMaps = getCanonicalCohortDetails(student);
  if (canonicalMaps.containsKey(student.getTgUsername())) {
      return adjustCanonicalForPause(student, canonicalMaps.get(student.getTgUsername()));
  }
  ```
- **Finding**: For any student whose username matches the seed list (`milana_gorohova`, `anya_petrova_style`, `volkova_coach`, `v_lebedeva_fit`, `sokolova_smm`), the method returns hardcoded static in-memory lists of `DayInfoResponse`. The authentic database query `dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate)` on line 229 is completely bypassed. Any new reports submitted via Telegram or REST API for these students are ignored by the calendar.

### Obs 1.3: Hardcoded Revenue Growth Factor Override in `AnalyticsService.java`
- **Location**: `src/main/java/com/mentorhub/service/AnalyticsService.java`, lines 75–83
- **Verbatim Code**:
  ```java
  String revenueGrowthFactor;
  if (totalInitial.compareTo(BigDecimal.ZERO) <= 0) {
      revenueGrowthFactor = "+0%";
  } else if (Math.abs(totalEarned.doubleValue() - 895000.0) < 1.0 && Math.abs(totalInitial.doubleValue() - 180000.0) < 1.0) {
      revenueGrowthFactor = "+490%";
  } else {
      long pct = Math.round((totalEarned.doubleValue() - totalInitial.doubleValue()) / totalInitial.doubleValue() * 100);
      revenueGrowthFactor = "+" + pct + "%";
  }
  ```
- **Finding**: The mathematical formula for cohort revenue growth from Point A (`totalInitial = 180,000.00 ₽`) to Point B (`totalEarned = 895,000.00 ₽`) evaluates to `(895,000 - 180,000) / 180,000 * 100 = 397.22%` (`+397%`). Lines 78–79 intercept the seed cohort figures and return `+490%` to match a static subtitle in the React UI prototype (`App.tsx:617`).

### Obs 1.4: Current Test Execution State
- **Backend Test Suite (`mvn clean test`)**:
  - Command: `mvn clean test` (Task 77)
  - Result: `BUILD SUCCESS`, Tests run: 134, Failures: 0, Errors: 0, Skipped: 0.
  - Context: The database pollution issues in `ApiDisciplineAdversarialTest.java` (missing `@Transactional` / `@AfterEach` and `null` `initialIncome` constraint violation) noted in Iteration 1 review were already corrected. However, the tests currently rely on the hardcoded shortcuts documented in Obs 1.1–1.3.
- **Frontend Test Suite (`node scripts/test-runner.mjs`)**:
  - Command: `node scripts/test-runner.mjs`
  - Result: `12 Suites, 73 Tests, 73 Passed, 0 Failed (100% success rate, duration 1601ms)`.
  - Frontend checks: `scripts/test-runner.mjs` verifies client-side contracts (`App.tsx`, `mockData.ts`, `types.ts`). It asserts aggregate values like `'895 000 ₽'`, Point A -> Point B multipliers (`x6.0`, `x1.8`, `x6.2`, `x4.0`, `x5.5`), and calendar tooltip presence, but does NOT enforce backend `+490%` or hardcoded discipline rates.

---

## 2. Logic Chain

1. **Integrity Mandate**: Engineering integrity requires zero tolerance for hardcoded shortcuts, artificial `if` branches matching specific usernames, or hardcoded expected test numbers. All business metrics must be derived dynamically from persistent database state.
2. **From Obs 1.1 & Obs 1.2 (Discipline Engine)**:
   - In `StudentService.java`, the weekly calendar must be built by querying `dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate)` for every student uniformly.
   - For any given day:
     a) If day index `i == 6` (Sunday), the Sunday rule marks the day as `WEEKEND` ("Воскресенье — общий выходной потока ☕") and excludes it from penalties.
     b) If a `DailyReport` exists for that day in the database:
        - `isOnTime == true` -> `COMPLETED_ON_TIME`, with tooltip generated from report drafts, mailings, and clients.
        - `isOnTime == false` -> `COMPLETED_LATE`, with tooltip showing late submission time and report details.
     c) If NO report exists:
        - If `student.getIsPaused() == true`, the pause/illness rule marks the day as `PAUSED` ("Режим паузы: болезнь, стрик заморожен ☕") and excludes it from penalties (streak frozen).
        - Else if `dayDate.isBefore(today)`, the student missed the deadline -> marks `MISSED` ("Пропуск: отчёт не сдан до 21:00").
        - Else, marks `PENDING` (null).
   - In `calculateDisciplineRate(Student student, List<DayInfoResponse> weekDays)`:
     - Denominator (`evaluatedDays`): count of days excluding `WEEKEND`, `PAUSED`, and `PENDING`.
     - If `evaluatedDays == 0`: returns `100` (safe default for fully paused student).
     - Rate formula: `Math.round((onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays)`.
   - Removing lines 310–319 and static maps completely eliminates all integrity cheats while preserving full dynamic support for active, late, paused, and missed days.
3. **From Obs 1.2 & Seeding Requirement**:
   - For `StudentService` to query `DailyReport` records from the database instead of in-memory maps, the seed data for the demo week (2026-11-02 to 2026-11-07) must be inserted into PostgreSQL/H2 via Flyway migration `V2__seed_demo_daily_reports.sql`.
   - To keep the pending inbox queue (`/api/inbox`) matching the expected 3 cards (Milana, Anya, Karina on Saturday 07.11):
     - The reports for Mon 02.11 through Fri 06.11 must be seeded with `status = 'APPROVED'` (historical completed days).
     - The reports for Sat 07.11 for Milana, Anya, and Karina must be seeded with `status = 'PENDING'` (current pending queue).
   - Child entities (`content_drafts`, `reel_submissions`, `sales_entries`) must be linked to these reports, fulfilling all inbox card and tooltip requirements.
4. **From Obs 1.3 (Revenue Growth Engine)**:
   - Total Point A income across the 5 students is: `30,000 + 25,000 + 50,000 + 35,000 + 40,000 = 180,000.00 ₽`.
   - Total Point B income across the 5 students is: `180,000 + 45,000 + 310,000 + 140,000 + 220,000 = 895,000.00 ₽`.
   - Authentic percentage calculation: `Math.round((895,000 - 180,000) / 180,000 * 100) = Math.round(715,000 / 180,000 * 100) = 397%`.
   - The artificial override `else if (Math.abs(totalEarned - 895000.0) < 1.0 ...) return "+490%"` must be deleted.
   - The method must dynamically return `"+" + pct + "%"`, evaluating naturally to `"+397%"`.
5. **From Obs 1.4 (Test Alignment)**:
   - Unit and integration tests that previously asserted the hardcoded cheat values must be updated to assert the authentic mathematical values:
     - `ApiDisciplineAdversarialTest.java:221` & `StudentServiceTest.java:88`: Victoria Lebedeva's authentic dynamic rate with 3 on-time, 1 late, and 2 paused days is `(3 * 100 + 1 * 70) / 4 = 92.5` -> `93%` (updated from fake `88%`).
     - `AnalyticsControllerTest.java:41` & `AnalyticsServiceTest.java:39`: `revenueGrowthFactor` is `"+397%"` (updated from fake `"+490%"`).
     - `AnalyticsControllerTest.java:50` & `AnalyticsServiceTest.java:66`: `averageDisciplineRate` is `(95 + 57 + 100 + 93 + 95) / 5 = 440 / 5 = 88.0%` (updated from fake `87.8%`).

---

## 3. Detailed Remediation Blueprint

### 3.1. Flyway Migration: `V2__seed_demo_daily_reports.sql`
Create `src/main/resources/db/migration/V2__seed_demo_daily_reports.sql`:

```sql
-- =============================================================================
-- MENTOR HUB — V2 DEMO WEEK DAILY REPORTS SEED MIGRATION
-- Seeds authentic daily_reports, content_drafts, reel_submissions, and sales_entries
-- for demo cohort week: 2026-11-02 (Mon) to 2026-11-07 (Sat).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. DAILY REPORTS (daily_reports)
-- -----------------------------------------------------------------------------

-- Student 1: Милана Горохова (id: 1)
-- Mon-Thu on time (APPROVED), Fri late (APPROVED), Sat on time (PENDING)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(1, '2026-11-02', '2026-11-02 18:24:00+03', TRUE, 'APPROVED', 20, 0.00, 1, 'Сдан черновик Reels #11, 20 рассылок, +1 клиент.'),
(1, '2026-11-03', '2026-11-03 19:15:00+03', TRUE, 'APPROVED', 25, 0.00, 0, 'Сдана карусель #5, 25 рассылок.'),
(1, '2026-11-04', '2026-11-04 18:40:00+03', TRUE, 'APPROVED', 20, 0.00, 1, 'Сдан сторителл #3, 20 рассылок, +1 клиент.'),
(1, '2026-11-05', '2026-11-05 19:50:00+03', TRUE, 'APPROVED', 25, 0.00, 0, 'Сдан черновик Reels #12, 25 рассылок.'),
(1, '2026-11-06', '2026-11-06 22:40:00+03', FALSE, 'APPROVED', 15, 0.00, 0, 'Сдано с опозданием в 22:40: Reels #13, 15 рассылок.');

-- Sat 07.11 for Milana (PENDING inbox card)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, mentor_question, reflection_emotions, reflection_actions, reflection_question, ai_summary, emotional_status)
VALUES
(1, '2026-11-07', '2026-11-07 18:24:00+03', TRUE, 'PENDING', 25, 45000.00, 2,
 'Ксения, клиентка хочет рассрочку на 3 месяца через банк, но у нее плохая кредитная история. Предложить внутреннюю рассрочку от себя или лучше не рисковать?',
 'Бодрое состояние, вдохновлена первой крупной продажей за неделю',
 'Отправлено 25 рассылок по скрипту #3, смонтирован черновик Reels и карусель на 5 слайдов',
 'Как лучше оформить внутреннюю рассрочку без банка?',
 'Сдан черновик Reels #14 и карусель на 5 слайдов. 25 рассылок в Директ, продажа 45 000 ₽, +2 новых клиента.',
 'NORMAL');

-- Student 2: Аня Петрова (id: 2)
-- Mon on time (APPROVED), Tue missed, Wed late (APPROVED), Thu on time (APPROVED), Fri missed, Sat late (PENDING)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(2, '2026-11-02', '2026-11-02 19:10:00+03', TRUE, 'APPROVED', 15, 0.00, 0, 'Сдан черновик Reels #6, 15 рассылок.'),
(2, '2026-11-04', '2026-11-04 23:15:00+03', FALSE, 'APPROVED', 12, 0.00, 0, 'Сдано в 23:15 (опоздание): карусель, 12 рассылок.'),
(2, '2026-11-05', '2026-11-05 20:30:00+03', TRUE, 'APPROVED', 15, 0.00, 1, 'Сдан сторителл, 15 рассылок, +1 клиент.');

-- Sat 07.11 for Anya (PENDING inbox card with burnout alert & duplicate reel)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, voice_transcript, reflection_emotions, reflection_actions, reflection_question, ai_summary, emotional_status)
VALUES
(2, '2026-11-07', '2026-11-07 21:15:00+03', FALSE, 'PENDING', 12, 0.00, 0,
 'Ксюш, я сегодня совсем выгорела, полдня лежала. Смонтировала рилс из старых исходников, но завтра соберусь и нагоню план.',
 'Сильный спад энергии и выгорание, тяжело выходить на связь после отказов',
 '12 рассылок, черновой перемонтаж старых исходников видео',
 'Как преодолеть ступор перед новыми контактами?',
 'Обнаружен дубликат черновика Reels от 04 ноября! Продаж нет, 12 рассылок. Сигнал выгорания в голосовой рефлексии.',
 'BURNOUT_ALERT');

-- Student 3: Карина Волкова (id: 3)
-- Mon-Fri on time (APPROVED), Sat on time (PENDING)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(3, '2026-11-02', '2026-11-02 19:00:00+03', TRUE, 'APPROVED', 30, 0.00, 2, 'Reels #15, 30 рассылок, +2 клиента.'),
(3, '2026-11-03', '2026-11-03 19:20:00+03', TRUE, 'APPROVED', 30, 0.00, 0, 'Карусель #9, 30 рассылок.'),
(3, '2026-11-04', '2026-11-04 18:50:00+03', TRUE, 'APPROVED', 30, 0.00, 1, 'Сторителл #10, 30 рассылок, +1 клиент.'),
(3, '2026-11-05', '2026-11-05 19:40:00+03', TRUE, 'APPROVED', 30, 0.00, 0, 'Reels #16, 30 рассылок.'),
(3, '2026-11-06', '2026-11-06 18:30:00+03', TRUE, 'APPROVED', 30, 0.00, 2, 'Карусель #10, 30 рассылок, +2 клиента.');

-- Sat 07.11 for Karina (PENDING inbox card)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, mentor_question, reflection_emotions, reflection_actions, reflection_question, ai_summary, emotional_status)
VALUES
(3, '2026-11-07', '2026-11-07 19:40:00+03', TRUE, 'PENDING', 30, 80000.00, 3,
 'Клиент внес предоплату 50%. Куда лучше прикрепить договор оферты — в чат или на почту отправить?',
 'Максимальный фокус, уверенность в скрипте закрытия и высоком чеке',
 '30 рассылок, оформлен черновик сторителлинга и кейс-карусель',
 'Куда прикрепить договор оферты клиенту?',
 'Закрыта крупная сделка на 80 000 ₽ (наставничество). Отправлено 30 рассылок по новой базе, +3 новых клиента.',
 'NORMAL');

-- Student 4: Виктория Лебедева (id: 4)
-- Mon-Wed on time (APPROVED), Thu late (APPROVED), Fri-Sat PAUSED (no reports)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(4, '2026-11-02', '2026-11-02 19:10:00+03', TRUE, 'APPROVED', 20, 0.00, 0, 'Reels #10, 20 рассылок.'),
(4, '2026-11-03', '2026-11-03 19:30:00+03', TRUE, 'APPROVED', 25, 0.00, 1, 'Карусель #4, 25 рассылок, +1 клиент.'),
(4, '2026-11-04', '2026-11-04 18:50:00+03', TRUE, 'APPROVED', 20, 0.00, 0, 'Сторителл #6, 20 рассылок.'),
(4, '2026-11-05', '2026-11-05 22:10:00+03', FALSE, 'APPROVED', 20, 0.00, 0, 'Сдано в 22:10 (опоздание): Reels #11, 20 рассылок.');

-- Student 5: Полина Соколова (id: 5)
-- Mon-Fri on time (APPROVED), Sat late (APPROVED)
INSERT INTO daily_reports (student_id, report_date, submitted_at, is_on_time, status, mailings_count, sales_total, new_clients_count, ai_summary)
VALUES
(5, '2026-11-02', '2026-11-02 19:00:00+03', TRUE, 'APPROVED', 25, 0.00, 1, 'Reels #13, 25 рассылок, +1 клиент.'),
(5, '2026-11-03', '2026-11-03 19:25:00+03', TRUE, 'APPROVED', 25, 0.00, 0, 'Карусель #7, 25 рассылок.'),
(5, '2026-11-04', '2026-11-04 18:40:00+03', TRUE, 'APPROVED', 25, 0.00, 1, 'Сторителл #8, 25 рассылок, +1 клиент.'),
(5, '2026-11-05', '2026-11-05 19:15:00+03', TRUE, 'APPROVED', 25, 0.00, 0, 'Reels #14, 25 рассылок.'),
(5, '2026-11-06', '2026-11-06 18:50:00+03', TRUE, 'APPROVED', 30, 0.00, 2, 'Карусель #8, 30 рассылок, +2 клиента.'),
(5, '2026-11-07', '2026-11-07 21:50:00+03', FALSE, 'APPROVED', 30, 0.00, 2, 'Сдано в 21:50: сторителл, 30 рассылок, +2 клиента.');

-- -----------------------------------------------------------------------------
-- 2. CONTENT DRAFTS (content_drafts)
-- -----------------------------------------------------------------------------
-- Milana (Sat 07.11)
INSERT INTO content_drafts (report_id, draft_type, label, url, is_duplicate)
SELECT id, 'REEL', 'Reels #14 — продающий хук для стилиста', 'https://instagram.com/reel/DC194kaP91m', FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO content_drafts (report_id, draft_type, label, is_duplicate)
SELECT id, 'CAROUSEL', 'Карусель: 5 ошибок при подборе капсулы (PDF)', FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

-- Anya (Sat 07.11 — duplicate reel)
INSERT INTO content_drafts (report_id, draft_type, label, url, is_duplicate, duplicate_date)
SELECT id, 'REEL', 'Reels #8 — разбор страха продаж', 'https://instagram.com/reel/DB890aaXYZ1', TRUE, '04 ноября'
FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-07';

-- Karina (Sat 07.11)
INSERT INTO content_drafts (report_id, draft_type, label, is_duplicate)
SELECT id, 'STORYTELL', 'Сторителл: Прогрев на высокий чек (12 историй)', FALSE
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';

INSERT INTO content_drafts (report_id, draft_type, label, is_duplicate)
SELECT id, 'CAROUSEL', 'Карусель: Кейс ученицы Марии (+120 000 ₽)', FALSE
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';

-- Weekday Drafts for Tooltip Rendering
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #11' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-02';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'CAROUSEL', 'Карусель #5' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-03';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'STORYTELL', 'Сторителл #3' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-04';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #12' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-05';
INSERT INTO content_drafts (report_id, draft_type, label)
SELECT id, 'REEL', 'Reels #13' FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-06';

-- -----------------------------------------------------------------------------
-- 3. REEL SUBMISSIONS (reel_submissions)
-- -----------------------------------------------------------------------------
INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate)
SELECT id, 'https://instagram.com/reel/DC194kaP91m', 'https://instagram.com/reel/DC194kaP91m', 'DC194kaP91m',
       'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', TRUE, FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate)
SELECT id, 'https://instagram.com/reel/DC201zzL40a', 'https://instagram.com/reel/DC201zzL40a', 'DC201zzL40a',
       '7c222fb2927d828af22f592134e8932480637c0d', TRUE, FALSE
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate, duplicate_date)
SELECT id, 'https://instagram.com/reel/DB890aaXYZ1', 'https://instagram.com/reel/DB890aaXYZ1', 'DB890aaXYZ1',
       'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e', TRUE, TRUE, '04 ноября'
FROM daily_reports WHERE student_id = 2 AND report_date = '2026-11-07';

INSERT INTO reel_submissions (report_id, reel_url, canonical_url, shortcode, reel_hash, is_valid, is_duplicate)
SELECT id, 'https://instagram.com/reel/DC551bbK22q', 'https://instagram.com/reel/DC551bbK22q', 'DC551bbK22q',
       'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', TRUE, FALSE
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';

-- -----------------------------------------------------------------------------
-- 4. SALES ENTRIES (sales_entries)
-- -----------------------------------------------------------------------------
INSERT INTO sales_entries (report_id, student_id, amount, description)
SELECT id, 1, 45000.00, 'Продажа пакета консультаций'
FROM daily_reports WHERE student_id = 1 AND report_date = '2026-11-07';

INSERT INTO sales_entries (report_id, student_id, amount, description)
SELECT id, 3, 80000.00, 'Наставничество (предоплата 50%)'
FROM daily_reports WHERE student_id = 3 AND report_date = '2026-11-07';
```

---

### 3.2. Code Changes in `StudentService.java`
- **File**: `src/main/java/com/mentorhub/service/StudentService.java`
- **Modifications**:
  1. Delete `getCanonicalCohortDetails(Student student)` (lines 342–395) and `adjustCanonicalForPause` (lines 267–295).
  2. In `buildWeeklyCalendar(Student student)`:
     - Remove lines 196–199 (`canonicalMaps.containsKey(...)`).
     - Anchor `monday` to current week or latest cohort week via `dailyReportRepository.findMaxReportDate()`.
     - Evaluate every day dynamically via `dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate)`.
  3. In `calculateDisciplineRate(Student student, List<DayInfoResponse> weekDays)`:
     - Delete lines 310–316 (the 5 hardcoded username checks).
     - Keep lines 317–318 as the sole calculation engine.

```java
    private List<DayInfoResponse> buildWeeklyCalendar(Student student) {
        List<DayInfoResponse> calendar = new ArrayList<>();
        LocalDate today = LocalDate.now(MINSK_ZONE);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Anchor to cohort active week if current calendar week has no reports
        if (!dailyReportRepository.existsByReportDateBetween(monday, monday.plusDays(6))) {
            LocalDate maxDate = dailyReportRepository.findMaxReportDate();
            if (maxDate != null) {
                monday = maxDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            }
        }

        for (int i = 0; i < 7; i++) {
            LocalDate dayDate = monday.plusDays(i);
            String dayName = RU_DAY_NAMES[i];
            String dateFormatted = DAY_DATE_FORMATTER.format(dayDate);

            // 1. Sunday rule: official cohort rest day
            if (i == 6) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "WEEKEND",
                        "Воскресенье — общий выходной потока ☕"
                ));
                continue;
            }

            // 2. Query database for authentic daily report
            Optional<DailyReport> reportOpt = dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate);
            if (reportOpt.isPresent()) {
                DailyReport report = reportOpt.get();
                if (Boolean.TRUE.equals(report.getIsOnTime())) {
                    calendar.add(new DayInfoResponse(
                            dayName,
                            dateFormatted,
                            "COMPLETED_ON_TIME",
                            formatReportDetails(report)
                    ));
                } else {
                    calendar.add(new DayInfoResponse(
                            dayName,
                            dateFormatted,
                            "COMPLETED_LATE",
                            formatLateReportDetails(report)
                    ));
                }
                continue;
            }

            // 3. Absence handling: check illness / pause mode
            if (Boolean.TRUE.equals(student.getIsPaused())) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "PAUSED",
                        "Режим паузы: болезнь, стрик заморожен ☕"
                ));
                continue;
            }

            // 4. Absence handling: active student missed deadline
            if (dayDate.isBefore(today)) {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "MISSED",
                        "Пропуск: отчёт не сдан до 21:00"
                ));
            } else {
                calendar.add(new DayInfoResponse(
                        dayName,
                        dateFormatted,
                        "PENDING",
                        null
                ));
            }
        }

        return calendar;
    }

    public int calculateDisciplineRate(Student student, List<DayInfoResponse> weekDays) {
        // Exclude WEEKEND, PAUSED, and PENDING days from denominator
        long evaluatedDays = weekDays.stream()
                .filter(d -> !"WEEKEND".equals(d.status()) && !"PAUSED".equals(d.status()) && !"PENDING".equals(d.status()))
                .count();

        if (evaluatedDays == 0) {
            return 100;
        }

        long onTimeCount = weekDays.stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
        long lateCount = weekDays.stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();

        double rate = (onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays;
        return (int) Math.round(rate);
    }

    private String formatReportDetails(DailyReport report) {
        StringBuilder sb = new StringBuilder();
        if (report.getContentDrafts() != null && !report.getContentDrafts().isEmpty()) {
            for (com.mentorhub.entity.ContentDraft draft : report.getContentDrafts()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(draft.getLabel());
            }
        }
        if (report.getMailingsCount() != null && report.getMailingsCount() > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(report.getMailingsCount()).append(" рассылок");
        }
        if (report.getNewClientsCount() != null && report.getNewClientsCount() > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("+").append(report.getNewClientsCount()).append(" клиента");
        }
        if (sb.isEmpty() && StringUtils.hasText(report.getAiSummary())) {
            sb.append(report.getAiSummary());
        }
        return sb.isEmpty() ? "Отчёт сдан" : sb.toString();
    }

    private String formatLateReportDetails(DailyReport report) {
        String base = formatReportDetails(report);
        if (report.getSubmittedAt() != null) {
            String timeStr = DateTimeFormatter.ofPattern("HH:mm").withZone(MINSK_ZONE).format(report.getSubmittedAt());
            return "Сдано в " + timeStr + " (опоздание): " + base;
        }
        return "Сдано с опозданием: " + base;
    }
```

---

### 3.3. Code Changes in `AnalyticsService.java`
- **File**: `src/main/java/com/mentorhub/service/AnalyticsService.java`
- **Lines 75–83**: Replace the branch override with pure calculation:

```java
<<<< OLD (lines 75-83)
        String revenueGrowthFactor;
        if (totalInitial.compareTo(BigDecimal.ZERO) <= 0) {
            revenueGrowthFactor = "+0%";
        } else if (Math.abs(totalEarned.doubleValue() - 895000.0) < 1.0 && Math.abs(totalInitial.doubleValue() - 180000.0) < 1.0) {
            revenueGrowthFactor = "+490%";
        } else {
            long pct = Math.round((totalEarned.doubleValue() - totalInitial.doubleValue()) / totalInitial.doubleValue() * 100);
            revenueGrowthFactor = "+" + pct + "%";
        }
==== NEW
        String revenueGrowthFactor;
        if (totalInitial.compareTo(BigDecimal.ZERO) <= 0) {
            revenueGrowthFactor = "+0%";
        } else {
            long pct = Math.round((totalEarned.doubleValue() - totalInitial.doubleValue()) / totalInitial.doubleValue() * 100);
            revenueGrowthFactor = "+" + pct + "%";
        }
>>>>
```

---

### 3.4. Code Changes in `DailyReportRepository.java`
- **File**: `src/main/java/com/mentorhub/repository/DailyReportRepository.java`
- Add two repository helper queries:

```java
    @org.springframework.data.jpa.repository.Query("SELECT MAX(r.reportDate) FROM DailyReport r")
    LocalDate findMaxReportDate();

    boolean existsByReportDateBetween(LocalDate startDate, LocalDate endDate);
```

---

### 3.5. Code Changes in `InitialDataSeeder.java`
- **File**: `src/main/java/com/mentorhub/service/InitialDataSeeder.java`
- Remove method `seedDailyReports(Student milana, Student anya, Student karina)` and lines 75–77, since all daily reports, content drafts, reel submissions, and sales entries are now seeded cleanly via Flyway `V2__seed_demo_daily_reports.sql`.
- Retain Zoom meeting seeding and `nextZoomAt` updates.

---

### 3.6. Test Alignment Changes
The tests must assert the mathematically authentic outputs instead of the cheated constants:

1. **`src/test/java/com/mentorhub/adversarial/ApiDisciplineAdversarialTest.java`**:
   - Line 221: Update `.andExpect(jsonPath("$.disciplineRate").value(88));` to `.value(93);`
   - Rationale: Victoria Lebedeva has 3 on-time (300) + 1 late (70) across 4 evaluated days = `370 / 4 = 92.5` -> `93%`. Friday/Saturday are paused and Sunday is weekend.

2. **`src/test/java/com/mentorhub/service/StudentServiceTest.java`**:
   - Line 88: Update `assertThat(response.disciplineRate()).isEqualTo(88);` to `isEqualTo(93);`

3. **`src/test/java/com/mentorhub/controller/AnalyticsControllerTest.java`**:
   - Line 41: Update `.andExpect(jsonPath("$.revenueGrowthFactor", is("+490%")))` to `is("+397%")`
   - Line 50: Update `.andExpect(jsonPath("$.averageDisciplineRate", is(87.8)))` to `is(88.0)`
   - Rationale: Cohort average discipline rate is `(95 + 57 + 100 + 93 + 95) / 5 = 440 / 5 = 88.0%`.

4. **`src/test/java/com/mentorhub/service/AnalyticsServiceTest.java`**:
   - Line 39: Update `assertThat(summary.revenueGrowthFactor()).isEqualTo("+490%");` to `isEqualTo("+397%");`
   - Line 66: Update `assertThat(summary.averageDisciplineRate()).isEqualTo(87.8);` to `isEqualTo(88.0);`

---

## 4. Caveats
1. **Clock Anchoring**: The application runs under timezone `Europe/Minsk`. In `buildWeeklyCalendar()`, anchoring `monday` via `existsByReportDateBetween()` with a fallback to `findMaxReportDate()` ensures the demo cohort week is properly presented regardless of the exact system date when automated test runners execute.
2. **Frontend UI Subtitle**: In `frontend/src/App.tsx:617`, the card subtitle currently displays a static text `+490% от старта`. The backend REST API endpoint `/api/analytics/summary` will return the mathematically authentic `revenueGrowthFactor: "+397%"`. `node scripts/test-runner.mjs` checks `895 000 ₽` and multipliers, passing 100%.

---

## 5. Conclusion
All three identified integrity violations can be eradicated without architectural regression:
1. `StudentService.java` will calculate discipline rates dynamically from `DailyReport` records, respecting `WEEKEND` and `PAUSED` without hardcoded username checks.
2. `V2__seed_demo_daily_reports.sql` will seed authentic demo reports, enabling `StudentService` to query the database directly for all calendar days and tooltips, allowing complete removal of `getCanonicalCohortDetails`.
3. `AnalyticsService.java` will dynamically compute `revenueGrowthFactor = "+397%"` via standard mathematical division without artificial branches.
4. With test expectations aligned to the authentic values, both `mvn clean test` (134 tests) and `node scripts/test-runner.mjs` (73 tests) will pass with 100% success rate.

---

## 6. Verification Method

### Step 1: Verify Elimination of Hardcoded Branches
```powershell
# Verify no hardcoded usernames in StudentService
rg "milana_gorohova" src/main/java/com/mentorhub/service/StudentService.java
# Must return: No results found

# Verify no hardcoded 490% branch in AnalyticsService
rg "895000" src/main/java/com/mentorhub/service/AnalyticsService.java
# Must return: No results found

# Verify no static cohort details map in StudentService
rg "getCanonicalCohortDetails" src/main/java/com/mentorhub/service/StudentService.java
# Must return: No results found
```

### Step 2: Verify Database Migration
Verify `src/main/resources/db/migration/V2__seed_demo_daily_reports.sql` exists and contains valid ANSI SQL syntax for all 26 reports, content drafts, reels, and sales entries.

### Step 3: Execute Backend Test Suite
```powershell
mvn clean test
```
- **Expected Outcome**: `BUILD SUCCESS`, `Tests run: 134, Failures: 0, Errors: 0, Skipped: 0`.

### Step 4: Execute Frontend Test Suite
```powershell
node scripts/test-runner.mjs
```
- **Expected Outcome**: `12 Suites, 73 Tests, 73 Passed, 0 Failed, Success Rate: 100%`.

### Step 5: Invalidation Conditions
This blueprint is invalidated if:
1. Any hardcoded username check or static calendar map remains in `StudentService.java`.
2. Any hardcoded constant override remains in `AnalyticsService.java`.
3. `mvn clean test` or `node scripts/test-runner.mjs` reports any error or failure.
