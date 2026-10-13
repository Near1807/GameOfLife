// All calls to the backend. Every number shown in the app comes from here: the frontend never
// computes XP, balance or streak itself (brief 3.2).

export interface LedgerEvent {
  id: number;
  key: string;
  type: string;
  occurredAt: string;
  gameDay: string;
  module: string;
  label: string;
  xp: number;
  currencyDelta: number;
  statSplits: Record<string, string>;
  meta: Record<string, unknown>;
}

/** Answer to any action. paid=false with an event means "already counted" (double tap). */
export interface ActionResult {
  event: LedgerEvent | null;
  paid: boolean;
  message: string;
}

export interface StreakInfo {
  days: number;
  freezes: number;
  todayCounted: boolean;
  comebackPending: boolean;
  multiplierToday: number;
  nextMilestone: number;
}

export interface GameState {
  today: string;
  serverNow: string;
  balance: number;
  xpToday: number;
  streak: StreakInfo;
  multipliers: { streak: number; weapon: number; ascension: number; comeback: number };
  statXp: Record<string, number>;
  weaponLevel: number;
  nextUpgradeCost: number;
  stage: StageView;
  chain: { valid: boolean; brokenAtId: number; message: string };
  log: LedgerEvent[];
}

export interface Boss {
  stage: number;
  name: string;
  title: string;
  sprite: string;
}

export interface StageView {
  stage: number;
  stageCount: number;
  target: number;
  damage: number;
  hp: number;
  overkill: number;
  cleared: boolean;
  ascendReady: boolean;
  ascensions: number;
  ascensionMultiplier: number;
  weekStart: string;
  endsAt: string;
  boss: Boss;
  rewardCoins: number;
  map: { stage: number; name: string; sprite: string; target: number; status: 'CLEARED' | 'CURRENT' | 'LOCKED' }[];
}

export interface WeekReview {
  weekStart: string;
  previousWeek: string | null;
  nextWeek: string | null;
  currentWeek: boolean;
  stage: number;
  bossName: string;
  bossSprite: string;
  target: number;
  damage: number;
  cleared: boolean;
  days: { date: string; xp: number; counted: boolean; rest: boolean; future: boolean }[];
  statXp: Record<string, number>;
  tasksDone: number;
  focusMinutes: number;
  habitsDone: number;
  averageMood: number | null;
  streakAtEnd: number;
  skippedHabits: { name: string; missed: number; scheduled: number }[];
  corrections: { day: string; label: string; xp: number; reason: string }[];
}

export interface Dashboard {
  days: number;
  history: { date: string; xp: number; statXp: Record<string, number>; streak: number; counted: boolean }[];
  statXpPeriod: Record<string, number>;
  xpPeriod: number;
  xpAllTime: number;
  focusHoursAllTime: number;
  tasksDoneAllTime: number;
  bestStreak: number;
  currentStreak: number;
  countedDaysAllTime: number;
  bossesDefeated: number;
}

export interface Stat {
  id: string;
  name: string;
  hint: string;
}

export interface Meta {
  stats: Stat[];
  todo: { xpPerMinute: number; minuteChips: number[]; defaultMinutes: number; suggestionCount: number };
  timer: {
    xpPerMinute: number;
    minuteChips: number[];
    minMinutes: number;
    maxMinutes: number;
    defaultMinutes: number;
    earlyStopMinMinutes: number;
    defaultTags: string[];
  };
  checkin: { xp: number; moodLevels: number; defaultTags: string[] };
  habitXp: number;
  lastTaskMinutes: number;
  maxRestDaysPerWeek: number;
  dailyXpGoal: number;
  lastCheckinTags: string[];
}

export interface StatView {
  id: string;
  name: string;
  hint: string;
  xp: number;
  level: number;
  xpIntoLevel: number;
  xpForNext: number;
  progress: number;
}

export interface Character {
  stats: StatView[];
  overallLevel: number;
  className: string;
  rank: string;
  balanced: boolean;
  topStats: string[];
}

