# BRIEFING — 2026-11-07T17:00:00Z

## Mission
Investigate docs and project requirements to produce comprehensive specifications and architectural designs for Mentor Hub / Mentor Tracker backend (R2 and R3), covering Telegram Bot architecture, AI pipeline, tech stack, tariff matrix & roadmap, PostgreSQL schema, and REST API specification.

## 🔒 My Identity
- Archetype: SPECIFICATION MINER
- Roles: Backend Spec Miner
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\
- Original parent: 63968e95-d6d9-4b5f-843b-c0a9626f44e5
- Milestone: M1 — Documentation & Technical Specification (R2 & R3)

## 🔒 Key Constraints
- Specification Miner only: do NOT implement anything, read-only with respect to project source code.
- Probe authoritative specifications (ORIGINAL_REQUEST.md, docs/*, existing repo structure).
- Document all features, inputs, outputs, error behaviors, and edge cases.
- Address Telegram Business API vs mini-groups, Reels URL validation, hashtag parsing, daily 20:00 reminders.
- Address AI flood filtering & audio transcription.
- Address Java / Spring Boot 4.x & Spring AI 2.x with documented fallback to 3.4.x / 1.x.
- Tariff Matrix: MVP (320 BYN) vs Full (475 BYN) with labor and phases.
- PostgreSQL schema and REST API contract.
- Write handoff report in handoff.md and report back to parent orchestrator via send_message.

## Current Parent
- Conversation ID: 63968e95-d6d9-4b5f-843b-c0a9626f44e5
- Updated: 2026-11-07T17:00:00Z

## Task Summary
- **What to build**: Comprehensive technical specification & architecture requirements for backend, Telegram bot, AI pipeline, database, tariffs, and REST API.
- **Success criteria**: Complete handoff.md with all R2/R3 requirements, tables of features and edge cases, gap analysis with existing docs, schema, API spec, tariff matrix.
- **Interface contracts**: c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md
- **Code layout**: c:\Users\Honor\Desktop\miranda\docs\

## Key Decisions Made
- Fully analyzed `ORIGINAL_REQUEST.md`, `docs/ARCHITECTURE_SPRING_BOOT.md`, and `frontend/src/*`.
- Formulated complete specification in `handoff.md` with:
  1. Telegram Business API (`business_message`) vs Mini-groups architecture.
  2. Reels anti-fraud (shortcode extraction, SHA-256 deduplication, async HTTP probe).
  3. Hashtag parsing (#рилс, #продажи, #рассылки, #вопрос) with daily upsert accumulator.
  4. Daily 20:00 reminder engine with anti-spam throttler.
  5. AI pipeline with Whisper STT, flood/complaint extraction, and burnout detection.
  6. Java 21 / Spring Boot 4.x & Spring AI 2.x specification with complete fallback to Spring Boot 3.4.2 / Spring AI 1.0.0-M5.
  7. Tariff matrix (320 BYN / 34 hrs vs 475 BYN / 52 hrs) with granular WBS.
  8. Full PostgreSQL DDL (7 tables, relations, constraints, indexes).
  9. OpenAPI REST API contracts matching React SPA.
  10. Features Discovered & Edge Cases catalog.

## Artifact Index
- c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\DISPATCH.md — Dispatch instructions
- c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\progress.md — Liveness & task progress
- c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_survey_backend\handoff.md — Final comprehensive handoff report

## Loaded Skills
- None required directly for read-only spec mining.
