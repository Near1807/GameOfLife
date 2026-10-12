import { useEffect, useState } from 'react';
import { api, type Day, type HabitView, type Meta } from '../api';
import { short, TagChips } from '../components/TagChips';
import type { Act } from '../types';

const ALL: Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const PRESETS: { label: string; days: Day[] }[] = [
  { label: 'every day', days: ALL },
  { label: 'weekdays', days: ALL.slice(0, 5) },
  { label: 'weekends', days: ALL.slice(5) },
];

/** Habits: one Yes tap on a scheduled day pays once (brief 6). */
export function HabitsScreen(props: { meta: Meta; act: Act; version: number; refresh: () => void; onError: (m: string) => void }) {
  const [habits, setHabits] = useState<HabitView[]>([]);
  const [name, setName] = useState('');
  const [tags, setTags] = useState<string[] | null>(null);

  useEffect(() => {
    api.habits().then(setHabits).catch(() => {});
  }, [props.version]);

  const create = (days: Day[]) =>
    api
      .createHabit(name.trim(), days, tags ?? undefined)
      .then(() => {
        setName('');
        setTags(null);
        props.refresh();
      })
      .catch((e) => props.onError(e.message));

  const today = habits.filter((h) => h.scheduledToday);
  const other = habits.filter((h) => !h.scheduledToday);

  return (
    <>
      <div className="panel">
        <h2>today's habits</h2>
        {today.length === 0 && <div className="dim">no habit scheduled today.</div>}
        {today.map((h) => (
          <div key={h.id} className={'item' + (h.doneToday ? ' done' : '')}>
            <button
              className={'tick' + (h.doneToday ? ' done' : '')}
              aria-label={`yes: ${h.name}`}
              disabled={h.doneToday}
              onClick={() => props.act(api.habitDone(h.id))}
            >
              ✓
            </button>
            <div className="grow name">
              {h.name}
              <div className="dim small">
                +{props.meta.habitXp} base {h.tags.map(short).join(' ')}
              </div>
            </div>
            <button className="icon-btn" aria-label={`remove ${h.name}`} onClick={() => api.archiveHabit(h.id).then(props.refresh)}>
              ✕
            </button>
          </div>
        ))}
      </div>

      {other.length > 0 && (
        <div className="panel">
          <h2>not today</h2>
          {other.map((h) => (
            <div key={h.id} className="item dim">
              <div className="grow">{h.name}</div>
              <span className="small">{h.days.map((d) => d.slice(0, 2)).join(' ')}</span>
            </div>
          ))}
        </div>
      )}

      <div className="panel">
        <h2>new habit</h2>
        <input className="input" placeholder="habit name" value={name} onChange={(e) => setName(e.target.value)} aria-label="habit name" />
        <div className="chips" style={{ marginTop: 10 }}>
          {PRESETS.map((p) => (
            <button key={p.label} className="chip" disabled={name.trim() === ''} onClick={() => create(p.days)}>
              {p.label}
            </button>
          ))}
        </div>
        {name.trim() !== '' && (
          <div style={{ marginTop: 10 }}>
            <TagChips stats={props.meta.stats} selected={tags ?? ['DISCIPLINE']} onChange={setTags} />
          </div>
        )}
      </div>
    </>
  );
}
