import type { ActionResult, FightView, GameState, Look, Meta, TimerView } from '../api';

export const meta: Meta = {
  stats: [
    { id: 'STRENGTH', name: 'Strength', hint: 'sport' },
    { id: 'INTELLECT', name: 'Intellect', hint: 'study' },
    { id: 'DISCIPLINE', name: 'Discipline', hint: 'focus' },
    { id: 'SPIRIT', name: 'Spirit', hint: 'mood' },
  ],
  todo: { xpPerMinute: 1, minuteChips: [5, 10, 15, 30, 60, 120], defaultMinutes: 15, suggestionCount: 8 },
  timer: {
    xpPerMinute: 1,
    minuteChips: [10, 25, 45, 60, 90],
    minMinutes: 10,
    maxMinutes: 90,
    defaultMinutes: 25,
    earlyStopMinMinutes: 10,
    defaultTags: ['DISCIPLINE'],
  },
  checkin: { xp: 5, moodLevels: 5, defaultTags: ['SPIRIT'] },
  habitXp: 20,
  lastTaskMinutes: 15,
  maxRestDaysPerWeek: 2,
  dailyXpGoal: 100,
  lastCheckinTags: ['SPIRIT'],
};

export const fight: FightView = {
  fightId: '0-1', ascensions: 0, boss: 1, bossName: 'Bug Queen', bossTitle: 'Mother of all regressions',
  bossSprite: 'content/enemies/boss_01.png', bossFrames: 4, variant: false, bossHp: 470, bossMaxHp: 800, bossAtk: 20,
  heroHp: 90, heroMaxHp: 110, armorReduction: 0, todayXp: 40, dailyGoal: 100, shieldToday: 0.4, protectedToday: false,
  expectedHit: 12, defeatsByThisBoss: 0, kills: 0, falls: 0, ascendReady: false, bossFiles: 10,
  region: 'content/regions/region_01.png', heroSprite: 'content/hero/hero_01.png', heroFrames: 4, defeatMode: 'mild',
  killReward: 100, pausedToday: false, pausesLeft: 7, recentAttacks: [], recentFalls: [], map: [],
};

export const look: Look = {
  themeId: 'theme_nebula', themeTokens: {}, heroSprite: 'content/hero/hero_01.png', heroFrames: 4, arenaFilter: null, worn: [],
};

export const state: GameState = {
  today: '2026-03-02', serverNow: '2026-03-02T10:00:00Z', balance: 120, xpToday: 40,
  streak: { days: 3, freezes: 0, todayCounted: true, comebackPending: false, multiplierToday: 1.17, nextMilestone: 7 },
  multipliers: { streak: 1.1, weapon: 1, ascension: 1, comeback: 1 },
  statXp: {}, weaponLevel: 0, nextUpgradeCost: 150, fight, look,
  loadout: { weaponLevel: 0, armorLevel: 0, owned: [], equipped: {}, charms: [] },
  chain: { valid: true, brokenAtId: 0, message: 'ok' }, log: [],
};

export const paid: ActionResult = {
  paid: true,
  message: 'Paid',
  event: {
    id: 1, key: 'k', type: 'TASK_DONE', occurredAt: '2026-03-02T10:00:00Z', gameDay: '2026-03-02',
    module: 'todo', label: 'x', xp: 11, currencyDelta: 11, statSplits: {}, meta: {},
  },
};

export const askingTimer: TimerView = {
  id: 7, label: 'Deep work', taskId: null, plannedMinutes: 25, tags: [], startedAt: '2026-03-02T10:00:00Z',
  endsAt: '2026-03-02T10:25:00Z', stoppedAt: null, serverNow: '2026-03-02T10:26:00Z', status: 'ASK',
  countedMinutes: 25, wouldPay: true, answer: null,
};
