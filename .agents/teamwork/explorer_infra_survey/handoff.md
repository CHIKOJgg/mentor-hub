# Handoff Report: Infrastructure, Frontend Integration & Test Preservation Survey

**Agent**: explorer_infra_survey  
**Timestamp**: 2026-11-08T14:35:00Z  
**Target Milestone**: Infrastructure, Maven Project Setup, Containerization, Frontend Integration & Test Suite Preservation  
**Working Directory**: `c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_infra_survey\`  

---

## 1. Observation

### 1.1. Workspace Structure & Existing Codebase
Direct filesystem inspection of `c:\Users\Honor\Desktop\miranda` reveals:
- **Root artifacts**:
  - `README.md` (3,751 bytes) — documents project overview, нейтральный брендинг «Mentor Hub», запуск через `start-demo.bat` или `npm run dev`.
  - `TEST_READY.md` (8,263 bytes) — reports output of the last test execution.
  - `demo.html` (56,488 bytes) & `start-demo.bat` (229 bytes) — standalone interactive demo runner.
  - `docs/`:
    - `ARCHITECTURE_SPECIFICATION.md` (88,323 bytes, 1,253 lines) — authoritative system specification for Spring Boot 3.4.2 GA / Java 21, TelegramBots 10.3.0, Spring AI 1.0.0-M5, PostgreSQL 16 DDL, 14 OpenAPI endpoints, and 360 BYN cohort contract.
    - `ARCHITECTURE_SPRING_BOOT.md` (22,867 bytes) — Java architectural design document.
    - `PITCH_PROPOSAL.md` (9,179 bytes) — commercial pitch deck script for Ksenia & Katya.
  - `frontend/`:
    - `package.json` — Vite 8.3.6, React 19.3.1, Tailwind CSS 4.3.5, Oxlint 1.90.0, TypeScript 7.0.2.
    - `vite.config.ts` — React + Tailwind plugins, `base: './'`. Currently lacks `/api` proxy definition.
    - `src/types.ts` (96 lines) — comprehensive domain TypeScript interfaces: `DailyReport`, `ContentDraft`, `ReelSubmission`, `ReflectionSummary`, `StudentDiscipline`, `DayInfo`, `DayStatus`, `TaskRule`, `TabType`.
    - `src/mockData.ts` (345 lines) — 3 pending reports (`initialReports`), 5 students with 7-day calendar grids (`studentDisciplines`), and 7 task hashtag rules (`taskRules`).
    - `src/App.tsx` (748 lines) — complete single-page application with 4 tabs («На проверку», «Ученицы», «Аналитика», «Хэштеги»), 2-admin profile switcher (Ксения / Катя), interactive pause/illness toggle («В строю 🔥 / Болеет ☕»), and story case generator.
  - `scripts/`:
    - `test-runner.mjs` (1,044 lines) — 4-tier automated test suite containing 73 assertions across 12 suites.

### 1.2. Automated Test Suite Execution & Invariants
Direct execution of `node scripts/test-runner.mjs` yielded:
```text
======================================================================
 TEST RUN SUMMARY
======================================================================
 Total Suites:    12
 Total Tests:     73
 Passed Tests:    73
 Failed Tests:    0
 Success Rate:    100%
 Total Duration:  2218ms
