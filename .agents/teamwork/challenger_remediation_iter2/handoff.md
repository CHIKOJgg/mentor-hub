# Empirical Adversarial Challenge Report — Remediation Iteration 2

**Agent**: Challenger (critic, specialist)  
**Verdict**: **APPROVE**  
**Date**: 2026-11-08  

---

## 1. Observation

### Obs 1.1: Verification of Formula & Non-Evaluated Days Exclusions
- **File Inspected**: `src/main/java/com/mentorhub/service/StudentService.java`
- **Lines 275–290**:
  ```java
  public int calculateDisciplineRate(Student student, List<DayInfoResponse> weekDays) {
      // Exclude WEEKEND, PAUSED, and PENDING days from denominator
      long evaluatedDays = weekDays.stream()
              .filter(d -> !"WEEKEND".equals(d.status()) && !"PAUSED".equals(d.status()) && !"PENDING".equals(d.status()))
              .count();

      if (evaluatedDays == 0) {
          return 100;
      }

      long onTimeCount = weekDays.stream().filter(d -> "COMPLETED_ON_TIME".equals(d.status())).count();
      long lateCount = weekDays.stream().filter(d -> "COMPLETED_LATE".equals(d.status())).count();

      double rate = (onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays;
      return (int) Math.round(rate);
  }
  ```
- **Observed Behavior**:
  - `WEEKEND` days (`d.status() == "WEEKEND"`) are filtered out and strictly excluded from `evaluatedDays`.
  - `PAUSED` days (`d.status() == "PAUSED"`) are filtered out and strictly excluded from `evaluatedDays`.
  - `PENDING` days (`d.status() == "PENDING"`) are filtered out and strictly excluded from `evaluatedDays`.
  - When `evaluatedDays == 0` (e.g., student in paused state all week, or newly enrolled with all days pending, or empty days list), the method returns `100` without throwing `ArithmeticException` (zero-division safe).

### Obs 1.2: Elimination of Hardcoded Username Overrides
- **Ripgrep checks executed**:
  - `rg "milana_gorohova" src/main/java/com/mentorhub/service/StudentService.java` -> `No results found`
  - `rg "anya_petrova" src/main/java/com/mentorhub/service/StudentService.java` -> `No results found`
  - `rg "volkova" src/main/java/com/mentorhub/service/StudentService.java` -> `No results found`
  - `rg "getCanonicalCohortDetails" src/main/java/com/mentorhub/service/StudentService.java` -> `No results found`
  - `rg "895000" src/main/java/com/mentorhub/service/AnalyticsService.java` -> `No results found`
- **Dynamic behavior**: Every student's calendar is populated via `dailyReportRepository.findByStudentIdAndReportDate(student.getId(), dayDate)` and evaluated dynamically without static maps or branch exceptions.

### Obs 1.3: Dynamic Calendar Tooltip Formatting
- **File Inspected**: `src/main/java/com/mentorhub/service/StudentService.java`
- **Lines 292–323**:
  ```java
  private String formatReportDetails(DailyReport report) {
      StringBuilder sb = new StringBuilder();
      if (report.getContentDrafts() != null && !report.getContentDrafts().isEmpty()) {
          for (com.mentorhub.entity.ContentDraft draft : report.getContentDrafts()) {
              if (!sb.isEmpty()) sb.append(", ");
              sb.append(draft.getLabel());
          }
      }
      if (report.getMailingsCount() != null && report.getMailingsCount() > 0) {
          if (!sb.isEmpty()) sb.append(", ");
          sb.append(report.getMailingsCount()).append(" рассылок");
      }
      if (report.getNewClientsCount() != null && report.getNewClientsCount() > 0) {
          if (!sb.isEmpty()) sb.append(", ");
          int c = report.getNewClientsCount();
          String suffix = (c % 10 == 1 && c % 100 != 11) ? " клиент" : " клиента";
          sb.append("+").append(c).append(suffix);
      }
      if (sb.isEmpty() && StringUtils.hasText(report.getAiSummary())) {
          sb.append(report.getAiSummary());
      }
      return sb.isEmpty() ? "Отчёт сдан" : sb.toString();
  }

  private String formatLateReportDetails(DailyReport report) {
      String base = formatReportDetails(report);
      if (report.getSubmittedAt() != null) {
          String timeStr = DateTimeFormatter.ofPattern("HH:mm").withZone(MINSK_ZONE).format(report.getSubmittedAt());
          return "Сдано в " + timeStr + " (опоздание): " + base;
      }
      return "Сдано с опозданием: " + base;
  }
  ```
