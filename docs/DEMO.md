# Demo

## Version 2, step 3 (weekly boss, review, stats)

1. **Home:** the Bug Queen (stage 1, 800 HP) sits under your hero. Tick any quest: the boss
   flashes and its HP bar drains by the XP you earned.
2. **Boss defeat:** follow "See a boss defeat" in the README (a single 800-minute quest).
   A full-screen "Boss defeated" plays for 1.4 s, you get +100 coins, and the boss card gets a
   "Defeated" stamp.
3. **Boss tab:** the campaign map with ten bosses: cleared (gold check), current (red glow),
   locked (silhouette).
4. **Boss → Weekly review:** XP per day, stat XP, quests, focus time, habits, mood, skipped habits.
   Use the arrows to go back a week.
5. **Boss → Stats:** 7, 30 or 90 days of XP and streak history, and stat XP per stat. Hover or
   tap a bar to read its value.

## Version 2, steps 1 and 2 (new look, stat tags, character sheet)

1. **Quests:** type a name, tap two stat icons (e.g. INT and DIS): the equal split of the base XP
   appears under them, and each duration chip shows the XP it will pay. Tap a chip to add.
2. Tick the quest: a gold "+XP" floats up with the stat amounts, the hero lunges, coins count up.
3. **Hero:** the stat you fed has a longer bar and the radar shape grew; at 100 stat XP it reaches
   level 2 and the overall level goes up. The class name follows your two highest stats.
4. **Home:** the same stat bars on the hero card, today's XP and coins with bars, the streak,
   and "Next best action" with a one-tap Done.
5. **Home → mood:** "Feeds: SPI" opens the stat picker for the check-in; the mood is still one tap.
6. **Habits:** each habit has a streak flame and a 28-day strip.
7. **Settings → Reduced effects:** every animation stops.

## Milestones 1 and 2 (daily tools and integrity)

Labels changed in v2: the mood faces are icons, "tasks" is "Quests", the rest button says "Rest",
export is "Settings → Export backup".

About 10 minutes. Start with `npm run build` then `npm start`, open <http://localhost:8080> on
the laptop and `http://<laptop-ip>:8080` on the phone. To start from zero, stop the backend and
delete `data/gamelife.db`.

## 1. Daily tools and taps (brief 3.1)

1. **Home → how are you?** Tap one face. Expect a gold `+6 XP` pop with `SPI`, the hero lunges,
   the streak shows `1 day x1.10`. Tap again: nothing more (once per day).
2. **Home → new task:** type `Study algorithms`, tap `60m`. That's name + one chip. Nothing paid.
3. **Tasks:** tap ✓ next to it. Expect `+66 XP`. Tap the green ✓ in "done today": the XP is taken
   back (log shows `undo`). Tap it again: `+66` again. Balance counts it once.
4. **Tasks → new task:** type `stu`: the autocomplete chip `+ Study algorithms 60m` appears.
5. **Habits:** type `Read 20 pages`, tap `every day`. Tap ✓: `+22 XP`. It stays ticked for today.
6. **Home → 💤 rest tomorrow:** one tap. The log shows the planned rest day.

## 2. Focus timer (brief 3.2)

1. **Focus:** tap `10m`. A countdown starts. Close the tab or lock the phone.
2. Reopen after 10 minutes: the "focus check" question appears on whatever screen opens.
   Tap **Yes**: `+11 XP` (or more with a streak). Nothing was paid when the timer ended.
3. Start another, tap **stop early** after 2 minutes, answer Yes: the screen warns it is under the
   minimum, and nothing is paid.
4. Try **Tasks → ▶** on an open task, then a length chip: the block is linked to the task, and Yes
   pays the block and marks the task done (`via focus`) without paying the task a second time.

## 3. Integrity (brief 3.2, 5)

1. **Settings → export backup file.** Open the JSON: every event with its `key`, `prevHash`, `hash`.
2. Stop the backend, tamper with the database, start again:
   `sqlite3 data/gamelife.db "UPDATE ledger_events SET xp='999.00' WHERE id=1"`
   (or any SQLite browser). Reload: a purple banner says the ledger was modified. The app still works.
3. **Settings → choose file → confirm restore** with the exported file: the banner disappears and
   the balance is back to its exported value.
4. Edit the exported file (change one `xp`) and try to restore it: refused, nothing changes.

## 4. Retuning (brief 4)

Change `"habit": { "xp": 20 }` to `50` in `config/gamelife.json`, restart, tick a habit on a new
day: it pays 50 x streak.

## What is not there yet

Character sheet, levels, achievements, weekly boss, campaign map, weekly review (milestone 3);
shop, weapon, themes, ascension, pacing script (milestone 4); final pixel art, orbs, confetti,
content validator, palette-snap tool, style guide, PWA install (milestone 5).
