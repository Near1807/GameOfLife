# Pacing simulation and tuning

Run it any time after editing `config/gamelife.json` or `content/`:

```bash
npm run simulate       # about 30 seconds
```

The simulation (`backend/src/main/java/gamelife/sim/PacingSimulation.java`) plays one year with the
real rules: the server's `Simulator` for every wave, `GameMath` for prices, income and stat
bonuses, the streak and stat-level formulas from the config, and the three maps.

| Profile | Daily base XP |
| --- | --- |
| lazy | 0 on 30% of the days (spread out), 100 on the others |
| average | 100 every day |
| strong | 200 every day |

Every day: the day's XP is paid (coins = XP x income bonus) and split equally over the seven stats.
When a newer map has opened (furthest wave 30, then 60), the player moves there: every tower is
sold for exactly what it cost, as in the app, and the number of open spots carries over. Then the
player spends greedily, and the day's 12 automatic attempts run and stop at a failure with an
unchanged build, as on the server.

**Greedy spending:** while something is affordable, buy the option that improves the current wave
the most per coin. Each option (a tower of each type on the best empty spot, the next tier of each
kind of tower, tier 4 only once its stat level is reached) is tried with the real simulation on
that wave. A Market (Commerce) is bought when it pays for itself within 40 days, at most one per
five open spots. A new spot is bought when no affordable tower or tier helps and every open spot is
taken. This player is short-sighted and noisy: two close configs can differ by 0.1 in the
strong/lazy ratio just from different early purchases.

Columns: *Last new wave* = the last day a new wave was cleared. *Longest stall* = most days in a
row without a new wave. *Options left* = purchases still possible at day 365.

## v4.1: maps 2 and 3, tier 4, stronger stats (shipped)

What changed:

- **Maps 2 and 3** (`content/maps/map_02.json` "Circuit Switchback", 22 spots, opens after wave 30;
  `map_03.json` "Data Serpent", 30 spots, opens after wave 60). Longer serpentine paths (2,870 and
  4,740 px against 1,810) with rows of spots between the lanes, so a tower covers two lanes.
- **Tier 4** on every path (`content/towers.json`), about 2.5 x tier 3, gated by a stat level that
  depends on the tower: Archer and Cannon Strength 10, Frost Spirit 10, Mage Intellect 10, Tesla
  Intellect 12, Venom Vitality 10, Banner Social 10, Market Commerce Social 10 and Bargain
  Creativity 10.
- **Config:**

| Setting | v4 | v4.1 | Why |
| --- | --- | --- | --- |
| `game.spotCostGrowth` | 1.80 | **1.30** | With 30 spots, 1.8 made spot 16 cost 170,000: nobody could fill map 2. At 1.3 the strong player fills most of map 3, the lazy one about half of map 2 |
| `game.strengthDamagePerLevel` | 0.03 | **0.09** | Stat levels are the one source of power with no ceiling; the strong player's extra levels must count |
| `game.intellectDamagePerLevel` | 0.03 | **0.09** | Same |
| `game.disciplineRatePerLevel` | 0.015 | **0.045** | Same |
| `game.spiritSlowPerLevel` | 0.004 | **0.012** | Same |
| `game.enemyHpGrowth` | 1.10 | 1.10 | Unchanged |
| Archer damage | 10 | 10 | The v4 fix is kept |

### Results with the shipped config

| Profile | Waves day 30 | Waves day 100 | Waves day 200 | Waves day 300 | Waves day 365 | Last new wave | Longest stall | Moves | Purchases | Last purchase | Income x | Spots open | Options left |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| lazy | 16 | 32 | 39 | 48 | 49 | day 335 | 52 days | map 2 day 89 | 99 | day 349 | 1.39 | 13 | 3 |
| average | 20 | 38 | 49 | 56 | 58 | day 335 | 49 days | map 2 day 52 | 148 | day 341 | 1.45 | 20 | 1 |
| strong | 35 | 54 | 63 | 71 | 75 | day 359 | 42 days | map 2 day 27, map 3 day 142 | 241 | day 322 | 2.40 | 26 | 3 |

Strong / lazy: 2.19 at day 30, 1.69 at day 100, 1.62 at day 200, 1.48 at day 300, **1.53 at day 365**.

For comparison, v4 (one map, no tier 4) ended at lazy 34, average 38, strong 38, with the strong
player stuck for 211 days in a row.

### Against your targets

- **Nobody hits a hard ceiling before day 300: met.** Every profile clears new waves until day 335
  or later, the longest stall is 52 days (v4: 211), and every profile is still buying after day 320.
- **Always something worth buying: met.** Spots, tiers and tier 4 remain at day 365 for every
  profile.
- **Strong at least 1.6 x lazy at day 365: not met, 1.53.** It holds through day 200 (1.62 to
  2.19) and slips to about 1.5 in the last quarter.

