import type { Stat } from '../api';
import { fmt, splitPreview, statStyle } from '../stats';

/**
 * The one stat tag picker used by every tool (v2 section 4.1): seven coloured icons,
 * multi-select, 0 to 7 tags. With baseXp it also shows how the stat XP will be split.
 */
export function StatTagPicker(props: {
  stats: Stat[];
  selected: string[];
  onChange: (tags: string[]) => void;
  baseXp?: number;
}) {
  const toggle = (id: string) =>
    props.onChange(props.selected.includes(id) ? props.selected.filter((t) => t !== id) : [...props.selected, id]);
  // keep the order of the stat list, whatever the tap order was
  const ordered = props.stats.map((s) => s.id).filter((id) => props.selected.includes(id));

  return (
    <div>
      <div className="tags" role="group" aria-label="stat tags">
        {props.stats.map((s) => {
          const st = statStyle(s.id);
          const on = props.selected.includes(s.id);
          return (
            <button
              key={s.id}
              type="button"
              className={'tag' + (on ? ' on' : '')}
              style={{ '--c': st.color } as React.CSSProperties}
              title={`${s.name}: ${s.hint}`}
              aria-pressed={on}
              aria-label={s.name}
              onClick={() => toggle(s.id)}
            >
              <st.Icon size={20} strokeWidth={2} />
              {st.short}
            </button>
          );
        })}
      </div>
      {props.baseXp !== undefined && <SplitLine baseXp={props.baseXp} tags={ordered} />}
    </div>
  );
}

/** "INT +15 · DIS +15", or a hint that untagged actions only earn currency. */
export function SplitLine(props: { baseXp: number; tags: string[] }) {
  const parts = splitPreview(props.baseXp, props.tags);
  return (
    <div className="split" aria-label="stat split">
      {parts.length === 0 ? (
        <span className="faint tiny">No stat tag: earns coins only.</span>
      ) : (
        parts.map((p) => {
          const st = statStyle(p.id);
          return (
            <span key={p.id} className="split-item" style={{ '--c': st.color } as React.CSSProperties}>
              <st.Icon size={12} /> +{fmt(p.xp)}
            </span>
          );
        })
      )}
    </div>
  );
}
