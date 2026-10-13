# GameLife

A single-user toolbox where real daily actions (tasks, focus blocks, habits, a mood check-in)
feed one append-only XP ledger, styled as a pixel-art game in a black terminal look.

**Status: version 2, steps 1 and 2 of 5** (see the upgrade request): a new modern sci-fi design
system on every screen, stat tags on every action, the character sheet and stats on the home
screen. Next: weekly boss and review (step 3), shop and achievements (step 4), juice and new hero
art (step 5). See `DECISIONS.md` for every assumption made, `docs/DEMO.md` for what to try and
`docs/screenshots/v2/` for how it looks.

## Requirements

| Tool | Version | Check with |
| --- | --- | --- |
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20+ (tested with 22) | `node -v` |

## Install (once)

```bash
npm run setup          # = cd frontend && npm install
```

Maven downloads the backend libraries by itself on the first run.

## Run

The backend serves both the API and the built frontend on **one port (8080)**, so the phone
needs only one address.

```bash
npm run build          # builds frontend/dist and the backend jar
npm start              # starts the backend (Ctrl+C to stop)
```

- Laptop: open <http://localhost:8080>
- Phone (same Wi-Fi): open `http://<your-laptop-ip>:8080`. Find the IP with `ipconfig` (Windows)
  or `ip addr` / `ifconfig` (Linux, macOS). If it does not load, allow port 8080 in the firewall.

There is no login (single user, brief 3.3). Only use it on your own network or through a
private tunnel; do not expose port 8080 to the internet.

Data lives in `data/gamelife.db` (one SQLite file). All game numbers live in
`config/gamelife.json`; edit it and restart the backend to retune.

### Dev mode (hot reload while editing the frontend)

```bash
npm start              # terminal 1: backend on :8080
npm run dev:frontend   # terminal 2: Vite on :5173, forwards /api to :8080
```

Open <http://localhost:5173> (or `http://<laptop-ip>:5173` on the phone).

## Test

```bash
npm test               # backend (JUnit, 65 tests) then frontend (Vitest, 14 tests)
```

Or separately: `npm run test:backend`, `npm run test:frontend`. The backend tests use their own
database in `backend/target/test-data/` and never touch your real data.

## Backup and restore

Export, any of these three:

- In the app: **settings → export backup file**
- `npm run backup` (backend must be running) writes `backups/gamelife-backup-YYYY-MM-DD.json`
- `curl -o backup.json http://localhost:8080/api/backup/export`

Restore (replaces **all** current data, only after the file's hash chain checks out):

- In the app: **settings → choose the file → confirm restore**
- `curl -X POST -H "Content-Type: application/json" --data-binary @backup.json http://localhost:8080/api/backup/import`

Copying `data/gamelife.db` while the backend is stopped is also a complete backup.

## Project layout

```
backend/    Spring Boot (Java 21), SQLite through JdbcTemplate, no ORM
  src/main/java/gamelife/
    ledger/   append-only ledger, unique keys, hash chain       (brief 5)
    payout/   the ONLY place that pays XP, applies multipliers  (brief 6)
    rules/    pure rules: streak, freezes, comeback, stat split, levels (brief 7, 8)
    character/ character sheet: levels, class, rank             (brief 8)
    tasks/ timer/ habits/ checkin/ rest/   the daily tools      (brief 6, 7)
    backup/   export / import                                   (brief 3.3)
    state/    read-only summaries for the home screen
  src/main/resources/schema.sql   all tables
frontend/   React + TypeScript + Vite
  src/api.ts        every call to the backend
  src/screens/      home, quests, focus, habits, hero (character sheet), settings
  src/ui/           design-system components: card, stat tag picker, bars, count-up,
                    ring, radar chart, hero portrait, mood picker, XP pops, timer question
  src/stats.ts      icon and colour of each stat, split preview
  src/nextAction.ts the "next best action" rule of the home screen
  src/theme.css     the free "Nebula" theme as design tokens
  src/test/         friction (tap counting), tag picker and logic tests
config/gamelife.json       every number and formula parameter
content/palette.json       the 32-color palette (brief 12)
content/character.json     class names (two highest stats) and rank names
content/sprites/           placeholder pixel art (served at the site root)
content/tools/             make-placeholder-sprites.mjs (npm run sprites)
scripts/backup.mjs         command-line backup
```

## How XP integrity works (brief 3.2), in one paragraph

Every payment is an event in `ledger_events` with a unique `event_key` such as
`task:12:done:0`, `timer:4`, `habit:3:2026-03-02` or `checkin:2026-03-02`. Writing the same key
twice returns the first event and pays nothing, so double taps and retries are harmless. Rows are
only inserted, never updated, and each row's hash covers the previous row's hash, so editing any
row shows a warning banner. Balance, stat XP and the streak are recomputed from the ledger on
every request; there is no stored total and no "add points" endpoint. Times come only from the
server clock, and the client can never send one.
