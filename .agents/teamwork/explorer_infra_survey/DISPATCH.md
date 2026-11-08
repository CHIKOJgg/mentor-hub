# Explorer: Project Setup, Maven, Docker, Frontend Integration & Test Preservation
Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_infra_survey\

## 2026-11-08T14:29:15Z
Sender: 833c6972-8874-4afe-bc51-0f986029e2f2
Priority: MESSAGE_PRIORITY_HIGH

You are a teamwork_explorer. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_infra_survey\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

YOUR TASK:
Investigate the project workspace and requirements:
1. Frontend integration:
   - Inspect frontend/ directory (package.json, src/, API calls, models, endpoints expected by the UI).
   - How does frontend connect to the backend (base URL, auth token header, proxy configuration in vite.config.ts, etc.)?
2. Existing test suite preservation:
   - Inspect scripts/test-runner.mjs and the 73 frontend automated tests.
   - Verify how they run and what conditions must be maintained so that all 73 existing tests continue to pass 100%.
3. Maven and Spring Boot project setup:
   - Recommended pom.xml dependencies for Java 21, Spring Boot 3.4.2 GA, Spring AI (1.0.0 M-series / milestone / preview or official stable with mock fallback), TelegramBots 10.3, PostgreSQL driver, Flyway, Lombok, MapStruct / validation, and testing (JUnit 5, Mockito, Testcontainers / H2 for offline tests).
   - Verify Java 21 compiler plugin and packaging configurations.
4. Containerization & Infrastructure:
   - Dockerfile requirements (multi-stage build for Spring Boot application).
   - docker-compose.yml requirements for running backend (Spring Boot), PostgreSQL database, and frontend React SPA with proper networking and environment variables.

Produce a detailed survey report with exact file recommendations, dependency versions, test runner analysis, and container configuration in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_infra_survey\handoff.md
Update progress.md in your directory as you work.
When complete, send a message to your caller notifying that handoff.md is ready.
