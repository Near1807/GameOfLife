/**
 * The pixel-art hero shown as framed artwork inside the modern UI (v2 section 3).
 * Scaled by a whole number, never smoothed (brief 12). `hitKey` changing replays a small
 * attack lunge.
 */
export function HeroPortrait(props: { scale?: number; frame?: number; hitKey?: number }) {
  const scale = props.scale ?? 4;
  const w = 32 * scale;
  const frame = props.frame ?? w + 24;
  return (
    <div className="portrait" style={{ width: frame, height: frame }}>
      <div
        key={props.hitKey}
        className={'sprite hero' + (props.hitKey ? ' attack' : '')}
        role="img"
        aria-label="your hero"
        style={
          {
            width: w,
            height: w,
            backgroundSize: `${128 * scale}px ${w}px`,
            '--frame-w': `${w}px`,
          } as React.CSSProperties
        }
      />
    </div>
  );
}
