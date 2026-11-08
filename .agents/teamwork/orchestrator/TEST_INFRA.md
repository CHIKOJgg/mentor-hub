# Test Infra: Mentor Hub Full-Stack Verification

## Test Philosophy
- Requirement-driven, multi-tier verification covering backend REST APIs, PostgreSQL Flyway migrations, Telegram bot handlers, Spring AI fallback, scheduler triggers, and existing React SPA frontend contracts.
- Zero regression tolerance: all 73 existing frontend automated tests must pass 100%.
- Offline resilience: backend unit/integration tests must pass without external internet connectivity using H2 (PostgreSQL mode) and Mockito.

## Test Suites & Runners
1. **Frontend Automated Test Suite**:
   - Runner: `node scripts/test-runner.mjs`
   - Scope: 73 tests across 12 suites (Feature Coverage, Boundary Cases, Cross-Feature, Real-World Scenarios, Oxlint, Vite Build).
   - Acceptance: 100% pass, 0 failures, exit code 0.
2. **Backend Automated Test Suite**:
   - Runner: `mvn clean test`
   - Scope:
     - JPA repository and Flyway migration tests.
     - Controller slice tests (`@WebMvcTest` with `@MockitoBean`) for `/api/inbox`, `/api/students`, `/api/analytics`, `/api/tasks`, `/api/system`.
     - Discipline streak freeze and Sunday weekend calculation tests.
     - 10 analytics metrics calculation and Instagram story case generator tests.
     - SHA-256 deduplication and anti-fraud tests.
     - Spring AI 3-bullet reflection summarizer and deterministic fallback tests.
     - Evening reminder and Saturday survey scheduler tests.
     - Magic link authentication filter and security tests.
   - Acceptance: 100% pass, 0 failures, exit code 0.
3. **Containerization & Deployment Verification**:
   - Command: `docker compose config`
   - Scope: Validates `docker-compose.yml`, syntax, service dependencies, environment variables, and healthchecks.
