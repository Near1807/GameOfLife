import { useEffect, useState } from 'react';
import { CheckCircle2, Clock, Play, Plus, RotateCcw, Swords, X } from 'lucide-react';
import { api, type Meta, type Suggestion, type TaskView } from '../api';
import { statStyle } from '../stats';
import { Card } from '../ui/Card';
import { QuickAdd } from '../ui/QuickAdd';
import type { Act } from '../types';

/**
 * Quests (the to-do list). A task on the list is ticked with ONE tap: the tick is the
 * confirmation. Known tasks appear as "again" chips: one tap puts one back on the list (it
 * pays nothing until ticked, brief 3.2).
 */
export function TasksScreen(props: {
  meta: Meta;
  multiplier: number;
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
  const xp = (t: TaskView) => Math.round(t.expectedMinutes * props.meta.todo.xpPerMinute * props.multiplier);

  return (
    <div className="page">
      <h1 className="page-title">Quests</h1>
      <p className="page-sub">Real tasks, real XP. Tick one when it is done.</p>
      <div className="grid two">
        <QuickAdd meta={props.meta} multiplier={props.multiplier} onAdded={props.refresh} onError={props.onError} />

        <div className="grid">
          <Card title={`Open quests · ${open.length}`} icon={Swords}>
            {open.length === 0 && <p className="muted small">Nothing open. Add a quest, or bring one back below.</p>}
            {open.map((t) => {
              const first = t.tags[0] ? statStyle(t.tags[0]).color : 'var(--cyan)';
              return (
                <div key={t.id} className="list-item">
                  <button
                    className="check"
                    style={{ '--c': first } as React.CSSProperties}
                    aria-label={`done: ${t.name}`}
                    onClick={() => props.act(api.tickTask(t.id))}
                  >
                    <CheckCircle2 size={22} />
                  </button>
                  <div className="grow">
                    <div className="item-name">{t.name}</div>
                    <div className="meta-row tiny faint">
                      <Clock size={12} /> {t.expectedMinutes} min
                      <TagIcons tags={t.tags} />
                    </div>
                  </div>
                  <span className="reward">+{xp(t)}</span>
                  <button className="icon-btn" aria-label={`focus on ${t.name}`} title="Focus on it" onClick={() => props.onFocus(t.id)}>
                    <Play size={17} />
                  </button>
                  <button className="icon-btn" aria-label={`remove ${t.name}`} title="Remove" onClick={() => api.archiveTask(t.id).then(props.refresh)}>
                    <X size={17} />
                  </button>
                </div>
              );
            })}
          </Card>

          {repeats.length > 0 && (
            <Card title="Again?" icon={RotateCcw}>
              <div className="chips">
                {repeats.map((s) => (
                  <button
                    key={s.name}
                    className="chip pill"
                    onClick={() => api.createTask(s.name).then(props.refresh).catch((e) => props.onError(e.message))}
                  >
                    <Plus size={14} /> {s.name} <TagIcons tags={s.tags} />
                  </button>
                ))}
              </div>
            </Card>
          )}

          {done.length > 0 && (
            <Card title={`Done today · ${done.length}`} icon={CheckCircle2} iconColor="var(--success)">
              {done.map((t) => {
                const first = t.tags[0] ? statStyle(t.tags[0]).color : 'var(--cyan)';
                return (
                  <div key={t.id} className="list-item done">
                    <button
                      className="check done"
                      style={{ '--c': first } as React.CSSProperties}
                      aria-label={`undo: ${t.name}`}
                      title="Un-tick"
                      onClick={() => props.act(api.untickTask(t.id))}
                    >
                      <CheckCircle2 size={22} />
                    </button>
                    <div className="grow item-name">{t.name}</div>
                    <span className="reward">{t.viaTimer ? 'via focus' : `+${Math.round(t.xpPaid)}`}</span>
                  </div>
                );
              })}
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

export function TagIcons(props: { tags: string[] }) {
  return (
    <span className="dots">
      {props.tags.map((id) => {
        const st = statStyle(id);
        return <st.Icon key={id} size={13} style={{ color: st.color }} aria-label={id} />;
      })}
    </span>
  );
}
