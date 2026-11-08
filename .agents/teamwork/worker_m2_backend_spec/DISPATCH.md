## 2026-11-07T17:01:50Z
You are a Worker subagent in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Also read the comprehensive survey specification report:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Identity & Working Directory:
- Identity: Backend Architecture Worker (Milestone M2)
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m2_backend_spec\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Exclusive Write File Boundaries:
You own and may ONLY edit:
1. `docs/ARCHITECTURE_SPECIFICATION.md` (create new comprehensive specification)
2. `docs/ARCHITECTURE_SPRING_BOOT.md` (keep updated and aligned)
DO NOT edit files in `frontend/` or `scripts/`.

Tasks:
Synthesize and write the authoritative, exhaustive technical architecture specification into `docs/ARCHITECTURE_SPECIFICATION.md` (in Russian, matching project standards), fully fulfilling R2 and R3 requirements:
1. Telegram Bot Architecture:
   - Dual-channel mechanics: 1-on-1 chats via Telegram Business Bot API (`business_message` with `business_connection_id`, `can_reply=true`, transparent replies sent on behalf of mentor Ksenia) vs Mini-groups (Bot Privacy Mode disabled, mapping `from.id` / `tg_chat_id`).
   - Anti-fraud Reels engine: canonical shortcode normalization regex, SHA-256 hash deduplication across all students with error alerts for cross-student duplicates, async non-blocking HTTP HEAD/GET accessibility probe (with browser User-Agent header and handling of 302/404/429).
   - Hashtag engine: parsing `#рилс`, `#продажи`, `#рассылки`, `#вопрос`, state accumulator upserting daily submissions (`uq_student_report_date`) for multi-message reporting.
   - Automated Daily 20:00 Reminder Engine: Spring `@Scheduled` cron alerting students who haven't submitted by 20:00, with rate-limiting.
2. AI Processing Pipeline:
   - Voice message transcription: OGG Opus (`.oga`) Telegram download -> Whisper / Gemini STT API.
   - Structured factual extraction: Spring AI `ChatClient` prompt separating chat flood and complaints from report facts into `ExtractedReportDto`, burnout alert detection (`BURNOUT_ALERT`), and deterministic regex fallback.
3. Technology Stack & Fallback Specification:
   - Java 21 LTS (Virtual Threads `spring.threads.virtual.enabled=true`) / Java 25.
   - Target: Spring Boot 4.0.0-M1 & Spring AI 2.0.0-M1.
   - Production Fallback: Spring Boot 3.4.2 & Spring AI 1.0.0-M5 (with full Maven POM properties).
   - TelegramBots 10.3.0 (`telegrambots-springboot-longpolling-starter`).
   - PostgreSQL 16+.
4. Commercial Tariff Matrix & Roadmap:
   - Granular side-by-side comparative table: Base MVP (320 бел. руб.) vs Full PRO (475 бел. руб.).
   - Detailed Work Breakdown Structure (WBS) across 7 engineering modules with hour estimates (34 hours vs 52 hours), timelines (5–7 days vs 10–14 days), and ongoing monthly server costs (~25 vs ~35 BYN).
5. Complete PostgreSQL 16 Schema (DDL):
   - 7 normalized tables: `students`, `task_rules`, `daily_reports`, `reel_submissions`, `sales_entries`, `mentor_settings`, `audit_message_logs` with primary/foreign keys, indexes, unique constraints, and seed data.
6. REST API Specification:
   - 14 OpenAPI-grade endpoints matching the React SPA frontend models.
7. Discovered Features & Edge Cases Catalog:
   - Catalog of features and edge case handling rules.
8. Verify brand neutrality: strictly "Mentor Hub / Mentor Tracker", zero mentions of "миранд" / "miranda".
9. Write a detailed handoff report in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m2_backend_spec\handoff.md`.
10. Notify parent orchestrator using send_message when complete.
