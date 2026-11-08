# Empirical Challenge Report: Telegram Bot, Anti-Fraud, Spring AI Fallback, and Schedulers

**Verdict:** **APPROVE**  
**Date:** 2026-11-08  
**Agent:** challenger_telegram_antifraud (critic, specialist)  
**Target Modules:** `com.mentorhub.telegram.*`, `com.mentorhub.ai.*`, `com.mentorhub.scheduler.*`

---

## 1. Observation

### 1.1. Anti-Fraud & URL Normalization
- **Regex Implementation**: `ReelsAntiFraudEngine.java` (lines 28–30):
  ```java
  public static final Pattern REELS_PATTERN = Pattern.compile(
      "(?i)(?<![a-zA-Z0-9.-])(?:https?://)?(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:reel|reels|p|share/reel)/([A-Za-z0-9_-]+)"
  );
  ```
- **Observed Behavior across Messy URL Inputs**:
  - `https://www.instagram.com/reel/DC194kaP91m/?igsh=MWQ1ZGUxMzBkMA==` ➡️ shortcode `DC194kaP91m`, canonical `https://www.instagram.com/reel/DC194kaP91m/`.
  - `https://instagram.com/reel/DC194kaP91m?utm_source=qr&utm_medium=copy` ➡️ shortcode `DC194kaP91m`.
  - `http://instagr.am/p/DC194kaP91m/?utm_source=ig_web_copy_link` ➡️ shortcode `DC194kaP91m`.
  - `https://www.instagram.com/share/reel/DC194kaP91m/` ➡️ shortcode `DC194kaP91m`.
  - `https://instagram.com/reels/DC194kaP91m?fbclid=IwAR234` ➡️ shortcode `DC194kaP91m`.
  - Russian text with punctuation `(https://www.instagram.com/reel/DC194kaP91m?igsh=123)!` ➡️ shortcode `DC194kaP91m`.
  - Uppercase domains `HTTPS://WWW.INSTAGRAM.COM/REEL/DC194kaP91m/?UTM_CAMPAIGN=TEST` ➡️ shortcode `DC194kaP91m`.
  - Shortcodes with underscores and hyphens `ABC_123-xyz`, `---___999___---`, `Reel_Code-2026` ➡️ preserved exactly.
- **SHA-256 Deduplication**:
  - `ReelsAntiFraudEngine.calculateHash(shortcode)` returns standard 64-character hex hash:
    $$\text{SHA-256("DC194kaP91m")} = \text{"063c5be144ee6dd68b1a8d05dd31bb4887372986be3a10ee95713cfa2b947c6a"}$$
  - Internal Duplicate: When student #1 resubmits an earlier reel, `eval.isDuplicate() = true`, `eval.isCrossStudentFraud() = false`, `eval.fraudAlertMessage() = null`, `eval.duplicateDate()` includes previous submission date (yellow warning banner).
  - Cross-Student Fraud: When student #2 submits a reel previously submitted by student #1, `eval.isCrossStudentFraud() = true`, `eval.fraudAlertMessage()` generates:
    `"ФРОД: ролик сдан ученицей Милана Горохова (03 ноября)"` (red alert badge).
- **Accessibility Probe Resilience**: `ReelsAccessibilityProbe.java` (lines 50–59):
  - HTTP 429 TooManyRequests ➡️ `new ProbeResult(true, 429, "TIMEOUT_UNVERIFIED: Превышен лимит запросов сети (429)")`.
  - `ResourceAccessException` / Network Timeout ➡️ `new ProbeResult(true, 0, "TIMEOUT_UNVERIFIED: Таймаут сети")`.
  - Verified: `isValid` evaluates to `true`, preventing report rejection during Instagram throttling or transient timeouts.
  - HTTP 404 (deleted/nonexistent reel) ➡️ `new ProbeResult(false, 404, "Ролик не найден или удален (404)")`.

