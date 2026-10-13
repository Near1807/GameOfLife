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
  /** coins = final XP x income (Markets and Social), see DefenseView.incomeMultiplier */
  multipliers: { streak: number; comeback: number };
  statXp: Record<string, number>;
  loadout: Loadout;
  look: Look;
  chain: { valid: boolean; brokenAtId: number; message: string };
  log: LedgerEvent[];
}

/** A cosmetic (v4: looks only). tokens: CSS design tokens (themes) or { filter } for map skins. */
export interface Item {
  id: string;
  name: string;
  kind: 'outfit' | 'skin' | 'theme';
  slot: string;
  price: number;
  requires: { stat: string; level: number } | null;
  description: string;
  sprite: string | null;
  frames: number;
  tokens: Record<string, string>;
}

export interface Loadout {
  owned: string[];
  equipped: Record<string, string>;
}

export interface Look {
  themeId: string | null;
  themeTokens: Record<string, string>;
  heroSprite: string;
  heroFrames: number;
  mapFilter: string | null;
}

export interface ShopItem {
  item: Item;
  status: 'EQUIPPED' | 'OWNED' | 'LOCKED' | 'AFFORDABLE' | 'TOO_EXPENSIVE';
  lockReason: string | null;
  effectText: string;
}

export interface ShopView {
  balance: number;
  items: ShopItem[];
}

export interface Catalog {
  heroes: { number: number; name: string; file: string; frames: number }[];
  items: Item[];
  warnings: string[];
}

/** Calendar-week report (v4 brief 4): real life and the map. */
export interface WeekReview {
  weekStart: string;
  previousWeek: string | null;
  nextWeek: string | null;
  currentWeek: boolean;
  xp: number;
  wavesCleared: number;
  wavesFailed: number;
  furthestWave: number;
  coinsEarned: number;
  coinsSpent: number;
  towersBuilt: number;
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
  furthestWave: number;
}