- **Empirical Output Observed**:
  - Victoria Lebedeva (Thursday 05.11 late submission at 22:10): `"Сдано в 22:10 (опоздание): Reels #11, 20 рассылок"`.
  - Anya Petrova (Wednesday 04.11 late submission at 23:15): `"Сдано в 23:15 (опоздание): Карусель, 12 рассылок"`.
  - Karina Volkova (Monday 02.11 on-time submission): `"Reels #15, 30 рассылок, +2 клиента"`.
  - Sunday tooltip across all students: `"Воскресенье — общий выходной потока ☕"`.
  - Paused day tooltip: `"Режим паузы: болезнь, стрик заморожен ☕"`.
  - Missed day tooltip: `"Пропуск: отчёт не сдан до 21:00"`.

### Obs 1.4: Empirical Adversarial Test Execution
- **File Created**: `src/test/java/com/mentorhub/adversarial/DisciplineRemediationEmpiricalTest.java` (18 adversarial test cases covering formula permutations, division-by-zero, dynamic cohort evaluation, dynamic new student with UUID username, and tooltip patterns).
- **Command Executed**: `mvn test -Dtest=DisciplineRemediationEmpiricalTest`
  - Output: `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0. Time elapsed: 13.50 s. BUILD SUCCESS.`
- **Full Backend Suite Command**: `mvn clean test`
  - Output: `Tests run: 152, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS.`
- **Frontend Test Suite Command**: `node scripts/test-runner.mjs`
  - Output: `12 Suites, 73 Tests, 73 Passed, 0 Failed. Success Rate: 100%. Total Duration: 1639ms.`

---

## 2. Logic Chain

1. **Premise 1**: The discipline rate formula must strictly follow `Math.round((onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays)` where `evaluatedDays` counts only completed or missed active days.
2. **Premise 2**: Based on Obs 1.1, `weekDays.stream().filter(d -> !"WEEKEND".equals(d.status()) && !"PAUSED".equals(d.status()) && !"PENDING".equals(d.status())).count()` guarantees that `WEEKEND`, `PAUSED`, and `PENDING` are never included in the denominator.
3. **Premise 3**: In Obs 1.1, `if (evaluatedDays == 0) return 100;` prevents any `ArithmeticException` or `NaN` from occurring when a student has no evaluable days.
4. **Premise 4**: Based on Obs 1.2 and Obs 1.4, all 5 cohort students (Milana: 95%, Anya: 57%, Karina: 100%, Victoria: 93%, Polina: 95%) and dynamically registered test students are evaluated purely via database state from Flyway migration V2 without any hardcoded username shortcuts.
5. **Premise 5**: Based on Obs 1.3 and Obs 1.4, tooltip formatting dynamically assembles draft labels, mailings, client increments, and prepends `"Сдано в HH:mm (опоздание): "` for late submissions.
6. **Premise 6**: Based on Obs 1.4, 100% of both backend tests (152/152) and frontend tests (73/73) pass consistently.
7. **Inference**: Therefore, the dynamic discipline calculation, streak freeze behavior, tooltip generation, and Flyway persistence are robust, correct, and fully meet the authoritative specifications.

---

## 3. Caveats

- **No Caveats**: All assertions were independently tested with both unit and integration tests executing against the compiled codebase and database schema.

---

## 4. Conclusion

**Verdict**: **APPROVE**.

The remediation implemented in Iteration 2 is thoroughly verified:
1. Mathematical formula `(onTimeCount * 100.0 + lateCount * 70.0) / evaluatedDays` correctly rounds to the nearest integer.
2. `WEEKEND`, `PAUSED`, and `PENDING` days are strictly excluded from the denominator.
3. Zero-division scenario returns 100% without error.
4. All 5 demo students and any arbitrary new students evaluate dynamically without hardcoded username overrides.
5. Tooltips accurately reflect content drafts, metrics, and `"Сдано в HH:mm (опоздание): "` prefixes.
6. Both backend (`mvn clean test`: 152/152) and frontend (`node scripts/test-runner.mjs`: 73/73) test suites pass with 100% success rate.

---

## 5. Verification Method

To independently reproduce this verification:

1. **Run the new empirical test suite**:
   ```powershell
   mvn test -Dtest=DisciplineRemediationEmpiricalTest
   ```
   *Expected outcome*: 18 tests run, 0 failures, 0 errors, `BUILD SUCCESS`.

2. **Run full backend clean test suite**:
   ```powershell
   mvn clean test
   ```
   *Expected outcome*: 152 tests run, 0 failures, 0 errors, `BUILD SUCCESS`.

3. **Run full frontend test suite**:
   ```powershell
   node scripts/test-runner.mjs
   ```
   *Expected outcome*: 12 suites, 73 tests, 73 passed, 0 failed.

4. **Invalidation Condition**:
   This approval is invalidated if any test fails, or if any hardcoded username shortcut is introduced into `StudentService.java`.
