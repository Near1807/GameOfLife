import { useEffect, useRef, useState } from 'react';

const reducedMotion = () =>
  typeof window !== 'undefined' &&
  (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches || document.querySelector('.reduced') !== null);

/** A whole number that counts up (or down) to its new value in about 0.7 s. */
export function CountUp(props: { value: number; className?: string; prefix?: string }) {
  const [shown, setShown] = useState(props.value);
  const from = useRef(props.value);

  useEffect(() => {
    const start = from.current;
    const end = props.value;
    if (start === end || reducedMotion()) {
      setShown(end);
      from.current = end;
      return;
    }
    const t0 = performance.now();
    let frame = 0;
    const step = (t: number) => {
      const k = Math.min(1, (t - t0) / 700);
      const eased = 1 - Math.pow(1 - k, 3);
      setShown(start + (end - start) * eased);
      if (k < 1) frame = requestAnimationFrame(step);
      else from.current = end;
    };
    frame = requestAnimationFrame(step);
    return () => {
      cancelAnimationFrame(frame);
      from.current = end;
    };
  }, [props.value]);

  return (
    <span className={props.className}>
      {props.prefix}
      {Math.round(shown).toLocaleString('en-US')}
    </span>
  );
}