======================================================================
✔ TEST_READY.md written to C:\Users\Honor\Desktop\miranda\TEST_READY.md
Exit code: 0
```

Detailed test suite architecture:
- **Suite 0: Static Build & Linter Verification**:
  - `TC-0.1`: `npm --prefix frontend run lint` executes `oxlint` with 0 warnings and 0 errors.
  - `TC-0.2`: `npm --prefix frontend run build` executes `tsc -b && vite build`, creating `dist/index.html`.
- **Tier 1: Feature Coverage (43 tests in 8 areas)**:
  - `Area 1` (Global Header & Brand Neutrality): `TC-1.1.1` scans 7 files (`App.tsx`, `mockData.ts`, `types.ts`, `index.html`, `package.json`, `ARCHITECTURE_SPECIFICATION.md`, `PITCH_PROPOSAL.md`) using `/миранда|miranda/gi`. Total violations must equal exactly 0. `TC-1.1.2` to `TC-1.1.5` check header text, dynamic counters, and bot indicators.
  - `Area 2` (Tab Navigation & Routing): checks tabs array (`inbox`, `students`, `analytics`, `tasks`), default tab `'inbox'`, and absence of pricing tab in client UI.
  - `Area 3` (Inbox Zero): checks 3 initial reports, clickable Reels links, duplicate alert (`04 ноября`), currency formatting, AI reflection summary (3 bullets: emotions, actions, question), and `handleApprove` state eviction.
  - `Area 4` (Discipline Calendar): checks 5 students, 7-day columns (Пн–Вс), Saturday highlight, status dot classes (`COMPLETED_ON_TIME`, `COMPLETED_LATE`, `MISSED`, `PAUSED`, `WEEKEND`, `PENDING`), tooltips (`missedDetails`), and `toggleStudentPause`.
  - `Area 5` (Analytics & Story Generator): checks 895 000 ₽ total, 66 reels, 40 600 ₽ average ticket, 702 mailings, multiplier calculations (6.0x, 1.8x, 6.2x, 4.0x, 5.5x), copy case handler, and 10 tracked metrics.
  - `Area 6` (Tasks & Hashtags): checks rules `#контент`, `#рилс`, `#карусель`, `#сторителл`, `#рассылки`, `#продажи`, `#вопрос`, `#клиенты`, and required vs optional flags.
  - `Area 7` (Commercial Tariff Matrix in docs): checks 320 BYN MVP, 475 BYN PRO, and 360 BYN contract in architecture specification and pitch doc.
  - `Area 8` (Architecture Spec Verification): checks Spring Boot 3.4.x / Spring AI 1.x fallback, TelegramBots 10.3.0, PostgreSQL DDL (`students`, `daily_reports`, `reel_submissions`, `sales_entries`, `admin_tokens`, `zoom_meetings`), and REST endpoints (`/api/inbox`, `/api/students/{id}/discipline-calendar`, `/api/analytics/case/{studentId}`).
- **Tier 2: Boundary & Corner Cases (11 tests)**:
  - `TC-2.1` to `TC-2.11`: zero sales formatting (`0 ₽`), missing optional fields, Reels URL query sanitization (`DC194kaP91m`), discipline threshold boundaries (100, 90, 89.9, 75, 74.9, 0), division by zero protection (`1.0`), empty search handling, CSS truncation, and clipboard `.catch` fallback.
- **Tier 3: Cross-Feature State Combinations (5 tests)**:
  - Search query consistency, multi-step sequential card approvals (2/5 -> 3/5 -> 4/5 -> 5/5), approval with comment vs blank comment toast, story generator synchronization, and hashtag rules alignment with `DailyReport`.
- **Tier 4: Real-World Scenarios (4 end-to-end user workflows)**:
  - Ksenia 20:30 evening routine (Inbox Zero cleanup across 3 cards), hot lead story generation, weekly discipline audit, and commercial pitch review.

### 1.3. Installed Runtime & Toolchain Environment
Direct tool commands executed on the host system confirmed:
- **Java**: `Java 25.0.4.1 LTS` (`C:\Program Files\Java\jdk-25.0.4.1`), Oracle Corporation, 64-Bit Server VM (compatible with Java 21 bytecode targets).
- **Maven**: `Apache Maven 3.9.16` (`C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3`), platform encoding `UTF-8`.
- **Node.js**: `v24.20.0` (native ESM and TypeScript type-stripping).
- **Docker**: `Docker version 29.8.2`, build 7fc2dff.
- **Docker Compose**: `Docker Compose version v5.6.0`.

---

## 2. Logic Chain

### 2.1. Frontend Integration Architecture
**Premise**: The React SPA currently runs with static state initialized from `mockData.ts`. It needs to interface with the Spring Boot backend REST API while retaining 100% demo resilience and avoiding test breakage.

