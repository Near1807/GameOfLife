import type { ActionResult, Meta, TimerView } from '../api';

export const meta: Meta = {
  stats: [
    { id: 'STRENGTH', name: 'Strength', hint: 'sport' },
    { id: 'INTELLECT', name: 'Intellect', hint: 'study' },
    { id: 'DISCIPLINE', name: 'Discipline', hint: 'focus' },
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