### Why 1.6 at day 365 is out of reach with config alone

I swept the levers you named (one year each, three profiles):

| Spot growth | Stat bonuses | Tier-4 levels | Lazy day 365 | Strong day 365 | Ratio |
| --- | --- | --- | --- | --- | --- |
| 1.20 | x3 | 10 | 52 | 72 | 1.38 |
| 1.20 | x4 | 10 | 58 | 78 | 1.34 |
| 1.25 | x3 | 10 | 50 | 71 | 1.42 |
| 1.25 | x4 | 10 | 50 | 78 | 1.56 |
| **1.30** | **x3** | **10** | **49** | **75** | **1.53** (shipped: the shortest stalls) |
| 1.30 | x3 | 14 | 48 | 76 | 1.58 |
| 1.30 | x4 | 12 | 54 | 74 | 1.37 |
| 1.30 | x6 | 12 | 59 | 87 | 1.47 |
| 1.30 | x8 | 12 | 70 | 93 | 1.33 |
| 1.35 | x4 | 10 | 54 | 75 | 1.39 |
| 1.30 | x3, stat level curve 1.10 | 12 | 49 | 77 | 1.57 |

("Stat bonuses x3" = the four damage, rate and slow bonuses three times the v4 values.)

Every setting lands between 1.33 and 1.58. The reason is structural:

1. Enemy HP grows 10% per wave, so each extra wave needs 10% more power, and waves grow with the
   *logarithm* of power. The strong player earns about 3 times the lazy player's coins; three times
   the power is only about 11 more waves.
2. At day 365 each profile sits near the ceiling of the map it can reach: the lazy player never
   reaches wave 60, so it ends near the top of map 2 (about 50); the strong player ends near the top
   of map 3 (about 75 to 80). The ratio is roughly the ratio of those two ceilings, about 1.5.
3. Stronger stat bonuses lift everyone. Past x4 the lazy player also reaches map 3, and the ratio
   drops.

Ways to reach 1.6, for you to choose (none applied):

- **A fourth map** (opens after wave 90, 40 spots): the strong player would reach it in the last
  months while the lazy one would not. Data only.
- **Enemy HP that grows more slowly at high waves** (for example linear after wave 40): waves would
  follow power more directly, and 3 times the coins would mean close to 3 times the progress. A
  small formula change with one more config value.
- **Accept about 1.5**, which already holds 1.6 or more for the first 200 days.

## Does one tower or path dominate?

`npm run simulate` also prints this table: each tower with its full path (tier 4), every stat at
level 12 (so every tier 4 is allowed), one copy on the best spot and three copies on the three best
spots of the first map, and the furthest wave each clears.

| Tower | Path | Cost (all tiers) | 1 tower | 3 towers |
| --- | --- | --- | --- | --- |
| Archer | Sniper | 2230 | 19 | 31 |
| Archer | Rapid | 2060 | 24 | 32 |
| Cannon | Heavy | 3460 | 19 | 29 |
| Cannon | Barrage | 3060 | 19 | 19 |
| Frost | Deep freeze | 2180 | 10 | 17 |
| Frost | Blizzard | 2590 | 25 | 35 |
| Mage | Fire | 3370 | 27 | 35 |
| Mage | Arcane | 3420 | 21 | 36 |
| Tesla | Storm | 4040 | 25 | 35 |
| Tesla | Overload | 3950 | 29 | 42 |
| Venom | Plague | 3430 | 24 | 28 |
| Venom | Toxin | 3430 | 14 | 28 |

- **No tower beats everything.** Tesla Overload goes furthest but is the most expensive and needs
  Intellect 5 to build and 12 for tier 4. Blizzard and the Archers are the best value per coin.
- **Barrage stays at 19 with three towers:** it is ground only, and its tier 4 adds rate, not
  reach, so flyers walk past three Barrages as easily as one. Pair it with an Archer or a Mage.
- **Deep freeze alone is weak by design:** it slows and pays off next to damage towers.
- **The greedy player likes Cannons** (5 to 9 per build at day 365): Heavy is the best damage per
  coin in the middle game once Strength bonuses are tripled. A Cannon cost or damage retune is the
  first thing to try if real play shows the same.

## v4 history (one map, before tier 4)

With the v4 brief's numbers (enemy HP x1.15, spots 150 x 1.45^n, Archer damage 12) everyone ended at
wave 28 or 29, and the average player had bought everything by day 184. The v4 tuning (HP x1.10,
spots x1.8, Archer damage 10) kept everyone buying all year, but the single map capped everyone near
wave 38 to 42: lazy 34, average 38, strong 38 at day 365, with stalls up to 211 days. That ceiling
is what maps 2 and 3 and tier 4 address.