1. **API Endpoints Expected by the UI**:
   - `/api/inbox` (`GET`): Returns an array of `DailyReport` objects matching `src/types.ts`.
   - `/api/inbox/{reportId}/approve` (`POST`): Receives `{ replyText: string }`, updates status to `APPROVED`, sends Telegram reply from mentor, and returns `{ success: true, reportId, status: 'APPROVED' }`.
   - `/api/inbox/{reportId}/reject` (`POST`): Receives `{ reason: string }`, updates status to `REJECTED`, and returns confirmation.
   - `/api/students` (`GET`): Returns array of `StudentDiscipline` objects including the 7-day `weekDays` array with `missedDetails` tooltips, `isOnPause`, `nextZoomDate`, and `completedZoomsCount`.
   - `/api/students/{id}` (`GET`, `PUT`, `DELETE`): CRUD operations for student profiles.
   - `/api/students/{id}/discipline-calendar` (`GET`): Historical discipline calendar grid.
   - `/api/students/{id}/toggle-pause` (`POST`): Toggles pause/illness mode, freezes streak, and sets `pauseReason`.
   - `/api/analytics/summary` (`GET`): Returns aggregated metrics for the 6 top KPI cards (total revenue 895 000 ₽, verified reels, avg ticket, carousels, storytells, new clients, mailings).
   - `/api/analytics/case/{studentId}` (`GET`): Returns student financial growth, multiplier string (e.g. `×6.0`), and pre-formatted marketing text for Instagram Stories.
   - `/api/tasks/rules` (`GET`, `PUT`): Hashtag rules and regulations configuration.
   - `/api/system/status` (`GET`): System health, bot status, and Telegram connection diagnostics.
   - `/api/system/sync-telegram` (`POST`): Trigger manual Telegram update polling.

2. **Backend Connection & Network Routing**:
   - **Local Development**:
     - Frontend dev server runs on `http://localhost:5173`.
     - Backend server runs on `http://localhost:8080`.
     - In `frontend/vite.config.ts`, configure `server.proxy`:
       ```typescript
       server: {
         port: 5173,
         proxy: {
           '/api': {
             target: 'http://localhost:8080',
             changeOrigin: true,
           },
         },
       }
       ```
   - **Production (Docker Compose)**:
     - Frontend SPA is served by Nginx on port `80` (or `5173:80`).
     - Nginx proxies `/api/` upstream to `http://backend:8080/api/`.
     - Client calls relative URL `/api/...`, completely bypassing CORS issues.

3. **Authentication Strategy (Magic Links & Admin Tokens)**:
   - Database table `admin_tokens` contains pre-seeded tokens for Ksenia (`role: 'MENTOR'`) and Katya (`role: 'CURATOR'`).
   - Frontend passes token via HTTP header `X-Admin-Token: <token>` (or `Authorization: Bearer <token>`).
   - UI active admin switcher ('Ксения' / 'Катя') in `App.tsx` lines 186–213 maps to corresponding admin tokens.
   - In development/demo mode without token, backend allows transparent read/write with mock fallback so UI never locks up.

### 2.2. Test Suite Preservation Strategy (Ensuring 73/73 Tests Continue to Pass)
**Premise**: `scripts/test-runner.mjs` directly checks source code text and runs builds. Any violation will fail the build.

1. **Brand Neutrality Rule**:
   - Scanned files: `App.tsx`, `mockData.ts`, `types.ts`, `index.html`, `package.json`, `ARCHITECTURE_SPECIFICATION.md`, `PITCH_PROPOSAL.md`.
   - **Invariant**: Never introduce the word "миранда" or "miranda" into any of these 7 files.
   
2. **Static Code Invariants in `App.tsx`**:
   - The test runner asserts exact string matches:
     - `useState<'inbox' | 'students' | 'analytics' | 'tasks'>('inbox')`
     - `const handleApprove = (reportId: string, name: string) => {`
     - `setReports(prev => prev.filter(r => r.id !== reportId))`
     - `toggleStudentPause`
     - `initialReports`, `studentDisciplines`, `taskRules`
     - `reports.length`
     - `salesAmount.toLocaleString('ru-RU')`
     - `d.missedDetails`
     - `nextZoomDate`
     - `contentDrafts`
     - `reflectionSummary`
   - **Design Rule**: If adding an API fetch effect or hook to `App.tsx`, keep the default `useState(initialReports)` and `useState(studentDisciplines)` intact as the initial fallback. Do not replace or delete these handler functions.

3. **Linter & Compiler Cleanliness**:
   - `npm --prefix frontend run lint` executes `oxlint`. Every file in `frontend/` must satisfy Oxlint (no unused hooks, no broken types).
   - `npm --prefix frontend run build` executes `tsc -b && vite build`. All TypeScript types in `types.ts` and imports must be 100% valid.

### 2.3. Maven and Spring Boot 3.4.2 GA Project Architecture
**Premise**: The project requires Java 21 LTS, Spring Boot 3.4.2 GA, Virtual Threads, Spring AI, TelegramBots 10.3.0, PostgreSQL, Flyway, and offline-capable testing.

