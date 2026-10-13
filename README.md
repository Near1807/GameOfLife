# GameLife

A single-user toolbox where real daily actions (quests, focus blocks, habits, a mood check-in)
feed one append-only XP ledger, and every XP is damage in a boss fight you can lose. A modern
dark sci-fi interface, with pixel art only for the game content.

**Status: version 3 (final scope of the v3 brief).** Losable boss fight, folder-driven content,
battle arena, shop with weapon and armour upgrades, charms, looks and themes, ascension,
38 achievements, pacing simulation, content validator, palette-snap tool, style guide and PWA
install. See `DECISIONS.md` for every default and assumption, `docs/DEMO.md` for what to try,
`docs/PACING.md` for the simulation, and `docs/screenshots/v3/` for how it looks.

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

The backend serves the API, the built frontend and the content pictures on **one port (8080)**.

```bash
npm run build          # checks the content, builds frontend/dist and the backend jar
npm start              # starts the backend (Ctrl+C to stop)
```

- **Laptop:** open <http://localhost:8080>.
- **Phone (same Wi-Fi):** open `http://<your-laptop-ip>:8080`, then use the browser's "Add to Home screen" to install it as an app.
- **No login:** single user (brief 3.3). Use it only on your own network or through a private tunnel.
- **Data:** `data/gamelife.db`, one SQLite file.
- **Numbers:** all of them live in `config/gamelife.json`. Edit the file, then restart.

### Dev mode (hot reload while editing the frontend)

```bash
npm start              # terminal 1: backend on :8080
npm run dev:frontend   # terminal 2: Vite on :5173, forwards /api and /content to :8080
```

## Test

```bash
npm test               # backend (JUnit, 98 tests) then frontend (Vitest, 30 tests)
```

Or separately: `npm run test:backend`, `npm run test:frontend`. The backend tests use their own
databases in `backend/target/test-data/` and never touch your real data.

## Pacing simulation

```bash
npm run simulate       # lazy, average and strong players over a year with the current config
```

The results and the tuning that was applied are in `docs/PACING.md`.

## How the fight works (brief 9.1)

- **Damage:** every XP you validate damages the current boss at once. Un-ticking gives the damage back. Extra damage on a kill is lost.
- **Nightly attack:** at the 04:00 reset the boss hits you for its attack × (1 − shield) × (1 − armour). The shield is that day's XP divided by the daily goal (100). A fully shielded day heals you 10 HP.
- **Full protection:** rest days, streak freezes and paused days (up to 7 per 30 days) are fully shielded.
- **Falling:** at 0 HP you fall. The boss heals to full, you get back up, and the boss shows "Defeated you N times". You keep coins, gear and stat XP (mild mode). `fight.defeatMode` in the config can be `medium` or `hard`.
- **Victory:** a kill pays 100 coins and the next boss appears at once.
- **Ascension:** after the last boss picture is beaten you can ascend (New Game+). Bosses come back stronger and you get ×1.05 XP for good.
- **Recap:** opening the app after the reset shows "While you were away".

### See a victory and a defeat quickly

Start on a throwaway database. The clock offset is a testing aid that pretends time has passed:

```bash
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments="--gamelife.db-path=../data/test.db"
```

- **Victory:** in **Quests**, type `Boss test`, type `800` in the **min** box, press Enter and tick it. You get 880 XP against the first boss's 800 HP.
- **Defeat:** stop the backend and restart it a week later:
  `... -Dspring-boot.run.arguments="--gamelife.db-path=../data/test.db --gamelife.clock-offset-hours=168"`.
  The recap shows seven nights of attacks, and the hero falls. Never use the offset on your real database.

## Adding content

Drop files into `content/`, then press **Settings → Reload content**. No code change and no
rebuild are needed. A bad file never crashes the app: it is skipped and listed under
**Settings → Content folder**.

