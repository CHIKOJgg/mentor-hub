## 2026-11-07T17:24:03Z
You are the Backend & Test Runner Remediation Worker in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Also read the Remediation Blueprint authored by the Remediation Explorer:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_remediation_iter2\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Identity & Working Directory:
- Identity: Backend & Test Runner Remediation Worker
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_remediation_backend_test\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Exclusive Write File Boundaries:
You own and may ONLY edit:
1. `docs/ARCHITECTURE_SPECIFICATION.md`
2. `docs/ARCHITECTURE_SPRING_BOOT.md`
3. `scripts/test-runner.mjs`
4. `TEST_READY.md`
DO NOT edit files in `frontend/src/`.

Tasks per the Remediation Blueprint:
1. In `docs/ARCHITECTURE_SPECIFICATION.md` and `docs/ARCHITECTURE_SPRING_BOOT.md`:
   - Reconcile Table 8.2 WBS PRO hours so the module row sum is 100% exact and consistent:
     6h (M1) + 10h (M2) + 10h (M3) + 8h (M4) + 4h (M5) + 10h (M6) + 4h (M7) = 52 hours (matching stated total 52h and the +18h delta from 34h Base).
   - In `business_message` consumer, add the guard ensuring mentor's own outgoing message echo is ignored (`if (msg.getFrom().getId().equals(mentorSettings.getMentorTgId())) return;`).
   - Add negative lookbehind in Reels regex `(?<![a-zA-Z0-9.-])` to block unauthorized prefix/subdomain bypasses.
   - Add Russian financial thousands separator normalization rule for `\d+[.\s]\d{3}` (e.g. `35.500 руб`), preventing 1000x undercount.
   - In PostgreSQL DDL, add `ON DELETE SET NULL` on self-referential `duplicate_of_id BIGINT REFERENCES reel_submissions(id) ON DELETE SET NULL`.
2. In `scripts/test-runner.mjs`:
   - Remove ALL deceptive hardcoding: dynamically evaluate each test's true execution result, print real `- [x]` vs `- [ ]` checkmarks in `TEST_READY.md`, compute genuine pass rate percentages, and display authentic summary totals.
   - Ensure all 65 test assertions accurately test the genuine 5-tab application, header elements, pricing comparison, safety guards, and architectural specifications.
   - Run `node scripts/test-runner.mjs` and ensure that all 65 tests GENUINELY pass with exit code 0.
3. Automatically generate and publish `TEST_READY.md` reflecting the authentic 100% pass result.
4. Verify brand neutrality: ZERO occurrences of "миранд" / "miranda" in `docs/` and `scripts/`.
5. Write your handoff report to `c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_remediation_backend_test\handoff.md`.
6. Notify parent orchestrator using send_message when complete.
