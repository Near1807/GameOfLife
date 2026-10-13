import { useState } from 'react';

/**
 * Small SVG charts for the review and the dashboard. One series each, so no legend: the card
 * title names it. Hover (or tap) a day to read its exact value; thin marks, rounded bar tops,
 * recessive grid.
 */

const W = 420;
const H = 180;
const PAD = { l: 36, r: 8, t: 12, b: 24 };

function dayLabel(iso: string, long = false) {
  const d = new Date(iso + 'T12:00:00Z');
  return d.toLocaleDateString('en-GB', long ? { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }
    : { weekday: 'narrow', timeZone: 'UTC' });
}

/** Rounds the axis maximum up to 1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6 or 8 x a power of ten. */
function niceMax(v: number) {
  if (v <= 0) return 10;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const step = [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find((k) => k * p >= v) ?? 10;
  return step * p;
}

/** XP per day as bars, with an optional dashed goal line. */
export function XpBars(props: { days: { date: string; xp: number; future?: boolean }[]; goal?: number; label: string }) {
  const [hover, setHover] = useState<number | null>(null);
  const n = props.days.length;
  const max = niceMax(Math.max(props.goal ?? 0, ...props.days.map((d) => d.xp)));
  const iw = W - PAD.l - PAD.r;
  const ih = H - PAD.t - PAD.b;
  const slot = iw / n;
  const bw = Math.max(3, Math.min(28, slot - 4));
  const y = (v: number) => PAD.t + ih - (Math.max(0, v) / max) * ih;
  const every = n > 14 ? Math.ceil(n / 8) : 1;
  const h = hover !== null ? props.days[hover] : null;

  return (
    <div className="chart">
      <div className="chart-tip" style={{ visibility: h ? 'visible' : 'hidden' }}>
        {h ? `${dayLabel(h.date, true)} · ${Math.round(h.xp)} XP` : '.'}
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={props.label} onMouseLeave={() => setHover(null)}>
        {[0, 0.5, 1].map((k) => (
          <g key={k}>
            <line x1={PAD.l} x2={W - PAD.r} y1={y(max * k)} y2={y(max * k)} className="grid-line" />
            <text x={PAD.l - 6} y={y(max * k) + 4} textAnchor="end" className="axis">{Math.round(max * k)}</text>
          </g>
        ))}
        {props.days.map((d, i) => {
          const x = PAD.l + i * slot + (slot - bw) / 2;
          const top = y(d.xp);
          const hgt = PAD.t + ih - top;
          return (
            <g key={d.date} onMouseEnter={() => setHover(i)} onClick={() => setHover(i)}>
              <rect x={PAD.l + i * slot} y={PAD.t} width={slot} height={ih} fill="transparent" />
              {hgt > 0 && (
                <path
                  d={`M${x},${top + hgt} V${top + Math.min(4, hgt)} Q${x},${top} ${x + Math.min(4, bw / 2)},${top} H${x + bw - Math.min(4, bw / 2)} Q${x + bw},${top} ${x + bw},${top + Math.min(4, hgt)} V${top + hgt} Z`}
                  className={'bar-mark' + (hover === i ? ' on' : '')}
                />
              )}
              {i % every === 0 && (
                <text x={PAD.l + i * slot + slot / 2} y={H - 6} textAnchor="middle" className="axis">
                  {n <= 7 ? dayLabel(d.date) : d.date.slice(8)}
                </text>
              )}
            </g>
          );
        })}
        {props.goal !== undefined && (
          <g>
            <line x1={PAD.l} x2={W - PAD.r} y1={y(props.goal)} y2={y(props.goal)} className="goal-line" />
            <text x={PAD.l + 4} y={y(props.goal) - 4} textAnchor="start" className="axis">goal {props.goal}</text>
          </g>
        )}
      </svg>
    </div>
  );
}

/** A single line over days (streak history), with a crosshair on hover. */
export function StreakLine(props: { days: { date: string; streak: number }[]; label: string }) {
  const [hover, setHover] = useState<number | null>(null);
  const n = props.days.length;
  const max = niceMax(Math.max(3, ...props.days.map((d) => d.streak)));
  const iw = W - PAD.l - PAD.r;
  const ih = H - PAD.t - PAD.b;
  const x = (i: number) => PAD.l + (n === 1 ? iw / 2 : (i / (n - 1)) * iw);
  const y = (v: number) => PAD.t + ih - (v / max) * ih;
  const line = props.days.map((d, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(d.streak)}`).join(' ');
  const area = `${line} L${x(n - 1)},${PAD.t + ih} L${x(0)},${PAD.t + ih} Z`;
  const h = hover !== null ? props.days[hover] : null;
  const every = Math.ceil(n / 8);

  return (
    <div className="chart">
      <div className="chart-tip" style={{ visibility: h ? 'visible' : 'hidden' }}>
        {h ? `${dayLabel(h.date, true)} · ${h.streak} day streak` : '.'}
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={props.label}
        onMouseLeave={() => setHover(null)}
        onMouseMove={(e) => {
          const r = (e.currentTarget as SVGSVGElement).getBoundingClientRect();
          const px = ((e.clientX - r.left) / r.width) * W;
          setHover(Math.max(0, Math.min(n - 1, Math.round(((px - PAD.l) / iw) * (n - 1)))));
        }}>
        <defs>
          <linearGradient id="streak-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#fb923c" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#fb923c" stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0, 0.5, 1].map((k) => (
          <g key={k}>
            <line x1={PAD.l} x2={W - PAD.r} y1={y(max * k)} y2={y(max * k)} className="grid-line" />
            <text x={PAD.l - 6} y={y(max * k) + 4} textAnchor="end" className="axis">{Math.round(max * k)}</text>
          </g>
        ))}
        <path d={area} fill="url(#streak-fill)" />
        <path d={line} fill="none" stroke="#fb923c" strokeWidth={2} strokeLinejoin="round" />
        {props.days.map((d, i) => i % every === 0 && (
          <text key={d.date} x={x(i)} y={H - 6} textAnchor="middle" className="axis">{d.date.slice(8)}</text>
        ))}
        {h && hover !== null && (
          <g>
            <line x1={x(hover)} x2={x(hover)} y1={PAD.t} y2={PAD.t + ih} className="crosshair" />
            <circle cx={x(hover)} cy={y(h.streak)} r={5} fill="#fb923c" stroke="var(--bg)" strokeWidth={2} />
          </g>
        )}
      </svg>
    </div>
  );
}