export interface TaskView {
  id: number;
  name: string;
  expectedMinutes: number;
  tags: string[];
  done: boolean;
  xpPaid: number;
  viaTimer: boolean;
}

export interface Suggestion {
  name: string;
  expectedMinutes: number;
  tags: string[];
  timesUsed: number;
}

export interface TimerView {
  id: number;
  label: string;
  taskId: number | null;
  plannedMinutes: number;
  tags: string[];
  startedAt: string;
  endsAt: string;
  stoppedAt: string | null;
  serverNow: string;
  status: 'RUNNING' | 'ASK' | 'DONE';
  countedMinutes: number;
  wouldPay: boolean;
  answer: string | null;
}

export type Day = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export interface HabitView {
  id: number;
  name: string;
  days: Day[];
  tags: string[];
  scheduledToday: boolean;
  doneToday: boolean;
  streak: number;
  history: { date: string; status: 'DONE' | 'MISSED' | 'OFF' | 'TODAY' }[];
}

/** Sends one request. Throws an Error with the server's message when refused. */
async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) throw new Error(data?.error ?? `Server error ${res.status}`);
  return data as T;
}

export const api = {
  state: () => request<GameState>('GET', '/api/state'),
  meta: () => request<Meta>('GET', '/api/meta'),
  character: () => request<Character>('GET', '/api/character'),
  review: (week?: string) => request<WeekReview>('GET', '/api/review' + (week ? `?week=${week}` : '')),
  dashboard: (days = 30) => request<Dashboard>('GET', `/api/dashboard?days=${days}`),
  ascend: () => request<ActionResult>('POST', '/api/ascend'),

  tasks: () => request<TaskView[]>('GET', '/api/tasks'),
  suggestions: (q = '') => request<Suggestion[]>('GET', `/api/tasks/suggestions?q=${encodeURIComponent(q)}`),
  createTask: (name: string, minutes?: number, tags?: string[]) =>
    request<TaskView>('POST', '/api/tasks', { name, minutes, tags }),
  tickTask: (id: number) => request<ActionResult>('POST', `/api/tasks/${id}/done`),
  untickTask: (id: number) => request<ActionResult>('POST', `/api/tasks/${id}/undo`),
  archiveTask: (id: number) => request<void>('POST', `/api/tasks/${id}/archive`),

  currentTimer: () => request<TimerView | undefined>('GET', '/api/timers/current'),
  startTimer: (minutes: number, label?: string, taskId?: number, tags?: string[]) =>
    request<TimerView>('POST', '/api/timers', { minutes, label, taskId, tags }),
  stopTimer: (id: number) => request<TimerView>('POST', `/api/timers/${id}/stop`),
  answerTimer: (id: number, yes: boolean) => request<ActionResult>('POST', `/api/timers/${id}/answer`, { yes }),

  habits: () => request<HabitView[]>('GET', '/api/habits'),
  createHabit: (name: string, days?: Day[], tags?: string[]) =>
    request<HabitView>('POST', '/api/habits', { name, days, tags }),
  habitDone: (id: number) => request<ActionResult>('POST', `/api/habits/${id}/done`),
  archiveHabit: (id: number) => request<void>('POST', `/api/habits/${id}/archive`),

  checkinToday: () => request<{ mood: number | null }>('GET', '/api/checkin/today'),
  checkin: (mood: number, tags?: string[]) => request<ActionResult>('POST', '/api/checkin', { mood, tags }),

  restDays: () => request<string[]>('GET', '/api/rest-days'),
  planRest: (date: string) => request<ActionResult>('POST', '/api/rest-days', { date }),

  importBackup: (json: string) =>
    fetch('/api/backup/import', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: json })
      .then(async (r) => {
        const data = await r.json();
        if (!r.ok) throw new Error(data?.error ?? 'Import failed');
        return data as { valid: boolean; message: string };
      }),
};

/** Shown amounts are rounded to whole numbers (brief 5). */
export const whole = (n: number) => Math.round(n).toLocaleString('en-US');
