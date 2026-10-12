# DECISIONS

Every assumption and default applied while building GameLife. Section numbers refer to the build
brief. Anything here can be changed; numbers are in `config/gamelife.json`, rules are in the
file named next to them.

## Contradictions and open points to check (please read these first)

1. **Comeback vs freezes (7).** The brief says that after 3+ empty days the streak "restarts at 1",
   and also that freezes and rest days keep the streak alive. With 2 rest days + 1 freeze, 3 empty
   days can be fully covered. **Applied:** the streak always follows the freeze and rest rules
   (it only restarts when nothing covered a missed day, which is the usual case). The comeback x2
   applies whenever the 3 days before today had no validated action, whether or not the streak
   survived. `rules/StreakCalculator.java`
2. **"A known repeating task takes one tap" vs "suggested tasks pay nothing until confirmed"
   (3.1, 3.2).** **Applied:** ticking a task that is on the list = 1 tap. The "again?" chips put a
   known task back on the list in 1 tap and pay nothing; ticking it is a second, separate tap.
   If you would rather have a one-tap "did it now" chip (add + tick in one tap), say so: it is a
   small change, but it means a mistaken tap on a chip pays (it can still be un-ticked).
3. **FREEZE_USED events (5, 7).** Freezes are "used automatically" on a missed day. Writing an
   event would need a background job that runs when nobody acts. **Applied:** freezes are
   computed from the ledger like the streak, and no FREEZE_USED event is written. The type stays
   in the list for later.
4. **Stat tags in milestone 2.** The ledger has a `statSplits` field, so tags and the equal split
   are already recorded now (otherwise early events would have no stat data). The character
   sheet, levels and dashboard come in milestone 3.
5. **Tests of section 14 not yet written**, because their feature belongs to a later milestone:
   "a purchase with too low a balance is refused" (milestone 4) and "the content validator
   rejects each violation" (milestone 5).
6. **Art (11, 12).** No licensed asset pack is integrated yet (milestone 5). The hero, flame and
   coin are placeholders drawn by `content/tools/make-placeholder-sprites.mjs` that already
   follow the contract: 32x32 hero, 16x16 icons, palette colors only, no semi-transparency,
   4-frame idle sheet at 6 fps.

## Ledger and data (5)

- Event keys: `task:{id}:done:{n}`, `task:{id}:undone:{n}` (n = number of earlier un-ticks),
  `timer:{id}`, `habit:{id}:{gameDay}`, `checkin:{gameDay}`, `rest:{date}`.
- A repeated key is not an error: the API answers 200 with the original event and `paid: false`.
- An extra column `game_day` is stored and hashed: the game day of an event is frozen when it is
  written, so changing the time zone or the 04:00 boundary later does not move past events.
- Hash = SHA-256(prevHash + canonical JSON). Canonical JSON = the fields id, key, type,
  occurredAt (epoch ms), gameDay, module, label, xp and currencyDelta (as 2-decimal strings),
  statSplits and meta (as their stored JSON text), with keys sorted and no spaces. The first
  prevHash is 64 zeros. `ledger/HashChain.java`
- `statSplits` and `meta` are stored as the exact JSON text that was hashed, so verification
  never depends on how a number is re-printed.
- Amounts are decimals with 2 digits, stored as text. Final XP = base x multipliers rounded
  half-up to 2 digits. The UI rounds to whole numbers.
- The chain is verified at startup (log), on every `/api/state` call (banner) and on import.
- Ids are explicit (last id + 1) because the id is part of the hash. Appends are serialized
  (`synchronized`); the UNIQUE constraint on the key is a second guard.
- A restore is the only operation that deletes ledger rows, and only after the new chain checks.
- No CORRECTION function exists yet: the type is reserved for the weekly review (milestone 3).

## Earning (6)

- Final XP = base x streak x weapon x ascension x comeback. Weapon and ascension multipliers are
  already read from the ledger (UPGRADE and ASCEND events), so they are 1 until milestone 4.
- Stat XP = base XP, split equally; leftover cents go to the first tags so the parts always sum
  to the base exactly. `rules/StatSplitter.java`
- To-do: creating a task pays nothing. Only the name is required. Missing minutes = the value last
  used for the same name (case-insensitive), else the configured `defaultMinutes` (15). The
  preselected chip for a new name is the minutes of the last task created. Missing tags = the tags
  last used with the same name, else none.
- The minute chips of the new-task form are the submit buttons, so a new task is literally its
  name plus one chip. Enter submits with the preselected chip. A small field accepts any number of
  minutes for longer tasks (no upper limit).
- Un-tick appends a TASK_UNDONE that reverses exactly the last tick (XP, currency and stat splits).
  Re-ticking pays the **base of the first tick**, not the current minutes, so editing a task never
  changes what it is worth once it has been completed. Un-tick is offered on tasks done today
  (tap the green check in "done today").
- Tasks done on an earlier day leave the list. "Remove" (✕) hides an open task and pays nothing.
- Repeat suggestions ("again?") show known task names, most used first, excluding names already on
  today's list. They pay nothing.
