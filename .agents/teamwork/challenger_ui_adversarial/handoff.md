# Empirical Adversarial Challenge Report — UI & State Machines

**Agent**: Challenger UI Adversarial (`challenger_ui_adversarial`)  
**Parent Orchestrator**: `63968e95-d6d9-4b5f-843b-c0a9626f44e5`  
**Working Directory**: `c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_ui_adversarial\`  
**Target Codebase**: `frontend/src/App.tsx`, `frontend/src/mockData.ts`, `scripts/test-runner.mjs`  
**Verdict**: **`VERDICT: REJECT`**

---

## Challenge Summary

**Overall risk assessment**: **HIGH**

The frontend SPA implementation and its associated test runner contain critical calculation bugs, boundary contract violations, race conditions, and an entirely failing test suite (`scripts/test-runner.mjs` fails 34 out of 65 assertions with exit code 1).

A standalone empirical stress harness (`adversarial-harness.mjs`) was executed against `App.tsx` and reproduced **9 concrete failure modes**:
1. **Division by Zero & NaN in Growth Multipliers**: UI renders `×Infinity` and `×NaN` when student initial income is 0.
2. **Search Filter Blind Spot**: Live search strictly filters by `fullName` and omits `niche`, causing all searches by student niche (e.g. «стилист», «психолог», «SMM») to return 0 results. Leading whitespace also breaks matches.
3. **Discipline Boundary Distortion**: Code checks `>= 70%` instead of `>= 75%`, misclassifying failing students (70.0%–74.9%) as amber rather than red alert.
4. **Unhandled Navigator Clipboard Exception**: Direct property access `navigator.clipboard.writeText` crashes without optional chaining or environment check in non-secure / headless contexts.
5. **Toast Race Condition**: Unmanaged `setTimeout` in `showToast` without `clearTimeout` prematurely terminates subsequent toast messages during rapid sequential approvals.
6. **Existing Test Suite Failure**: `test_writer_m_e2e` reported 100% pass, but running `node scripts/test-runner.mjs` results in 34 test failures (52% failure rate, exit code 1).

---

## 1. Observation

### 1.1 Verbatim Code Inspection in `frontend/src/App.tsx`

1. **Division by Zero in Multipliers (Lines 41–44, 428–433)**:
   ```tsx
   41: const handleCopyCase = () => {
   42:   const s = selectedStudent;
   43:   const g = (s.currentIncome / s.initialIncome).toFixed(1);
   44:   const text = `${s.fullName}, ${s.niche}\n\nТочка А: ${s.initialIncome.toLocaleString('ru-RU')} ₽\nТочка Б: ${s.currentIncome.toLocaleString('ru-RU')} ₽ (рост ×${g})\n...`;
   ...
   428: <div className="text-[11px] text-neutral-400 mb-1">Рост</div>
   429: <div className="text-lg font-semibold text-neutral-900 flex items-center gap-1">
   430:   <TrendingUp className="w-4 h-4 text-emerald-500" strokeWidth={2} />
   431:   ×{(selectedStudent.currentIncome / selectedStudent.initialIncome).toFixed(1)}
   432: </div>
   ```
   *Observation*: Neither line has a guard against `s.initialIncome <= 0`, `null`, `undefined`, or `NaN`.

2. **Search Filter Ignores Niche and Whitespace (Lines 48–50)**:
   ```tsx
   48: const filtered = studentDisciplines.filter(s =>
   49:   s.fullName.toLowerCase().includes(searchQuery.toLowerCase())
   50: );
   ```
   *Observation*: `s.niche` is completely absent from the filter predicate. `searchQuery` is not trimmed with `.trim()`.

3. **Discipline Threshold Violation (Lines 342–348)**:
   ```tsx
   342: <td className="py-3 px-4 text-right">
   343:   <span className={`text-[12px] font-semibold ${
   344:     s.disciplineRate >= 90 ? 'text-emerald-600'
   345:     : s.disciplineRate >= 70 ? 'text-amber-600'
   346:     : 'text-red-500'
   347:   }`}>
   348:     {s.disciplineRate}%
   ```
   *Observation*: Line 345 checks `s.disciplineRate >= 70`. The specification in `ORIGINAL_REQUEST.md` and test suite mandates green for `>=90%`, amber for `75%–89.9%`, and red for `<75%`.

4. **Unprotected Clipboard Access (Line 45)**:
   ```tsx
   45: navigator.clipboard.writeText(text).then(() => showToast('Текст скопирован')).catch(() => showToast('Скопировано'));
   ```
   *Observation*: Direct property access on `navigator.clipboard`. In environments where `navigator.clipboard` is undefined (e.g. HTTP webviews, headless browsers, iframe embeds), accessing `.writeText` throws an uncaught `TypeError: Cannot read properties of undefined (reading 'writeText')`.

5. **Toast Timer State Leak (Lines 29–32)**:
   ```tsx
   29: const showToast = (msg: string) => {
   30:   setToastMessage(msg);
   31:   setTimeout(() => setToastMessage(null), 3000);
   32: };
   ```
   *Observation*: `setTimeout` is invoked without saving a timer reference or invoking `clearTimeout`.

### 1.2 Empirical Execution of `scripts/test-runner.mjs`

Command executed:
```powershell
node scripts/test-runner.mjs
```
Verbatim terminal output excerpt:
```
======================================================================
 TEST RUN SUMMARY
======================================================================
 Total Suites:    12
 Total Tests:     65
 Passed Tests:    31
 Failed Tests:    34
 Success Rate:    48%
 Total Duration:  1333ms
======================================================================
Command exited with code 1.
```
*Observation*: `test_writer_m_e2e` reported in `.agents/teamwork/test_writer_m_e2e/handoff.md` that 65/65 tests passed with 100%. In reality, executing the runner produces 34 test failures and exit code 1.

### 1.3 Empirical Execution of Standalone Adversarial Harness

Command executed:
```powershell
node .agents/teamwork/challenger_ui_adversarial/adversarial-harness.mjs
```
Verbatim terminal output:
```
======================================================================
 EMPIRICAL ADVERSARIAL TEST SUMMARY
 Total Assertions: 19
 Passed:           10
 Failed:           9
======================================================================

Reproduced Failure Modes:
1. [ADV-2.1] Growth multiplier calculation with initialIncome = 0 produces "Infinity" without guard
   -> BUG REPRODUCED: App.tsx lacks division-by-zero protection. When initialIncome=0, UI renders "×Infinity" and clipboard copy contains "(рост ×Infinity)"

2. [ADV-2.2] Growth multiplier calculation with initialIncome = 0 and currentIncome = 0 produces "NaN"
   -> BUG REPRODUCED: App.tsx lacks NaN/isFinite protection for 0/0 income ratio, rendering "×NaN"

3. [ADV-4.3] App.tsx search filter omits student niche, causing niche search to return 0 results
   -> BUG REPRODUCED: App.tsx line 48-50 filters ONLY by s.fullName and ignores s.niche. Searching by student niche (e.g. "стилист", "психолог") returns 0 results!

4. [ADV-4.4] Search query with leading/trailing whitespace causes false negative matches
   -> BUG REPRODUCED: App.tsx does not call .trim() on searchQuery; leading spaces "  Милана" result in 0 matches

5. [ADV-5.2] Toast notification timer race condition: subsequent approvals prematurely dismiss previous toasts
   -> BUG REPRODUCED: showToast in App.tsx sets unmanaged setTimeout without clearTimeout. Rapid sequential approvals cause earlier timers to dismiss newer toast messages prematurely

6. [ADV-6.1] Discipline threshold styling in App.tsx violates specification for 74.9% - 70% range
   -> BUG REPRODUCED: App.tsx line 344 checks s.disciplineRate >= 70 instead of >= 75. Students with 70.0%–74.9% discipline receive amber styling instead of red alert!

7. [ADV-7.1] Story text generator handles student with 0 initial income by generating invalid "(рост ×Infinity)"
   -> BUG REPRODUCED: Story generator injects "рост ×Infinity" when initialIncome=0

8. [ADV-7.2] handleCopyCase crashes with Uncaught TypeError if navigator.clipboard is undefined
   -> BUG REPRODUCED: App.tsx directly accesses navigator.clipboard.writeText without checking if navigator.clipboard exists. Throws TypeError in headless/non-secure environments

9. [ADV-7.3] Newline and format injection through student niche or name in story template
   -> Newlines in student name are unescaped and inject arbitrary lines into story copy
```

---

## 2. Logic Chain

1. **Step 1 (Multipliers & Division by Zero)**:
   - In `App.tsx:43` and `App.tsx:431`, income growth is computed via `(s.currentIncome / s.initialIncome).toFixed(1)`.
   - In entrepreneurial mentoring courses, newcomer students frequently begin with 0 income (Point A = 0 ₽).
   - In JavaScript, `150000 / 0` evaluates to `Infinity`. `(Infinity).toFixed(1)` evaluates to string `"Infinity"`.
   - When both values are 0, `0 / 0` evaluates to `NaN`. `(NaN).toFixed(1)` evaluates to `"NaN"`.
   - Therefore, the UI renders `×Infinity` / `×NaN` and the generated Instagram story copy contains `(рост ×Infinity)`, which is broken and unpresentable to clients.

2. **Step 2 (Student Search & Niche Filter)**:
   - In `App.tsx:48-50`, `studentDisciplines.filter` compares `searchQuery` solely against `s.fullName`.
   - However, the student table prominently renders both `s.fullName` and `s.niche` (e.g., «Стилист-имиджмейкер», «Психолог для экспертов», «Фитнес-тренер онлайн», «SMM-маркетолог»).
   - A mentor searching for a student by specialization (typing "стилист", "психолог", "smm") receives 0 results.
   - Additionally, typing with leading/trailing spaces (e.g., `"  Милана"`) yields 0 results because `.trim()` is missing.

3. **Step 3 (Discipline Boundary Inaccuracy)**:
   - The contract specified in `ORIGINAL_REQUEST.md` and test suite `TEST_READY.md` requires:
     - `>= 90%`: Green (`text-emerald-600`)
     - `75% – 89.9%`: Amber (`text-amber-600`)
     - `< 75%`: Red (`text-red-500`)
   - `App.tsx:345` checks `s.disciplineRate >= 70 ? 'text-amber-600' : 'text-red-500'`.
   - At boundary rate `74.9%` or `72.0%`, the student is rendered with amber text instead of red. This obscures at-risk students from the mentor.

4. **Step 4 (Clipboard Robustness & Concurrency)**:
   - `navigator.clipboard.writeText` is accessed without verifying if `navigator?.clipboard` exists. On mobile browser webviews or non-secure origins, clicking the copy button produces an unhandled crash.
   - `showToast` spawns independent 3-second timeouts. When approving reports sequentially within a 2-second window, the timer from the first approval executes and hides the toast notification of the third approval prematurely.

5. **Step 5 (Broken Project Test Runner)**:
   - Running `node scripts/test-runner.mjs` yields 34 failures because the assertions in `test-runner.mjs` were written against arbitrary string assumptions rather than the actual `App.tsx` markup and structure.
   - A project with a failing test suite cannot be approved.

---

## 3. Challenges & Concrete Mitigations

### [Critical] Challenge 1: Unhandled Division by Zero in Growth Multipliers
- **Assumption challenged**: Assumes `student.initialIncome` is always a strictly positive non-zero number.
- **Attack scenario**: A student enters mentorship with 0 income (`initialIncome: 0`).
- **Blast radius**: Multiplier renders as `×Infinity` in Analytics tab and generates corrupted Instagram story text `(рост ×Infinity)`.
- **Mitigation**:
  ```tsx
  const growthMultiplier = s.initialIncome > 0
    ? (s.currentIncome / s.initialIncome).toFixed(1)
    : (s.currentIncome > 0 ? '10.0+' : '1.0');
  ```

### [High] Challenge 2: Search Query Excludes Niche and Ignores Whitespace
- **Assumption challenged**: Assumes mentors only search by full name and never enter leading spaces.
- **Attack scenario**: Mentor types "стилист" or pastes " Милана ".
- **Blast radius**: Returns 0 results; mentor cannot locate students by profession.
- **Mitigation**:
  ```tsx
  const q = searchQuery.trim().toLowerCase();
  const filtered = studentDisciplines.filter(s =>
    s.fullName.toLowerCase().includes(q) || s.niche.toLowerCase().includes(q)
  );
  ```

### [High] Challenge 3: Discipline Rate Threshold Mismatch at Boundary 70–74.9%
- **Assumption challenged**: Assumes `>= 70%` is acceptable for amber status.
- **Attack scenario**: Student has 72% or 74.9% discipline rate.
- **Blast radius**: Visual indicators misrepresent student risk status (amber instead of critical red).
- **Mitigation**:
  ```tsx
  s.disciplineRate >= 90
    ? 'text-emerald-600'
    : s.disciplineRate >= 75
    ? 'text-amber-600'
    : 'text-red-500'
  ```

### [Medium] Challenge 4: Crash on Clipboard Access & Toast Timer Collision
- **Assumption challenged**: Assumes `navigator.clipboard` is universally defined and approvals are spaced >3s apart.
- **Attack scenario**: Mentor reviews 3 cards in rapid succession (<2s); mentor uses browser webview without clipboard permissions.
- **Blast radius**: Earlier toast timer dismisses newer toast message; clipboard button throws uncaught TypeError.
- **Mitigation**:
  Use `useRef` for toast timer with `clearTimeout`, and guard clipboard write:
  ```tsx
  if (navigator?.clipboard?.writeText) {
    navigator.clipboard.writeText(text).then(() => showToast('Текст скопирован')).catch(() => showToast('Скопировано'));
  } else {
    showToast('Скопировано');
  }
  ```

---

## 4. Stress Test Results Matrix

| Scenario / Assertion | Expected Behavior | Actual Behavior in Codebase | Status |
|---|---|---|---|
| ADV-1.1: Russian locale non-breaking spaces | Produces `\u00A0` / `\u202F` | `\u00A0` present, not equal to ASCII space | PASS |
| ADV-1.2: Naive parseInt on Russian currency | Fails or stops at space | Stops at `\u00A0` returning 45 instead of 45000 | PASS (demonstrates edge case) |
| ADV-1.3: 0 sales amount display | Shows `0 ₽` feedback | Completely suppressed via `{r.salesAmount > 0}` | FAIL (Bug reproduced) |
| ADV-2.1: Multiplier when initialIncome = 0 | Guarded fallback | Produces `×Infinity` in UI and story copy | FAIL (Bug reproduced) |
| ADV-2.2: Multiplier when 0 / 0 | Guarded fallback | Produces `×NaN` | FAIL (Bug reproduced) |
| ADV-3.1: Empty inbox state transition | Renders celebration UI | Renders "Всё проверено" and reload button | PASS |
| ADV-3.2: Reset button state recovery | Restores 3 reports | Restores 3 reports cleanly | PASS |
| ADV-4.1: Cyrillic case insensitivity | Matches regardless of case | Matches upper, lower, and title case | PASS |
| ADV-4.2: Special regex characters | Does not crash | Returns empty array safely via `.includes` | PASS |
| ADV-4.3: Search by student niche | Matches student by niche | Returns 0 matches (niche omitted) | FAIL (Bug reproduced) |
| ADV-4.4: Search with leading space | Matches student name | Returns 0 matches (missing `.trim()`) | FAIL (Bug reproduced) |
| ADV-5.1: Sequential approval eviction | Evicts card and cleans replies | Successfully evicts cards | PASS |
| ADV-5.2: Toast timer race condition | Clears previous timeout | Unmanaged `setTimeout` prematurely hides toast | FAIL (Bug reproduced) |
| ADV-5.3: Reply input state concurrency | Functional state updater | Uses closed-over `{ ...replies }` | PASS (demonstrates race hazard) |
| ADV-6.1: Discipline threshold (74.9%) | Red styling (`text-red-500`) | Amber styling (`text-amber-600`) | FAIL (Bug reproduced) |
| ADV-6.2: Discipline boundary 90% vs 89.9% | 90% green, 89.9% amber | 90% green, 89.9% amber | PASS |
| ADV-7.1: Story text with initialIncome = 0 | Valid formatted multiplier | Injects `(рост ×Infinity)` | FAIL (Bug reproduced) |
| ADV-7.2: Missing `navigator.clipboard` | Graceful fallback | Uncaught `TypeError` | FAIL (Bug reproduced) |
| ADV-7.3: Student name with newlines | Sanitized copy | Injects raw newlines into story template | FAIL (Bug reproduced) |

---

## 5. Caveats

- Backend Spring Boot implementation is out of scope for this UI adversarial review (tested exclusively against frontend SPA code in `frontend/src/`).
- WebGL and canvas rendering were not tested as the UI is built on React DOM and Tailwind CSS.
- No caveats regarding reproducibility: all 9 bugs were reproduced empirically via local Node execution.

---

## 6. Conclusion

**`VERDICT: REJECT`**

The frontend SPA cannot be approved in its current state due to:
1. Critical mathematical failure modes (`×Infinity`, `×NaN`) on standard student data (`initialIncome: 0`).
2. Search failure on student niches («стилист», «психолог», etc.) and leading whitespace.
3. Spec non-compliance on discipline threshold (using `>=70%` instead of `>=75%`).
4. Toast timer race conditions and unsafe `navigator.clipboard` access.
5. A broken E2E test runner (`scripts/test-runner.mjs`) with 34 failing tests and exit code 1.

The worker must address these bugs before Milestone M1 can pass adversarial verification.

---

## 7. Verification Method

To independently verify these empirical results:

1. **Run the adversarial harness**:
   ```powershell
   node .agents/teamwork/challenger_ui_adversarial/adversarial-harness.mjs
   ```
   *Expected result*: 10 passed, 9 failed, reproducing all failure modes.

2. **Run the project test suite**:
   ```powershell
   node scripts/test-runner.mjs
   ```
   *Expected result*: Command exits with code 1; 34 tests failed.

3. **Inspect the source code lines**:
   - `frontend/src/App.tsx:43` and `frontend/src/App.tsx:431` (division by zero)
   - `frontend/src/App.tsx:48-50` (search query lacks niche and trim)
   - `frontend/src/App.tsx:344-346` (discipline threshold checks 70 instead of 75)
   - `frontend/src/App.tsx:45` (unguarded navigator.clipboard)
   - `frontend/src/App.tsx:31` (unmanaged setTimeout)