| What | Where | Size | Name |
| --- | --- | --- | --- |
| Boss | `content/enemies/` | 64×64, or a strip of 4 frames (256×64) | `boss_NN.png`, optional `boss_NN.json` `{"name": "...", "title": "..."}` |
| Hero look | `content/hero/` | 32×32, or a 4-frame strip (128×32) | `hero_NN.png`, optional `hero_NN.json` `{"name": "..."}` |
| Region background | `content/regions/` | 320×180 (ground at y = 150) | `region_NN.png`, optional `region_NN.json` |
| Shop item | `content/items/` | 32×32 (outfits: 128×32 strip) | `NAME.png` + `NAME.json` |
| Theme | `content/themes/` | (none) | `NAME.json` (+ optional `NAME.png` preview) |
| Achievement | `content/achievements.json` | (none) | one entry per achievement |

- **Bosses** come in number order: boss 1 is `boss_01`, and so on. After the last picture they loop back as tinted "stronger variants", and Settings asks for more boss art. Boss HP and attack always come from the config, never from a file.
- **The hero look** changes every 10 overall levels (`fight.heroEveryNLevels`). The background changes every 3 bosses (`fight.regionEveryNBosses`).
- **A shop item** is a PNG and a JSON with `id`, `name`, `kind` (weapon, armor, charm, outfit, skin), `price`, and optionally:
  - `bonus` `{damagePct, maxHp, reduction}`;
  - `effect` + `value` (charms: `shieldBonus`, `shieldHeal`, `freezeCapacity`, `comebackBonus`, `goalReduction`);
  - `requires` `{stat, level}` and `description`.

  Skins carry `tokens.filter`, a CSS filter applied to the arena. See `content/schemas/item.schema.json`.
- **A theme** is a JSON with `id`, `name`, `kind: "theme"`, `price` and `tokens`, a map of CSS design tokens such as `--bg`, `--violet` or `--accent-grad`. See `content/themes/` for examples.
- **Before adding pictures,** read `content/styleguide/README.md`. The style guide holds the rules, reference sprites, the palette and a prompt template for image AIs.

The content tools:

```bash
node content/tools/palette-snap.mjs in.png content/enemies/boss_11.png   # snap to the 32 colours, check the size
node content/tools/validate-content.mjs                                  # check everything (also runs before every build)
node content/tools/make-sprites.mjs                                      # redraw the placeholder art
```

## Backup and restore

Export, any of these:

- In the app: **Settings → Export backup**.
- `npm run backup` (backend running) writes `backups/gamelife-backup-YYYY-MM-DD.json`.
- `curl -o backup.json http://localhost:8080/api/backup/export`

Restore (replaces **all** current data, only after the file's hash chain checks out):

- In the app: **Settings → Choose a backup file → Confirm restore**.
- `curl -X POST -H "Content-Type: application/json" --data-binary @backup.json http://localhost:8080/api/backup/import`

Everything is in the ledger, including purchases, equipped items, boss attacks and falls, so one
backup file restores the whole game. Copying `data/gamelife.db` while the backend is stopped
works too.

## Project layout

```
backend/    Spring Boot (Java 21), SQLite through JdbcTemplate, no ORM
  src/main/java/gamelife/
    ledger/       append-only ledger, unique keys, hash chain
    payout/       the ONLY place that pays XP, applies multipliers
    fight/        the losable boss fight: rules, replay from the ledger, nightly attacks
    content/      folder-driven content scanner, served at /content
    gear/         owned and equipped items, shop, upgrades, loot
    achievements/ data-driven achievements
    sim/          pacing simulation (npm run simulate)
    rules/        streak, freezes, stat split, levels
    tasks/ timer/ habits/ checkin/ rest/   the daily tools
    character/ review/ state/ backup/
frontend/   React + TypeScript + Vite (PWA)
  src/ui/Arena.tsx         the battle arena
  src/screens/             home, quests, focus, habits, boss (fight, review, stats), shop, hero, settings
  src/theme.css            the free "Nebula" theme as design tokens
  public/                  PWA manifest, service worker, icons
content/    enemies, hero, regions, items, themes, schemas, styleguide, tools, achievements.json
config/gamelife.json       every number and formula parameter
```
