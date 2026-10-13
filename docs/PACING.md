# Pacing simulation and tuning (v4 brief 6)

Run it any time after editing `config/gamelife.json` or `content/towers.json`:

```bash
npm run simulate       # about 5 seconds
```

The simulation (`backend/src/main/java/gamelife/sim/PacingSimulation.java`) plays one year on the
first map with the real rules: the server's `Simulator` for every wave, `GameMath` for prices,
income and stat bonuses, the streak and stat-level formulas from the config.

| Profile | Daily base XP |
| --- | --- |
| lazy | 0 on 30% of the days (spread out), 100 on the others |
| average | 100 every day |
| strong | 200 every day |

Every day: the day's XP is paid (coins = XP x income bonus) and split equally over the seven stats;
the player spends greedily; then the day's 12 automatic attempts run and stop at a failure with an
unchanged build, as on the server.

**Greedy spending:** while something is affordable, buy the option that improves the current wave
the most per coin. Each option (a tower of each type on the best empty spot, the next tier of each
tower) is tried with the real simulation on that wave. A Market (Commerce) is bought when it pays
for itself within 40 days, at most one per five open spots. A new spot is bought when no affordable
tower or tier helps and every open spot is taken. This is a short-sighted player: a person who plans
ahead (saving for a Mage, placing Banners) does better.

Columns: *First day stuck* = the first day that cleared no new wave. *Longest stall* = most days in
a row without a new wave. *Options left* = purchases still possible at day 365 (unbought tiers and
spots), so the player still has decisions.

## With the brief's numbers

Enemy HP x1.15 per wave, spots 150 x 1.45^n, Archer damage 12.

| Profile | Waves day 30 | Waves day 100 | Waves day 365 | First day stuck | Longest stall | Last purchase | Income x | Options left |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| lazy | 11 | 21 | 28 | day 3 | 113 days | day 335 | 1.39 | 1 |
| average | 16 | 24 | 28 | day 3 | 161 days | day 184 | 1.45 | 0 |
| strong | 20 | 26 | 29 | day 2 | 99 days | day 92 | 2.08 | 3 |

Two problems:

1. **Everyone ends at the same wave (28 or 29).** Enemy HP grows 15% a wave, so each wave needs
   15% more power, while a build's power grows roughly with the coins spent. Waves therefore grow
   with the *logarithm* of the coins: twice the coins buys only a few more waves. And the map is
   finite: once all 15 spots hold tier-3 towers, only stat levels still add power, and they add a
   few percent a level.
2. **The build is full too early.** A full build costs about 50,000 coins. The average player has
   bought everything by day 184 and the strong player by day 92: after that there is nothing left
   to decide, which breaks the "always something worth buying" target.

## Tuning applied

| Setting | Brief | Now | Why |
| --- | --- | --- | --- |
| `game.enemyHpGrowth` | 1.15 | **1.10** | More waves for the same power, and the gap between profiles shows for longer |
| `game.spotCostGrowth` | 1.45 | **1.80** | The last spots become long-term goals (the 15th costs about 96,000; all twelve about 217,000), so there is something to save for all year |
| Archer base `dmg` (`content/towers.json`) | 12 | **10** | The Archer was the best tower per coin by far; the greedy player built almost only Archers (see below) |

## Results with the shipped config

| Profile | Waves day 30 | Waves day 100 | Waves day 365 | First day stuck | Longest stall | Purchases | Last purchase | Income x | Spots open | Options left |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| lazy | 14 | 28 | 34 | day 3 | 111 days | 52 | day 315 | 1.39 | 11 | 1 |
| average | 20 | 30 | 38 | day 3 | 129 days | 62 | day 295 | 1.45 | 13 | 1 |
| strong | 26 | 32 | 38 | day 2 | 211 days | 65 | day 315 | 2.02 | 15 | 4 |

Builds at day 365:

- lazy: 10 Archers (8 Sniper, 2 Rapid), 1 Cannon (Heavy)
- average: 9 Archers, 3 Teslas (Overload), 1 Cannon (Heavy)
- strong: 5 Archers (Sniper), 3 Venoms (Toxin), 3 Frosts, 1 Cannon (Heavy), 3 Markets

### Against the targets

- **All profiles progress: met.** Every profile clears more waves at day 100 than at day 30, and
  more at day 365 than at day 100.
- **Nobody runs out of decisions within a year: met.** Every profile still buys after day 290 and
  still has something left to buy at day 365 (a spot to save for, or tiers).
- **Strong reaches about twice the waves of lazy: met only early.** Day 30: 26 against 14 (1.9x).
  Day 365: 38 against 34. This cannot be fixed with these numbers alone, for the two reasons above:
  waves follow the logarithm of power, and the first map has a ceiling (around wave 38 to 42 for a
  full build). The strong player reaches that ceiling around day 100 and then stalls for long
  stretches (211 days in a row), waiting for stat levels. **Options for you to choose from:**
  - ship maps 2 and 3 (the brief's "unlock after wave 30, 60, 90"): each new map is a new set of
    spots to buy and lifts the ceiling. This is the cleanest fix and is only data;
  - make enemy HP grow linearly instead of exponentially (a formula change): waves would then follow
    power almost directly, and twice the coins would mean close to twice the waves;
  - make stat bonuses stronger (`...PerLevel` in the config), so the strong player's higher levels
    keep pushing after the build is full.

## Does one tower or path dominate?

`npm run simulate` also prints this table: each tower and path at tier 3, every stat at level 6,
one copy on the best spot and three copies on the three free spots, and the furthest wave each
clears alone.

| Tower | Path | Cost | 1 tower | 3 towers |
| --- | --- | --- | --- | --- |
| Archer | Sniper | 930 | 7 | 18 |
| Archer | Rapid | 860 | 12 | 18 |
| Cannon | Heavy | 1460 | 14 | 19 |
| Cannon | Barrage | 1310 | 12 | 16 |
| Frost | Deep freeze | 930 | 3 | 5 |
| Frost | Blizzard | 1090 | 9 | 17 |
| Mage | Fire | 1470 | 16 | 24 |
| Mage | Arcane | 1470 | 9 | 17 |
| Tesla | Storm | 1790 | 9 | 19 |
| Tesla | Overload | 1750 | 17 | 25 |
| Venom | Plague | 1480 | 15 | 19 |
| Venom | Toxin | 1480 | 8 | 18 |

- **No tower beats everything.** Tesla Overload and Mage Fire go furthest, but cost the most and
  need Intellect 5 and 3. Frost Deep freeze alone is weak by design: it only slows, and pays off
  next to damage towers.
- **Per coin, the Archer led.** With damage 12, three Rapid Archers cleared wave 20 for 860 each,
  and the greedy player built 10 to 13 Archers in every profile. With damage 10 the greedy builds
  are more mixed (Tesla, Venom, Frost and Cannon appear), although the cheap Archer is still the
  first tower of every build. A smarter player who saves will use more of the expensive towers.
- **Arcane and Toxin trail their other path** (17 and 18 against 24 and 19 with three towers).
  They are kept as the prototype has them; Arcane's full pierce is aimed at the warded and boss
  waves, which this table's single-tower setup does not stress. Worth a look in the next retune.