1. **Root Maven Configuration (`pom.xml`)**:
   - Group ID: `com.mentorhub`
   - Artifact ID: `mentor-tracker-backend`
   - Version: `1.0.0`
   - Parent: `org.springframework.boot:spring-boot-starter-parent:3.4.2`
   - Java Version: `21`
   - Properties:
     - `<java.version>21</java.version>`
     - `<spring-ai.version>1.0.0-M5</spring-ai.version>`
     - `<telegrambots.version>10.3.0</telegrambots.version>`
     - `<postgresql.version>42.7.4</postgresql.version>`
     - `<mapstruct.version>1.6.3</mapstruct.version>`
     - `<lombok.version>1.18.36</lombok.version>`
     - `<lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>`

2. **Core Dependencies**:
   - **Web & Actuator**:
     - `spring-boot-starter-web`
     - `spring-boot-starter-validation`
     - `spring-boot-starter-actuator`
   - **Persistence & Migrations**:
     - `spring-boot-starter-data-jpa`
     - `postgresql:42.7.4` (runtime)
     - `flyway-core`
     - `flyway-database-postgresql` (essential for PostgreSQL support in Flyway 10+)
   - **Telegram Engine**:
     - `org.telegram:telegrambots-springboot-longpolling-starter:10.3.0`
     - `org.telegram:telegrambots-client:10.3.0`
   - **Spring AI**:
     - `org.springframework.ai:spring-ai-bom:1.0.0-M5` (in `<dependencyManagement>`)
     - `org.springframework.ai:spring-ai-openai-spring-boot-starter`
     - Repository: `https://repo.spring.io/milestone`
   - **Security / Hashing**:
     - `commons-codec:commons-codec:1.17.1` (for `DigestUtils.sha256Hex`)
   - **Lombok & MapStruct**:
     - `org.projectlombok:lombok` (provided)
     - `org.mapstruct:mapstruct:1.6.3`
   - **Testing**:
     - `spring-boot-starter-test` (JUnit 5 Jupiter, Mockito, AssertJ, Spring Test)
     - `com.h2database:h2` (scope: test, PostgreSQL emulation mode for offline tests without live DB)
     - `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql` (scope: test, for Dockerized integration tests)

3. **Compiler Plugin Configuration**:
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-compiler-plugin</artifactId>
       <version>3.13.0</version>
       <configuration>
           <source>21</source>
           <target>21</target>
           <parameters>true</parameters>
           <annotationProcessorPaths>
               <path>
                   <groupId>org.projectlombok</groupId>
                   <artifactId>lombok</artifactId>
                   <version>${lombok.version}</version>
               </path>
               <path>
                   <groupId>org.projectlombok</groupId>
                   <artifactId>lombok-mapstruct-binding</artifactId>
                   <version>${lombok-mapstruct-binding.version}</version>
               </path>
               <path>
                   <groupId>org.mapstruct</groupId>
                   <artifactId>mapstruct-processor</artifactId>
                   <version>${mapstruct.version}</version>
               </path>
           </annotationProcessorPaths>
       </configuration>
   </plugin>
   ```

4. **Offline Test Strategy**:
   - Unit tests use Mockito and `@WebMvcTest`.
   - In Spring Boot 3.4.2, use `@MockitoBean` (replacing deprecated `@MockBean`).
   - Repository tests and JPA integration tests run with an active test profile using in-memory H2:
     `spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`
   - Telegram long-polling is disabled in tests via `@TestPropertySource(properties = "telegram.bot.enabled=false")` or mock consumer bean so tests never hang waiting for Telegram connection.
   - Spring AI service has an automatic mock/stub fallback when `spring.ai.openai.api-key` is missing or equal to `mock-key`, ensuring offline execution passes 100%.

### 2.4. Containerization & Infrastructure Blueprint
1. **Spring Boot Multi-Stage Dockerfile (`Dockerfile`)**:
   - Stage 1 (`builder`): `maven:3.9.9-eclipse-temurin-21-alpine` copies `pom.xml`, downloads dependencies, compiles source, and packages executable JAR with `-DskipTests`.
   - Stage 2 (`runtime`): `eclipse-temurin:21-jre-alpine` runs as non-root user `spring:spring`, exposes port 8080, and uses optimized JVM parameters for Virtual Threads and memory:
     `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0`
2. **Frontend Multi-Stage Dockerfile (`frontend/Dockerfile`)**:
   - Stage 1 (`build`): `node:24-alpine` runs `npm ci && npm run build`.
   - Stage 2 (`serve`): `nginx:alpine` copies `/dist` to `/usr/share/nginx/html` and mounts `nginx.conf`.
3. **Frontend Nginx Proxy Configuration (`frontend/nginx.conf`)**:
   - Configures SPA fallback routing: `try_files $uri $uri/ /index.html;`
   - Configures reverse proxy:
     ```nginx
     location /api/ {
         proxy_pass http://backend:8080/api/;
         proxy_http_version 1.1;
         proxy_set_header Host $host;
         proxy_set_header X-Real-IP $remote_addr;
         proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
         proxy_set_header X-Forwarded-Proto $scheme;
     }
     ```
4. **Production Orchestration (`docker-compose.yml`)**:
   - `postgres`: image `postgres:16-alpine`, port 5432, healthcheck via `pg_isready -U postgres -d mentor_hub`, persistent volume `pgdata`.
   - `backend`: builds root `Dockerfile`, depends on `postgres` healthy condition, injects datasource URL `jdbc:postgresql://postgres:5432/mentor_hub`, port 8080.
   - `frontend`: builds `./frontend/Dockerfile`, depends on `backend`, maps port `80:80` (or `5173:80`).
   - Shared bridge network `mentor-net`.

