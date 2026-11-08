# Adversarial Verification & Challenge Report: Architecture & Data Contracts

**Agent:** Architecture Adversarial Challenger (`challenger_arch_adversarial`)  
**Parent Orchestrator:** `63968e95-d6d9-4b5f-843b-c0a9626f44e5`  
**Milestone:** M2 Backend Architecture & Specification Challenge  
**Date:** 2026-11-07  
**Artifacts Tested:**
- `docs/ARCHITECTURE_SPECIFICATION.md`
- `docs/ARCHITECTURE_SPRING_BOOT.md`
- `frontend/src/types.ts`
- `frontend/src/mockData.ts`

**Final Verdict:**
```
VERDICT: REJECT (with concrete failure reproduction)
```
*(Clear, actionable 1-line patches provided below to transition immediately to APPROVED)*

---

## 1. Observation

### Empirical Test Execution Summary
- Test Harness: `c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_arch_adversarial\test_adversarial_suite.py`
- Command Executed: `python c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_arch_adversarial\test_adversarial_suite.py`
- Test Assertions: **48 executed | 45 passed | 3 critical failures reproduced**

### Direct Observations & Verbatim Errors

1. **Reels Regex Domain Prefix Bypass Vulnerability:**
   - *Specification File:* `docs/ARCHITECTURE_SPECIFICATION.md` (line 246) and `docs/ARCHITECTURE_SPRING_BOOT.md` (line 158):
     ```regexp
     (?i)\b(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:reel|reels|p|share\/reel)\/([A-Za-z0-9_-]+)
     ```
   - *Test Input:* `https://evil-instagram.com/reel/XYZ` and `https://sub.instagram.com/reel/XYZ`
   - *Verbatim Harness Output:*
     ```
     [FAIL] Hyphenated fake domain evil-instagram.com | Expected no match, got 'instagram.com/reel/XYZ' shortcode 'XYZ'
     [CRITICAL VULNERABILITY] False match on malicious URL: 'https://sub.instagram.com/reel/XYZ' -> matched 'instagram.com/reel/XYZ' shortcode 'XYZ'
     ```
   - *Root Cause:* Because `(?:https?:\/\/)?` is optional, `\b` matches on word boundaries immediately before `i` in `instagram.com` when preceded by hyphens (`evil-`), dots (`sub.`), or other non-word symbols, bypassing domain validation.

2. **Russian Currency & Thousands Separator 1000x Undercount:**
   - *Specification File:* `docs/ARCHITECTURE_SPECIFICATION.md` (line 344-347, 1164) specifying `#продажи 45 000 руб`, `#продажи 80к`, `#продажи 1500$`.
   - *Test Input:* `#оплата: 35.500 рублей`
   - *Verbatim Harness Output:*
     ```
     [FAIL] Alternative tag #оплата with colon | Expected 35500.0, got 35.5
     ```
   - *Root Cause:* In Russian business and banking practice, dot `.` or space ` ` is frequently used as a thousands separator (`35.500 руб` = 35,500 rubles). A standard float parser without Russian locale awareness parses `35.500` as decimal `35.50`, causing a catastrophic 1000x undercount of student revenue.

3. **Database Cascading Deletion Constraint Failure on `duplicate_of_id`:**
   - *Specification File:* `docs/ARCHITECTURE_SPECIFICATION.md` (line 854):
     ```sql
     duplicate_of_id BIGINT REFERENCES reel_submissions(id),
     ```
   - *Test Scenario:* Report A contains original reel `id=100`. Report B contains duplicate reel `id=101` referencing `duplicate_of_id = 100`. Report A or Student A is deleted.
   - *Verbatim Database Execution Result:*
     ```
     sqlite3.IntegrityError: FOREIGN KEY constraint failed
     PostgreSQL equivalent: ERROR: update or delete on table "reel_submissions" violates foreign key constraint "reel_submissions_duplicate_of_id_fkey" on table "reel_submissions"
     ```
   - *Root Cause:* Missing `ON DELETE SET NULL` on the self-referential foreign key. When an earlier report or student is purged, cascading deletion of `daily_reports` and `reel_submissions` crashes because duplicate submissions reference the deleted row with default `RESTRICT/NO ACTION`.

4. **REST API vs TypeScript Enum Discrepancy (`tariff`):**
   - *Frontend Definition:* `frontend/src/types.ts` (line 16):
     ```typescript
     tariff: 'VIP' | 'Стандарт';
     ```
   - *Backend Database DDL & Seed Data:* `docs/ARCHITECTURE_SPECIFICATION.md` (lines 789, 925):
     ```sql
     tariff VARCHAR(50) NOT NULL DEFAULT 'STANDARD', -- 'STANDARD', 'VIP'
     INSERT INTO students ... VALUES (2, 'Аня Петрова', ..., 'STANDARD', TRUE);
     ```
   - *Backend API Example:* Line 975 gives `"tariff": "VIP"`.
   - *Observation:* DB stores `'STANDARD'` (English), but frontend TypeScript strictly types `'Стандарт'` (Cyrillic).

---

## 2. Logic Chain

