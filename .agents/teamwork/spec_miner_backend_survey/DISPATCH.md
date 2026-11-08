## 2026-11-08T14:29:03Z
You are a teamwork_spec_miner. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_backend_survey\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

YOUR TASK:
Examine docs/ARCHITECTURE_SPECIFICATION.md and docs/ARCHITECTURE_SPRING_BOOT.md to extract the precise specification for:
1. REST API Controllers & Endpoints:
   - `/api/inbox`: Review queue for mentor (content drafts BEFORE publication: #контент, #рилс, #карусель, #сторителл; recognized mentor questions; AI-extracted reflection summary), approval & rejection logic, payload contracts.
   - `/api/students`: Student list and profile DTOs, discipline calendar with interactive tooltips explaining missed days, sick/pause toggle ("В строю 🔥 / Болеет ☕") with streak freeze (Sundays are official rest days).
   - `/api/analytics`: 10 cross-cutting metrics (Reels, Карусели, Сторителлы, Рассылки, Выручка, Средний чек, Рост дохода Точка А -> Точка Б, Рост чека, Новые клиенты, AI-рефлексия), Instagram case generation text.
   - `/api/tasks`: Assignment management and hashtag rules.
   - `/api/system`: System health and Zoom tracker (next call date, completed meetings count).
2. Data Model & Flyway Migrations:
   - Complete database schema: tables, columns, constraints, foreign keys, indexes, enums.
   - `admin_tokens` table for passwordless magic-link authentication (Ksenia: Main Mentor, Katya: Curator).
   - Audit logs, report entities, submission history, student entities.
3. Security & Access Control:
   - Magic link authentication mechanics, token lifetime, roles and permissions.

Produce a detailed specification report with all exact schemas, endpoints, request/response JSON payloads, and validation rules in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_backend_survey\handoff.md
Update progress.md in your directory as you work.
When complete, send a message to your caller notifying that handoff.md is ready.
