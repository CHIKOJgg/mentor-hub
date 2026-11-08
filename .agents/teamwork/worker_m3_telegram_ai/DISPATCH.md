## 2026-11-08T15:04:55Z
You are a teamwork_worker. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m3_telegram_ai\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

READ YOUR INPUT SPECIFICATIONS:
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_telegram_survey\handoff.md (Sections 2, 3, 4, 5.3, 5.4)
- Read c:\Users\Honor\Desktop\miranda\docs\ARCHITECTURE_SPECIFICATION.md (Sections 2.2, 2.3, 3, 4, 5, 6)
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\orchestrator\PROJECT.md

YOUR MILESTONE SCOPE (M3: Telegram Bot, Spring AI Engine, Anti-Fraud & Background Scheduler):
You have exclusive write ownership over:
- src/main/java/com/mentorhub/telegram/**
- src/main/java/com/mentorhub/ai/**
- src/main/java/com/mentorhub/scheduler/**
- src/test/java/com/mentorhub/telegram/**
- src/test/java/com/mentorhub/ai/**
- src/test/java/com/mentorhub/scheduler/**

IMPLEMENTATION INSTRUCTIONS:
1. Implement Telegram Bot subsystem in com.mentorhub.telegram:
   - MentorBotConsumer: implements LongPollingSingleThreadUpdateConsumer, conditionally enabled via @ConditionalOnProperty(name = "telegram.bot.mode", havingValue = "long-polling", matchIfMissing = true) and conditional on bot token.
   - TelegramWebhookController: @RestController @RequestMapping("/api/telegram") for Webhook mode (POST /api/telegram/webhook) with X-Telegram-Bot-Api-Secret-Token validation.
   - TelegramMessageRouter & Handler:
     * Handles group messages (mini-groups "Ксения + Катя + Ученица + Бот").
     * Filters out echo from mentors (Ksenia mentor_tg_id, Katya admin_tokens tg_user_id) and bots.
     * Identifies student by tg_chat_id / tg_user_id. Unknown users logged to audit_message_logs as UNMATCHED_USER.
     * Ingestion of drafts BEFORE publication: #контент, #рилс, #карусель, #сторителл. Captures attachments (video, video notes, pdf, photo, text).
     * Question recognition: #вопрос, direct address "Ксения, ...", and AI extraction. Concatenates multiple questions over the day.
     * Accumulator upsert: saves to daily_reports using (student_id, report_date) unique key. Normalizes sales amounts with thousands separators (#продажи 35.500 руб -> 35500.00).
     * Sends caring acknowledgment reply in mini-group: "Принято на проверку Ксении и Кате! ✍️ Черновик сохранён."
2. Implement Anti-Fraud & Deduplication Engine in com.mentorhub.telegram.antifraud:
   - ReelsAntiFraudEngine:
     * Canonical shortcode regex: (?i)(?<![a-zA-Z0-9.-])(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:reel|reels|p|share\/reel)\/([A-Za-z0-9_-]+)
     * Computes SHA-256 hash of shortcode or media file_unique_id / bytes.
     * Checks reel_submissions table:
       - Same student match -> is_duplicate = true, duplicate_date recorded (yellow alert).
       - Cross-student match -> is_duplicate = true, critical plagiarism alert "ФРОД: ролик сдан ученицей {name}" (red alert).
     * ReelsAccessibilityProbe: non-blocking HTTP HEAD/GET probe. Sets TIMEOUT_UNVERIFIED on 429/timeout without rejecting report.
3. Implement Spring AI integration in com.mentorhub.ai:
   - SpringAiReportExtractor: uses Whisper STT for audio (.oga) and video notes (.mp4), and ChatClient for structured 3-bullet reflection summarization:
     1) emotions (emotions / za-tyk)
     2) actions (daily actions)
     3) questionToMentor (question to Ksenia)
     Plus burnout detection (BURNOUT_ALERT).
   - DeterministicAiFallbackService: automated fallback when AI_API_KEY is missing/mock or on network timeout (>3000ms). Extracts #рассылки, #продажи, #вопрос via regex, detects burnout keywords (выгорела, устала, руки опустились, нет сил), and formats 3-bullet reflection. Guarantees 0 report loss.
4. Implement Background Scheduler in com.mentorhub.scheduler:
   - DailyReminderScheduler: @Scheduled(cron = "0 0 20 * * MON-SAT", zone = "Europe/Minsk"). Sends reminders to mini-groups for students missing reports 1 hr before 21:00 deadline. Strictly excludes sick/paused students (is_paused = true) and Sundays.
   - SaturdaySurveyScheduler: @Scheduled(cron = "0 0 18 * * SAT", zone = "Europe/Minsk"). Pings mini-groups with interactive inline buttons [0] [1] [2] [3] [4] [5+] (callback_data: survey:clients:{studentId}:{count}).
   - CallbackQueryHandler: records new_clients_count in Saturday's daily_report, updates students.total_new_clients, and confirms with AnswerCallbackQuery.
5. Write thorough unit and integration tests in test directories:
   - Tests for message router, hashtag parsing, accumulator upsert.
   - Tests for SHA-256 deduplication (same student vs cross-student plagiarism).
   - Tests for Spring AI 3-bullet reflection and deterministic fallback.
   - Tests for daily 20:00 reminder logic (skipping paused students & Sundays).
   - Tests for Saturday 18:00 survey and callback query handling.
6. Verification:
   - Run `mvn clean test` and verify that ALL backend tests pass with 100% success rate.
   - Run `node scripts/test-runner.mjs` and verify that all 73 frontend automated tests continue to pass 100%.

Produce a detailed handoff report in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m3_telegram_ai\handoff.md
Include build commands and execution logs in your handoff report.
Update progress.md in your directory as you work.
When done, message your caller.