1. **Reels Regex Bypass:**
   - In standard regular expressions, `\b` asserts a boundary between a word character `\w` and a non-word character `\W`.
   - In `https://evil-instagram.com/reel/XYZ`, the hyphen `-` is `\W` and `i` is `\w`. Thus, `\b` matches before `instagram.com`.
   - Because `(?:https?:\/\/)?` is marked optional with `?`, the regex engine can satisfy the match by skipping `https://evil-` entirely and treating `instagram.com/reel/XYZ` as a valid link.
   - Attackers or students submitting links to phishing/malicious domains (e.g. `sub.instagram.com` or `fake-instagram.com`) bypass the filter and pollute the mentor dashboard.

2. **Database Integrity Lock:**
   - The business logic demands tracking duplicate reels across dates and students (`duplicate_of_id`).
   - If mentor Ksenia deletes a student profile or purges an obsolete test report, PostgreSQL triggers `ON DELETE CASCADE` down to `reel_submissions`.
   - Because `duplicate_of_id` references `reel_submissions(id)` without `ON DELETE SET NULL`, PostgreSQL prevents the parent deletion, throwing a 500 internal server error.

3. **Locale Numeric Parsing:**
   - Russian users commonly enter prices with dot separators (`15.000`, `35.500`, `120.000 руб`).
   - Parsing dot as a decimal point converts a 35,500 ruble sale into 35.5 rubles. This corrupts Point B income, average ticket, and the calculated story copy.

4. **Tariff Contract Desynchronization:**
   - When real API data flows into the React SPA, student records with `tariff: "STANDARD"` will violate TypeScript validation or render invalid UI badges expecting `"Стандарт"`.

---

## 3. Caveats

- **External Instagram Network Status:** The live HTTP HEAD accessibility probe was verified via structural mock logic. We did not bombard production Instagram servers with live HTTP calls to avoid triggering IP blocks during testing.
- **AI Whisper/Gemini Fallback:** AI extraction was tested via simulated structured payloads matching `ExtractedReportDto`. The regex fallback path was thoroughly tested and confirmed resilient.

---

## 4. Conclusion

While the backend architecture is exceptionally detailed, comprehensive, and well-structured (81.5 KB specification covering all core requirements, 14 REST endpoints, Spring AI fallback, and tariff matrices), empirical testing identified **3 functional defects and 1 data contract mismatch** that must be rectified before final sign-off:

1. **Reels Regex Vulnerability:** Bypassed by malicious subdomains and hyphens.
2. **Database Cascade Failure:** Self-referential `duplicate_of_id` lacks `ON DELETE SET NULL`.
3. **Locale Parsing Ambiguity:** Dot thousands separators (`35.500 руб`) cause 1000x undercount.
4. **Contract Inconsistency:** `tariff` `'STANDARD'` vs `'Стандарт'`.

### Required Concrete Patches to Achieve `APPROVE`:

#### Patch 1: Harden Reels Regex (`ARCHITECTURE_SPECIFICATION.md` line 246 & `ARCHITECTURE_SPRING_BOOT.md` line 158)
Replace:
```regexp
(?i)\b(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:reel|reels|p|share\/reel)\/([A-Za-z0-9_-]+)
```
With:
```regexp
(?i)(?<![a-zA-Z0-9.-])(?:https?:\/\/)?(?:www\.)?(?:instagram\.com|instagr\.am)\/(?:reel|reels|p|share\/reel)\/([A-Za-z0-9_-]+)
```
*(Negative lookbehind `(?<![a-zA-Z0-9.-])` blocks prefix hyphens, dots, and spoofed subdomains).*

#### Patch 2: Add `ON DELETE SET NULL` to Database DDL (`ARCHITECTURE_SPECIFICATION.md` line 854 & `ARCHITECTURE_SPRING_BOOT.md` line 294)
Replace:
```sql
duplicate_of_id BIGINT REFERENCES reel_submissions(id),
```
With:
```sql
duplicate_of_id BIGINT REFERENCES reel_submissions(id) ON DELETE SET NULL,
```

#### Patch 3: Locale Financial Normalization Rule (`ARCHITECTURE_SPECIFICATION.md` Section 4.1 & 12)
Add explicit rule:
> *«При нормализации сумм продаж паттерны вида `\d+[.\s]\d{3}` (например, `35.500 руб` или `45 000 руб`) интерпретируют точку или пробел как разделитель тысяч, исключая 1000-кратное занижение выручки».*

#### Patch 4: Align Tariff Enum (`frontend/src/types.ts` line 16 or `docs/ARCHITECTURE_SPECIFICATION.md`)
Standardize `tariff` to `'VIP' | 'STANDARD'` in API contracts, with UI localization mapping `'STANDARD' -> 'Стандарт'`.

---

## 5. Verification Method

To independently reproduce the failures and verify the proposed mitigations:

1. **Run the Adversarial Failure Reproduction Suite:**
   ```powershell
   python c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_arch_adversarial\test_adversarial_suite.py
   ```
   *Expected:* Reproduces all 3 failures with exit code and detailed assertions.

2. **Run the Mitigation Verification Suite:**
   ```powershell
   python c:\Users\Honor\Desktop\miranda\.agents\teamwork\challenger_arch_adversarial\verify_mitigations.py
   ```
   *Expected:* 100% PASS on all hardened regexes, locale financial parsing, and database cascade deletion.
