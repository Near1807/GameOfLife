import type { ActionResult, GameState, Meta, StageView, TimerView } from '../api';

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

export const stage: StageView = {
  stage: 1, stageCount: 10, target: 800, damage: 330, hp: 470, overkill: 0, cleared: false, ascendReady: false,
  ascensions: 0, ascensionMultiplier: 1, weekStart: '2026-03-02', endsAt: '2099-03-09T03:00:00Z',
  boss: { stage: 1, name: 'Bug Queen', title: 'Mother of all regressions', sprite: 'enemies/enemy_bug_queen_idle.png' },
  rewardCoins: 100,
  map: [],
};

export const state: GameState = {
  today: '2026-03-02', serverNow: '2026-03-02T10:00:00Z', balance: 120, xpToday: 40,
  streak: { days: 3, freezes: 0, todayCounted: true, comebackPending: false, multiplierToday: 1.17, nextMilestone: 7 },
  multipliers: { streak: 1.1, weapon: 1, ascension: 1, comeback: 1 },
  statXp: {}, weaponLevel: 0, nextUpgradeCost: 150, stage,
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
