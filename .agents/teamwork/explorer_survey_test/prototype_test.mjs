import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert';

console.log('Testing prototype verification runner in explorer directory...');

const rootDir = 'c:\\Users\\Honor\\Desktop\\miranda';
const frontendDir = path.join(rootDir, 'frontend');
const docsDir = path.join(rootDir, 'docs');

// Test 1: Brand check
const filesToCheck = [
  path.join(frontendDir, 'src', 'App.tsx'),
  path.join(frontendDir, 'src', 'mockData.ts'),
  path.join(frontendDir, 'src', 'types.ts'),
  path.join(frontendDir, 'index.html'),
  path.join(docsDir, 'ARCHITECTURE_SPRING_BOOT.md'),
  path.join(docsDir, 'PITCH_PROPOSAL.md')
];

let brandViolations = 0;
for (const file of filesToCheck) {
  if (fs.existsSync(file)) {
    const content = fs.readFileSync(file, 'utf8');
    const matches = content.match(/миранда|miranda/gi);
    if (matches) {
      console.error(`Violation in ${file}: found ${matches.length} matches`);
      brandViolations += matches.length;
    }
  }
}
assert.strictEqual(brandViolations, 0, 'No brand violations allowed');
console.log('✔ Brand check passed: 0 mentions of Miranda');

// Test 2: Architecture Spec structure
const archDoc = fs.readFileSync(path.join(docsDir, 'ARCHITECTURE_SPRING_BOOT.md'), 'utf8');
assert.ok(archDoc.includes('320 бел. руб.'), 'Includes Base MVP price 320');
assert.ok(archDoc.includes('475 бел. руб.'), 'Includes Full PRO price 475');
assert.ok(archDoc.includes('TelegramBots 10.3.0'), 'Includes TelegramBots 10.3.0');
assert.ok(archDoc.includes('CREATE TABLE students'), 'Includes PostgreSQL students table');
assert.ok(archDoc.includes('/api/inbox'), 'Includes REST API /api/inbox');
console.log('✔ Architecture documentation contract passed');

console.log('All prototype assertions passed successfully!');