---

## 3. Caveats

1. **H2 vs PostgreSQL Dialect Incompatibilities**:
   - In offline unit/slice tests, H2 in `MODE=PostgreSQL` supports `JSONB` as `JSON` and `BIGSERIAL` as `BIGINT AUTO_INCREMENT`. However, PostgreSQL extensions like `CREATE EXTENSION IF NOT EXISTS "uuid-ossp";` can fail on H2.
   - **Recommendation**: In `V1__initial_schema.sql`, avoid hard dependency on `uuid-ossp` by using standard `BIGSERIAL PRIMARY KEY` (as is already used for all primary keys in `docs/ARCHITECTURE_SPECIFICATION.md`) or guard extension creation with a conditional Flyway configuration.
2. **Spring AI Milestone Repository Requirement**:
   - Spring AI `1.0.0-M5` resides in `https://repo.spring.io/milestone`, not Maven Central. The `pom.xml` must include the Spring Milestones repository block in both `<repositories>` and `<pluginRepositories>`.
   - A mock AI fallback service (`FallbackReportExtractor`) must be provided so that even if the OpenAI API is unreachable or tokens expire, reports are parsed via deterministic regex without failure.
3. **TelegramBots Long Polling Thread Management**:
   - `TelegramBots 10.3.0` automatically spawns background polling threads. When running tests with `mvn clean test`, the long polling starter might attempt to connect to Telegram if credentials are set.
   - **Recommendation**: Guard the Telegram consumer bean with `@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true", matchIfMissing = true)` and set `telegram.bot.enabled=false` in `src/test/resources/application-test.yml`.
4. **Brand Neutrality Vigilance**:
   - Any new file added to `frontend/` or `docs/` must strictly avoid the forbidden project name alias tested by `TC-1.1.1`. Always use "Mentor Hub" or "Mentor Tracker".

---

## 4. Conclusion

The existing workspace is in an optimal state for backend implementation:
1. **Frontend**: Clean React 19 SPA with established data types in `src/types.ts` and sample structures in `src/mockData.ts`. All 73 tests currently pass 100%.
2. **Toolchain**: The host environment provides Java 25 (Java 21 bytecode target ready), Apache Maven 3.9.16, Node v24.20.0, and Docker 29.8.2 / Compose v5.6.0.
3. **Backend Stack**: Spring Boot 3.4.2 GA with Java 21 Virtual Threads, Spring AI 1.0.0-M5, TelegramBots 10.3.0, PostgreSQL 42.7.4, Flyway 10.20+, and MapStruct 1.6.3 provides an enterprise-grade, modern, and production-ready architecture.
4. **Preservation**: The implementation plan preserves all 73 frontend automated tests without modifications to existing test invariants.

### Concrete Recommendations for Implementation:

