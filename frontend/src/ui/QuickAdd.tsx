import { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { api, type Meta, type Suggestion } from '../api';
import { Card } from './Card';
import { StatTagPicker } from './StatTagPicker';

/**
 * Add a task with its name plus ONE chip (brief 3.1): the duration chips are the submit
 * buttons, and each shows the XP it will pay once ticked. Enter submits with the preselected
 * chip (the value last used for that name, else the last value used). Stat tags: 0 to 7,
 * preselected from the last use of the same name. Adding never pays: only ticking does.
 */
export function QuickAdd(props: {
  meta: Meta;
  multiplier: number;
  onAdded: () => void;
  onError: (msg: string) => void;
}) {
  const [name, setName] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagsTouched, setTagsTouched] = useState(false);
  const [matches, setMatches] = useState<Suggestion[]>([]);
  const [custom, setCustom] = useState('');

  const exact = matches.find((m) => m.name.toLowerCase() === name.trim().toLowerCase());
  const preselected = exact?.expectedMinutes ?? props.meta.lastTaskMinutes;
  const typed = parseInt(custom, 10);
  const minutesNow = typed > 0 ? typed : preselected;
  const xpFor = (minutes: number) => Math.round(minutes * props.meta.todo.xpPerMinute * props.multiplier);

  useEffect(() => {
    if (name.trim() === '') {
      setMatches([]);
      return;
    }
    let cancelled = false;
    api.suggestions(name.trim()).then((s) => !cancelled && setMatches(s)).catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [name]);

  // Preselect the tags remembered for this name, until the owner touches them.
  useEffect(() => {
    if (!tagsTouched) setTags(exact?.tags ?? []);
  }, [exact?.name, tagsTouched]); // eslint-disable-line react-hooks/exhaustive-deps

  async function add(taskName: string, minutes: number, taskTags?: string[]) {
    if (taskName.trim() === '') return;
    try {
      await api.createTask(taskName.trim(), minutes, taskTags);
      setName('');
      setCustom('');
      setTags([]);
      setTagsTouched(false);
      props.onAdded();
    } catch (e) {
      props.onError((e as Error).message);
    }
  }

  const sendTags = tagsTouched ? tags : undefined;

  return (
    <Card title="New quest" icon={Plus} iconColor="var(--cyan)">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          add(name, minutesNow, sendTags);
        }}
      >
        <input
          className="input"
          placeholder="What will you do?"
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-label="task name"
          autoComplete="off"
        />
      </form>

      {matches.length > 0 && !exact && (
        <div className="chips" style={{ marginTop: 10 }}>
          {matches.slice(0, 4).map((m) => (
            <button key={m.name} className="chip pill" onClick={() => add(m.name, m.expectedMinutes, m.tags)}>
              <Plus size={14} /> {m.name} <span className="faint">{m.expectedMinutes}m</span>
            </button>
          ))}
        </div>
      )}

      <div className="label" style={{ margin: '16px 0 8px' }}>
        Stats it trains
      </div>
      <StatTagPicker
        stats={props.meta.stats}
        selected={tags}
        baseXp={minutesNow * props.meta.todo.xpPerMinute}
        onChange={(t) => {
          setTags(t);
          setTagsTouched(true);
        }}
      />

      <div className="label" style={{ margin: '16px 0 8px' }}>
        Expected time · tap to add
      </div>
      <div className="chips" aria-label="expected minutes">
        {props.meta.todo.minuteChips.map((m) => (
          <button
            key={m}
            type="button"
            className={'chip' + (m === preselected && !(typed > 0) ? ' selected' : '')}
            disabled={name.trim() === ''}
            aria-label={`${m} minutes`}
            onClick={() => add(name, m, sendTags)}
          >
            <span className="chip-main">{m < 60 ? `${m}m` : `${m / 60}h`}</span>
            <span className="chip-sub">+{xpFor(m)}</span>
          </button>
        ))}
        <input
          className="input small"
          inputMode="numeric"
          placeholder="min"
          aria-label="custom minutes"
          value={custom}
          onChange={(e) => setCustom(e.target.value.replace(/\D/g, ''))}
          onKeyDown={(e) => e.key === 'Enter' && add(name, minutesNow, sendTags)}
        />
      </div>
    </Card>
  );
}
