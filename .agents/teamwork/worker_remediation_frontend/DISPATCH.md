## 2026-11-07T17:23:52Z
[Message] timestamp=2026-11-07T17:23:52Z sender=63968e95-d6d9-4b5f-843b-c0a9626f44e5 priority=MESSAGE_PRIORITY_HIGH content=You are the Frontend Remediation Worker in the Mentor Hub / Mentor Tracker project.

MANDATORY FIRST STEP: Read ORIGINAL_REQUEST.md located at:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\ORIGINAL_REQUEST.md

Also read the Remediation Blueprint authored by the Remediation Explorer:
c:\Users\Honor\Desktop\miranda\.agents\teamwork\explorer_remediation_iter2\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Identity & Working Directory:
- Identity: Frontend Remediation Worker
- Working directory: c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_remediation_frontend\
- Your parent orchestrator: 63968e95-d6d9-4b5f-843b-c0a9626f44e5

Exclusive Write File Boundaries:
You own and may ONLY edit:
1. `frontend/src/App.tsx`
2. `frontend/src/types.ts`
DO NOT edit `docs/` or `scripts/`.

Tasks per the Remediation Blueprint:
1. In `frontend/src/types.ts`, ensure `TabType` supports `'pricing'` if applicable, and types match.
2. In `frontend/src/App.tsx`, implement the complete genuine UI including:
   - 5 functional tabs: «🔥 На проверку» (`inbox`), «👩‍🎓 Ученицы & Календарь дисциплины» (`students`), «📊 Аналитика & Кейсы для Инсты» (`analytics`), «📋 Задания & Хэштеги» (`tasks`), and the 5th tab «💰 Тарифы (320 / 475 BYN)» (`pricing`).
   - The 5th Pricing tab must render a responsive side-by-side comparative table:
     * Base MVP: 320 BYN, 5–7 days, ~25 BYN hosting, manual voice handling, button "Выбрать Базовый (320 BYN)" triggering toast.
     * Full PRO: 475 BYN, 10–14 days, ~35 BYN hosting, AI Whisper voice recognition, conversational flood filter, auto-pings at 20:00, "Рекомендуется" badge, border highlight, button "Выбрать Полный PRO (475 BYN)" triggering toast.
   - Global Header: Neutral brand title "Mentor Hub", subtitle "Панель контроля наставничества • Ноябрь 2026", pulsing emerald indicator with "Бот активен", dynamic daily counter `${5 - reports.length} из 5 учениц`, and header sync action button.
   - Student live search filter: must search BOTH `s.fullName` AND `s.niche` with `.trim().toLowerCase()`.
   - Growth multiplier calculation: guard against `s.initialIncome <= 0`, `NaN`, `Infinity`, rendering fallback `1.0` or `1.0x` (never `×Infinity` or `×NaN`).
   - Discipline rate badge styling: strictly `>= 90%` green, `>= 75%` amber, `< 75%` red.
   - Clipboard copy handler: safely check `navigator?.clipboard?.writeText` with toast fallback for non-secure/headless environments.
   - Toast timer: manage with `clearTimeout` via ref/state so sequential clicks do not cancel toasts prematurely.
   - Inbox card layout: ensure duplicate Reel alert banner displays original date (`04 ноября`), sales displays `#продажи` (including `0 ₽` without suppression), outreach displays `#рассылки`.
3. Verify build & linting:
   - Run `npm --prefix frontend run build` -> must build with exit code 0.
   - Run `npm --prefix frontend run lint` -> oxlint must pass with 0 errors and 0 warnings.
4. Verify brand neutrality: ZERO occurrences of "миранд" / "miranda".
5. Write your handoff report to `c:\Users\Honor\Desktop\miranda\.agents\teamwork\worker_remediation_frontend\handoff.md`.
6. Notify parent orchestrator using send_message when complete.
