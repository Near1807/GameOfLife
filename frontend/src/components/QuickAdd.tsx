import { useEffect, useState } from 'react';
import { api, type Meta, type Suggestion } from '../api';
import { TagChips } from './TagChips';

/**
 * Add a task with its name plus ONE chip (brief 3.1): the minute chips are the submit
 * buttons. Enter submits with the preselected chip (the value last used for that name,
 * else the last value used). Autocomplete: tap a known name to add it with its remembered
 * minutes and tags. Adding never pays: only ticking does.
 */
export function QuickAdd(props: { meta: Meta; onAdded: () => void; onError: (msg: string) => void }) {
  const [name, setName] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagsTouched, setTagsTouched] = useState(false);
  const [matches, setMatches] = useState<Suggestion[]>([]);
  const [custom, setCustom] = useState('');

  const exact = matches.find((m) => m.name.toLowerCase() === name.trim().toLowerCase());
  const preselected = exact?.expectedMinutes ?? props.meta.lastTaskMinutes;

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

  return (
    <div className="panel">
      <h2>new task</h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const typed = parseInt(custom, 10);
          add(name, typed > 0 ? typed : preselected, tagsTouched ? tags : undefined);
        }}
      >
        <input
          className="input"
          placeholder="what will you do?"
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-label="task name"
          autoComplete="off"
        />
      </form>

      {matches.length > 0 && !exact && (
        <div className="chips" style={{ marginTop: 8 }}>
          {matches.slice(0, 4).map((m) => (
            <button key={m.name} className="chip" onClick={() => add(m.name, m.expectedMinutes, m.tags)}>
              + {m.name} <span className="dim">{m.expectedMinutes}m</span>
            </button>
          ))}
        </div>
      )}

      <div className="chips" style={{ marginTop: 10 }} aria-label="expected minutes">
        {props.meta.todo.minuteChips.map((m) => (
          <button
            key={m}
            type="button"
            className={'chip' + (m === preselected ? ' selected' : '')}
            disabled={name.trim() === ''}
            onClick={() => add(name, m, tagsTouched ? tags : undefined)}
          >
            {m}m
          </button>
        ))}
        <input
          className="input"
          style={{ width: 90, minHeight: 44 }}
          inputMode="numeric"
          placeholder="min"
          aria-label="custom minutes"
          value={custom}
          onChange={(e) => setCustom(e.target.value.replace(/\D/g, ''))}
          onKeyDown={(e) => e.key === 'Enter' && add(name, parseInt(custom, 10) || preselected, tagsTouched ? tags : undefined)}
        />
      </div>

      {name.trim() !== '' && (
        <div style={{ marginTop: 10 }}>
          <TagChips
            stats={props.meta.stats}
            selected={tags}
            onChange={(t) => {
              setTags(t);
              setTagsTouched(true);
            }}
          />
        </div>
      )}
    </div>
  );
}
