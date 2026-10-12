import type { Stat } from '../api';

/** Short label of a stat: STRENGTH -> STR. */
export const short = (statId: string) => statId.slice(0, 3);

/**
 * One-tap stat tag chips (brief 8). Optional: no tag = currency only.
 * Several tags split the stat XP equally (done by the backend).
 */
export function TagChips(props: { stats: Stat[]; selected: string[]; onChange: (tags: string[]) => void }) {
  const toggle = (id: string) =>
    props.onChange(props.selected.includes(id) ? props.selected.filter((t) => t !== id) : [...props.selected, id]);
  return (
    <div className="chips" aria-label="stat tags">
      {props.stats.map((s) => (
        <button
          key={s.id}
          type="button"
          className={'chip tag' + (props.selected.includes(s.id) ? ' selected' : '')}
          title={`${s.name}: ${s.hint}`}
          aria-pressed={props.selected.includes(s.id)}
          onClick={() => toggle(s.id)}
        >
          {short(s.id)}
        </button>
      ))}
    </div>
  );
}
