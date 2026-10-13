import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, CalendarRange, Coins, LayoutDashboard, ListTodo, Repeat, Settings as SettingsIcon, Timer, User, WifiOff, Zap } from 'lucide-react';
import { api, type BackupStatus, type AchievementStatus, type ActionResult, type Character, type GameState, type LedgerEvent, type Meta, type TimerView } from './api';
import { ReviewScreen } from './screens/ReviewScreen';
import { HabitsScreen } from './screens/HabitsScreen';
import { HeroScreen } from './screens/HeroScreen';
import { HomeScreen } from './screens/HomeScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { TasksScreen } from './screens/TasksScreen';
import { TimerScreen } from './screens/TimerScreen';
import { chime, loadSettings, saveSettings, type Settings } from './settings';
import type { Act, Screen } from './types';
import { ShopScreen } from './screens/ShopScreen';
import { statStyle } from './stats';
import { achievementIcon } from './ui/Achievements';
import { CountUp } from './ui/CountUp';
import { ago } from './ui/BackupCard';
import { TimerQuestion } from './ui/TimerQuestion';
import { XpPops, type Pop } from './ui/XpPops';

const NAV: { id: Screen; label: string; Icon: typeof Timer }[] = [
  { id: 'home', label: 'Home', Icon: LayoutDashboard },
  { id: 'tasks', label: 'Quests', Icon: ListTodo },
  { id: 'timer', label: 'Focus', Icon: Timer },
  { id: 'habits', label: 'Habits', Icon: Repeat },
  { id: 'review', label: 'Review', Icon: CalendarRange },
  { id: 'hero', label: 'Hero', Icon: User },
];

/**
 * Stat orbs (brief 11): one glowing orb per stat tag flies from the map into the matching stat
 * bar of the home screen. Plain Web Animations, removed after 0.9 s, never blocks a tap.
 */
