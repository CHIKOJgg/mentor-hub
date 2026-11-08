#!/usr/bin/env node
/**
 * =============================================================================
 * EMPIRICAL ADVERSARIAL TEST HARNESS — CHALLENGER UI ADVERSARIAL
 * =============================================================================
 * Stress-testing:
 * Area 1: Currency & Number Parsing/Formatting Across Locales
 * Area 2: Division by Zero & NaN Handling in Growth Multipliers
 * Area 3: Empty Inbox Zero State & Recovery After Reset
 * Area 4: Live Search Filtering Against Edge Cases
 * Area 5: Rapid Sequential Card Approvals & State Cleanup
 * Area 6: Discipline Rate Boundaries (90%, 89.9%, 75%, 74.9%, 70%)
 * Area 7: Story Text Generation Variable Injection & Formatting
 * =============================================================================
 */

import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';
import { pathToFileURL } from 'node:url';

const rootDir = path.resolve('c:/Users/Honor/Desktop/miranda');
const frontendDir = path.join(rootDir, 'frontend');
const appTsxPath = path.join(frontendDir, 'src', 'App.tsx');
const mockDataPath = path.join(frontendDir, 'src', 'mockData.ts');

const appTsxContent = fs.readFileSync(appTsxPath, 'utf8');
const { initialReports, studentDisciplines, taskRules } = await import(
  pathToFileURL(mockDataPath).href
);

console.log('======================================================================');
console.log(' EMPIRICAL ADVERSARIAL TEST HARNESS: FRONTEND UI & STATE MACHINES');
console.log(' Node Version:', process.version);
console.log(' Target:', appTsxPath);
console.log('======================================================================\n');

let total = 0;
let passed = 0;
let failed = 0;
const failures = [];

function check(testId, name, fn) {
  total++;
  try {
    fn();
    passed++;
    console.log(`[PASS] ${testId}: ${name}`);
  } catch (err) {
    failed++;
    console.error(`[FAIL] ${testId}: ${name}`);
    console.error(`       Error: ${err.message}`);
    failures.push({ testId, name, error: err.message });
  }
}

// -----------------------------------------------------------------------------
// AREA 1: Currency and Number Parsing / Formatting Across Locales
// -----------------------------------------------------------------------------
console.log('\n--- AREA 1: Currency & Number Formatting Across Locales ---');

check('ADV-1.1', 'Russian locale uses non-breaking space (\\u00A0 or \\u202F), not ASCII space', () => {
  const formatted = (45000).toLocaleString('ru-RU');
  const hasNonBreakingSpace = formatted.includes('\u00A0') || formatted.includes('\u202F');
  assert.ok(
    hasNonBreakingSpace,
    `Expected non-breaking space in Russian locale formatting, got codepoints: ${[...formatted].map(c => c.charCodeAt(0))}`
  );
  // Naive comparison with ASCII space must fail
  assert.notStrictEqual(formatted, '45 000', 'Should NOT equal ASCII space "45 000"');
});

check('ADV-1.2', 'Naive parseInt on Russian formatted currency string fails without non-breaking space normalization', () => {
  const formattedWithSymbol = `${(45000).toLocaleString('ru-RU')} ₽`;
  // If naive parseInt is used:
  // Note: in JavaScript, parseInt('45\u00A0000') in V8 parses as 45!
  const naiveParsed = parseInt(formattedWithSymbol);
  // This demonstrates why parsing formatted strings without space stripping truncates:
  assert.strictEqual(naiveParsed, 45, 'V8 parseInt stops at non-breaking space yielding 45 instead of 45000');
});

