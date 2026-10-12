// Downloads a full backup from the running backend into backups/gamelife-backup-YYYY-MM-DD.json
// Usage (from the repo root):  npm run backup        or   node scripts/backup.mjs http://host:8080
// No dependencies: uses the fetch built into Node 18+.
import fs from 'node:fs';
import path from 'node:path';

const server = process.argv[2] ?? 'http://localhost:8080';
const res = await fetch(`${server}/api/backup/export`);
if (!res.ok) {
  console.error(`Backup failed: HTTP ${res.status}. Is the backend running at ${server}?`);
  process.exit(1);
}
const body = await res.text();
const events = JSON.parse(body).events.length;
const file = path.join('backups', `gamelife-backup-${new Date().toISOString().slice(0, 10)}.json`);
fs.mkdirSync('backups', { recursive: true });
fs.writeFileSync(file, body);
console.log(`Saved ${events} ledger events to ${file}`);
