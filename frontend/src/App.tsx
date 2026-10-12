import { useCallback, useEffect, useRef, useState } from 'react';
import { api, whole, type ActionResult, type GameState, type LedgerEvent, type Meta, type TimerView } from './api';
import { NavBar } from './components/NavBar';
import { short } from './components/TagChips';
import { TimerQuestion } from './components/TimerQuestion';
import { XpPops, type Pop } from './components/XpPops';
import { HabitsScreen } from './screens/HabitsScreen';
import { HomeScreen } from './screens/HomeScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { TasksScreen } from './screens/TasksScreen';
import { TimerScreen } from './screens/TimerScreen';
import { blip, loadSettings, saveSettings, type Settings } from './settings';
import type { Act, Screen } from './types';

export default function App() {
  const [screen, setScreen] = useState<Screen>('home');
  const [state, setState] = useState<GameState | null>(null);
  const [meta, setMeta] = useState<Meta | null>(null);
  const [timer, setTimer] = useState<TimerView | null>(null);
  const [focusTaskId, setFocusTaskId] = useState<number | null>(null);
  const [pops, setPops] = useState<Pop[]>([]);
  const [toast, setToast] = useState<string | null>(null);
  const [attackKey, setAttackKey] = useState(0);
  const [version, setVersion] = useState(0); // bumped after each change so lists reload
  const [settings, setSettings] = useState<Settings>(loadSettings);
  const [offline, setOffline] = useState(false);
  const toastTimer = useRef<number>(undefined);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setToast(null), 2500);
  }, []);

  const refresh = useCallback(async () => {
    try {
      setState(await api.state());
      setOffline(false);
    } catch {
      setOffline(true);
    }
    setVersion((v) => v + 1);
  }, []);

  /** Asks the server for the timer waiting for an answer: on start, and every time the app is reopened. */
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

  /** Visual feedback for a payment: number pop, hero attack, sound. All under 1.5 s. */
  const celebrate = useCallback(
    (e: LedgerEvent) => {
      const id = Date.now() + Math.random();
      const stats = Object.keys(e.statSplits).map(short).join(' ');
      setPops((p) => [...p, { id, text: `+${whole(e.xp)} XP`, sub: stats }]);
      window.setTimeout(() => setPops((p) => p.filter((x) => x.id !== id)), 1300);
      setAttackKey((k) => k + 1);
      if (settings.sound) blip('gain');
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

  // When a running block should be over, ask the server (it decides, not this device's clock).
  useEffect(() => {
    if (!timer || timer.status !== 'RUNNING') return;
    const msLeft = new Date(timer.endsAt).getTime() - new Date(timer.serverNow).getTime();
    const id = window.setTimeout(() => {
      if (settings.sound) blip('alarm');
      loadTimer();
    }, Math.max(0, msLeft) + 500);
    return () => window.clearTimeout(id);
  }, [timer, settings.sound, loadTimer]);

  const changeSettings = (s: Settings) => {
    setSettings(s);
    saveSettings(s);
  };

  const classes = ['app', settings.scanlines && !settings.reducedEffects ? 'scanlines' : '', settings.reducedEffects ? 'reduced' : '']
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes}>
      {offline && <div className="banner">Cannot reach the GameLife server. Is the backend running?</div>}
      {state && !state.chain.valid && (
        <div className="banner">⚠ Ledger check: {state.chain.message}. You can keep playing; tell future-you to look at it.</div>
      )}

      {!state || !meta ? (
        <p className="cursor dim">booting</p>
      ) : (
        <>
          {screen === 'home' && (
            <HomeScreen state={state} meta={meta} act={act} attackKey={attackKey} version={version} refresh={refresh} onError={showToast} />
          )}
          {screen === 'tasks' && (
            <TasksScreen
              meta={meta}
              act={act}
              version={version}
              refresh={refresh}
              onError={showToast}
              onFocus={(id) => {
                setFocusTaskId(id);
                setScreen('timer');
              }}
            />
          )}
          {screen === 'timer' && (
            <TimerScreen
              meta={meta}
              timer={timer}
              taskId={focusTaskId}
              clearTask={() => setFocusTaskId(null)}
              onTimer={setTimer}
              onError={showToast}
            />
          )}
          {screen === 'habits' && <HabitsScreen meta={meta} act={act} version={version} refresh={refresh} onError={showToast} />}
          {screen === 'settings' && <SettingsScreen settings={settings} onSettings={changeSettings} refresh={refresh} onMessage={showToast} />}
        </>
      )}

      {timer && timer.status === 'RUNNING' && screen !== 'timer' && (
        <button className="btn" style={{ width: '100%' }} onClick={() => setScreen('timer')}>
          ◷ focus block running: {timer.label}
        </button>
      )}
      {timer && timer.status === 'ASK' && <TimerQuestion timer={timer} onAnswer={answerTimer} />}

      <XpPops pops={pops} />
      {toast && <div className="toast">{toast}</div>}
      <NavBar screen={screen} onChange={setScreen} />
    </div>
  );
}
