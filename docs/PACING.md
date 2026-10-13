# Pacing simulation and tuning (brief 9.4)

Run it any time after editing `config/gamelife.json`:

```bash
npm run simulate
```

The simulation (`backend/src/main/java/gamelife/sim/PacingSimulation.java`) plays one year with the
real rules of the game (fight formulas, streak and freezes, stat levels) for three profiles. It
pays one task a day and buys the cheaper of the next weapon or armour level as soon as it can.

| Profile | Daily base XP |
| --- | --- |
| lazy | 0 on 30% of the days (spread out, never 3 in a row), 100 on the others |
| average | 100 every day |
| strong | 200 every day |

## With the brief's default numbers

Boss HP growth x1.3 per boss, weapon +10% per level for 150 x 1.35^n coins.

| Profile | Boss 1 beaten | Boss 5 beaten | Bosses in a year | Falls | Weapon | Armour |
| --- | --- | --- | --- | --- | --- | --- |
| lazy | day 10 | day 61 | 13 | 0 | 15 | 16 |
| average | day 7 | day 35 | 19 | 0 | 20 | 21 |
| strong | day 4 | day 20 | 22 | 0 | 24 | 24 |

All three profiles progress, at different speeds. But bosses get harder faster than the player
gets stronger: by day 60, a single boss takes an average player about 12 days, and even a strong
week (double XP) needs **6 days** to clear one. The brief's target is 1 to 2 days.

## Tuned (now in config)

Three numbers changed. All other defaults are kept.

| Setting | Brief default | Tuned |
| --- | --- | --- |
| `fight.bossHpGrowth` | 1.3 | **1.12** |
| `weapon.bonusPerLevel` | 1.10 | **1.12** |
| `weapon.costGrowth` | 1.35 | **1.25** |

| Profile | Boss 1 beaten | Boss 5 beaten | Bosses in a year | Falls | Weapon | Armour |
| --- | --- | --- | --- | --- | --- | --- |
| lazy | day 10 | day 40 | 31 | 0 | 24 | 19 |
| average | day 7 | day 25 | 48 | 0 | 33 | 25 |
| strong | day 4 | day 15 | 61 | 0 | 39 | 30 |

Checks against the pacing goals of brief 9.1:

- An average day beats boss 1 in **7 days** (goal: 6 to 8).
- A fresh hero (110 HP) falls after **6 zero-XP days in a row** against boss 1 (goal: about 5).
- An average player at day 60 who has a strong week (2x XP) clears a fresh boss in **2 days** (goal: 1 to 2).
- All three profiles progress, at different speeds. None cannot progress.

## What to watch after two weeks of real use

- **Falls:** no profile falls, because its lazy days are spread out. A real lazy *stretch* of 6+ days does cause a fall.
- **Coins grow fast late in the year:** the weapon multiplies coins as well as damage. Stat XP is never affected, because it uses base XP. If the coin numbers feel silly, raise `weapon.costGrowth` back towards 1.35.
- **Boss attack:** it grows slowly (20 + 4 per boss). Late bosses will feel harmless to a geared player. Raising `fight.bossAtkPerBoss` makes laziness costlier later on.