check('ADV-1.3', 'App.tsx suppresses sales display completely when salesAmount is 0 (missing fallback "0 ₽")', () => {
  // Line 231 of App.tsx: {r.salesAmount > 0 && (<span ...>Продажи: ...</span>)}
  // If student made 0 sales, does it show 0 ₽ or nothing?
  const zeroSalesReport = initialReports.find(r => r.salesAmount === 0);
  assert.ok(zeroSalesReport, 'Mock data should have a report with 0 sales (Anya Petrova)');
  
  // App.tsx line 231 checks salesAmount > 0
  const hasZeroSuppression = appTsxContent.includes('r.salesAmount > 0 &&');
  assert.ok(hasZeroSuppression, 'App.tsx hides salesAmount if <= 0');

  // Verify that there is NO fallback rendering for 0 ₽ sales in App.tsx
  const hasZeroFallback = appTsxContent.includes('r.salesAmount === 0') || appTsxContent.includes('0 ₽');
  // In App.tsx, does it display '0 ₽' for sales? NO!
  if (!hasZeroFallback) {
    throw new Error('BUG REPRODUCED: App.tsx completely suppresses sales metric when salesAmount is 0, omitting "0 ₽" feedback for mentor');
  }
});

// -----------------------------------------------------------------------------
// AREA 2: Division by Zero and NaN Handling in Growth Multipliers
// -----------------------------------------------------------------------------
console.log('\n--- AREA 2: Division by Zero & NaN Handling in Growth Multipliers ---');

check('ADV-2.1', 'Growth multiplier calculation with initialIncome = 0 produces "Infinity" without guard', () => {
  // App.tsx line 43: const g = (s.currentIncome / s.initialIncome).toFixed(1);
  // App.tsx line 431: ×{(selectedStudent.currentIncome / selectedStudent.initialIncome).toFixed(1)}
  const studentZeroStart = {
    fullName: 'Новичок Без Дохода',
    niche: 'Тестировщик',
    initialIncome: 0,
    currentIncome: 150000,
  };

  const multiplierRaw = (studentZeroStart.currentIncome / studentZeroStart.initialIncome).toFixed(1);
  assert.strictEqual(multiplierRaw, 'Infinity', 'Unchecked division yields string "Infinity"');

  // Check if App.tsx contains ANY guard against initialIncome <= 0
  const hasGuardInCopy = appTsxContent.includes('s.initialIncome <= 0') || appTsxContent.includes('s.initialIncome === 0') || appTsxContent.includes('!s.initialIncome ?');
  const hasGuardInJSX = appTsxContent.includes('selectedStudent.initialIncome <= 0') || appTsxContent.includes('selectedStudent.initialIncome === 0') || appTsxContent.includes('!selectedStudent.initialIncome ?');

  if (!hasGuardInCopy || !hasGuardInJSX) {
    throw new Error('BUG REPRODUCED: App.tsx lacks division-by-zero protection. When initialIncome=0, UI renders "×Infinity" and clipboard copy contains "(рост ×Infinity)"');
  }
});

check('ADV-2.2', 'Growth multiplier calculation with initialIncome = 0 and currentIncome = 0 produces "NaN"', () => {
  const studentZeroBoth = { initialIncome: 0, currentIncome: 0 };
  const multiplier = (studentZeroBoth.currentIncome / studentZeroBoth.initialIncome).toFixed(1);
  assert.strictEqual(multiplier, 'NaN', '0 / 0 produces NaN');

  const hasProtection = appTsxContent.includes('isNaN(') || appTsxContent.includes('isFinite(');
  if (!hasProtection) {
    throw new Error('BUG REPRODUCED: App.tsx lacks NaN/isFinite protection for 0/0 income ratio, rendering "×NaN"');
  }
});

// -----------------------------------------------------------------------------
// AREA 3: Empty Inbox Zero State and Recovery After Reset Button Click
// -----------------------------------------------------------------------------
console.log('\n--- AREA 3: Empty Inbox Zero State & Recovery After Reset ---');

check('ADV-3.1', 'Inbox Zero state machine transitions correctly to empty state upon full eviction', () => {
  let reports = [...initialReports];
  assert.strictEqual(reports.length, 3, 'Initial pending reports count is 3');

  // Simulate sequential approvals
  const reportIds = reports.map(r => r.id);
  for (const id of reportIds) {
    reports = reports.filter(r => r.id !== id);
  }

  assert.strictEqual(reports.length, 0, 'Reports count reaches 0');

  // Verify App.tsx renders Inbox Zero screen when reports.length === 0
  assert.ok(appTsxContent.includes("reports.length === 0 ? ("), 'App.tsx conditionally branches on reports.length === 0');
  assert.ok(appTsxContent.includes("Всё проверено"), 'Renders celebration title');
  assert.ok(appTsxContent.includes("Загрузить демо-данные"), 'Renders recovery button');
});

