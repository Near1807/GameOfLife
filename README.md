# GameLife

A single-user toolbox where real daily actions (quests, focus blocks, habits, a mood check-in)
feed one append-only XP ledger. Real work produces coins and stat levels; the coins are spent in a
tower-defense game that plays itself every two hours on the server. A modern dark sci-fi
interface; the map is vector art, the hero is pixel art.

**Status: version 4.1, also as a Windows desktop app** (see "Install (Windows desktop app)"). The boss fight is replaced by a deterministic tower-defense game: 8
towers with two upgrade paths of four tiers each, three maps that open as you progress, automatic
waves with catch-up, replays, Markets that raise real earnings, stats that power the towers.
Cosmetic shop, 39 achievements, pacing simulation, content validator, PWA. See `DECISIONS.md` for
every default and assumption, `docs/DEMO.md` for what to try, `docs/PACING.md` for the simulation,
and `docs/screenshots/v4/` and `v4.1/` for how it looks.

## Install (Windows desktop app)

GameLife runs as a normal Windows program: its own window, no browser, no internet, nothing else to
install (it brings its own small Java runtime).

1. Download `GameLife-Setup-<version>.exe` from the repository's **Releases** page (or from the
   artifact of the "Windows installer" workflow run).
2. Double-click it. It installs for your Windows user only, so it **needs no administrator rights**,
   and puts GameLife in the Start menu and on the desktop. It opens GameLife when it is done.
3. To remove it: Windows Settings → Apps → GameLife → Uninstall. Your data stays (see below).

**"Windows protected your PC".** The installer is **not signed**: signing needs a paid code-signing
certificate, which this project does not have. Windows SmartScreen therefore warns about any
unsigned program it has not seen often. To install anyway: click **More info**, check that the file
name is `GameLife-Setup-<version>.exe`, then click **Run anyway**. Only do this with an installer
you downloaded from this repository's own Releases page or Actions run.

**Good to know**
- One GameLife at a time: opening it again brings the open window to the front.
- It works with the network completely off. The server inside listens on `127.0.0.1` only, on a
  free port chosen at each start: nothing on your network (or the internet) can reach it, and the
  app itself refuses every request that would leave the computer.
- Settings → **Start with Windows** (off by default) opens GameLife when you sign in, so waves missed
  while the computer was off are worked out right away. They are also worked out at every start:
  after days off, every missed attempt is computed once, in order, with the build you had then.
- If the server cannot start, a window says so and shows the path of the log file
  (`%APPDATA%\GameLife\logs\server.log`).

### Where my data is

Everything that is yours lives in **`%APPDATA%\GameLife`** (type that in the Explorer address
bar; usually `C:\Users\<you>\AppData\Roaming\GameLife`), never in the program folder:

| File or folder | What |
| --- | --- |
| `gamelife.db` | the database: the whole ledger, quests, habits |
| `gamelife.json` | the game numbers, which you may edit (restart to apply) |
| `settings.json` | the backup folder you chose |
| `backups\` | automatic backups (the default backup folder) |
| `logs\server.log` | what the server did, for bug reports |
| `electron\` | the window's own cache |

The program itself is in `%LOCALAPPDATA%\Programs\GameLife` and contains nothing of yours.
Settings → **Desktop app** shows the data folder and opens it.

### Backups

- **Automatic:** a copy of everything is written when GameLife closes and once a day while it is
  open. Each one is read back and its hash chain checked. The 14 newest are kept, plus the newest
  of each of the last 8 weeks; older ones are deleted.
- **Where:** `%APPDATA%\GameLife\backups` by default. Settings → **Automatic backups** lets you
  choose another folder (a OneDrive or USB folder is a good idea: a copy that survives the computer).
- **Status:** Settings shows the last backup, and whether each one verifies. Home shows a warning
  when the last backup is more than 3 days old.
- **Restore:** Settings → Automatic backups → **Restore** next to a backup, then tap again to
  confirm. The file's hash chain is checked first: a damaged file changes nothing. Your data from
  just before the restore is backed up first, so a restore can be undone.
- A manual export and the "choose a file" restore are still under Settings → **Backup**.

### Updating

Download and run the newer `GameLife-Setup-<version>.exe`. It replaces the program and never touches
`%APPDATA%\GameLife`: your database, backups and settings stay. Your `gamelife.json` is merged with
the new version's numbers: a number you changed keeps your value, a number you never touched takes
the new default, new settings are added. Close GameLife first (the installer asks if it is open).

### Build the installer yourself (Windows)

On a Windows 10 or 11 computer with **JDK 21** (for example Eclipse Temurin, with `JAVA_HOME` set),
**Maven 3.9+**, **Node.js 22** and Git:

```powershell
git clone <this repository> GameLife
cd GameLife
npm run setup             # frontend packages
npm run desktop:setup     # Electron and electron-builder (downloads Electron for Windows)
npm run desktop:dist      # build + icon + staging + jlink runtime + NSIS installer
```

The installer is `desktop\dist\GameLife-Setup-<version>.exe`. `npm run desktop:test` runs the
desktop shell tests (server start and stop, local-only port, data folder, offline, the jlink
runtime, and the Electron window). To try the shell without building an installer:
`npm run build`, then `npm run desktop` (it uses the `java` on your PATH and a separate
`%APPDATA%\GameLife-dev` data folder, so it never touches your real data).

**GitHub Actions** builds the same installer on `windows-latest`
(`.github/workflows/windows-installer.yml`), runs every test there, and attaches the `.exe` to a
release when a version tag is pushed (`git tag v0.4.1 && git push origin v0.4.1`). "Run workflow" in
the Actions tab builds it without a release (download it from the run's artifacts).

## Requirements (development)

| Tool | Version | Check with |
| --- | --- | --- |
| Java (JDK) | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20+ (tested with 22) | `node -v` |

## Install for development (once)

```bash
npm run setup          # = cd frontend && npm install
```

Maven downloads the backend libraries by itself on the first run.

## Run in the browser (development, or on your phone)

The backend serves the API, the built frontend and the content pictures on **one port (8080)**.

```bash
npm run build          # checks the content, builds frontend/dist and the backend jar
npm start              # starts the backend (Ctrl+C to stop)
```

- **Laptop:** open <http://localhost:8080>.
- **Phone (same Wi-Fi):** open `http://<your-laptop-ip>:8080`, then use the browser's "Add to Home screen" to install it as an app.
- **No login:** single user (brief 3.3). Use it only on your own network or through a private tunnel.
- **Data:** `data/gamelife.db`, one SQLite file; automatic backups in `data/backups` (the desktop app uses `%APPDATA%\GameLife` instead).
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
desktop/    Electron shell for Windows: starts the server, window, installer (electron-builder)
frontend/   React + TypeScript + Vite (PWA)
  src/ui/DefenseMap.tsx    the map canvas and replay playback
  src/screens/             home (map), quests, focus, habits, review (week, stats), shop, hero, settings
  src/theme.css            the free "Nebula" theme as design tokens
  public/                  PWA manifest, service worker, icons
content/    towers.json, waves.json, maps, hero, items, themes, schemas, styleguide, tools, achievements.json
config/gamelife.json       every number and formula parameter
```
