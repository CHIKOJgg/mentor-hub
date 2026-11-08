## 2026-11-08T14:36:18Z
You are a teamwork_worker. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m1_foundation\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

READ YOUR INPUT SPECIFICATIONS:
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\spec_miner_backend_survey\handoff.md (Part II Database Schema & Part III Security)
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_infra_survey\handoff.md (Part 4 A pom.xml specification)
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\orchestrator\PROJECT.md

YOUR MILESTONE SCOPE (M1: Backend Foundation, Flyway Migrations, Data Model & Security):
You have exclusive write ownership over:
- pom.xml
- src/main/resources/application.yml
- src/main/resources/db/migration/V1__init_schema.sql
- src/main/java/com/mentorhub/MentorHubApplication.java
- src/main/java/com/mentorhub/entity/**
- src/main/java/com/mentorhub/repository/**
- src/main/java/com/mentorhub/security/**
- src/test/resources/application-test.yml

IMPLEMENTATION INSTRUCTIONS:
1. Create root pom.xml with Spring Boot 3.4.2 GA, Java 21, Spring AI 1.0.0-M5 (with milestone repository configured), TelegramBots 10.3.0, PostgreSQL 42.7.4, Flyway (core + database-postgresql), Lombok, MapStruct 1.6.3, and test dependencies (H2, Mockito, spring-boot-starter-test).
2. Create src/main/resources/application.yml with datasource, flyway, and telegram properties.
3. Create src/main/resources/db/migration/V1__init_schema.sql with all 10 PostgreSQL tables:
   - students
   - admin_tokens
   - zoom_meetings
   - task_rules
   - daily_reports
   - content_drafts
   - reel_submissions
   - sales_entries
   - mentor_settings
   - audit_message_logs
   Include complete seed data (admin tokens for Ksenia 'MENTOR' and Katya 'CURATOR', initial task rules, students, mentor settings). Note: Avoid hard dependency on postgres uuid-ossp extension in H2 compatibility by using BIGSERIAL PRIMARY KEY and standard types.
4. Create src/main/java/com/mentorhub/MentorHubApplication.java with @SpringBootApplication and @EnableScheduling.
5. Create all 10 JPA entities in com.mentorhub.entity with correct relations, JPA annotations, and column mappings.
6. Create Spring Data JPA repositories in com.mentorhub.repository for all entities.
7. Create security components in com.mentorhub.security:
   - AdminTokenAuthenticationFilter: inspects X-Admin-Token header and ?token= query parameter, validates token against admin_tokens repository (is_active = true), sets PreAuthenticatedAuthenticationToken with ROLE_MENTOR or ROLE_CURATOR.
   - SecurityConfig: permits /api/system/status and /api/telegram/** without auth, requires authentication for protected endpoints, disables CSRF for REST API, sets stateless session.
8. Create src/test/resources/application-test.yml with H2 in PostgreSQL mode (jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH) and telegram.bot.enabled=false.
9. Run `mvn clean compile` to verify that the build compiles cleanly with exit code 0.
10. Verify that `node scripts/test-runner.mjs` continues to pass 73/73 tests without regression.

Produce a detailed handoff report in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m1_foundation\handoff.md
Include build commands and execution logs in your handoff report.
Update progress.md in your directory as you work.
When done, message your caller.