check('ADV-3.2', 'Reset button state recovery restores initialReports array length without object corruption', () => {
  let reports = [...initialReports];
  reports = [];
  assert.strictEqual(reports.length, 0);

  // Recovery action in App.tsx: onClick={() => setReports(initialReports)}
  reports = initialReports;
  assert.strictEqual(reports.length, 3, 'Restores exactly 3 reports');
  assert.strictEqual(reports[0].studentName, 'Милана Горохова');
  assert.strictEqual(reports[1].studentName, 'Аня Петрова');
  assert.strictEqual(reports[2].studentName, 'Карина Волкова');
});

// -----------------------------------------------------------------------------
// AREA 4: Live Search Filtering Against Edge Cases
// -----------------------------------------------------------------------------
console.log('\n--- AREA 4: Live Search Filtering Against Edge Cases ---');

check('ADV-4.1', 'Cyrillic case-insensitive search by student name matches properly', () => {
  const queryUpper = 'МИЛАНА';
  const queryLower = 'милана';
  const queryMixed = 'МиЛаНа';

  const filterFn = (q) => studentDisciplines.filter(s =>
    s.fullName.toLowerCase().includes(q.toLowerCase())
  );

  assert.strictEqual(filterFn(queryUpper).length, 1);
  assert.strictEqual(filterFn(queryLower).length, 1);
  assert.strictEqual(filterFn(queryMixed).length, 1);
  assert.strictEqual(filterFn(queryUpper)[0].id, 'st-1');
});

check('ADV-4.2', 'Special regex characters do not crash search because includes() is used instead of RegExp', () => {
  const regexSpecialQueries = ['.*', '[a-z]', '(test)', '^start$', 'a+b', 'c?d', '\\'];
  for (const q of regexSpecialQueries) {
    const res = studentDisciplines.filter(s =>
      s.fullName.toLowerCase().includes(q.toLowerCase())
    );
    assert.strictEqual(Array.isArray(res), true, `Query ${q} should return an array without crashing`);
    assert.strictEqual(res.length, 0, `Query ${q} should yield 0 matches on names`);
  }
});

check('ADV-4.3', 'App.tsx search filter omits student niche, causing niche search to return 0 results', () => {
  // App.tsx line 48-50:
  // const filtered = studentDisciplines.filter(s =>
  //   s.fullName.toLowerCase().includes(searchQuery.toLowerCase())
  // );
  const appSearchCode = appTsxContent.slice(
    appTsxContent.indexOf('const filtered = studentDisciplines.filter'),
    appTsxContent.indexOf('const tabs =')
  );

  const searchesNiche = appSearchCode.includes('s.niche');
  if (!searchesNiche) {
    // Demonstrate empirical failure: user types "стилист" or "психолог"
    const resultsForNiche = studentDisciplines.filter(s =>
      s.fullName.toLowerCase().includes('стилист')
    );
    assert.strictEqual(resultsForNiche.length, 0, 'No student has "стилист" in their full name');
    throw new Error('BUG REPRODUCED: App.tsx line 48-50 filters ONLY by s.fullName and ignores s.niche. Searching by student niche (e.g. "стилист", "психолог") returns 0 results!');
  }
});

check('ADV-4.4', 'Search query with leading/trailing whitespace causes false negative matches', () => {
  // App.tsx does NOT trim searchQuery before calling includes:
  const queryWithLeadingSpace = '  Милана';
  const matches = studentDisciplines.filter(s =>
    s.fullName.toLowerCase().includes(queryWithLeadingSpace.toLowerCase())
  );
  if (matches.length === 0) {
    throw new Error('BUG REPRODUCED: App.tsx does not call .trim() on searchQuery; leading spaces "  Милана" result in 0 matches');
  }
});

// -----------------------------------------------------------------------------
// AREA 5: Rapid Sequential Card Approvals & State Cleanup
// -----------------------------------------------------------------------------
console.log('\n--- AREA 5: Rapid Sequential Card Approvals & State Cleanup ---');

