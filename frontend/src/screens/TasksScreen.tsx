import { useEffect, useState } from 'react';
import { api, whole, type Meta, type Suggestion, type TaskView } from '../api';
import { QuickAdd } from '../components/QuickAdd';
import { short } from '../components/TagChips';
import type { Act } from '../types';

/**
 * To-do list. A task on the list is ticked with ONE tap (the tick is the confirmation).
 * Known tasks appear as repeat chips: one tap puts one back on the list (it pays nothing
 * until ticked, brief 3.2).
 */
export function TasksScreen(props: {
  meta: Meta;
  act: Act;
  version: number;
  refresh: () => void;
  onError: (msg: string) => void;
  onFocus: (taskId: number) => void;
}) {
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [repeats, setRepeats] = useState<Suggestion[]>([]);

  useEffect(() => {
    api.tasks().then(setTasks).catch(() => {});
    api.suggestions('').then(setRepeats).catch(() => {});
  }, [props.version]);

  const open = tasks.filter((t) => !t.done);
  const done = tasks.filter((t) => t.done);

  return (
    <>
      <QuickAdd meta={props.meta} onAdded={props.refresh} onError={props.onError} />

      {repeats.length > 0 && (
        <div className="panel">
          <h2>again?</h2>
          <div className="chips">
            {repeats.map((s) => (
              <button
                key={s.name}
                className="chip"
                onClick={() => api.createTask(s.name).then(props.refresh).catch((e) => props.onError(e.message))}
              >
                + {s.name} <span className="dim">{s.expectedMinutes}m</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="panel">
        <h2>quests ({open.length})</h2>
        {open.length === 0 && <div className="dim">nothing open. add something above.</div>}
        {open.map((t) => (
          <div key={t.id} className="item">
            <button className="tick" aria-label={`done: ${t.name}`} onClick={() => props.act(api.tickTask(t.id))}>
              ✓
            </button>
            <div className="grow name">
              {t.name}
              <div className="dim small">
                {t.expectedMinutes}m {t.tags.map(short).join(' ')}
              </div>
            </div>
            <button className="icon-btn" aria-label={`focus on ${t.name}`} onClick={() => props.onFocus(t.id)}>
              ▶
            </button>
            <button
              className="icon-btn"
              aria-label={`remove ${t.name}`}
              onClick={() => api.archiveTask(t.id).then(props.refresh)}
            >
              ✕
            </button>
          </div>
        ))}
      </div>

      {done.length > 0 && (
        <div className="panel">
          <h2>done today</h2>
          {done.map((t) => (
            <div key={t.id} className="item done">
              <button className="tick done" aria-label={`undo: ${t.name}`} onClick={() => props.act(api.untickTask(t.id))}>
                ✓
              </button>
              <div className="grow name">{t.name}</div>
              <span className="gold small">{t.viaTimer ? 'via focus' : `+${whole(t.xpPaid)}`}</span>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
