# GameLife

A single-user toolbox where real daily actions (quests, focus blocks, habits, a mood check-in)
feed one append-only XP ledger. Real work produces coins and stat levels; the coins are spent in a
tower-defense game that plays itself every two hours on the server. A modern dark sci-fi
interface; the map is vector art, the hero is pixel art.

**Status: version 4.1.** The boss fight is replaced by a deterministic tower-defense game: 8
towers with two upgrade paths of four tiers each, three maps that open as you progress, automatic
waves with catch-up, replays, Markets that raise real earnings, stats that power the towers.
Cosmetic shop, 39 achievements, pacing simulation, content validator, PWA. See `DECISIONS.md` for
every default and assumption, `docs/DEMO.md` for what to try, `docs/PACING.md` for the simulation,
and `docs/screenshots/v4/` and `v4.1/` for how it looks.

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
npm test               # backend (JUnit) then frontend (Vitest)
```

Or separately: `npm run test:backend`, `npm run test:frontend`. The backend tests use their own
databases in `backend/target/test-data/` and never touch your real data.

## Pacing simulation

```bash
npm run simulate       # lazy, average and strong players play the map for a year with the current config
```

The results and the tuning that was applied are in `docs/PACING.md`.

## How the game works (v4)

- **Real work pays coins.** Every validated action pays XP as before. The coins it pays are
  XP x your income bonus (Markets on the map and your Social level). Nothing in the game ever
  creates coins or XP.
- **The maps.** Each map is a path with tower spots: 3 open, the others bought for 150, 195,
  254... coins (permanent). Tap a spot to build, tap a tower to upgrade or sell. Map 2 (22 spots)
  opens at wave 30 and map 3 (30 spots) at wave 60: moving there sells your towers for a full
  refund, the wave count carries on, and as many spots are open as you had bought.
- **Towers.** Archer, Cannon, Frost, Mage, Tesla, Venom, Banner (buffs towers in range) and
  Market (no attack: +coins on real actions, or cheaper towers with the Bargain path). Each has two
  paths of four tiers; the first upgrade picks a path for good, and tier 4 needs a stat at level 10
  (Tesla: Intellect 12). Selling refunds everything you paid for that tower.
- **Stats power the towers.** Strength: physical damage. Intellect: magic damage. Discipline:
  fire rate. Spirit: slows. Vitality: lives. Social: income. Creativity: cheaper towers. Some
  towers need a stat level (the Hero screen lists them).
- **Waves run alone.** Every 2 hours the server plays the current wave with your build. A win
  moves to the next wave. A loss costs nothing; the wave is tried again only once something that
  matters changed (otherwise it is "stuck"). At most 16 attempts per game day. "Run now" is an
  extra attempt under the same rules. Missed attempts are worked out when the app opens.
- **Watch.** The server sends a replay of any attempt; the app plays it at x1, x3 or x10.

### See a few waves quickly

Start on a throwaway database. The clock offset is a testing aid that pretends time has passed:

```bash
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments="--gamelife.db-path=../data/test.db"
```

- In **Quests**, type `Big day`, type `600` in the **min** box, press Enter and tick it (660 coins).
- On **Home**, tap the `+` spots and build an Archer, then a Mage or a Market. Tap **Run now**:
  wave 1 plays, then press **Watch last attempt**.
- Stop the backend and restart it 12 hours later:
  `... -Dspring-boot.run.arguments="--gamelife.db-path=../data/test.db --gamelife.clock-offset-hours=12"`.
  Home says which waves were cleared while it was closed, and after a failure the map is "stuck"
  until you change the build. Never use the offset on your real database.

## Adding content

Drop files into `content/`, then press **Settings → Reload content**. No code change and no
rebuild are needed. A bad file never crashes the app: it is skipped and listed under
**Settings → Content folder**.

| What | Where | Size | Name |
| --- | --- | --- | --- |
| Tower | `content/towers.json` | (none) | one entry: id, name, kind, role, cost, requires, base stats, 2 paths x 3 or 4 tiers (a tier may have its own `requires`) |
| Enemy and waves | `content/waves.json` | (none) | enemy types and wave groups |
| Map | `content/maps/` | (none) | `map_NN.json`: path points, base, spots (in opening order), free spots, `unlockAfterWave` |
| Hero look | `content/hero/` | 32×32, or a 4-frame strip (128×32) | `hero_NN.png`, optional `hero_NN.json` `{"name": "..."}` |
| Shop look | `content/items/` | 32×32 (outfits: 128×32 strip) | `NAME.png` + `NAME.json` |
| Theme | `content/themes/` | (none) | `NAME.json` (+ optional `NAME.png` preview) |
| Achievement | `content/achievements.json` | (none) | one entry per achievement |

- **A tower** tier changes stats with `{"dmg": {"mul": 1.5}, "range": {"add": 25}, "pierce": {"set": 0.5}}`.
  The stat names are those of the prototype (dmg, rate, range, rad, slow, slowT, burn, pierce,
  multi, chain, pois, spread, pslow, rbuff, dbuff, coin, disc). `role` is `attack`, `support`
  (a Banner aura) or `income` (a Market). See `content/schemas/tower.schema.json`. A broken game
  file on reload keeps the old data and shows a warning.
- **The hero look** changes every 10 overall levels (`character.heroEveryNLevels`).
- **A shop item** is a PNG and a JSON with `id`, `name`, `kind` (`outfit` or `skin`), `price`,
  optional `requires` `{stat, level}` and `description`. Skins carry `tokens.filter`, a CSS filter
  applied to the map. Looks only.
- **A theme** is a JSON with `id`, `name`, `kind: "theme"`, `price` and `tokens`, a map of CSS design tokens such as `--bg`, `--violet` or `--accent-grad`. See `content/themes/` for examples.
- **Before adding pictures,** read `content/styleguide/README.md`. The style guide holds the rules, reference sprites, the palette and a prompt template for image AIs.

The content tools:

```bash
node content/tools/palette-snap.mjs in.png content/hero/hero_04.png      # snap to the 32 colours, check the size
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

Everything is in the ledger, including purchases, towers, spots and every wave attempt, so one
backup file restores the whole game. Copying `data/gamelife.db` while the backend is stopped
works too.

## Project layout

```
backend/    Spring Boot (Java 21), SQLite through JdbcTemplate, no ORM
  src/main/java/gamelife/
    ledger/       append-only ledger, unique keys, hash chain
    payout/       the ONLY place that pays XP, applies multipliers
    game/         tower defense: data, deterministic simulation, build from the ledger, waves
    content/      folder-driven content scanner, served at /content
    gear/         owned and equipped looks, cosmetic shop, loot
    achievements/ data-driven achievements
    sim/          pacing simulation (npm run simulate)
    rules/        streak, freezes, stat split, levels
    tasks/ timer/ habits/ checkin/ rest/   the daily tools
    character/ review/ state/ backup/
frontend/   React + TypeScript + Vite (PWA)
  src/ui/DefenseMap.tsx    the map canvas and replay playback
  src/screens/             home (map), quests, focus, habits, review (week, stats), shop, hero, settings
  src/theme.css            the free "Nebula" theme as design tokens
  public/                  PWA manifest, service worker, icons
content/    towers.json, waves.json, maps, hero, items, themes, schemas, styleguide, tools, achievements.json
config/gamelife.json       every number and formula parameter
```
