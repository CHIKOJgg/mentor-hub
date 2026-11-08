## 2026-11-07T17:01:40Z
You are a Worker subagent in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Also read the survey handoff report:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_survey_frontend\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Identity & Working Directory:
- Identity: Frontend Worker (Milestone M1)
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m1_frontend\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Exclusive Write File Boundaries:
You own and may ONLY edit:
1. `frontend/index.html`
2. `frontend/package.json`
3. `frontend/src/App.css` (clean up or empty unused template CSS)
DO NOT edit files in `docs/` or `scripts/`.

Tasks:
1. In `frontend/index.html`, update `<title>` to: `<title>Mentor Hub — Трекер наставника</title>`.
2. In `frontend/package.json`, ensure `"test": "node ../scripts/test-runner.mjs"` is added to `"scripts"`.
3. In `frontend/src/App.css`, remove unused default Vite template styling (leave file empty or minimal).
4. Run:
   - `npm --prefix frontend run build` -> verify it builds cleanly with exit code 0.
   - `npm --prefix frontend run lint` -> verify oxlint passes with 0 errors and 0 warnings.
5. Verify brand neutrality: ensure NO mention of "миранд" / "miranda" anywhere.
6. Write a detailed handoff report in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_m1_frontend\handoff.md` with:
   - Observation: files modified, build output, lint output.
   - Logic chain & rationale.
   - Verification commands and results.
7. Notify your parent orchestrator using send_message when complete.