- Focus timer: only one timer at a time. The label is optional ("Focus" by default). Default tags:
  the last tags used with the same label, else Discipline.
- The Yes/No question is only accepted after the planned end or after "stop early". Counted
  minutes = whole minutes between the server start time and the planned end (or the stop if
  earlier), clamped between 0 and the planned length. Answering later adds nothing.
- Stopped early under 10 minutes: Yes is accepted but pays nothing (the screen says so first).
- The first answer is final: a second Yes, or a Yes after a No, pays nothing.
- A timer started on a task: Yes pays the block and marks the task done with 0 XP. A task that
  was ever paid through a validated block pays 0 if it is later un-ticked and re-ticked. A timer
  cannot be started on a task that is already done.
- Habits: default schedule every day, default tag Discipline. The Yes tap is only accepted on a
  scheduled day, for today only (the request carries no date). There is no un-tick for habits
  (the brief only describes it for tasks).
- Mood check-in: 5 levels shown as `x_x -_- o_o ^_^ *o*`, tag Spirit, the first answer of the day
  is kept.

## Streaks and rest (7)

- A day counts if it has at least one validated action (task tick, timer Yes, habit, check-in)
  that was not un-ticked afterwards.
- The streak includes today once today counts. The first action of a day is already paid with
  the multiplier of the new streak value (day 1 pays x1.1, day 4 pays x1.2), and the same
  multiplier applies to everything else that day.
- Freezes: one per 14 counted days **in total** (not only in the current streak), at most 2 stored.
- A missed day is checked in order: planned rest day, else a freeze if one is stored, else the
  streak drops to 0. Today never breaks the streak while it is still in progress.
- Comeback: needs at least one earlier counted day (the very first action is not a comeback).
  Only the first validated action of the day gets x2. If it is un-ticked, the next first action
  gets it instead, so at most one doubled action remains.
- Rest days: one tap ("rest tomorrow" on the home screen). The API accepts today up to 13 days
  ahead, at most 2 per Monday-to-Sunday game week. They pay nothing and cannot be cancelled in
  this version (the ledger is append-only and no "cancel rest" event exists yet).
- No red, no failure wording: the broken-chain banner and warnings use purple, not red.

## Time (3.2, 4)

- Time zone Europe/Brussels, day boundary 04:00 local, both in config.
- Every timestamp is taken from the server clock (`java.time.Clock` bean; tests replace it).
  The frontend countdown is only a display corrected by the server/device clock offset; when the
  block should be over, the app asks the server.
- When the app is opened or comes back to the foreground, it asks the server for a timer waiting
  for an answer and shows the question on any screen.

## Stack and hosting (4)

- Java 21, Spring Boot 3.5.16, SQLite (xerial sqlite-jdbc) with JdbcTemplate, no ORM, no
  connection pool (SQLite is a local file; busy timeout 5 s).
- React 19 + TypeScript + Vite 7. No router library: screens switch with one state variable.
  Tests: JUnit 5 + AssertJ + MockMvc; Vitest 5 + Testing Library + jsdom. Vitest 5 was chosen
  because older versions had open security advisories and Vitest 4.1.11 hit an npm install bug.
- The backend serves the built frontend from `frontend/dist`, so the phone needs one address.
- Listens on 0.0.0.0:8080 with no authentication: for the local network or a private tunnel only.
  Public exposure would need the "one fixed secret token" of section 4 (not built; ask first).
- Online-first: no offline writes. If the server is unreachable a banner says so.
- PWA install (manifest, service worker) is milestone 5.

## Interface (3.1, 11)

- The default theme is design tokens (CSS variables) in `frontend/src/theme.css`: near-black
  background, neon green for actions, cyan for info, gold only for XP and rewards, monospace font,
  optional CRT scanlines, blinking cursor, console log of events.
- Every tappable control is at least 44 px tall (52 px for main buttons).
- Feedback in this milestone: XP number pop with stat names (1.2 s, pointer-events none, never
  blocks), hero lunge, a short 8-bit sound made with Web Audio (no sound files yet). Orbs, boss
  damage and celebrations come with milestone 3 and 5.
- The streak flame grows at 7, 30, 100 and 365 days by whole-number scaling.
- Reduced effects, sound and scanlines switches are stored per device (browser localStorage),
  since they are display preferences and not game data.

## Backup (3.3, 5)

- One JSON file: format name and version, the ledger rows exactly as stored, tasks, habits,
  timers, and `ownedItems` (empty until the shop exists; owned items will be derived from
  PURCHASE events anyway).
- Import is refused if the format is unknown, the version is newer, or the chain is broken.
  It then replaces everything in one transaction.

## Configuration (4)

- `config/gamelife.json` already holds the numbers of later milestones (weapon, ascension, stages,
  stat levels, loot) so the file is complete; they are marked "Used from milestone X".
- Changing a value needs a backend restart. A test starts the app on an edited copy of the file
  and checks that payouts change (`ConfigChangeTest`).
