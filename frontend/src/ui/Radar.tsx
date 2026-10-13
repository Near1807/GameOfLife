import type { StatView } from '../api';
import { statStyle } from '../stats';

/**
 * Radar chart of the stat levels (brief 8). Plain SVG, no chart library.
 * The scale is the highest level rounded up to a multiple of 5, so the shape stays readable.
 */
export function Radar(props: { stats: StatView[]; size?: number }) {
  const size = props.size ?? 300;
  const cx = size / 2;
  const cy = size / 2;
  const radius = size / 2 - 42;
  const n = props.stats.length;
  const maxLevel = Math.max(5, Math.ceil(Math.max(...props.stats.map((s) => s.level)) / 5) * 5);
  const angle = (i: number) => -Math.PI / 2 + (i * 2 * Math.PI) / n;
  const point = (i: number, r: number) => [cx + Math.cos(angle(i)) * r, cy + Math.sin(angle(i)) * r];

  // level + progress inside the level, so even small gains move the shape
  const value = (s: StatView) => Math.min(maxLevel, s.level + s.progress);
  const shape = props.stats.map((s, i) => point(i, (value(s) / maxLevel) * radius).join(',')).join(' ');

  return (
    <svg className="radar" width="100%" viewBox={`0 0 ${size} ${size}`} role="img" aria-label="stat balance radar">
      <defs>
        <linearGradient id="radar-fill" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#8b5cf6" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#22d3ee" stopOpacity="0.35" />
        </linearGradient>
      </defs>
      {[0.25, 0.5, 0.75, 1].map((k) => (
        <polygon
          key={k}
          points={props.stats.map((_, i) => point(i, radius * k).join(',')).join(' ')}
          fill="none"
          stroke="rgba(160,170,255,0.12)"
        />
      ))}
      {props.stats.map((_, i) => {
        const [x, y] = point(i, radius);
        return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke="rgba(160,170,255,0.10)" />;
      })}
      <polygon className="shape" points={shape} fill="url(#radar-fill)" stroke="#a78bfa" strokeWidth={2} strokeLinejoin="round" />
      {props.stats.map((s, i) => {
        const [x, y] = point(i, (value(s) / maxLevel) * radius);
        return <circle key={s.id} cx={x} cy={y} r={4} fill={statStyle(s.id).color} />;
      })}
      {props.stats.map((s, i) => {
        const [x, y] = point(i, radius + 24);
        const st = statStyle(s.id);
        return (
          <g key={s.id}>
            <text x={x} y={y - 2} textAnchor="middle" dominantBaseline="middle" style={{ fill: st.color }}>
              {st.short}
            </text>
            <text x={x} y={y + 12} textAnchor="middle" dominantBaseline="middle">
              {s.level}
            </text>
          </g>
        );
      })}
    </svg>
  );
}
