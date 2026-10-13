# DECISIONS

Every assumption and default applied while building GameLife. Section numbers refer to the build
brief; "v2" refers to the version 2 upgrade request, which wins where the two disagree. Numbers
are in `config/gamelife.json`, rules are in the file named next to them.

## Version 3 (final scope): what was applied

The v3 brief replaces the earlier ones. Its "Required" rules came first, then its "Default" rules.
Everything below is configurable in `config/gamelife.json` or the content folder unless noted.

### Contradictions and tuning (please read)

1. **Pacing defaults vs pacing goals (9.1, 9.4).** With the brief's numbers (boss HP x1.3 per boss,
   weapon +10% for 150 x 1.35^n), all three profiles progress, but a strong week cannot clear a
   boss in 1 to 2 days: it takes 6 days at day 60. As 9.4 asks, I tuned three numbers:
   `fight.bossHpGrowth` 1.3 → **1.12**, `weapon.bonusPerLevel` 1.10 → **1.12**,
   `weapon.costGrowth` 1.35 → **1.25**. Full results are in `docs/PACING.md`.
2. **"Strong" in the simulation.** A player who is *always* strong reaches a balance where each boss
   takes 2 or more days, because boss HP and income both grow exponentially. I read the goal "a
   strong week with a good weapon clears a boss in 1-2 days" as an average player at day 60 who
   doubles their effort for a week. That is what the simulation and its test measure.
3. **Lazy profile.** "30% of days with 0 XP" leaves the other days unspecified. I used the average
   100 base XP on them, with the zero days spread out (never three in a row).
4. **"Equipped weapon, armour and charm appear on the hero" (11).** Item pictures are 32x32 icons.
   They are shown as small framed badges at the hero's feet in the arena, not drawn as layers onto
   the sprite, because owner-made icons cannot be aligned with the hero. Outfits do replace the
   hero picture.
5. **Weapon and armour items vs levels (9.2, 9.3, 10).** The weapon and armour *levels* are the
   economy upgrades of 9.2 and 9.3. Weapon and armour *items* in the registry are extra gear with
   a `bonus` (damage %, max HP, reduction), equipped one per slot.
6. **Skin.** The brief never defines it. A skin is an arena backdrop filter (`tokens.filter`),
   cosmetic only.
7. **Pause and the streak.** The brief says a pause shields the fight. I also let paused days keep
   the streak, like rest days: illness should not break it.
8. **Stage 7 HP in the old table** no longer matters: the ladder now uses the tuned growth.

### The fight (9.1)

- **Derived state:** everything comes from the ledger (`fight/FightReplay.java`): boss HP, hero
  HP (kept as "missing HP", so a higher max HP raises current HP too), kills, falls and defeat
  counters. New events: `BOSS_ATTACK`, `HERO_FELL`, `PAUSE`, `EQUIP`.
- **When it syncs:** before every read of the game state and before and after every payment. It
  writes one `BOSS_ATTACK` per past game day not yet attacked, in date order, with the key
  `boss-attack:{ascensions-boss}:{date}`. The attack's numbers (ATK, shield, reduction, damage,
  heal) are stored in its meta, so changing the config or gear later never rewrites the past.
- **First attack:** the boss attacks from the first day with any event (planned rest or pause days
  alone do not start the game).
- **Shield:** min(1, day XP / goal + charm bonus). The day's XP includes un-ticks. Frozen, rest
  and paused days give shield 1. A shield of 1 heals `fullShieldHeal` (10) plus any charm heal.
- **Overkill:** discarded; the next boss appears at full HP the same moment, and the hero starts it
  at full HP.
- **Un-tick:** gives back the damage only if the tick hit the boss still being fought. A kill is
  never undone (the ledger is append-only).
- **Defeat modes:** `mild` (default, nothing lost), `medium` (the next kill of that boss pays 0
  coins), `hard` (a fall costs floor(20%) of the coins). Stat XP is never taken.
- **Kill reward:** 100 coins (`fight.killRewardCoins`), no XP, so a kill cannot inflate stats or
  damage.
- **Pause:** one tap for today (or a coming day), at most 7 within any 30-day window, in Settings
  and on the Boss screen.
