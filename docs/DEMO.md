# Demo

## Version 4.1 (maps, tier 4)

1. **Tier 4:** take a tower to tier 3 of a path. The fourth pip shows "Needs Strength 10" (or the
   tower's stat) until the stat reaches that level, then it can be bought like any tier.
2. **Maps:** over the map, chips list the maps. Map 2 says "after wave 30" until then. Once open,
   tap "Move to Circuit Switchback": it turns into "Tap again: move, +N refund". Tap again: every
   tower is sold for what it cost, the bigger map appears with as many spots open as you had
   bought, and the wave count goes on.
3. **Hero:** each stat lists the towers and the tier 4s it unlocks.

## Version 4 (tower defense)

Use a throwaway database (`--gamelife.db-path=../data/test.db`, see the README).

1. **Earn:** in Quests, add `Big day` with `600` minutes and tags INT + STR, tick it: 660 coins,
   Intellect and Strength reach level 3.
2. **Build (2 taps):** on Home, tap a `+` spot, then Archer. Tap another spot: the Cannon and the
   Mage are open now (Strength 3, Intellect 3); Tesla still says "Needs Intellect 5".
3. **Upgrade (2 taps):** tap the Archer, then Sniper. Rapid now says "Locked: you chose the other
   path". The tower gets a pip.
4. **Sell (2 taps):** tap a tower, Sell, "Tap again to sell": you get back exactly what you paid.
5. **Spots:** tap a dashed LOCK spot: unlock for 150 (then 218, 315...).
6. **Run now, Watch:** tap Run now, then Watch last attempt. Enemies walk the path, towers fire,
   rings and beams show splash and chain lightning. x1, x3, x10 change the speed. The end card shows
   the same result as the server.
7. **Market:** build a Market: the "Income bonus" chip goes to x1.06. Tick a quest: the coins in
   the log are 6% more than the XP.
8. **While you were away:** restart with `--gamelife.clock-offset-hours=12`. Home says which waves
   were cleared; after a failure the status says "Stuck" until the build changes.
9. **Hero:** each stat shows what it boosts in the game and which towers it unlocks.
10. **Review:** waves cleared, furthest wave, towers built, coins earned and spent this week.
11. **Shop (tap the coins):** outfits, map skins and themes only.
12. **Content:** edit a number in `content/towers.json`, Settings → Reload content: the change is
    live; a stuck wave is tried again because the rules changed.


## Version 3 (losable fight, shop, content folder; replaced by version 4)

Use a throwaway database (`--gamelife.db-path=../data/test.db`, see the README).

1. **Home → arena:** the Bug Queen (800 HP, hits 20 a night) faces your hero. Tick a quest: a
   projectile flies, the boss flashes, the damage floats up, stat orbs fly into the stat bars.
   A quest of 100+ XP is a critical hit.
2. **Shield:** under the arena, "Shield today" fills with today's XP. Below the goal it says how
   hard the boss will hit tonight; at 100 XP you are fully shielded and heal.
3. **The morning after:** restart with `--gamelife.clock-offset-hours=24`. The arena shows
   "While you were away: the boss hit for 12, your shield blocked 8" and the hero bar drops.
4. **Defeat:** restart with `--gamelife.clock-offset-hours=192`. After a lazy week the hero falls:
   a "You fell" moment, the boss heals, and it shows "Defeated you 1×". Coins and stats stay.
5. **Victory:** a quest of 800 minutes kills the boss: "Boss defeated", +100 coins, an achievement
   toast, then the next boss's intro.
6. **Shop (tap the coins):** weapon and armour levels with their effect in words, charms (2 max),
   gear, looks and themes. Buy takes two taps. A theme restyles the whole app.
7. **Boss tab:** the boss, the campaign map from `content/enemies`, the pause button, and the
   ascension card after the last boss. Weekly review and stats are the other two tabs.
8. **Hero → Achievements:** 38 badges with progress bars.
9. **Settings → Content folder:** drop `content/enemies/boss_11.png` (64×64) and press
   "Reload content": it joins the map. Drop a wrong-size picture: a warning appears instead.
10. **Install:** on the phone, "Add to Home screen".

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