export interface AchievementStatus {
  id: string;
  name: string;
  description: string;
  icon: string;
  unlocked: boolean;
  current: number;
  target: number;
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

// ---------------------------------------------------------------- tower defense (v4)

/** needs: the stat level the next tier requires and you lack (tier 4), e.g. "Needs Intellect 10". */
export interface PathView {
  name: string; tier: number; locked: boolean; price: number | null; next: string | null; tiers: string[]; needs: string | null;
}

/** A map (chapter). unlocked once the furthest wave reaches unlockAfterWave. */
export interface MapChoice { id: string; name: string; spots: number; unlockAfterWave: number; unlocked: boolean; active: boolean }

export interface TowerView {
  type: string; name: string; kind: string; role: 'attack' | 'support' | 'income'; color: string; shape: string;
  a: number; b: number; paid: number;
  stats: { dmg: number; rate: number; range: number; rad: number; slow: number; burn: number; pois: number; chain: number;
    multi: number; pierce: number; rbuff: number; dbuff: number; coin: number; disc: number };
  paths: PathView[];
}

export interface SpotView { index: number; x: number; y: number; open: boolean; tower: TowerView | null }

export interface TowerOption {
  id: string; name: string; kind: string; role: string; color: string; shape: string; price: number; listPrice: number;
  locked: string | null;
}

export interface AttemptView {
  key: string; wave: number; attempt: number; won: boolean; manual: boolean; at: string; lives: number; livesMax: number;
  enemies: number; killed: number; leaked: number; seconds: number;
}

export interface EnemyView { name: string; color: string; radius: number; air: boolean; armor: number; ward: number }

export interface DefenseView {
  mapId: string; mapName: string; width: number; height: number; path: [number, number][]; base: [number, number];
  spots: SpotView[]; towers: TowerOption[]; wave: number; furthestCleared: number; livesMax: number;
  status: 'idle' | 'waiting' | 'stuck' | 'capped'; nextAt: string | null; attemptsToday: number; maxAttemptsPerDay: number;
  balance: number; incomeMultiplier: number; discount: number; nextSpotCost: number;
  last: AttemptView | null; recent: AttemptView[]; enemies: Record<string, EnemyView>; stats: Record<string, number>;
  statEffects: { stat: string; boosts: string; now: string; unlocks: string[] }[];
  maps: MapChoice[];
}

/** One replay sample. enemies: [id, x, y, hp %, flags 1 slowed 2 burning 4 poisoned]. */
export interface ReplayFrame {
  t: number; lives: number; enemies: number[][]; shots: number[][]; chains: number[][]; pops: number[][];
}

export interface Watch {
  attempt: { key: string; wave: number; attempt: number; won: boolean; manual: boolean; at: string };
  result: { wave: number; won: boolean; lives: number; livesMax: number; enemies: number; killed: number; leaked: number;
    seconds: number; replay: { fps: number; types: string[]; frames: ReplayFrame[] } };
  sameAsRecorded: boolean;
}

/** Automatic backups (desktop brief 4). */
export interface BackupEntry { file: string; at: string; reason: string; bytes: number; events: number; valid: boolean; problem: string | null }
export interface BackupStatus {
  folder: string; defaultFolder: boolean; lastBackup: string | null; lastValid: boolean | null; stale: boolean;
  lastError: string | null; backups: BackupEntry[];
}

/**
 * Only in the Windows desktop app (desktop/preload.cjs). Undefined in the browser version, where
 * these settings are hidden.
 */
export interface DesktopBridge {
  dataFolder: string;
  getStartWithWindows(): Promise<boolean>;
  setStartWithWindows(on: boolean): Promise<boolean>;
  chooseFolder(): Promise<string | null>;
  openFolder(path: string): Promise<void>;
}
export const desktop: DesktopBridge | undefined = (globalThis as { gamelifeDesktop?: DesktopBridge }).gamelifeDesktop;

export const api = {
  backupStatus: () => request<BackupStatus>('GET', '/api/backup/auto'),
  backupNow: () => request<BackupEntry>('POST', '/api/backup/auto'),
  setBackupFolder: (folder: string) => request<BackupStatus>('PUT', '/api/backup/folder', { folder }),
  restoreBackup: (file: string) => request<{ valid: boolean; message: string }>('POST', '/api/backup/restore', { file }),
  game: () => request<DefenseView>('GET', '/api/game'),
  unlockSpot: (spot: number) => request<ActionResult>('POST', '/api/game/unlock', { spot }),
  buildTower: (spot: number, tower: string) => request<ActionResult>('POST', '/api/game/build', { spot, tower }),
  upgradeTower: (spot: number, path: number, tier: number) =>
    request<ActionResult>('POST', '/api/game/upgrade', { spot, path, tier }),
  sellTower: (spot: number) => request<ActionResult>('POST', '/api/game/sell', { spot, confirm: true }),
  moveMap: (map: string) => request<ActionResult>('POST', '/api/game/move', { map, confirm: true }),
  runWave: () => request<ActionResult>('POST', '/api/game/run'),
  replay: (key?: string) => request<Watch>('GET', '/api/game/replay' + (key ? `?key=${encodeURIComponent(key)}` : '')),

  state: () => request<GameState>('GET', '/api/state'),
  meta: () => request<Meta>('GET', '/api/meta'),
  character: () => request<Character>('GET', '/api/character'),
  review: (week?: string) => request<WeekReview>('GET', '/api/review' + (week ? `?week=${week}` : '')),
  dashboard: (days = 30) => request<Dashboard>('GET', `/api/dashboard?days=${days}`),
  shop: () => request<ShopView>('GET', '/api/shop'),
  buy: (itemId: string) => request<ActionResult>('POST', '/api/shop/buy', { itemId, confirm: true }),
  equip: (itemId: string, on: boolean) => request<ActionResult>('POST', '/api/shop/equip', { itemId, on }),
  content: () => request<Catalog>('GET', '/api/content'),
  achievements: () => request<AchievementStatus[]>('GET', '/api/achievements'),
  reloadContent: () => request<Catalog>('POST', '/api/content/reload'),

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
