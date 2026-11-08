# BRIEFING — 2026-11-08T14:47:00Z

## Mission
Implement Milestone M1: Backend Foundation, Flyway Migrations, Data Model & Security for Mentor Hub on Spring Boot 3.4.2 GA / Java 21 / PostgreSQL / Flyway / TelegramBots 10.3 / Spring AI 1.0.0-M5.

## 🔒 My Identity
- Archetype: teamwork_worker
- Roles: implementer, qa
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m1_foundation\
- Original parent: 833c6972-8874-4afe-bc51-0f986029e2f2
- Milestone: M1 (Backend Foundation, Flyway Migrations, Data Model & Security)

## 🔒 Key Constraints
- Exclusive write ownership:
  - pom.xml
  - src/main/resources/application.yml
  - src/main/resources/db/migration/V1__init_schema.sql
  - src/main/java/com/mentorhub/MentorHubApplication.java
  - src/main/java/com/mentorhub/entity/**
  - src/main/java/com/mentorhub/repository/**
  - src/main/java/com/mentorhub/security/**
  - src/test/resources/application-test.yml
- Brand Neutrality: Never leak forbidden brand aliases ("миранда" / "miranda") in frontend or docs.
- Integrity: Genuine implementation, no hardcoding, no facades.
- All 73 frontend automated tests in `scripts/test-runner.mjs` must continue passing 100%.
- `mvn clean compile` must succeed cleanly with exit code 0.

## Current Parent
- Conversation ID: 833c6972-8874-4afe-bc51-0f986029e2f2
- Updated: 2026-11-08T14:47:00Z

## Task Summary
- **What to build**: Root pom.xml, application.yml, V1__init_schema.sql (10 tables + seed data), MentorHubApplication.java, 10 JPA entities, 10 Spring Data repositories, AdminTokenAuthenticationFilter & SecurityConfig, application-test.yml (H2 PostgreSQL mode).
- **Success criteria**: Clean compilation (`mvn clean compile`), 73/73 tests pass in test-runner.mjs.
- **Interface contracts**: PROJECT.md, docs/ARCHITECTURE_SPECIFICATION.md
- **Code layout**: PROJECT.md § Code Layout

## Key Decisions Made
- Standardized all 10 tables on BIGSERIAL PRIMARY KEY without hard dependency on postgres uuid-ossp extension to ensure 100% H2 in-memory test compatibility.
- Configured Lombok 1.18.38 for compatibility with JDK 25 and Java 21 bytecode compiler targets.
- Implemented `AdminTokenAuthenticationFilter` inspecting `X-Admin-Token` header, `Authorization: Bearer` header, and `?token=` parameter, granting `ROLE_MENTOR` and `ROLE_CURATOR` authorities.
- Configured `SecurityConfig` with stateless session management, CSRF disabled, public access to `/api/system/status` and `/api/telegram/**`, and authenticated access to `/api/**`.

## Artifact Index
- `.agents/teamwork/worker_m1_foundation/DISPATCH.md` — Assigned task instructions
- `.agents/teamwork/worker_m1_foundation/progress.md` — Liveness heartbeat and progress log
- `.agents/teamwork/worker_m1_foundation/handoff.md` — Final 5-component handoff report

## Change Tracker
- **Files modified**:
  - `pom.xml` — Root Maven project configuration (Spring Boot 3.4.2 GA, Java 21, Spring AI 1.0.0-M5, TelegramBots 10.3.0, PostgreSQL 42.7.4, Flyway, Lombok 1.18.38, MapStruct 1.6.3)
  - `src/main/resources/application.yml` — Datasource, Flyway, Virtual Threads, Telegram, and Spring AI configuration
  - `src/main/resources/db/migration/V1__init_schema.sql` — Flyway initial schema (10 tables + seed data)
  - `src/main/java/com/mentorhub/MentorHubApplication.java` — Main application class with `@SpringBootApplication` and `@EnableScheduling`
  - `src/main/java/com/mentorhub/entity/*.java` — 10 JPA entities (Student, AdminToken, ZoomMeeting, TaskRule, DailyReport, ContentDraft, ReelSubmission, SalesEntry, MentorSetting, AuditMessageLog)
  - `src/main/java/com/mentorhub/repository/*.java` — 10 Spring Data JPA repositories
  - `src/main/java/com/mentorhub/security/AdminTokenAuthenticationFilter.java` — Magic-link token security filter
  - `src/main/java/com/mentorhub/security/SecurityConfig.java` — Spring Security filter chain configuration
  - `src/test/resources/application-test.yml` — In-memory H2 PostgreSQL mode test profile
  - `src/test/java/com/mentorhub/MentorHubApplicationTests.java` — Seed data and Flyway context tests
  - `src/test/java/com/mentorhub/security/SecurityIntegrationTest.java` — Token authentication and security integration tests
- **Build status**: `mvn clean compile` passed (code 0), `mvn clean test` passed (11/11 tests pass), `node scripts/test-runner.mjs` passed (73/73 tests pass).
- **Pending issues**: None.

## Quality Status
- **Build/test result**: Pass (Backend 11/11 tests pass, Frontend 73/73 tests pass).
- **Lint status**: Clean (Oxlint 0 warnings, 0 errors).
- **Tests added/modified**: 11 new tests in `MentorHubApplicationTests` and `SecurityIntegrationTest`.

## Loaded Skills
- None.