check('ADV-5.1', 'Sequential card approvals correctly evict cards and clean replies map', () => {
  let reports = [...initialReports];
  let replies = { 'rep-1': 'Отличная работа!', 'rep-2': 'Держись, завтра созвон' };

  function handleApprove(reportId) {
    reports = reports.filter(r => r.id !== reportId);
    delete replies[reportId];
  }

  handleApprove('rep-1');
  assert.strictEqual(reports.length, 2);
  assert.strictEqual(replies['rep-1'], undefined);
  assert.strictEqual(replies['rep-2'], 'Держись, завтра созвон');

  handleApprove('rep-2');
  assert.strictEqual(reports.length, 1);
  assert.strictEqual(replies['rep-2'], undefined);
});

check('ADV-5.2', 'Toast notification timer race condition: subsequent approvals prematurely dismiss previous toasts', () => {
  // App.tsx line 29-32:
  // const showToast = (msg: string) => {
  //   setToastMessage(msg);
  //   setTimeout(() => setToastMessage(null), 3000);
  // };
  // There is NO clearTimeout(timerRef.current)!
  const hasClearTimeout = appTsxContent.includes('clearTimeout');
  if (!hasClearTimeout) {
    throw new Error('BUG REPRODUCED: showToast in App.tsx sets unmanaged setTimeout without clearTimeout. Rapid sequential approvals cause earlier timers to dismiss newer toast messages prematurely');
  }
});

check('ADV-5.3', 'Non-functional state updater in reply input onChange allows race conditions under rapid typing', () => {
  // App.tsx line 261: onChange={e => setReplies({ ...replies, [r.id]: e.target.value })}
  // This uses closed-over `replies` rather than functional updater `setReplies(prev => ({ ...prev, [r.id]: e.target.value }))`
  const hasFunctionalReplyUpdate = appTsxContent.includes('setReplies(prev =>');
  const hasDirectReplySpread = appTsxContent.includes('setReplies({ ...replies');

  if (hasDirectReplySpread && !hasFunctionalReplyUpdate) {
    throw new Error('BUG REPRODUCED: App.tsx line 261 uses closed-over { ...replies } instead of functional updater setReplies(prev => ...), exposing concurrent inputs to lost update anomalies');
  }
});

// -----------------------------------------------------------------------------
// AREA 6: Discipline Rate Boundaries
// -----------------------------------------------------------------------------
console.log('\n--- AREA 6: Discipline Rate Boundaries ---');

