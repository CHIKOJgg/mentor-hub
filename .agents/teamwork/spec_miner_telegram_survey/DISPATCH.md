## 2026-11-08T14:29:09Z
You are a teamwork_spec_miner. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_telegram_survey\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

YOUR TASK:
Examine docs/ARCHITECTURE_SPECIFICATION.md and docs/ARCHITECTURE_SPRING_BOOT.md to extract the precise specification for:
1. Telegram Bot (TelegramBots 10.3 / telegram-bot-spring-boot-starter):
   - Mini-groups architecture: "Ксения + Катя + Ученица + Бот" (bot acts as caring assistant, no participation in main stream chat).
   - Connection mode: Long Polling by default (for local run and docker-compose) with configuration switch to Webhook.
   - Message handling: receiving drafts of content BEFORE publication (#контент, #рилс, #карусель, #сторителл).
   - Deduplication (SHA-256 hash of media/content) & anti-fraud rules.
   - Recognition of questions directed to Ksenia.
2. Spring AI Integration:
   - Summarization of voice messages and video notes (кружочки) into 3 structured bullets:
     1) Эмоции / затык
     2) Действия за день
     3) Вопрос к Ксении
   - External LLM API configuration via environment variables.
   - Automatic mock/stub fallback if API key is absent for local dev and offline tests.
3. Background Scheduler (@Scheduled):
   - Daily evening reminders for report submission.
   - Saturday auto-survey at 18:00 regarding new clients and revenue.

Produce a detailed specification report with exact event flows, handler logic, hashing algorithms, prompt templates, fallback behaviors, and cron schedules in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_telegram_survey\handoff.md
Update progress.md in your directory as you work.
When complete, send a message to your caller notifying that handoff.md is ready.
