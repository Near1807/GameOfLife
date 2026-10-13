import { useEffect, useRef, useState } from 'react';
import { Square, Timer } from 'lucide-react';
import { api, type Meta, type TaskView, type TimerView } from '../api';
import { Card } from '../ui/Card';
import { Ring } from '../ui/Ring';
import { StatTagPicker } from '../ui/StatTagPicker';

/**
 * Focus timer. Start = one tap on a length chip. The countdown is only a display: the server
 * measures time from its own timestamps (brief 3.2). When the block ends, the Yes / No question
 * appears on whatever screen is open.
 */
export function TimerScreen(props: {
  meta: Meta;
  multiplier: number;
  timer: TimerView | null;
  taskId: number | null;
  clearTask: () => void;
  onTimer: (t: TimerView | null) => void;
  onError: (msg: string) => void;
}) {
  const [label, setLabel] = useState('');
  const [tags, setTags] = useState<string[] | null>(null);
  const [task, setTask] = useState<TaskView | null>(null);
  const cfg = props.meta.timer;

  useEffect(() => {
    if (props.taskId === null) return setTask(null);
    api.tasks().then((ts) => setTask(ts.find((t) => t.id === props.taskId) ?? null)).catch(() => {});
  }, [props.taskId]);

  const start = (minutes: number) =>
    api
      .startTimer(minutes, task ? undefined : label || undefined, task?.id, tags ?? undefined)
      .then((t) => {
        props.onTimer(t);
        props.clearTask();
      })
      .catch((e) => props.onError(e.message));

  if (props.timer && props.timer.status === 'RUNNING') {
    const id = props.timer.id;
    return <Running timer={props.timer} onStop={() => api.stopTimer(id).then(props.onTimer)} />;
  }

  const shownTags = tags ?? task?.tags ?? cfg.defaultTags;
  return (
    <div className="page">
      <h1 className="page-title">Focus</h1>
      <p className="page-sub">Pick a length to start. You confirm at the end, and only then it pays.</p>
      <Card glow className="focus-card">
        <Ring progress={1} dim size={220}>
          <div className="ring-time">{String(cfg.defaultMinutes).padStart(2, '0')}:00</div>
          <div className="label">ready</div>
        </Ring>
        {task ? (
          <div className="row">
            <Timer size={16} className="cyan" />
            <span className="grow">
              Working on <b className="cyan">{task.name}</b>
            </span>
            <button className="btn ghost" onClick={props.clearTask}>
              Clear
            </button>
          </div>
        ) : (
          <input className="input" placeholder="What are you focusing on? (optional)" value={label} onChange={(e) => setLabel(e.target.value)} aria-label="timer label" />
        )}
        <div className="label" style={{ margin: '16px 0 8px' }}>
          Stats it trains
        </div>
        <StatTagPicker stats={props.meta.stats} selected={shownTags} onChange={setTags} />
        <div className="label" style={{ margin: '18px 0 8px' }}>
          Length · tap to start
        </div>
        <div className="chips" aria-label="timer length">
          {cfg.minuteChips.map((m) => (
            <button
              key={m}
              className={'chip grow' + (m === cfg.defaultMinutes ? ' selected' : '')}
              aria-label={`${m} minutes`}
              onClick={() => start(m)}
            >
              <span className="chip-main">{m}m</span>
              <span className="chip-sub">+{Math.round(m * cfg.xpPerMinute * props.multiplier)}</span>
            </button>
          ))}
        </div>
      </Card>
    </div>
  );
}

/**
 * Countdown from the server's endsAt, corrected by the server/device clock offset.
 * Display only: App asks the server again when the block should be over.
 */
function Running(props: { timer: TimerView; onStop: () => void }) {
  const t = props.timer;
  const offset = useRef(new Date(t.serverNow).getTime() - Date.now());
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, []);

  const end = new Date(t.endsAt).getTime();
  const total = t.plannedMinutes * 60_000;
  const left = Math.max(0, end - (now + offset.current));
  const mm = String(Math.floor(left / 60000)).padStart(2, '0');
  const ss = String(Math.floor((left % 60000) / 1000)).padStart(2, '0');
  return (
    <div className="page">
      <h1 className="page-title">Focus</h1>
      <p className="page-sub">You can close the app. The question waits for you.</p>
      <Card glow>
        <Ring progress={1 - left / total} size={280}>
          <div className="ring-time">
            {mm}:{ss}
          </div>
          <div className="muted small">{t.label}</div>
        </Ring>
        <button className="btn block" onClick={props.onStop}>
          <Square size={16} /> Stop early
        </button>
      </Card>
    </div>
  );
}