check('ADV-6.1', 'Discipline threshold styling in App.tsx violates specification for 74.9% - 70% range', () => {
  // Spec & Test Runner requirement:
  // >= 90%: Green
  // 75% - 89.9%: Amber
  // < 75%: Red
  
  // App.tsx line 343-346:
  // s.disciplineRate >= 90 ? 'text-emerald-600'
  // : s.disciplineRate >= 70 ? 'text-amber-600'
  // : 'text-red-500'
  
  const thresholdMatch = appTsxContent.match(/s\.disciplineRate >= (\d+)\s*\?\s*['"]text-amber-600['"]/);
  assert.ok(thresholdMatch, 'Found amber threshold check in App.tsx');
  const actualAmberThreshold = parseInt(thresholdMatch[1]);

  if (actualAmberThreshold !== 75) {
    // Test exact boundary: 74.9%
    const rate74 = 74.9;
    const actualClass = rate74 >= 90 ? 'text-emerald-600' : rate74 >= 70 ? 'text-amber-600' : 'text-red-500';
    const expectedClass = rate74 >= 90 ? 'text-emerald-600' : rate74 >= 75 ? 'text-amber-600' : 'text-red-500';
    
    assert.strictEqual(actualClass, 'text-amber-600');
    assert.strictEqual(expectedClass, 'text-red-500');
    throw new Error(`BUG REPRODUCED: App.tsx line 344 checks s.disciplineRate >= 70 instead of >= 75. Students with 70.0%–74.9% discipline receive amber styling instead of red alert!`);
  }
});

check('ADV-6.2', 'Discipline rate 90.0% vs 89.9% boundary transition', () => {
  const rate90 = 90.0;
  const rate89 = 89.9;
  const class90 = rate90 >= 90 ? 'green' : 'amber';
  const class89 = rate89 >= 90 ? 'green' : 'amber';
  assert.strictEqual(class90, 'green', '90.0% is green');
  assert.strictEqual(class89, 'amber', '89.9% is amber');
});

// -----------------------------------------------------------------------------
// AREA 7: Story Text Generation Variable Injection & Formatting
// -----------------------------------------------------------------------------
console.log('\n--- AREA 7: Story Text Generation Variable Injection & Formatting ---');

check('ADV-7.1', 'Story text generator handles student with 0 initial income by generating invalid "(рост ×Infinity)"', () => {
  const student = {
    fullName: 'Елена Смирнова',
    niche: 'Психосоматика',
    initialIncome: 0,
    currentIncome: 90000,
    initialAvgTicket: 0,
    currentAvgTicket: 15000,
    totalReels: 10,
    totalMailings: 80,
    disciplineRate: 85,
  };

  const g = (student.currentIncome / student.initialIncome).toFixed(1);
  const text = `${student.fullName}, ${student.niche}\n\nТочка А: ${student.initialIncome.toLocaleString('ru-RU')} ₽\nТочка Б: ${student.currentIncome.toLocaleString('ru-RU')} ₽ (рост ×${g})\nСредний чек: ${student.initialAvgTicket.toLocaleString('ru-RU')} → ${student.currentAvgTicket.toLocaleString('ru-RU')} ₽\n${student.totalReels} рилсов, ${student.totalMailings} рассылок\nДисциплина: ${student.disciplineRate}%`;

  assert.ok(text.includes('рост ×Infinity'), 'Text contains invalid Infinity growth');
  if (text.includes('×Infinity')) {
    throw new Error('BUG REPRODUCED: Story generator injects "рост ×Infinity" when initialIncome=0');
  }
});

check('ADV-7.2', 'handleCopyCase crashes with Uncaught TypeError if navigator.clipboard is undefined', () => {
  // App.tsx line 45: navigator.clipboard.writeText(text)...
  // In environments without clipboard permissions or non-secure contexts:
  // navigator.clipboard is undefined.
  const hasClipboardGuard = appTsxContent.includes('navigator.clipboard?') || appTsxContent.includes('typeof navigator.clipboard') || appTsxContent.includes('navigator?.clipboard');

  if (!hasClipboardGuard) {
    throw new Error('BUG REPRODUCED: App.tsx directly accesses navigator.clipboard.writeText without checking if navigator.clipboard exists. Throws TypeError in headless/non-secure environments');
  }
});

check('ADV-7.3', 'Newline and format injection through student niche or name in story template', () => {
  const maliciousStudent = {
    fullName: 'Тест\nХАКЕРСКИЙ ТЕКСТ',
    niche: 'Ниша\nТочка Б: 999 999 999 ₽',
    initialIncome: 10000,
    currentIncome: 50000,
    initialAvgTicket: 1000,
    currentAvgTicket: 5000,
    totalReels: 5,
    totalMailings: 20,
    disciplineRate: 100,
  };

  const g = (maliciousStudent.currentIncome / maliciousStudent.initialIncome).toFixed(1);
  const text = `${maliciousStudent.fullName}, ${maliciousStudent.niche}\n\nТочка А: ${maliciousStudent.initialIncome.toLocaleString('ru-RU')} ₽\nТочка Б: ${maliciousStudent.currentIncome.toLocaleString('ru-RU')} ₽ (рост ×${g})\n...`;

  const lines = text.split('\n');
  assert.ok(lines.includes('ХАКЕРСКИЙ ТЕКСТ'), 'Newlines in student name are unescaped and inject arbitrary lines into story copy');
});

// -----------------------------------------------------------------------------
// SUMMARY
// -----------------------------------------------------------------------------
console.log('\n======================================================================');
console.log(` EMPIRICAL ADVERSARIAL TEST SUMMARY`);
console.log(` Total Assertions: ${total}`);
console.log(` Passed:           ${passed}`);
console.log(` Failed:           ${failed}`);
console.log('======================================================================');

if (failures.length > 0) {
  console.log('\nReproduced Failure Modes:');
  failures.forEach((f, i) => {
    console.log(`${i + 1}. [${f.testId}] ${f.name}`);
    console.log(`   -> ${f.error}\n`);
  });
}
