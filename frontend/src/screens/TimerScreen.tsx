import { useEffect, useRef, useState } from 'react';
import { api, type Meta, type TaskView, type TimerView } from '../api';
import { TagChips } from '../components/TagChips';

/**
 * Focus timer. Start = one tap on a length chip. The countdown is only a display: the
 * server measures time from its own timestamps (brief 3.2). When the block ends, the
 * Yes / No question appears (TimerQuestion, shown by App on every screen).
 */
export function TimerScreen(props: {
  meta: Meta;
  timer: TimerView | null;
  taskId: number | null;
  clearTask: () => void;
  onTimer: (t: TimerView | null) => void;
  onError: (msg: string) => void;
}) {
  const [label, setLabel] = useState('');
  const [tags, setTags] = useState<string[] | null>(null);
  const [task, setTask] = useState<TaskView | null>(null);

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

  return (
    <div className="panel">
      <h2>focus block</h2>
      {task ? (
        <div className="row" style={{ marginBottom: 10 }}>
          <span className="grow">
            on: <b className="cyan">{task.name}</b>
          </span>
          <button className="icon-btn" onClick={props.clearTask} aria-label="no task">
            ✕
          </button>
        </div>
      ) : (
        <input
          className="input"
          placeholder="Focus (optional label)"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          aria-label="timer label"
        />
      )}
      <p className="dim small">Tap a length to start. You will be asked at the end.</p>
      <div className="chips" aria-label="timer length">
        {props.meta.timer.minuteChips.map((m) => (
          <button key={m} className={'chip' + (m === props.meta.timer.defaultMinutes ? ' selected' : '')} onClick={() => start(m)}>
            {m}m
          </button>
        ))}
      </div>
      <div style={{ marginTop: 10 }}>
        <TagChips stats={props.meta.stats} selected={tags ?? task?.tags ?? props.meta.timer.defaultTags} onChange={setTags} />
      </div>
    </div>
  );
}

/**
 * Countdown computed from the server's endsAt, corrected by the server/device clock offset.
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
    <div className="panel">
      <h2>focusing: {t.label}</h2>
      <div className="countdown">
        {mm}:{ss}
      </div>
      <div className="bar">
        <div style={{ width: `${100 - (left / total) * 100}%` }} />
      </div>
      <p className="dim small">You can close the app. The question waits for you.</p>
      <button className="btn" onClick={props.onStop}>
        stop early
      </button>
    </div>
  );
}