function flyOrbs(statIds: string[]) {
  const from = document.querySelector('.defense-card')?.getBoundingClientRect();
  if (!from) return;
  statIds.forEach((id, i) => {
    const to = document.querySelector(`[data-stat="${id}"]`)?.getBoundingClientRect();
    if (!to) return;
    const orb = document.createElement('span');
    orb.className = 'orb';
    orb.style.color = statStyle(id).color;
    orb.style.left = `${from.left + from.width * 0.72}px`;
    orb.style.top = `${from.top + from.height * 0.45}px`;
    document.body.appendChild(orb);
    const dx = to.left + to.width / 2 - (from.left + from.width * 0.72);
    const dy = to.top + to.height / 2 - (from.top + from.height * 0.45);
    const anim = orb.animate(
      [{ transform: 'translate(0, 0) scale(1.4)', opacity: 1 }, { transform: `translate(${dx * 0.4}px, ${dy * 0.3 - 60}px) scale(1)`, opacity: 1, offset: 0.4 },
        { transform: `translate(${dx}px, ${dy}px) scale(0.4)`, opacity: 0.2 }],
      { duration: 850, delay: 150 + i * 90, easing: 'cubic-bezier(0.5, 0, 0.5, 1)', fill: 'backwards' });
    anim.onfinish = () => orb.remove();
  });
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('home');
  const [state, setState] = useState<GameState | null>(null);
  const [meta, setMeta] = useState<Meta | null>(null);
  const [character, setCharacter] = useState<Character | null>(null);
  const [timer, setTimer] = useState<TimerView | null>(null);
  const [focusTaskId, setFocusTaskId] = useState<number | null>(null);
  const [pops, setPops] = useState<Pop[]>([]);
  const [toast, setToast] = useState<string | null>(null);
  const [hitKey, setHitKey] = useState(0);
  const [version, setVersion] = useState(0); // bumped after each change so screens reload
  const [settings, setSettings] = useState<Settings>(loadSettings);
  const [offline, setOffline] = useState(false);
  const [backup, setBackup] = useState<BackupStatus | null>(null);
  const toastTimer = useRef<number>(undefined);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2600);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const [s, c] = await Promise.all([api.state(), api.character()]);
      watchAchievements();
      api.backupStatus().then(setBackup).catch(() => {});
      setState(s);
      setCharacter(c);
      setOffline(false);
    } catch {
      setOffline(true);
    }
    setVersion((v) => v + 1);
  }, []);

  /** Asks the server for the timer waiting for an answer: on start, and whenever the app is reopened. */
  const loadTimer = useCallback(() => {
    api.currentTimer().then((t) => setTimer(t ?? null)).catch(() => {});
  }, []);

  useEffect(() => {
    api.meta().then(setMeta).catch(() => setOffline(true));
    refresh();
    loadTimer();
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        refresh();
        loadTimer();
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [refresh, loadTimer]);

  // When a running block should be over, ask the server (it decides, not this device's clock).
  useEffect(() => {
    if (!timer || timer.status !== 'RUNNING') return;
    const msLeft = new Date(timer.endsAt).getTime() - new Date(timer.serverNow).getTime();
    const id = window.setTimeout(() => {
      if (settings.sound) chime('alarm');
      loadTimer();
    }, Math.max(0, msLeft) + 500);
    return () => window.clearTimeout(id);
  }, [timer, settings.sound, loadTimer]);

  /** A toast for each achievement unlocked since this device last looked (shown once). */
  const watchAchievements = useCallback(() => {
    api.achievements().then((list) => {
      const unlocked = list.filter((a) => a.unlocked).map((a) => a.id);
      let known: string[] | null = null;
      try { known = JSON.parse(localStorage.getItem('gamelife.achievements') ?? 'null'); } catch { known = null; }
      if (known) {
        const fresh = list.filter((a) => a.unlocked && !known!.includes(a.id));
        fresh.forEach((a, i) => window.setTimeout(() => setUnlock(a), 1600 * i + 400));
      }
      try { localStorage.setItem('gamelife.achievements', JSON.stringify(unlocked)); } catch { /* ignore */ }
    }).catch(() => {});
  }, []);
  const [unlock, setUnlock] = useState<AchievementStatus | null>(null);
  useEffect(() => {
    if (!unlock) return;
    const id = window.setTimeout(() => setUnlock(null), 2600);
    return () => window.clearTimeout(id);
  }, [unlock]);

  // The equipped theme restyles the whole app through its design tokens (brief 10).
  const appliedTokens = useRef<string[]>([]);
  useEffect(() => {
    const tokens = state?.look.themeTokens ?? {};
    const root = document.documentElement;
    appliedTokens.current.forEach((k) => root.style.removeProperty(k));
    Object.entries(tokens).forEach(([k, v]) => root.style.setProperty(k, v));
    appliedTokens.current = Object.keys(tokens);
  }, [state?.look.themeTokens]);

  /** Feedback for a payment: floating XP with its stat split, hero lunge, chime. Under 1.5 s. */
  const celebrate = useCallback(
    (e: LedgerEvent) => {
      const id = Date.now() + Math.random();
      const splits = Object.entries(e.statSplits).map(([sid, v]) => ({ id: sid, xp: Number(v) }));
      setPops((p) => [...p, { id, xp: e.xp, splits }]);
      window.setTimeout(() => setPops((p) => p.filter((x) => x.id !== id)), 1350);
      setHitKey((k) => k + 1);
      if (!settings.reducedEffects) flyOrbs(splits.map((x) => x.id));
      if (settings.sound) chime('gain');
    },
    [settings.sound],
  );

  const act: Act = useCallback(
    async (action: Promise<ActionResult>) => {
      try {
        const r = await action;
        if (r.paid && r.event && r.event.xp > 0) celebrate(r.event);
        else if (r.message && !r.paid) showToast(r.message);
        await refresh();
        return r;
      } catch (e) {
        showToast((e as Error).message);
        await refresh();
        return undefined;
      }
    },
    [celebrate, refresh, showToast],
  );

  const answerTimer = async (yes: boolean) => {
    if (!timer) return;
    setTimer(null);
    await act(api.answerTimer(timer.id, yes));
    loadTimer();
  };

  const changeSettings = (s: Settings) => {
    setSettings(s);
    saveSettings(s);
  };

  const m = state?.multipliers;
  const mult = m ? m.streak * m.comeback : 1;

  return (
    <div className={'shell' + (settings.reducedEffects ? ' reduced' : '')}>
      <nav className="nav" aria-label="main">
        {NAV.map((n) => (
          <button key={n.id} className={'nav-item' + (screen === n.id ? ' active' : '')} onClick={() => setScreen(n.id)} aria-current={screen === n.id}>
            <n.Icon size={22} strokeWidth={2} />
            {n.label}
          </button>
        ))}
      </nav>

      <main className="main">
        <header className="topbar">
          <div className="brand grow">
            <span className="brand-mark">
              <Zap size={16} color="#fff" />
            </span>
            GameLife
          </div>
          {state && (
            <button className="pill gold" aria-label="coins, open the shop" style={{ cursor: 'pointer', font: 'inherit' }} onClick={() => setScreen('shop')}>
              <Coins size={14} /> <CountUp value={state.balance} className="num" />
            </button>
          )}
          {character && (
            <button className="level-badge" style={{ border: 'none', cursor: 'pointer' }} onClick={() => setScreen('hero')}>
              Lv {character.overallLevel}
            </button>
          )}
          <button className="icon-btn" aria-label="settings" onClick={() => setScreen('settings')}>
            <SettingsIcon size={19} />
          </button>
        </header>

        {offline && (
          <div className="banner">
            <WifiOff size={16} /> Cannot reach the GameLife server. Is the backend running?
          </div>
        )}
        {state && !state.chain.valid && (
          <div className="banner">
            <AlertTriangle size={16} /> Ledger check: {state.chain.message}. You can keep playing.
          </div>
        )}

        {screen === 'home' && backup?.stale && (
          <button className="banner warn" onClick={() => setScreen('settings')} style={{ width: '100%', font: 'inherit', cursor: 'pointer', textAlign: 'left' }}>
            <AlertTriangle size={16} /> {backup.lastBackup ? `Last backup ${ago(backup.lastBackup)}.` : 'No backup yet.'} Open Settings to back up now.
          </button>
        )}
        {!state || !meta ? (
          <p className="muted">Loading…</p>
        ) : (
          <div key={screen}>
            {screen === 'home' && (
              <HomeScreen state={state} meta={meta} character={character} act={act} hitKey={hitKey} version={version}
                refresh={refresh} onError={showToast} go={setScreen} />
            )}
            {screen === 'shop' && <ShopScreen act={act} version={version} />}
            {screen === 'tasks' && (
              <TasksScreen meta={meta} multiplier={mult} act={act} version={version} refresh={refresh} onError={showToast}
                onFocus={(id) => {
                  setFocusTaskId(id);
                  setScreen('timer');
                }} />
            )}
            {screen === 'timer' && (
              <TimerScreen meta={meta} multiplier={mult} timer={timer} taskId={focusTaskId}
                clearTask={() => setFocusTaskId(null)} onTimer={setTimer} onError={showToast} />
            )}
            {screen === 'habits' && <HabitsScreen meta={meta} state={state} act={act} version={version} refresh={refresh} onError={showToast} />}
            {screen === 'review' && <ReviewScreen meta={meta} version={version} />}
            {screen === 'hero' && character && <HeroScreen character={character} state={state} version={version} />}
            {screen === 'settings' && <SettingsScreen settings={settings} onSettings={changeSettings} refresh={refresh}
              onMessage={showToast} openShop={() => setScreen('shop')} />}
          </div>
        )}

        {timer && timer.status === 'RUNNING' && screen !== 'timer' && (
          <button className="btn block" style={{ marginTop: 14 }} onClick={() => setScreen('timer')}>
            <Timer size={16} /> Focus block running: {timer.label}
          </button>
        )}
      </main>

      {timer && timer.status === 'ASK' && (
        <TimerQuestion timer={timer} previewXp={timer.countedMinutes * (meta?.timer.xpPerMinute ?? 1) * mult} onAnswer={answerTimer} />
      )}
      <XpPops pops={pops} />
      {toast && <div className="toast">{toast}</div>}
      {unlock && (
        <div className="unlock" role="status">
          <span className="badge-icon on">{(() => { const I = achievementIcon(unlock.icon); return <I size={20} />; })()}</span>
          <div><div className="label" style={{ color: 'var(--gold)' }}>Achievement unlocked</div><b>{unlock.name}</b></div>
        </div>
      )}
    </div>
  );
}
