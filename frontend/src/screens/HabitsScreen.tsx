import { useEffect, useState } from 'react';
import { CalendarCheck, Check, Flame, Moon, Plus, Snowflake, X } from 'lucide-react';
import { api, type Day, type GameState, type HabitView, type Meta } from '../api';
import { statStyle } from '../stats';
import { Card } from '../ui/Card';
import { StatTagPicker } from '../ui/StatTagPicker';
import type { Act } from '../types';
import { TagIcons } from './TasksScreen';

const ALL: Day[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const PRESETS: { label: string; days: Day[] }[] = [
  { label: 'Every day', days: ALL },
  { label: 'Weekdays', days: ALL.slice(0, 5) },
  { label: 'Weekends', days: ALL.slice(5) },
];

/** Habits: one Yes tap on a scheduled day pays once (brief 6). */
export function HabitsScreen(props: {
  meta: Meta;
  state: GameState;
  act: Act;
  version: number;
  refresh: () => void;
  onError: (m: string) => void;
}) {
  const [habits, setHabits] = useState<HabitView[]>([]);
  const [rest, setRest] = useState<string[]>([]);
  const [name, setName] = useState('');
  const [tags, setTags] = useState<string[]>(['DISCIPLINE']);
  const mult = props.state.multipliers.streak * props.state.multipliers.weapon * props.state.multipliers.ascension;

  useEffect(() => {
    api.habits().then(setHabits).catch(() => {});
    api.restDays().then(setRest).catch(() => {});
  }, [props.version]);

  const create = (days: Day[]) =>
    api
      .createHabit(name.trim(), days, tags)
      .then(() => {
        setName('');
        setTags(['DISCIPLINE']);
        props.refresh();
      })
      .catch((e) => props.onError(e.message));

  const today = habits.filter((h) => h.scheduledToday);
  const other = habits.filter((h) => !h.scheduledToday);
  const s = props.state.streak;

  return (
    <div className="page">
      <h1 className="page-title">Habits</h1>
      <div className="row wrap" style={{ marginBottom: 16 }}>
        <span className="pill gold">
          <Flame size={14} /> {s.days} day streak · x{s.multiplierToday.toFixed(2)}
        </span>
        <span className="pill cyan">
          <Snowflake size={14} /> {s.freezes} freeze{s.freezes === 1 ? '' : 's'} ready
        </span>
        <span className="pill violet">
          <Moon size={14} /> {rest.length === 0 ? 'no rest day planned' : `rest: ${rest.map(shortDate).join(', ')}`}
        </span>
      </div>

      <div className="grid two">
        <div className="grid">
          <Card title={`Today · ${today.filter((h) => h.doneToday).length}/${today.length}`} icon={CalendarCheck} iconColor="var(--cyan)">
            {today.length === 0 && <p className="muted small">No habit scheduled today.</p>}
            {today.map((h) => (
              <HabitRow key={h.id} habit={h} xp={Math.round(props.meta.habitXp * mult)} act={props.act} refresh={props.refresh} />
            ))}
          </Card>
          {other.length > 0 && (
            <Card title="Off today">
              {other.map((h) => (
                <HabitRow key={h.id} habit={h} xp={0} act={props.act} refresh={props.refresh} />
              ))}
            </Card>
          )}
        </div>

        <Card title="New habit" icon={Plus} iconColor="var(--cyan)">
          <input className="input" placeholder="Habit name" value={name} onChange={(e) => setName(e.target.value)} aria-label="habit name" />
          <div className="label" style={{ margin: '16px 0 8px' }}>
            Stats it trains
          </div>
          <StatTagPicker stats={props.meta.stats} selected={tags} onChange={setTags} baseXp={props.meta.habitXp} />
          <div className="label" style={{ margin: '16px 0 8px' }}>
            Schedule · tap to create
          </div>
          <div className="chips">
            {PRESETS.map((p) => (
              <button key={p.label} className="chip pill" disabled={name.trim() === ''} onClick={() => create(p.days)}>
                {p.label}
              </button>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}

function HabitRow(props: { habit: HabitView; xp: number; act: Act; refresh: () => void }) {
  const h = props.habit;
  const color = h.tags[0] ? statStyle(h.tags[0]).color : 'var(--cyan)';
  return (
    <div className={'list-item' + (h.scheduledToday ? '' : ' off')} style={{ display: 'block', opacity: h.scheduledToday ? 1 : 0.6 }}>
      <div className="row">
        {h.scheduledToday ? (
          <button
            className={'check' + (h.doneToday ? ' done' : '')}
            style={{ '--c': color } as React.CSSProperties}
            aria-label={`yes: ${h.name}`}
            disabled={h.doneToday}
            onClick={() => props.act(api.habitDone(h.id))}
          >
            <Check size={22} />
          </button>
        ) : (
          <span className="check" style={{ '--c': 'var(--text-3)', cursor: 'default' } as React.CSSProperties} />
        )}
        <div className="grow">
          <div className="item-name">{h.name}</div>
          <div className="meta-row tiny faint">
            <Flame size={13} className={h.streak > 0 ? 'flame' : 'flame off'} /> {h.streak}
            <span>·</span>
            {h.days.length === 7 ? 'every day' : h.days.map((d) => d.slice(0, 2).toLowerCase()).join(' ')}
            <TagIcons tags={h.tags} />
          </div>
        </div>
        {h.scheduledToday && !h.doneToday && <span className="reward">+{props.xp}</span>}
        <button className="icon-btn" aria-label={`remove ${h.name}`} title="Remove" onClick={() => api.archiveHabit(h.id).then(props.refresh)}>
          <X size={16} />
        </button>
      </div>
      <div className="heat" style={{ '--c': color } as React.CSSProperties} aria-label="last 28 days">
        {h.history.map((d) => (
          <span key={d.date} className={d.status} title={`${d.date}: ${d.status.toLowerCase()}`} />
        ))}
      </div>
    </div>
  );
}

function shortDate(iso: string) {
  return new Date(iso + 'T12:00:00Z').toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', timeZone: 'UTC' });
}
