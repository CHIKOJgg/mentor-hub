## 2026-11-08T15:25:13Z
You are a teamwork_worker. Your Working Directory is:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m4_infra\

MANDATORY FIRST STEP:
Read the authoritative user request at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

READ YOUR INPUT SPECIFICATIONS:
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_infra_survey\handoff.md (Sections 2.4 B, C, D)
- Read c:\Users\Honor\Desktop\miranda\.agents\teamwork\orchestrator\PROJECT.md

YOUR MILESTONE SCOPE (M4: Containerization & Full Test Verification):
You have exclusive write ownership over:
- Dockerfile
- docker-compose.yml
- frontend/Dockerfile
- frontend/nginx.conf
- frontend/vite.config.ts

IMPLEMENTATION INSTRUCTIONS:
1. Create root Dockerfile:
   - Stage 1: maven:3.9.9-eclipse-temurin-21-alpine as builder, compiles and packages the Spring Boot JAR with -DskipTests.
   - Stage 2: eclipse-temurin:21-jre-alpine, runs as non-root user spring:spring, exposes 8080, ENTRYPOINT with JAVA_OPTS.
2. Create frontend/Dockerfile:
   - Stage 1: node:24-alpine, runs npm ci && npm run build.
   - Stage 2: nginx:alpine, copies dist to /usr/share/nginx/html, copies nginx.conf to /etc/nginx/conf.d/default.conf, exposes 80.
3. Create frontend/nginx.conf:
   - Configures SPA fallback routing (try_files $uri $uri/ /index.html;).
   - Configures reverse proxy location /api/ { proxy_pass http://backend:8080/api/; ... }.
4. Update frontend/vite.config.ts:
   - Add dev proxy for /api -> target: 'http://localhost:8080', changeOrigin: true.
   - Keep existing plugins and base configuration intact.
5. Create docker-compose.yml:
   - Services:
     * postgres: image postgres:16-alpine, env POSTGRES_DB=mentor_hub, POSTGRES_USER=postgres, POSTGRES_PASSWORD=postgres, ports "5432:5432", volume pgdata, healthcheck using pg_isready -U postgres -d mentor_hub.
     * backend: build ., env SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mentor_hub, SPRING_DATASOURCE_USERNAME=postgres, SPRING_DATASOURCE_PASSWORD=postgres, SPRING_FLYWAY_ENABLED=true, depends_on postgres (condition: service_healthy), ports "8080:8080".
     * frontend: build ./frontend, ports "5173:80", depends_on backend.
   - Volumes: pgdata.
   - Networks: mentor-network (bridge).
6. Verification commands to execute:
   - Run `docker compose config` and verify valid configuration with exit code 0.
   - Run `mvn clean test` and verify that ALL 89 backend tests pass with 100% success rate.
   - Run `mvn clean package -DskipTests` and verify clean build of target/mentor-tracker-backend-1.0.0.jar.
   - Run `node scripts/test-runner.mjs` and verify that ALL 73 frontend automated tests pass 100%.

Produce a detailed handoff report in:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m4_infra\handoff.md
Include build commands and execution logs in your handoff report.
Update progress.md in your directory as you work.
When done, message your caller.
