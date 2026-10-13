import { useEffect, useState } from 'react';

/**
 * Animated progress bar. It fills from its previous value, so every gain is visible.
 * `from` sets where the first animation starts (default 0), e.g. the hero's HP before a recap.
 */
export function Bar(props: { value: number; from?: number; color?: string; thick?: boolean; label?: string }) {
  const target = Math.max(0, Math.min(1, props.value));
  const [shown, setShown] = useState(Math.max(0, Math.min(1, props.from ?? 0)));
  useEffect(() => {
    const id = requestAnimationFrame(() => setShown(target)); // next frame, so the CSS transition runs
    return () => cancelAnimationFrame(id);
  }, [target]);
  return (
    <div
      className={'bar' + (props.thick ? ' thick' : '')}
      role="progressbar"
      aria-label={props.label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(target * 100)}
    >
      <div className="bar-fill" style={{ width: `${shown * 100}%`, '--c': props.color ?? 'var(--cyan)' } as React.CSSProperties} />
    </div>
  );
}
