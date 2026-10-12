export interface Pop {
  id: number;
  text: string;
  sub: string;
}

/** Floating "+33 XP" numbers. pointer-events: none, so they never block the next tap. */
export function XpPops(props: { pops: Pop[] }) {
  return (
    <div className="pop-layer" aria-live="polite">
      {props.pops.map((p) => (
        <div key={p.id} className="pop">
          {p.text}
          {p.sub && <span className="sub">{p.sub}</span>}
        </div>
      ))}
    </div>
  );
}