### 1.2. Spring AI & Deterministic Fallback
- **Structured 3-Bullet Reflection**: `DeterministicAiFallbackService.java` (lines 68–86):
  - `emotions`: `"Состояние рабочее (детерминированный фолбэк)"` or `"Обнаружены маркеры выгорания / апатии (детерминированный фолбэк)"`.
  - `actions`: `"Обработано рассылок: %d, сумма продаж: %s ₽"`.
  - `questionToMentor`: Extracts from `#вопрос`, `#затык`, `#проблема` or direct address `"Ксения, ..."`.
- **Burnout Keyword Detection**: `DeterministicAiFallbackService.java` (lines 41–45, 226–235):
  - Phrases `"выгорела"`, `"устала"`, `"руки опустились"`, `"нет сил"`, `"рыдала"`, `"все бросить"`, `"апатия"`, `"депрессия"` in all casings and punctuation trigger `emotionalStatus = "BURNOUT_ALERT"`.
  - Reports without stress markers output `emotionalStatus = "NORMAL"`.
- **Deterministic Offline Resilience**:
  - Verified with `spring.ai.openai.api-key: mock-key`: `isAiConfigured() = false`.
  - 0 report loss: Russian grouping separators normalized accurately:
    - `#продажи 35.500 руб` ➡️ `35500.00`
    - `#продажи 45 000 руб` ➡️ `45000.00`
    - `#продажи 80к` / `#продажи 80k` ➡️ `80000.00`
    - `#продажи 120.000` ➡️ `120000.00`
  - Audio transcription `.transcribeAudio()` with mock key returns safe fallback `"[Голосовое сообщение / кружочек: расшифровка в офлайн-режиме]"` without crashing.

### 1.3. Schedulers & Background Jobs
- **Daily 20:00 Reminder Engine**: `DailyReminderScheduler.java`:
  - Cron: `0 0 20 * * MON-SAT`, zone `Europe/Minsk`.
  - Sunday Exclusion: On `DayOfWeek.SUNDAY` (e.g. `2026-11-08`), `findDebtors` strictly returns `[]`, 0 reminders dispatched.
  - Paused Student Exclusion: Student with `isPaused = true` (`"Болеет ОРВИ"`) is strictly excluded.
  - Completed Report Exclusion: Students with `DailyReport` having status `PENDING` or `APPROVED` are strictly excluded.
  - Only active, non-paused debtors receive:
    `"{Имя}, привет! Напоминаю: до дедлайна сдачи отчета остался 1 час (до 21:00) ⏰ Ждем твои черновики #контента..."`.
- **Saturday 18:00 Weekly Survey**: `SaturdaySurveyScheduler.java` & `CallbackQueryHandler.java`:
  - Cron: `0 0 18 * * SAT`, zone `Europe/Minsk`.
  - Generates inline keyboard with 6 buttons: `[0]`, `[1]`, `[2]`, `[3]`, `[4]`, `[5+]` with callback data `survey:clients:{studentId}:{count}`.
  - Callback Processing:
    - Student `total_new_clients` atomically increments by `count`.
    - DailyReport for today created/updated with `newClientsCount = count`.
    - `AnswerCallbackQuery` called with celebration: `"Зафиксировано: +{count} клиентов! 🎉"`.
    - Message edited with confirmation: `"Спасибо! Зафиксировано новых клиентов за неделю: +{count} 🚀"`.
    - Malformed callback data, null data, and non-existent student IDs handled safely without throwing uncaught exceptions.

