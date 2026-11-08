# Technical Review Report: Telegram Bot, Spring AI, Schedulers & Infrastructure

**Reviewer Archetype**: reviewer, critic  
**Review Target**: Telegram Bot Integration, Anti-Fraud Engine, Spring AI & Fallback Services, Schedulers, Docker & Containerization  
**Review Date**: 2026-11-08  
**Definitive Verdict**: **APPROVE**  

---

## 1. Observation

Direct observations from inspecting codebase and executing verification commands:

### A. Telegram Bot Integration (`com.mentorhub.telegram`)
1. **Dual-Channel Connection**:
   - `MentorBotConsumer.java` (lines 16-20): Implements `LongPollingSingleThreadUpdateConsumer` and `SpringLongPollingBot` (TelegramBots 10.3). Guarded by `@ConditionalOnProperty(name = "telegram.bot.mode", havingValue = "long-polling", matchIfMissing = true)` and `@ConditionalOnExpression("!'${telegram.bot.token:}'.isEmpty() && !'${telegram.bot.token:}'.startsWith('mock')")` to prevent crash on mock environments.
   - `TelegramWebhookController.java` (lines 24-41): Exposes `POST /api/telegram/webhook` with `X-Telegram-Bot-Api-Secret-Token` validation before forwarding updates to router.
2. **Echo Filtering & Mini-Group Routing**:
   - `TelegramMessageRouter.java` (lines 106-125): Explicitly filters bot echoes (`from.getIsBot()`), mentor echoes (`isMentorEcho(fromId)` checking `mentor_tg_id` in `mentor_settings` and `admin_tokens` for MENTOR role), and curator echoes (`isCuratorEcho(fromId)` checking `admin_tokens` for CURATOR role).
   - Lines 54-70: Classifies update sources into `BUSINESS_1ON1`, `MINI_GROUP` (detecting group/supergroup chats), and `DIRECT_BOT`.
   - Lines 127-147: Matches students by group `tg_chat_id`, direct `tg_user_id`, or `tg_username`. Unmatched incoming messages are recorded into `audit_message_logs` with status `UNMATCHED_USER`.
3. **Accumulator Upsert & Draft Intake**:
   - `TelegramReportHandler.java` (lines 58-76): Finds existing daily report for student and date (`findByStudentIdAndReportDate`) or instantiates a new builder with `PENDING` status.
   - Lines 104-140: Accumulates incremental sales amounts (`report.getSalesTotal().add(...)`) with `SalesEntry` persistence, increments mailings count, and increments new clients count.
   - Lines 194-259 (`processAttachmentsAndDrafts`): Ingests video (`REEL`), video note кружочек (`REEL`), PDF document (`CAROUSEL`), photos (`CAROUSEL`), and text hashtags `#контент`, `#рилс`, `#карусель`, `#сторителл` into `content_drafts`.
   - Lines 344-353 (`appendQuestion`): Appends questions with timestamp prefix `[HH:mm]` to `mentor_question`.
   - Lines 181-186: Automatically dispatches caring acknowledgment reply: `"Принято на проверку Ксении и Кате! ✍️ Черновик сохранён."`.

### B. Anti-Fraud & Deduplication (`com.mentorhub.telegram.antifraud`)
1. **Canonical Shortcode Extraction & Hash**:
   - `ReelsAntiFraudEngine.java` (line 29): `REELS_PATTERN = Pattern.compile("(?i)(?<![a-zA-Z0-9.-])(?:https?://)?(?:www\\.)?(?:instagram\\.com|instagr\\.am)/(?:reel|reels|p|share/reel)/([A-Za-z0-9_-]+)")`.
   - Normalizes to canonical URL `https://www.instagram.com/reel/<shortcode>/`.
   - Computes standard SHA-256 hex digest via `DigestUtils.sha256Hex(shortcode)`.
2. **Plagiarism & Duplicate Evaluation**:
   - Lines 82-112: Queries `reelSubmissionRepository.findByReelHash(reelHash)`.
   - If submitted by same student: flags `isDuplicate = true`, formats Russian date `dd MMMM` (e.g., "04 ноября").
   - If submitted by a different student (`!prevStudent.getId().equals(currentStudent.getId())`): flags `isCrossStudentFraud = true`, logs `CRITICAL ANTI-FRAUD ALERT`, sets draft label to `ФРОД: ролик сдан ученицей <Имя> (<Дата>)`.
