# Project: Mentor Hub Backend Server Implementation

## Architecture
The system consists of three interconnected layers designed with neutral branding for mentor Ksenia and curator Katya:
1. **Frontend SPA (Web Tracker)**:
   - React 19, Vite 8, Tailwind CSS v4, TypeScript SPA.
   - 4 main tabs: «🔥 На проверку» (Inbox Zero), «👩‍🎓 Ученицы & Календарь дисциплины», «📊 Аналитика & Кейсы для Инсты», «📋 Задания & Хэштеги».
   - Nginx reverse proxy routing `/api/` upstream to the Spring Boot backend.
2. **Backend Server (Spring Boot 3.4.2 GA / Java 21)**:
   - Spring Boot 3.4.2 GA, Java 21, Spring Data JPA, Flyway, Virtual Threads.
   - 5 REST controllers: `/api/inbox`, `/api/students`, `/api/analytics`, `/api/tasks`, `/api/system`.
   - Two-tier passwordless administrator authentication (`admin_tokens`, `X-Admin-Token` header / `?token=`, roles `ROLE_MENTOR` and `ROLE_CURATOR`).
   - PostgreSQL 16 schema with Flyway migrations (`db/migration/V1__init_schema.sql`) and seed data.
3. **Telegram Engine, Spring AI & Schedulers**:
   - `telegrambots-springboot-longpolling-starter:10.3.0` operating in individual mini-groups «Ксения + Катя + Ученица + Бот».
   - Long Polling by default with configuration switch to Webhook (`telegram.bot.mode`).
   - Content drafts intake before publication (#контент, #рилс, #карусель, #сторителл).
   - SHA-256 deduplication and anti-fraud alerts for Reels and media.
   - Spring AI 1.0.0-M5 audio transcription and 3-bullet reflection summarization (emotions, actions, questionToMentor) with deterministic offline regex fallback.
   - Spring `@Scheduled` background tasks: daily 20:00 reminders (excluding paused students and Sundays) and Saturday 18:00 inline button auto-poll.
4. **Infrastructure & Containerization**:
   - Multi-stage `Dockerfile` (builder + minimal JRE runtime).
   - `docker-compose.yml` running PostgreSQL 16, backend, and frontend React SPA.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Maven & Spring Boot 3.4.2 GA Setup | Java 21, Spring Boot 3.4.2, Virtual Threads, Lombok, MapStruct, actuator | M1 | ORIGINAL_REQUEST §R1 |
| 2 | PostgreSQL Schema & Flyway Migrations | Flyway V1__init_schema.sql creating all 10 tables, constraints, indexes & seed data | M1 | ORIGINAL_REQUEST §R1 |
| 3 | JPA Entities & Spring Data Repositories | Relational entities: Student, AdminToken, ZoomMeeting, TaskRule, DailyReport, ContentDraft, ReelSubmission, SalesEntry, MentorSetting, AuditMessageLog | M1 | docs/ARCHITECTURE_SPECIFICATION.md §2, 9 |
| 4 | Passwordless Magic-Link Security | `admin_tokens` table, `AdminTokenAuthenticationFilter`, roles MENTOR & CURATOR | M1 | ORIGINAL_REQUEST §R1.2 |
| 5 | REST Controller: /api/inbox | Review queue, content drafts, mentor questions, approve (with TG feedback) and reject endpoints | M2 | ORIGINAL_REQUEST §R1.1 |
| 6 | REST Controller: /api/students | Student listing, CRUD, discipline calendar breakdown, profile details | M2 | ORIGINAL_REQUEST §R1.1 |
| 7 | Discipline Calendar & Streak Freeze | Interactive tooltips (`missedDetails`), sick/pause toggle («В строю 🔥 / Болеет ☕») with streak freeze, Sunday rest day | M2 | ORIGINAL_REQUEST §R1.1, AC |
| 8 | REST Controller: /api/analytics | 10 cross-cutting metrics, Instagram story case generator with Point A->B multiplier & div-by-zero protection | M2 | ORIGINAL_REQUEST §R1.1 |
| 9 | REST Controller: /api/tasks | Hashtag rules management and cohort regulations endpoints | M2 | ORIGINAL_REQUEST §R1.1 |
| 10 | REST Controller: /api/system | Health diagnostics, Telegram sync trigger, and Zoom tracker endpoints (next call date, completed count) | M2 | ORIGINAL_REQUEST §R1.1 |
| 11 | Telegram Bot Mini-Groups Architecture | TelegramBots 10.3 in closed groups «Ксения + Катя + Ученица + Бот», caring assistant persona, no general chat clutter | M3 | ORIGINAL_REQUEST §R2 |
| 12 | Dual Connection: Long Polling & Webhook | Long Polling default for dev/docker with config switch to Webhook (`POST /api/telegram/webhook`) | M3 | ORIGINAL_REQUEST §R2.1 |
| 13 | Pre-Publication Drafts Intake | Receiving drafts of content BEFORE publication (#контент, #рилс, #карусель, #сторителл) | M3 | ORIGINAL_REQUEST §R2.2 |
| 14 | Reels Anti-Fraud & Deduplication | Canonical shortcode regex, SHA-256 hash deduplication, cross-student plagiarism alerts, async HTTP accessibility probe | M3 | docs/ARCHITECTURE_SPECIFICATION.md §3 |
| 15 | Spring AI STT & 3-Bullet Reflection | Voice/video note transcription, summarization into 3 bullets (emotions, actions, question), burnout detection | M3 | ORIGINAL_REQUEST §R2.3 |
| 16 | Spring AI Mock/Stub Fallback | Deterministic regex fallback when AI_API_KEY is missing or offline, ensuring zero report loss | M3 | ORIGINAL_REQUEST §R2.3 |
| 17 | Daily 20:00 Evening Reminders | `@Scheduled` cron at 20:00 (Mon-Sat, Minsk) pinging debtors, excluding paused students and Sundays | M3 | ORIGINAL_REQUEST §R2.4 |
| 18 | Saturday 18:00 Auto-Survey | `@Scheduled` cron at 18:00 (Sat, Minsk) polling new clients via Telegram inline buttons [0]..[5+] | M3 | ORIGINAL_REQUEST §R2.4 |
| 19 | Backend Multi-Stage Dockerfile | Maven 3.9 builder + Eclipse Temurin 21 JRE runtime, non-root user spring:spring | M4 | ORIGINAL_REQUEST §R3 |
| 20 | Frontend Dockerfile & Nginx Proxy | Node 24 alpine builder + Nginx alpine reverse proxy for `/api/` | M4 | ORIGINAL_REQUEST §R3 |
| 21 | Docker Compose Infrastructure | `docker-compose.yml` linking PostgreSQL 16 (healthcheck), backend, and frontend | M4 | ORIGINAL_REQUEST §R3, AC |
| 22 | Full Verification & Test Pass | 100% pass of `mvn clean test` (backend unit/integration tests) and 100% pass of existing 73 frontend tests (`test-runner.mjs`) | M4 | Acceptance Criteria |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Backend Foundation, Schema & Security | `pom.xml`, Flyway V1__init_schema.sql, JPA entities, repositories, admin_tokens magic-link security | none | DONE |
| M2 | REST API Controllers & Business Logic | /api/inbox, /api/students, /api/analytics, /api/tasks, /api/system, streak freeze, 10 metrics, controller tests | M1 | DONE |
| M3 | Telegram Bot, Spring AI & Schedulers | TelegramBots 10.3 mini-groups, drafts intake, SHA-256 anti-fraud, Spring AI 3-bullet reflection with offline stub, 20:00 & Sat 18:00 schedulers | M1, M2 | DONE |
| M4 | Containerization & Full Test Verification | Multi-stage Dockerfiles, docker-compose.yml, mvn clean test 100% pass, frontend 73 tests 100% pass, docker compose config | M1, M2, M3 | DONE |

---

## Interface Contracts

### Frontend ↔ Backend REST API Contract
- `GET /api/inbox` -> `DailyReportResponse[]` (pending queue with content drafts, mentor questions, AI reflection)
- `POST /api/inbox/{id}/approve` `{ replyText?: string }` -> `{ success: true, reportId, status: "APPROVED", tgMessageDelivered: boolean }`
- `POST /api/inbox/{id}/reject` `{ reason: string }` -> `{ success: true, reportId, status: "REJECTED" }`
- `GET /api/students` `?search=&active=` -> `StudentDisciplineResponse[]` (with 7-day weekDays array, tooltips, streak status)
- `GET /api/students/{id}` -> full student profile
- `POST /api/students` -> creates new student
- `PUT /api/students/{id}` -> updates student profile
- `DELETE /api/students/{id}` -> soft deletes student
- `POST /api/students/{id}/toggle-pause` `{ isPaused: boolean, pauseReason?: string }` -> `{ studentId, isOnPause, streakFrozen: true }`
- `GET /api/students/{id}/discipline-calendar` -> month/cohort discipline calendar breakdown
- `GET /api/analytics/summary` -> 10 cross-cutting metrics JSON
- `GET /api/analytics/case/{studentId}` -> story case details with Point A->B multiplier and Instagram copy text
- `GET /api/tasks/rules` -> `TaskRuleResponse[]`
- `PUT /api/tasks/rules/{id}` -> updates task rule
- `GET /api/system/status` -> system health and bot diagnostics
- `POST /api/system/sync-telegram` -> trigger Telegram queue flush
- `GET /api/system/zoom-tracker` -> Zoom tracker overview
- `POST /api/system/zoom-tracker` `{ studentId, zoomUrl, scheduledAt, meetingNotes }` -> creates/logs Zoom call

### Code Layout
```
miranda/
├── pom.xml                                  # Maven project descriptor (Spring Boot 3.4.2 GA / Java 21)
├── Dockerfile                               # Multi-stage Dockerfile for backend
├── docker-compose.yml                       # Production orchestration (postgres, backend, frontend)
├── src/
│   ├── main/
│   │   ├── java/com/mentorhub/
│   │   │   ├── MentorHubApplication.java    # Spring Boot application entry point
│   │   │   ├── config/                      # Web, Security, Telegram, Async config
│   │   │   ├── controller/                  # REST controllers (Inbox, Student, Analytics, Task, System)
│   │   │   ├── dto/                         # Request and Response DTOs
│   │   │   ├── entity/                      # JPA Entities
│   │   │   ├── repository/                  # Spring Data JPA Repositories
│   │   │   ├── security/                    # Admin token authentication filter & provider
│   │   │   ├── service/                     # Business logic services (Discipline, Analytics, Report, Zoom)
│   │   │   ├── telegram/                    # TelegramBots 10.3 consumer, router, handlers, anti-fraud
│   │   │   ├── ai/                          # Spring AI transcription, summarization, offline fallback
│   │   │   └── scheduler/                   # Evening reminders & Saturday auto-survey cron tasks
│   │   └── resources/
│   │       ├── application.yml              # Spring Boot configuration
│   │       └── db/migration/
│   │           └── V1__init_schema.sql      # Flyway initial schema & seed data
│   └── test/
│       ├── java/com/mentorhub/              # Backend unit and integration tests
│       └── resources/
│           └── application-test.yml         # Test configuration (H2, disabled TG long polling)
├── frontend/                                # Existing React 19 + Vite 8 SPA
│   ├── Dockerfile                           # Frontend multi-stage build (Node 24 + Nginx)
│   ├── nginx.conf                           # Nginx proxy configuration
│   └── vite.config.ts                       # Vite dev proxy configuration
├── docs/                                    # Architectural specifications
│   ├── ARCHITECTURE_SPECIFICATION.md
│   └── ARCHITECTURE_SPRING_BOOT.md
├── scripts/                                 # Test runner
│   └── test-runner.mjs                      # 73 frontend automated tests
└── .agents/teamwork/                        # Orchestrator & subagent metadata
```
