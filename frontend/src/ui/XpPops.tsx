import { fmt, statStyle } from '../stats';

export interface Pop {
  id: number;
  xp: number;
  splits: { id: string; xp: number }[];
}

/** Floating "+33 XP" with the stat XP it fed. pointer-events: none, never blocks a tap. */
export function XpPops(props: { pops: Pop[] }) {
  return (
    <div className="pop-layer" aria-live="polite">
      {props.pops.map((p) => (
        <div key={p.id} className="pop">
          <div className="pop-xp">+{Math.round(p.xp)} XP</div>
          {p.splits.length > 0 && (
            <div className="pop-splits">
              {p.splits.map((s) => {
                const st = statStyle(s.id);
                return (
                  <span key={s.id} className="split-item" style={{ '--c': st.color } as React.CSSProperties}>
                    <st.Icon size={12} /> +{fmt(s.xp)}
                  </span>
                );
              })}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
