## 2026-11-07T17:10:37Z
You are Challenger 2 in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Your Identity & Working Directory:
- Identity: Architecture Adversarial Challenger
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_arch_adversarial\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Mission:
Perform empirical adversarial testing on backend architecture specifications, regex algorithms, database models, and API contracts:
1. Write a standalone test script/harness in your working directory to verify:
   - Instagram Reels regex against tricky and malformed URLs (`https://instagram.com/reel/XYZ?igsh=abc`, `https://www.instagr.am/p/XYZ/`, invalid domains, missing shortcodes).
   - SHA-256 deduplication logic: verify collisions, case variations, query parameter stripping before hashing.
   - Hashtag parsing regex against complex strings (e.g. `#продажи 45 000 руб`, `#продажи 80к`, `#рассылки 25`, `#вопрос`, multiple hashtags in a single message).
   - PostgreSQL DDL syntax and constraint validation: foreign keys, `ON DELETE CASCADE`, unique constraints (`uq_student_report_date`), indexes.
   - REST API contracts alignment with frontend TypeScript definitions (`types.ts`).
2. Run your harness and document all empirical test results.
3. Record findings and your final verdict in `c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_arch_adversarial\handoff.md`.
Verdict must be:
`VERDICT: APPROVE`
or
`VERDICT: REJECT` (with concrete failure reproduction)
4. Notify your parent orchestrator using send_message when complete.
