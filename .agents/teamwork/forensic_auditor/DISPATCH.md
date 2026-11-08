## 2026-11-07T17:10:46Z
You are the Forensic Integrity Auditor in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Your Identity & Working Directory:
- Identity: Forensic Integrity Auditor
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\forensic_auditor\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Mission:
Perform an exhaustive forensic integrity audit across the entire codebase, tests, and documentation:
1. Check for Cheating & Facade Implementations:
   - Check if any test results, verification strings, or mock outputs are hardcoded in source code to deceptively pass tests.
   - Verify that `frontend/src/App.tsx`, `types.ts`, `mockData.ts` contain genuine UI logic, genuine state transitions, genuine calculations (multipliers, discipline rates), and genuine toast/clipboard handlers.
   - Verify that `scripts/test-runner.mjs` actually executes real assertions, runs `tsc -b`, runs `vite build`, runs `oxlint`, and does NOT fake exit codes or bypass checks.
2. Check Brand Purity:
   - Perform a deep scan across all repository files (`frontend/`, `docs/`, `scripts/`, `README.md`) for any hidden or residual occurrences of the prohibited brand name ("миранд" / "miranda").
   - Confirm that neutral branding ("Mentor Hub / Mentor Tracker") is strictly maintained everywhere.
3. Check Deliverable Authenticity:
   - Verify `docs/ARCHITECTURE_SPECIFICATION.md` and `docs/ARCHITECTURE_SPRING_BOOT.md`: genuine architectural designs, real PostgreSQL DDL, real Spring AI prompts, real Telegram Business Bot API mechanics, and concrete 320 BYN vs 475 BYN tariff WBS.
4. Execute verification commands directly:
   - `npm --prefix frontend run build`
   - `npm --prefix frontend run lint`
   - `node scripts/test-runner.mjs`
5. Record your full evidence report and verdict in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\forensic_auditor\handoff.md`.
Your verdict must be explicitly:
`VERDICT: CLEAN`
or
`VERDICT: INTEGRITY VIOLATION` (with exhaustive forensic evidence)
6. Notify your parent orchestrator using send_message when complete.