- **Ascension:** offered once kills in this cycle reach the number of boss pictures. It needs a
  second tap. Bosses come back with HP and ATK x (1 + 0.1 x ascensions) (`fight.ascensionBossScale`).
  The weapon level goes back to 0; armour, items, stats and achievements stay; XP gets x1.05 per
  ascension.
- **Without ascending,** the ladder continues past the last picture with tinted variants of the
  pictures (boss numbers keep growing), and Settings asks for more boss art.
- **Hero max HP** = 100 + 10 x Vitality level + 20 x armour level + item HP. Armour reduction =
  min(0.6, 0.05 x armour level + item reduction).
- **The weekly review** is a calendar-week report: XP per day and per stat, damage dealt (validated
  XP), damage taken and blocked (attacks for days of that week), falls, bosses beaten, skipped
  habits, streak, corrections.
- **Testing aid:** `gamelife.clock-offset-hours` shifts the server clock (default 0), to see the
  morning after without waiting. It is meant for a throwaway database only.

### Folder-driven content (12b)

- `ContentService` scans `content/enemies`, `hero`, `regions`, `items` and `themes` at startup and
  on **Reload content**. The backend serves `/content/**` straight from disk with no caching, so
  no rebuild is needed.
- **Sizes:** strips of 4 frames are accepted for bosses, hero looks and outfits; a single frame
  works too. Sorting is by number, and `boss_3` and `boss_03` count as a duplicate (the first name
  wins, the other is a warning).
- **Warnings** (Settings → Content folder): wrong size, wrong name or format, unreadable PNG,
  duplicate number or id, shop picture without JSON, JSON without picture, invalid JSON, unknown
  kind, themes without tokens. A bad file is skipped and never crashes the app.
- **Placeholder art** is drawn by `content/tools/make-sprites.mjs`: 3 hero looks (Initiate,
  Netrunner, Archmage), 10 bosses, 4 regions (Neon City, Data Forest, Frozen Server Farm, Ember
  Wastes), 13 items and 4 themes. They are original, not a licensed pack.

### Shop, gear, themes, loot (9.2, 9.3, 10)

- **Prices:** charms 300 to 500; weapon and armour items 300 to 500; outfits 800 and 2,000; skins
  300 and 800; themes 300, 800 and 2,000. The default theme "Nebula" is free. The default hero look
  is the free starter outfit.
- **Spending** always takes two taps in the UI, and the API refuses a purchase without
  `confirm: true` or with too few coins. Keys: `purchase:{itemId}` (once per item) and
  `upgrade:{slot}:{cycle}:{level}`, so a double tap pays once.
- **A new purchase is worn at once;** one tap takes it off. At most 2 charms (`shop.maxCharms`).
  Equipping is a ledger event (`EQUIP`, no coins), so backups keep the loadout.
- **Forecasts** use the average final XP of the last 14 days (`shop.forecastDays`), or the daily
  goal when there is no history yet: "+12% damage: boss now takes about 14 days instead of 16" and
  "you survive about 5 lazy days in a row instead of 4".