3. **HTTP Accessibility Probe**:
   - `ReelsAccessibilityProbe.java` (lines 31-60): Performs probe using RestClient with real browser User-Agent header.
   - Status 2xx/301/302 -> `isValid = true`.
   - Status 404 -> `isValid = false` ("Ролик не найден или удален (404)").
   - Status 429 (Rate Limit) / Socket Timeout / Network exception -> gracefully handles as `isValid = true` with note `TIMEOUT_UNVERIFIED` preventing false student penalization.

### C. Spring AI & Schedulers (`com.mentorhub.ai`, `com.mentorhub.scheduler`)
1. **Spring AI & Deterministic Fallback**:
   - `SpringAiReportExtractor.java` (lines 59-64, 116-134): Checks `isAiConfigured()`. If mock key or offline, immediately delegates to fallback. If enabled, executes ChatClient in Virtual Threads with a strict 3000ms timeout (`future.get(AI_TIMEOUT_MS, TimeUnit.MILLISECONDS)`). Any timeout or exception triggers fallback, ensuring zero report loss.
   - Prompt (lines 137-159): Enforces 3 structured bullets (`emotions`, `actions`, `questionToMentor`), burnout detection (`BURNOUT_ALERT` on fatigue/crying keywords), sales/mailings normalization, and iPad summary.
   - `DeterministicAiFallbackService.java` (lines 145-202): Normalizes sales amounts with thousands grouping: `"35.500 руб"` -> `35500.00`, `"80к"` -> `80000.00`, `"45 000 руб"` -> `45000.00`. Detects burnout keywords.
2. **Schedulers**:
   - `DailyReminderScheduler.java` (lines 34-43, 66-84): Cron `@Scheduled(cron = "${telegram.reminder.cron:0 0 20 * * MON-SAT}", zone = "${telegram.reminder.zone:Europe/Minsk}")`. Explicitly skips Sunday (`today.getDayOfWeek() == DayOfWeek.SUNDAY`). Excludes paused/sick students (`!Boolean.TRUE.equals(s.getIsPaused())`). Excludes students with pending/approved reports today.
   - `SaturdaySurveyScheduler.java` (lines 30-38): Cron `@Scheduled(cron = "${telegram.poll.cron:0 0 18 * * SAT}", zone = "${telegram.poll.zone:Europe/Minsk}")`. Sends inline button keyboard `[0]`, `[1]`, `[2]`, `[3]`, `[4]`, `[5+]` with callback `survey:clients:<studentId>:<count>`.
   - `CallbackQueryHandler.java` (lines 33-95): Handles callback queries, records new clients into today's report and student totals, sends answer callback popup, and edits chat message with confirmation.

### D. Containerization
1. `Dockerfile`: Two-stage build: Maven 3.9.9 / Temurin 21 JDK Alpine -> Temurin 21 JRE Alpine. Non-root user `spring:spring`, exposes 8080.
2. `frontend/Dockerfile`: Two-stage build: Node 24 Alpine -> Nginx Alpine. Copies dist and `nginx.conf`, exposes 80.
3. `frontend/nginx.conf`: Serves SPA with `try_files $uri $uri/ /index.html;`, reverse-proxies `/api/` to `http://backend:8080/api/`.
4. `frontend/vite.config.ts`: Configures `base: './'` and dev proxy `/api` -> `http://localhost:8080`.
5. `docker-compose.yml`: Defines `postgres:16-alpine`, `backend`, `frontend`, networks `mentor-network`, volumes `pgdata`, healthcheck `pg_isready -U postgres -d mentor_hub`, `depends_on: postgres: condition: service_healthy`.

### E. Verification Execution
1. `docker compose config`:
   ```
   Command exited with code 0.
   Validated services: postgres, backend, frontend, networks: mentor-network, volumes: pgdata.
   ```
2. `mvn clean test`:
   ```
   [INFO] Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   [INFO] Total time: 43.804 s
   ```
3. `node scripts/test-runner.mjs`:
   ```
   Total Suites: 12
   Total Tests: 73
   Passed Tests: 73
   Failed Tests: 0
   Success Rate: 100%
   Total Duration: 2718ms
   ```

---

## 2. Logic Chain

1. **Telegram Ingestion & Architecture**:
   - `MentorBotConsumer` and `TelegramWebhookController` properly instantiate TelegramBots 10.3 components and provide flexibility between long-polling and webhook.
   - Mini-groups require filtering mentor and curator echoes so Ksenia's feedback doesn't get recorded as student submissions. `TelegramMessageRouter` verifies sender IDs against `MentorSetting` and `AdminToken` tables, fulfilling this isolation requirement.
   - Pre-publication drafts intake recognizes `#контент`, `#рилс`, `#карусель`, `#сторителл` and stores attachments (video, video notes, PDFs, photos) into `content_drafts`.
   - Multi-message submissions within the same day are accumulated into a single `DailyReport` without overwriting prior data, and questions are appended with time tags (`[HH:mm]`).

