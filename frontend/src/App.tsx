import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertTriangle, Coins, LayoutDashboard, ListTodo, Repeat, Settings as SettingsIcon, Swords, Timer, User, WifiOff, Zap } from 'lucide-react';
import { api, type ActionResult, type Character, type GameState, type LedgerEvent, type Meta, type StageView, type TimerView } from './api';
import { CampaignScreen } from './screens/CampaignScreen';
import { HabitsScreen } from './screens/HabitsScreen';
import { HeroScreen } from './screens/HeroScreen';
import { HomeScreen } from './screens/HomeScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { TasksScreen } from './screens/TasksScreen';
import { TimerScreen } from './screens/TimerScreen';
import { chime, loadSettings, saveSettings, type Settings } from './settings';
import type { Act, Screen } from './types';
import { BossDefeated } from './ui/BossDefeated';
import { CountUp } from './ui/CountUp';
import { TimerQuestion } from './ui/TimerQuestion';
import { XpPops, type Pop } from './ui/XpPops';

const NAV: { id: Screen; label: string; Icon: typeof Timer }[] = [
  { id: 'home', label: 'Home', Icon: LayoutDashboard },
  { id: 'tasks', label: 'Quests', Icon: ListTodo },
  { id: 'timer', label: 'Focus', Icon: Timer },
  { id: 'habits', label: 'Habits', Icon: Repeat },
  { id: 'campaign', label: 'Boss', Icon: Swords },
  { id: 'hero', label: 'Hero', Icon: User },
];

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
  const [victory, setVictory] = useState<StageView | null>(null);
  const lastStage = useRef<StageView | null>(null);
  const toastTimer = useRef<number>(undefined);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2600);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const [s, c] = await Promise.all([api.state(), api.character()]);
      // Boss just died in this session: celebrate (brief 11). Not on first load.
      const before = lastStage.current;
      if (before && !before.cleared && s.stage.cleared && before.weekStart === s.stage.weekStart) {
        setVictory(s.stage);
        window.setTimeout(() => setVictory(null), 1500);
      }
      lastStage.current = s.stage;
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

  /** Feedback for a payment: floating XP with its stat split, hero lunge, chime. Under 1.5 s. */
  const celebrate = useCallback(
    (e: LedgerEvent) => {
      const id = Date.now() + Math.random();
      const splits = Object.entries(e.statSplits).map(([sid, v]) => ({ id: sid, xp: Number(v) }));
      setPops((p) => [...p, { id, xp: e.xp, splits }]);
      window.setTimeout(() => setPops((p) => p.filter((x) => x.id !== id)), 1350);
      setHitKey((k) => k + 1);
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
  const mult = m ? m.streak * m.weapon * m.ascension * m.comeback : 1;

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
            <span className="pill gold" aria-label="coins">
              <Coins size={14} /> <CountUp value={state.balance} className="num" />
            </span>
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

        {!state || !meta ? (
          <p className="muted">Loading…</p>
        ) : (
          <div key={screen}>
            {screen === 'home' && (
              <HomeScreen state={state} meta={meta} character={character} act={act} hitKey={hitKey} version={version}
                refresh={refresh} onError={showToast} go={setScreen} />
            )}
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
            {screen === 'campaign' && <CampaignScreen state={state} meta={meta} act={act} version={version} />}
            {screen === 'hero' && character && <HeroScreen character={character} state={state} />}
            {screen === 'settings' && <SettingsScreen settings={settings} onSettings={changeSettings} refresh={refresh} onMessage={showToast} />}
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
      {!victory && <XpPops pops={pops} />}
      {victory && <BossDefeated stage={victory} />}
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