#### A. Recommended Root `pom.xml` Specification
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.2</version>
        <relativePath/>
    </parent>

    <groupId>com.mentorhub</groupId>
    <artifactId>mentor-tracker-backend</artifactId>
    <version>1.0.0</version>
    <name>Mentor Hub Backend</name>
    <description>Серверная часть платформы наставника Mentor Hub / Mentor Tracker</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-ai.version>1.0.0-M5</spring-ai.version>
        <telegrambots.version>10.3.0</telegrambots.version>
        <postgresql.version>42.7.4</postgresql.version>
        <mapstruct.version>1.6.3</mapstruct.version>
        <lombok.version>1.18.36</lombok.version>
        <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot Web & Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- PostgreSQL & Flyway -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgresql.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <!-- TelegramBots 10.3.0 -->
        <dependency>
            <groupId>org.telegram</groupId>
            <artifactId>telegrambots-springboot-longpolling-starter</artifactId>
            <version>${telegrambots.version}</version>
        </dependency>
        <dependency>
            <groupId>org.telegram</groupId>
            <artifactId>telegrambots-client</artifactId>
            <version>${telegrambots.version}</version>
        </dependency>

        <!-- Spring AI (OpenAI Whisper & ChatClient) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
        </dependency>

        <!-- Utilities -->
        <dependency>
            <groupId>commons-codec</groupId>
            <artifactId>commons-codec</artifactId>
            <version>1.17.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>${mapstruct.version}</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <parameters>true</parameters>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>${lombok-mapstruct-binding.version}</version>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </repository>
    </repositories>
    <pluginRepositories>
        <pluginRepository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots><enabled>false</enabled></snapshots>
        </pluginRepository>
    </pluginRepositories>
</project>
```

#### B. Recommended Multi-Stage Backend `Dockerfile`
```dockerfile
# =============================================================================
# Stage 1: Build Application with Maven & JDK 21
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests

# =============================================================================
# Stage 2: Minimal Production JRE Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

#### C. Recommended Frontend `frontend/Dockerfile` & `frontend/nginx.conf`
**`frontend/Dockerfile`**:
```dockerfile
# Stage 1: Build React SPA
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Production Nginx Server
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

**`frontend/nginx.conf`**:
```nginx
server {
    listen 80;
    server_name localhost;

    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### D. Recommended `docker-compose.yml`
```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: mentor_postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: mentor_hub
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d mentor_hub"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - mentor-network

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: mentor_backend
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/mentor_hub
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      SPRING_FLYWAY_ENABLED: "true"
      TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN:-mock-token}
      TELEGRAM_BOT_USERNAME: ${TELEGRAM_BOT_USERNAME:-MentorHubBot}
      AI_API_KEY: ${AI_API_KEY:-mock-key}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - mentor-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: mentor_frontend
    restart: unless-stopped
    ports:
      - "5173:80"
    depends_on:
      - backend
    networks:
      - mentor-network

volumes:
  pgdata:

networks:
  mentor-network:
    driver: bridge
```

---

## 5. Verification Method

### 5.1. Automated Test Suite Verification
Run from workspace root:
```powershell
node scripts/test-runner.mjs
```
**Expected outcome**: All 73 tests pass with 100% success rate, 0 failures, and `TEST_READY.md` updated with exit code 0.

### 5.2. Frontend Build and Lint Verification
```powershell
npm --prefix frontend run lint
npm --prefix frontend run build
```
**Expected outcome**:
- Oxlint finishes with "Found 0 warnings and 0 errors".
- Vite and TypeScript compile cleanly with output in `frontend/dist/`.

### 5.3. Brand Neutrality Audit
```powershell
node -e "
const fs = require('fs');
const files = [
  'frontend/src/App.tsx',
  'frontend/src/mockData.ts',
  'frontend/src/types.ts',
  'frontend/index.html',
  'frontend/package.json',
  'docs/ARCHITECTURE_SPECIFICATION.md',
  'docs/PITCH_PROPOSAL.md'
];
let viol = 0;
for (const f of files) {
  if (fs.existsSync(f)) {
    const c = fs.readFileSync(f, 'utf8');
    const m = c.match(/миранда|miranda/gi);
    if (m) { console.error('Violation in ' + f + ': ' + m.length); viol += m.length; }
  }
}
console.log('Total brand violations: ' + viol);
process.exit(viol === 0 ? 0 : 1);
"
```
**Expected outcome**: Total brand violations = 0.

### 5.4. Backend Build & Container Verification
Once the backend files are generated:
```powershell
# Maven package & test
mvn clean test
mvn clean package -DskipTests

# Docker Compose validation
docker compose config
```
**Expected outcome**:
- `mvn clean test` runs with 100% passing tests using H2 offline / Mockito.
- `docker compose config` validates schema with zero syntax errors.