2. **Anti-Fraud Integrity**:
   - Shortcodes are reliably extracted across multiple URL formats (`instagram.com/reel/`, `instagr.am/p/`, `instagram.com/share/reel/`) with URL query parameter sanitization.
   - SHA-256 hashing allows fast, deterministic lookup against historical submissions.
   - Cross-student collision detection correctly distinguishes between a student resubmitting their own Reel (soft duplicate warning) versus submitting another student's Reel (critical red alert for plagiarism).
   - Network resilience is maintained by falling back to `TIMEOUT_UNVERIFIED` rather than marking links invalid on HTTP 429 or network timeouts.

3. **Spring AI & Scheduling Discipline**:
   - The AI pipeline is resilient: timeouts (3000ms) or missing API keys gracefully route to the deterministic regex fallback service with zero report loss.
   - The 3-bullet reflection structure (emotions, actions, questionToMentor) and burnout detection are implemented both in the AI prompt and in the deterministic fallback.
   - Schedulers respect cohort discipline: daily reminders fire at 20:00 (1 hour before deadline), exclude Sundays, exclude students on medical pause/leave, and query only debtors.
   - Saturday survey at 18:00 delivers inline buttons `[0]`..`[5+]`, updates client totals, and modifies the message in-place upon answer.

4. **Containerization & Deployment**:
   - Both backend and frontend Dockerfiles follow multi-stage build best practices with minimal runtime images and non-root execution.
   - `docker-compose.yml` binds PostgreSQL, Backend, and Frontend on a dedicated bridge network with dependency health checks. Syntax validated via `docker compose config`.

5. **Integrity & Code Quality**:
   - No mock facades or hardcoded bypasses found in business logic.
   - Real database queries, real regex engines, real hashing, and real API integrations are employed.
   - All 89 Java unit/integration tests and all 73 frontend tests pass with 100% success.

---

## 3. Caveats

1. **Instagram Probe Rate Limiting**: Under real Instagram traffic, checking links via direct HTTP GET may trigger Instagram's anti-bot protections (HTTP 429 or login redirects). The code correctly handles this by defaulting to `TIMEOUT_UNVERIFIED` without rejecting student work.
2. **Missing `.dockerignore`**: Neither root nor `frontend/` contains a `.dockerignore` file. While `Dockerfile` specifies exact directories (`COPY src ./src`, `COPY pom.xml .`), adding `.dockerignore` (excluding `.git`, `target`, `node_modules`) is a recommended operational enhancement.
3. **Draft Unique Check**: `contentDraftRepository.findAll().stream().anyMatch(...)` is used in `TelegramReportHandler` for checking duplicate `fileTgId`. For a cohort of 5-20 students this is negligible, but for large scale an indexed repository method `findByFileTgId` is preferable.

---

## 4. Conclusion

The Telegram Bot Integration, Anti-Fraud Engine, Spring AI & Schedulers, and Containerization fully conform to `docs/ARCHITECTURE_SPECIFICATION.md` and satisfy all requirements of `ORIGINAL_REQUEST.md` (R1, R2, R3).
No integrity violations or blocking defects were detected.

**Final Verdict**: **APPROVE**

---

## 5. Verification Method

To independently reproduce and verify this assessment:

1. **Verify Docker Compose Configuration**:
   ```powershell
   docker compose config
   ```
   *Expected output*: Validated YAML configuration without errors, exit code 0.

2. **Verify Backend Maven Tests**:
   ```powershell
   mvn clean test
   ```
   *Expected output*: 89 tests run, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.

3. **Verify Frontend Test Suite**:
   ```powershell
   node scripts/test-runner.mjs
   ```
   *Expected output*: 73 tests run across 12 suites, 100% passing, `TEST_READY.md` written.

4. **Inspect Key Source Files**:
   - `src/main/java/com/mentorhub/telegram/router/TelegramMessageRouter.java` (lines 115-125: echo filtering)
   - `src/main/java/com/mentorhub/telegram/antifraud/ReelsAntiFraudEngine.java` (lines 80-130: cross-student plagiarism)
   - `src/main/java/com/mentorhub/ai/service/SpringAiReportExtractor.java` (lines 116-135: 3s virtual thread timeout & fallback)
   - `src/main/java/com/mentorhub/scheduler/DailyReminderScheduler.java` (lines 34-43: Sunday off, debtor filtering)
   - `docker-compose.yml` (lines 1-59: healthcheck and network configuration)
