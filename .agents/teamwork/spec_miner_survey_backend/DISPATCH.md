## 2026-11-07T16:54:44Z
You are a Spec Miner subagent in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Your Identity and Working Directory:
- Identity: Backend Spec Miner
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Mission:
Investigate existing documentation in `c:\Users\Honor\Desktop\miranda\docs\` and all project requirements to extract complete specifications for R2 and R3:
1. Examine what docs already exist in `docs/` or elsewhere in the repo.
2. Formulate comprehensive technical specifications and architectural requirements for:
   - Telegram Bot Architecture: listening to student chats (1-on-1 via Telegram Business API `business_message` vs mini-groups), Reels URL validation (accessibility check & hash-based deduplication), hashtag parsing (#рилс, #продажи, #рассылки), question extraction for the mentor, automated daily 20:00 reminders for missing submissions.
   - AI Processing Pipeline: separating conversational flood/complaints from report facts, audio/voice message transcription.
   - Technology Stack: Java, Spring Boot 4.x & Spring AI 2.x (milestone/preview) with documented fallback to stable Spring Boot 3.4.x / Spring AI 1.x, PostgreSQL, TelegramBots 10.x.
   - Tariff Matrix & Roadmap: granular feature-by-feature breakdown, development phases, and labor estimate for:
     * Basic MVP (320 бел. руб.)
     * Full Variant (475 бел. руб.)
   - PostgreSQL schema (entities: students/mentees, daily reports, metrics/analytics, hashtag definitions/rules, questions/tasks).
   - REST API specification between backend and frontend web-tracker.
3. Detail all gaps between existing docs and R2/R3 requirements.
4. Produce a structured handoff report in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\handoff.md`.
5. Update your progress in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\progress.md`.
6. Once finished, send a completion message to your parent orchestrator using send_message.