- **Stat locks:** a locked item stays visible with its requirement ("needs Vitality level 3, you
  have 1").
- **Themes** are JSON design tokens applied to the whole app. One default token is kept for Nebula
  so the schema stays uniform.
- **Loot:** about 5% of validations (`loot.dropRate`) drop an outfit or skin you do not own. It is
  decided by a hash of the event key, so it is deterministic and a retry gives the same result.
  Cosmetic only: no coins, no XP.

### Battle arena and feedback (11)

- **Layout:** the arena is the first thing on Home. The region background is shown at x1 on phones
  and x2 from 640 px wide, repeated sideways. A 64 px strip above the scene holds the health
  panels, so they never cover a fighter. Hero and boss are drawn at x2 / x4 of their pixel size.
- **A payout** sends a projectile, flashes the boss, floats the damage number up and drains the
  bar. A payout of 100 XP or more is a "Critical": bigger number, gold flash.
- **Stat orbs** fly from the arena into the stat bars on Home (skipped with reduced effects).
- **Recap:** the attacks newer than the last one this device has seen (remembered in the browser).
  The hero bar animates from the HP before the night. The very first visit on a device shows no
  recap of old history.
- **Moments:** victory, defeat and boss intro are full-screen for 1.5 s each, queued one after the
  other. They never block a tap (pointer-events: none).
- **Sound is off by default** (11). Reduced effects removes all motion.

### Achievements (8)

- 38 entries in `content/achievements.json`, read on every request (no restart). Each rule is
  `{fact, min}` (`statLevel` also needs `stat`); 23 facts are computed from the ledger.
- Nothing is stored: an un-tick can re-lock "First Quest", while kills are never undone. A toast
  shows each new unlock once per device.

### Content tools and PWA (12, 4)

- **`validate-content.mjs`** runs before every frontend build (`prebuild`). It understands the
  subset of JSON Schema that `content/schemas/*.schema.json` uses, and decodes PNGs itself
  (8-bit grey, RGB, RGBA and palette PNGs).
- **`palette-snap.mjs`** maps pixels to the nearest palette colour by weighted RGB distance. Pixels
  below 50% alpha become transparent, the rest opaque. It does not resize: it reports wrong sizes,
  because scaling would blur pixel art.
- **Style guide:** `content/styleguide/` holds the rules, reference sprites, a palette image and a
  prompt template.
- **PWA:** manifest, icons made from the hero, and a network-first service worker. It never caches
  `/api`; only the app shell is a fallback. There are no offline writes, and it is registered in
  production builds only.
- **New dev dependency:** `@types/node`, so the test of the content tools can be type-checked.

### Not done or partly done

- The old v2 weekly-stage code and its tests were removed. The weekly review remains, as a
  calendar-week report.
- A purchase cannot be refunded (no "sell" in the brief).
- Item pictures are not drawn onto the hero sprite (see contradiction 4).
- The hero has only an idle animation. The attack, hit and victory sheets of the contract are
  played as CSS motion on the idle sprite, because placeholder sheets for them were not drawn.

## Version 2, step 3 (weekly boss, review, stats dashboard, new art)

> The weekly stage below was **replaced in v3** by the losable fight. The rest still applies.

Owner decisions applied: keep the level formula; keep the stricter "Balanced" rule; the daily goal
is config (`home.dailyXpGoal`, 100); hero and boss art moved from step 5 into step 3.

### Weekly boss (brief 9.1, v2 4.4)
- Rules in `stage/StageRules.java`, pure and computed from the ledger. A game week runs Monday
  04:00 to Monday 04:00. Damage = the final XP of the week (un-ticks count negative).
- Stage fought this week = 1 + bosses killed in earlier weeks since the last ascension. A week
  without a kill therefore replays the same stage, and because damage only counts the current week,
  the boss is back to full HP. No other penalty.
- Boss HP = 800 x 1.3^(k-1), rounded to whole HP. **Brief table mismatch:** stage 7 is 3,861 with
  the formula (800 x 1.3^6 = 3,861.45); the brief's table says 3,862. All other stages match. The
  formula was kept, as for the level curve.
- The kill happens at once, inside the payment that crosses the HP: a STAGE_CLEARED event with key
  `stage:{ascensions}:{weekStart}` (so once per week and cycle), XP 0, and a reward of
  `stages.clearRewardCoins` = **100 coins** (configurable; set it to 0 for no reward). It is a
  direct consequence of the owner's own confirmed act and cannot repeat, so it respects 3.2. It
  gives no XP, so it is neither damage nor stat XP.
- **Un-tick after a kill:** the damage goes back down, but the kill stays (the ledger is
  append-only). Accepted: the owner does not cheat himself, and estimates are trusted (brief 6).
- After stage 10 is cleared there is no boss until the owner ascends. Ascension (New Game+) needs a
  second tap on the Campaign screen. It writes ASCEND: stage back to 1, weapon level back to 0 (both
  computed from the ledger), and every later payment gets x1.05 per ascension. Stats are kept.
  An ascension in the middle of a week starts the new cycle at once, with zero damage.
- Bosses are data: `content/bosses.json` (name, flavour line, sprite). Ten bosses: Bug Queen,
  Doomscroll Wraith, Null Pointer, Merge Conflict, Burnout, Kernel Panic, Infinite Loop,
  Distraction Phantom, Overclock, Entropy.

### Feedback
- Home shows the boss card (sprite, stage, HP bar, time left in the week). When damage goes up,
  the sprite flashes and shakes and the bar drains.
- A kill shows a full-screen "Boss defeated" for 1.4 s with sparks and the coin reward. It never
  blocks a tap (pointer-events: none). It only appears when the kill happens during the session,
  not when the app is opened on an already-dead boss. The XP pop is held back while it shows.

### Campaign screen ("Boss" tab)
- Six tabs on the phone now: Home, Quests, Focus, Habits, Boss, Hero.
- Boss: the boss card, the campaign map (ten nodes: cleared, current, locked as a silhouette), the
  ascension card when ready, and the rules.
- Weekly review: week navigation, the stage result (defeated, in progress, or survived and
  replays), XP per day with the goal line, stat XP per stat, quests, focus time, habits, average
  mood, streak at the end of the week, skipped habits (scheduled days before today, after creation,
  not rest days), and CORRECTION events (none can be written yet; the list is ready).
- Stats dashboard: last 7, 30 or 90 days; XP of the period, best streak ever, focus hours, bosses
  defeated, XP per day, streak history, and stat XP per stat.

### Charts and colours
- Hand-made SVG charts (no library), one series each with the card title naming it, hover or tap
  for exact values, and dashed goal lines.
- The seven stat colours were re-picked and checked with a palette validator for dark mode:
  lightness band, contrast, and colour-blind separation. One pair (blue Intellect next to fuchsia
  Creativity) sits in the "allowed with a second cue" band. Every stat always shows its icon and
  name too, so colour is never the only cue. New colours: Strength red, Creativity fuchsia,
  Intellect blue, Discipline orange, Social green, Vitality violet, Spirit teal.

### Pixel art (brief 11, 12; v2 4.9)
- `content/tools/make-sprites.mjs` (no dependencies; `npm run sprites`) draws every sprite into
  a grid of palette indices, so an off-palette colour cannot happen. It uses shapes, mirroring,
  top-left shading and an automatic 1-pixel outline.
- Hero (32x32, 4-frame idle at 6 fps): a hooded hacker-mage in an indigo robe, with a glowing cyan
  visor, a chest rune, a gold belt and a wooden staff topped by a crystal orb. The orb sparkles and
  the body bobs.
- Bosses (64x64, 4-frame idle): three body designs (cyber beetle, hooded wraith, mechanical eye),
  recoloured with palette ramps into ten bosses. They bob and their accents pulse.
- These are my own designs, not a licensed pack. I think they are clean enough to ship. If you
  want a different look later, replace the PNG files with the same names and sizes.

## Version 2, steps 1 and 2

### Design system (v2 section 3)
- Free default theme "Nebula" in `frontend/src/theme.css`, all as CSS tokens: near-black with soft
  violet/cyan glows, translucent glass cards with blur, thin glowing borders, cyan + violet accents,
  gold only for XP and coins, one colour token per stat. A shop theme will override tokens only.
- Fonts: Inter (text), Space Grotesk (headings, big numbers), JetBrains Mono (only for small
  numbers such as XP previews). Bundled with `@fontsource-variable/*` so the app works without
  internet access (no Google Fonts call from the phone).
- Icons: Lucide (`lucide-react` 1.45.0), one set everywhere. Stat icons: Strength dumbbell,
  Creativity palette, Intellect brain, Discipline target, Social users, Vitality heart-pulse,
  Spirit sparkles (`frontend/src/stats.ts`). Stat colours were changed in step 3 (see above).
- Removed: terminal headings, ASCII mood faces, blinking cursor, scanlines (and its setting),
  console log (replaced by a "Recent activity" feed), monospace body text.
- Layout: bottom floating tab bar on phones, side rail from 900 px wide, two-column grids on
  laptops. Tabs: Home, Quests, Focus, Habits, Hero; Settings sits behind the gear in the top bar.
- Motion: animated bar fills (from the previous value), count-up numbers, page fade-in, press
  scale, check pop, flame flicker, hero lunge on payout, floating "+XP" with its stat split.
  All of it is turned off by the reduced-effects switch and by the system "reduce motion" setting.
- Pixel art only as framed artwork (hero portrait), scaled by whole numbers, never smoothed.
  The hero and boss art was redone in step 3 (see above).

### Stat tags (v2 4.1)
- One component, `StatTagPicker`, used by quests, focus, habits and the check-in: seven coloured
  icon toggles, 0 to 7 selected, and a live preview of the equal split of the base XP
  ("No stat tag: earns coins only" when empty). The preview uses the same cent rule as the
  backend, and a test checks they agree.
- Duration chips show the final XP they will pay (base x current multipliers).
- Check-in tags: the API now accepts tags. Default = the tags of the last check-in, else Spirit.
  On the home screen, "Feeds: SPI" opens the picker; the mood tap stays a single tap.
- Habit tags are chosen at creation (default Discipline). Editing the tags of an existing habit
  is not offered yet.

### Character sheet (v2 4.2, brief 8)
- Level formula exactly as in the brief: L to L+1 needs 100 x 1.15^(L-1), every stat starts at 1,
  overall level = sum of the seven levels - 6. `rules/LevelCurve.java`.
- **Brief inconsistency:** the brief says 30 stat XP a day reaches level 10 in about 68 days and
  level 20 in about 340. With the formula above it is about 56 and 294 days; the brief's numbers
  correspond to counting from level 0. The formula was kept (it is the rule), and the sanity
  numbers in the test use the formula's values.
- Class = the two stats with the most XP (ties: config order), named from
  `content/character.json` (21 pair names, e.g. Intellect + Discipline = "Code Monk"). With fewer
  than two stats trained: "Unawakened". Rank from the overall level (Novice 1, Apprentice 8,
  Adept 20, Expert 40, Master 70, Legend 110).
- "Balanced": every stat at least level 5 (config `balancedMinLevel`) **and** highest minus
  lowest at most 5. The brief only states the second condition, but then every new character
  would be "Balanced" on day one. Balanced replaces the class with "Polymath". Cosmetic only.
- The radar uses level + progress inside the level, so small gains move the shape. Its scale is
  the highest level rounded up to a multiple of 5.
- Gear slots are shown (weapon level and multiplier, armour and charm empty) until the shop
  exists in step 4.

### Home dashboard (v2 4.3)
- Hero card (portrait, overall level, class, rank, seven stat bars), today's XP against a daily
  goal (config `home.dailyXpGoal` = 100, from brief 6), coins against the cost of the next weapon
  upgrade (150 x 1.35^n, the first real thing to buy), streak with multiplier, freezes, next
  milestone and the "Rest" button, next best action, mood check-in, quick-add, recent activity.
- Next best action (`frontend/src/nextAction.ts`): check-in if not done; else a habit scheduled
  today and not done; else the open quest that trains the lowest stat (shortest first); else a
  focus block. Its "Done" button is the explicit confirmation of that one item, like ticking it.

### Habits screen
- Per-habit streak (consecutive scheduled days done; unscheduled days and planned rest days are
  skipped; today does not break it while open). Display only: the XP multiplier still uses the
  global streak of brief 7.
- 28-day heat strip per habit: done (stat colour), missed (thin grey outline, never red), off,
  today. Global streak, freezes and planned rest days shown at the top.

## Version 1 decisions (still valid unless replaced above)

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
6. **Art (11, 12).** No licensed asset pack: the hero and the ten bosses are original sprites
   drawn by `content/tools/make-sprites.mjs` (see version 2, step 3). The old
   `make-placeholder-sprites.mjs` only still draws the flame and coin icons, which the interface
   no longer uses; they stay as contract examples.

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

- Replaced by the version 2 design system above. Still true: every tappable control is at least
  44 px, animations last under 1.5 s and never block the next tap, the reduced-effects and sound
  switches are stored per device (browser localStorage), and the sound is a short chime made with
  Web Audio (no sound files yet).

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
