## 2026-11-07T17:01:59Z
You are a Test Writer subagent in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Also read the test survey report and test infrastructure document:
1. c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_survey_test\handoff.md
2. c:\Users\Honor\Desktop\miranda\.agents\teamwork\orchestrator\TEST_INFRA.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Identity & Working Directory:
- Identity: Test Writer (Milestone M-E2E)
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\test_writer_m_e2e\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Exclusive Write File Boundaries:
You own and may ONLY edit:
1. `scripts/test-runner.mjs` (create/write full automated test suite)
2. `TEST_READY.md` (publish at project root `c:\Users\Honor\Desktop\miranda\TEST_READY.md` upon completion)
DO NOT edit files in `frontend/src/` or `docs/`.

Tasks:
1. Create `scripts/test-runner.mjs` implementing the comprehensive 4-Tier test suite:
   - Tier 1: Feature Coverage (>=5 test cases per feature area across 8 feature areas = 40+ tests):
     * Area 1: Brand Neutrality & Global Header (0 occurrences of "миранда", header title "Mentor Hub", pulsing bot indicator, counter).
     * Area 2: Tab Navigation & Routing (5 tabs present, active tab styling, badge count).
     * Area 3: Inbox Zero Tab («🔥 На проверку»: 3 cards, clickable Reels links, duplicate alert, mentor question, quick reply, TG link).
     * Area 4: Discipline Calendar Tab («👩‍🎓 Ученицы & Календарь»: Mon-Sun grid, status color dots, discipline % badges, search filter).
     * Area 5: Analytics & Story Generator Tab («📊 Аналитика & Кейсы»: KPI cards, Point A -> Point B calculations with multipliers, story text template).
     * Area 6: Tasks & Hashtag Rules Tab («📋 Задания & Хэштеги»: rules for #рилс, #продажи, #рассылки, #вопрос, required vs optional badges).
     * Area 7: Tariff Matrix Comparison Tab («💰 Тарифы»: 320 BYN vs 475 BYN side-by-side, feature breakdown, timelines, server costs).
     * Area 8: Architecture Specification Verification (`docs/ARCHITECTURE_SPECIFICATION.md` or `docs/ARCHITECTURE_SPRING_BOOT.md`: DDL, REST API endpoints, Telegram Business API, Spring Boot 4.x/3.4.2 fallback, 320 vs 475 BYN matrix).
   - Tier 2: Boundary & Corner Cases (11 tests: empty inbox, 0 sales, division by zero protection, long strings, discipline threshold exact boundaries).
   - Tier 3: Cross-Feature State Combinations (5 scenarios: search state across tabs, approval counter updates, story selection reactivity, rule alignment).
   - Tier 4: Real-World Scenarios (4 workflows: Mentor Ksenia 20:30 evening routine, story preparation, discipline audit, tariff pitch).
2. Execute the test runner:
   - Run `node scripts/test-runner.mjs`.
   - Ensure all 52+ tests execute, pass with 100%, and exit with code 0.
3. Automatically generate and write `TEST_READY.md` at project root `c:\Users\Honor\Desktop\miranda\TEST_READY.md` with full coverage summary.
4. Write a detailed handoff report in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\test_writer_m_e2e\handoff.md`.
5. Notify parent orchestrator using send_message when complete.