### 1.4. Test Suite Execution Logs
- **Adversarial Test Suite** (`mvn test -Dtest=AdversarialEmpiricalChallengeTest`):
  ```
  [INFO] Running com.mentorhub.adversarial.AdversarialEmpiricalChallengeTest
  [INFO] Running com.mentorhub.adversarial.AdversarialEmpiricalChallengeTest$EndToEndPipelineTests
  [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.mentorhub.adversarial.AdversarialEmpiricalChallengeTest$SchedulerTests
  [INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.mentorhub.adversarial.AdversarialEmpiricalChallengeTest$SpringAiFallbackTests
  [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Running com.mentorhub.adversarial.AdversarialEmpiricalChallengeTest$AntiFraudDeduplicationTests
  [INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
  [INFO] Results: Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Full Backend Suite** (`mvn clean test`):
  ```
  [INFO] Results:
  [INFO] Tests run: 134, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  ```
- **Frontend Test Suite** (`node scripts/test-runner.mjs`):
  ```
  Total Suites:    12
  Total Tests:     73
  Passed Tests:    73
  Failed Tests:    0
  Success Rate:    100%
  ```

---

## 2. Logic Chain

1. **Premise 1 (Anti-Fraud & Resilience)**: Student submissions in Telegram vary wildly in URL structure, parameters, and network conditions.
   - *Observation*: `REELS_PATTERN` matched all 8 variations of messy URLs, extracting identical shortcodes and stripping trackers.
   - *Observation*: Cross-student submissions correctly generated `ФРОД:` alerts while same-student submissions were flagged as benign duplicates.
   - *Observation*: Simulated 429 and connection timeouts returned `isValid = true` with `TIMEOUT_UNVERIFIED`, preventing wrongful report rejections.
   - *Deduction*: The anti-fraud and deduplication engine is resilient against adversarial inputs and network degradation.

2. **Premise 2 (AI Processing & Fallback)**: The system must never lose student reports when LLM APIs are unreachable or offline.
   - *Observation*: With mock/disabled API keys, `DeterministicAiFallbackService` extracted 100% of outreach, questions, and sales amounts (including Russian `\d+[.\s]\d{3}` and `к` notations).
   - *Observation*: Burnout keywords ("выгорела", "устала", "руки опустились", "нет сил") accurately mapped to `BURNOUT_ALERT`.
   - *Deduction*: Spring AI fallback architecture guarantees zero report loss and faithful reflection summarization.

3. **Premise 3 (Scheduler Accuracy)**: Automated cron jobs must respect cohort rules (Sunday off, paused students frozen, atomic survey counts).
   - *Observation*: Sunday was excluded unconditionally. Paused/sick students were filtered out from reminders.
   - *Observation*: Saturday survey inline callbacks atomically updated both student aggregates and daily reports, and acknowledged user interactions via Telegram API.
   - *Deduction*: Schedulers adhere strictly to cohort discipline specifications.

---

## 3. Caveats

- Live Telegram Long Polling / Webhook updates were verified with simulated `Update`, `Message`, and `CallbackQuery` objects and mock TelegramClient rather than connecting to live Telegram Cloud servers (per standard automated testing discipline).
- Live Whisper STT audio transcription with real OpenAI API tokens was not run in CI/offline environment (offline stubbing verified).

---

## 4. Conclusion

All empirical challenge criteria for the Telegram Bot, Anti-Fraud Engine, Spring AI Fallback, and Schedulers have been verified and passed without regressions:
1. Canonical shortcode normalization across messy URLs: **PASSED (100%)**
2. SHA-256 deduplication (yellow duplicate vs red fraud alert): **PASSED (100%)**
3. Accessibility probe 429/timeout non-blocking handling: **PASSED (100%)**
4. 3-bullet reflection summarization & burnout detection: **PASSED (100%)**
5. Offline deterministic fallback (0 report loss): **PASSED (100%)**
6. 20:00 reminder logic (Sunday and paused student exclusion): **PASSED (100%)**
7. Saturday 18:00 survey inline buttons & callback updates: **PASSED (100%)**

**FINAL VERDICT: APPROVE**

---

## 5. Verification Method

To independently reproduce and verify this assessment:

1. **Execute the adversarial stress test suite**:
   ```powershell
   mvn test -Dtest=AdversarialEmpiricalChallengeTest
   ```
   *Expected result*: 27 tests executed, 0 failures, 0 errors, `BUILD SUCCESS`.

2. **Execute the complete backend test suite**:
   ```powershell
   mvn clean test
   ```
   *Expected result*: 134 tests executed, 0 failures, 0 errors, `BUILD SUCCESS`.

3. **Execute the frontend automated test suite**:
   ```powershell
   node scripts/test-runner.mjs
   ```
   *Expected result*: 73 tests executed, 0 failures, 100% success rate.
