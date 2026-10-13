import { useEffect, useRef } from 'react';
import type { DefenseView, ReplayFrame, Watch } from '../api';

/**
 * The tower-defense map (v4 brief 4): minimalist vector art on a canvas, as in the prototype.
 * Path, spots, towers (coloured shapes with upgrade pips) and, during playback, enemies and effects.
 *
 * This component never simulates anything. A replay is a list of server samples (10 per second);
 * between two samples the enemies are moved in a straight line so the motion looks smooth.
 */
export function DefenseMap(props: {
  view: DefenseView;
  selected: number | null;
  onSelect: (spot: number | null) => void;
  watch: Watch | null;
  speed: number;
  onEnd: () => void;
  filter?: string | null;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const { view, selected, watch, speed } = props;
  const onEnd = useRef(props.onEnd);
  onEnd.current = props.onEnd;
  const speedRef = useRef(speed);
  speedRef.current = speed;

  // static picture (no replay), redrawn when the build or the selection changes
  useEffect(() => {
    if (watch) return;
    const ctx = setup(canvas.current, view);
    if (ctx) drawScene(ctx, view, selected, null, 0, []);
  }, [view, selected, watch]);

  // replay playback
  useEffect(() => {
    if (!watch) return;
    const ctx = setup(canvas.current, view);
    if (!ctx) {
      onEnd.current(); // no canvas (tests, very old browsers): go straight to the result
      return;
    }
    const { frames, fps } = watch.result.replay;
    let t = 0; // replay seconds
    let last = performance.now();
    let shown = -1;
    let fx: Fx[] = [];
    let raf = 0;
    let ended = false;
    const tick = (now: number) => {
      const dt = Math.min(0.1, (now - last) / 1000);
      last = now;
      t += dt * speedRef.current;
      const i = Math.min(frames.length - 1, Math.floor(t * fps));
      // effects of every sample passed since the last paint
      for (let j = shown + 1; j <= i; j++) fx = fx.concat(effects(frames[j], view, t));
      shown = Math.max(shown, i);
      fx = fx.filter((f) => t - f.born < f.life);
      drawScene(ctx, view, selected, { a: frames[i], b: frames[Math.min(frames.length - 1, i + 1)],
        k: Math.min(1, t * fps - i), watch }, t, fx);
      if (i >= frames.length - 1 && t * fps >= frames.length) {
        if (!ended) {
          ended = true;
          onEnd.current();
        }
        return;
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [watch, view, selected]);

  // Each spot is a real (transparent) button over the canvas: big tap target, keyboard and screen reader friendly.
  return (
    <div className="defense-stage">
      <canvas ref={canvas} className="defense-map" style={{ filter: props.filter ?? undefined }}
        role="img" aria-label={`Map ${view.mapName}, wave ${view.wave}`} onClick={() => props.onSelect(null)} />
      {!watch && view.spots.map((s) => (
        <button key={s.index} className="spot-hit" style={{ left: `${(s.x / view.width) * 100}%`, top: `${(s.y / view.height) * 100}%` }}
          aria-pressed={selected === s.index} onClick={() => props.onSelect(selected === s.index ? null : s.index)}
          aria-label={`spot ${s.index + 1}: ${!s.open ? 'locked' : s.tower ? s.tower.name : 'empty'}`} />
      ))}
    </div>
  );
}

interface Fx { kind: 'beam' | 'ring' | 'pop' | 'leak'; born: number; life: number; color: string; a?: number[]; b?: number[]; r?: number }

interface Playback { a: ReplayFrame; b: ReplayFrame; k: number; watch: Watch }

const GRID = 'rgba(80,110,180,.07)';

/** Sizes the canvas for the screen's pixel density; drawing then uses map coordinates. */
function setup(c: HTMLCanvasElement | null, v: DefenseView) {
  if (!c) return null;
  const ctx = c.getContext('2d');
  if (!ctx) return null; // jsdom in tests
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const w = c.clientWidth || v.width;
  c.width = Math.round(w * dpr);
  c.height = Math.round((w * v.height / v.width) * dpr);
  ctx.setTransform(c.width / v.width, 0, 0, c.height / v.height, 0, 0);
  return ctx;
}

/** Effects of one sample: tower shots, chain lightning, deaths, leaks. */
function effects(f: ReplayFrame, v: DefenseView, t: number): Fx[] {
  const out: Fx[] = [];
  f.shots.forEach(([spot, x, y, r]) => {
    const s = v.spots[spot];
    const color = s.tower?.color ?? '#fff';
    if (r > 0) out.push({ kind: 'ring', born: t, life: 0.25, color, a: [x, y], r });
    else out.push({ kind: 'beam', born: t, life: 0.12, color, a: [s.x, s.y], b: [x, y] });
  });
  f.chains.forEach(([x1, y1, x2, y2, spot]) =>
    out.push({ kind: 'beam', born: t, life: 0.15, color: v.spots[spot].tower?.color ?? '#7aa2ff', a: [x1, y1], b: [x2, y2] }));
  f.pops.forEach(([x, y]) => out.push({ kind: 'pop', born: t, life: 0.3, color: '#fff', a: [x, y] }));
  return out;
}

function drawScene(ctx: CanvasRenderingContext2D, v: DefenseView, selected: number | null, p: Playback | null, t: number, fx: Fx[]) {
  const W = v.width;
  const H = v.height;
  ctx.clearRect(0, 0, W, H);
  ctx.strokeStyle = GRID;
  ctx.lineWidth = 1;
  for (let x = 0; x < W; x += 40) line(ctx, x, 0, x, H);
  for (let y = 0; y < H; y += 40) line(ctx, 0, y, W, y);

  // the road
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.strokeStyle = 'rgba(77,225,255,.12)';
  ctx.lineWidth = 34;
  strokePath(ctx, v.path);
  ctx.strokeStyle = '#18233f';
  ctx.lineWidth = 26;
  strokePath(ctx, v.path);
  ctx.setLineDash([6, 10]);
  ctx.strokeStyle = 'rgba(77,225,255,.35)';
  ctx.lineWidth = 2;
  strokePath(ctx, v.path);
  ctx.setLineDash([]);

  // the base
  ctx.fillStyle = 'rgba(255,107,129,.25)';
  circle(ctx, v.base[0], v.base[1], 22);
  ctx.fill();
  ctx.fillStyle = '#ff6b81';
  ctx.font = '700 11px "Space Grotesk Variable", sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('BASE', v.base[0] - 10, v.base[1] + 25);

  // spots
  v.spots.forEach((s) => {
    if (!s.open) {
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = 'rgba(255,200,87,.55)';
      ctx.lineWidth = s.index === selected ? 2.5 : 1.5;
      circle(ctx, s.x, s.y, 19);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = 'rgba(255,200,87,.7)';
      ctx.font = '700 9px "JetBrains Mono Variable", monospace';
      ctx.fillText('LOCK', s.x, s.y + 3);
    } else if (!s.tower) {
      ctx.strokeStyle = s.index === selected ? '#4de1ff' : 'rgba(140,160,210,.45)';
      ctx.lineWidth = s.index === selected ? 2.5 : 1.5;
      circle(ctx, s.x, s.y, 19);
      ctx.stroke();
      ctx.fillStyle = 'rgba(140,160,210,.55)';
      ctx.font = '600 18px "Space Grotesk Variable", sans-serif';
      ctx.fillText('+', s.x, s.y + 6);
    }
  });

  // the selected tower's range
  const sel = selected === null ? null : v.spots[selected];
  if (sel?.tower && sel.tower.stats.range > 0) {
    ctx.fillStyle = 'rgba(77,225,255,.07)';
    ctx.strokeStyle = 'rgba(77,225,255,.5)';
    ctx.lineWidth = 1;
    circle(ctx, sel.x, sel.y, sel.tower.stats.range);
    ctx.fill();
    ctx.stroke();
  }

  // towers
  v.spots.forEach((s) => {
    if (!s.tower) return;
    const tw = s.tower;
    ctx.fillStyle = '#0e1424';
    ctx.strokeStyle = tw.color;
    ctx.lineWidth = s.index === selected ? 3 : 2;
    ctx.shadowColor = tw.color;
    ctx.shadowBlur = 10;
    circle(ctx, s.x, s.y, 17);
    ctx.fill();
    ctx.stroke();
    ctx.shadowBlur = 0;
    drawShape(ctx, tw.shape, s.x, s.y, tw.color);
    ctx.fillStyle = '#fff';
    for (let j = 0; j < tw.a + tw.b; j++) ctx.fillRect(s.x - 10 + j * 8, s.y + 21, 4, 4);
  });

  if (!p) return;
  // enemies, moved in a straight line between two server samples
  const next = new Map(p.b.enemies.map((e) => [e[0], e]));
  p.a.enemies.forEach((e) => {
    const n = next.get(e[0]);
    const x = n ? e[1] + (n[1] - e[1]) * p.k : e[1];
    const y = n ? e[2] + (n[2] - e[2]) * p.k : e[2];
    const type = p.watch.result.replay.types[e[0]];
    const def = v.enemies[type];
    if (!def) return;
    const r = def.radius;
    ctx.fillStyle = def.color;
    ctx.beginPath();
    if (def.air) {
      ctx.moveTo(x, y - r - 2);
      ctx.lineTo(x + r + 2, y);
      ctx.lineTo(x, y + r + 2);
      ctx.lineTo(x - r - 2, y);
      ctx.closePath();
    } else ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
    const flags = e[4];
    const ring = (c: string) => {
      ctx.strokeStyle = c;
      ctx.lineWidth = 2;
      circle(ctx, x, y, r + 3);
      ctx.stroke();
    };
    if (flags & 1) ring('#4de1ff');
    if (flags & 2) ring('#ff9f43');
    if (flags & 4) ring('#b6f24d');
    if (def.armor >= 0.5) {
      ctx.strokeStyle = '#fff';
      ctx.lineWidth = 2;
      circle(ctx, x, y, r - 2);
      ctx.stroke();
    }
    const w = r * 2 + 6;
    ctx.fillStyle = 'rgba(0,0,0,.6)';
    ctx.fillRect(x - w / 2, y - r - 9, w, 3);
    ctx.fillStyle = '#5af0b4';
    ctx.fillRect(x - w / 2, y - r - 9, (w * e[3]) / 100, 3);
  });

  // effects
  fx.forEach((f) => {
    const a = 1 - (t - f.born) / f.life;
    ctx.globalAlpha = Math.max(0, a);
    ctx.strokeStyle = f.color;
    if (f.kind === 'beam' && f.a && f.b) {
      ctx.lineWidth = 2;
      line(ctx, f.a[0], f.a[1], f.b[0], f.b[1]);
    } else if (f.kind === 'ring' && f.a) {
      ctx.lineWidth = 3;
      circle(ctx, f.a[0], f.a[1], (f.r ?? 20) * (1.2 - a * 0.2));
      ctx.stroke();
    } else if (f.kind === 'pop' && f.a) {
      ctx.lineWidth = 2;
      circle(ctx, f.a[0], f.a[1], 8 + (1 - a) * 14);
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
  });

  // HUD line, as in the prototype
  ctx.fillStyle = '#8b97b5';
  ctx.font = '500 13px "JetBrains Mono Variable", monospace';
  ctx.textAlign = 'left';
  ctx.fillText(`Wave ${p.watch.attempt.wave} · lives ${p.a.lives}/${p.watch.result.livesMax} · ${p.a.enemies.length} on map · ${p.a.t.toFixed(1)} s`, 12, H - 12);
}

function drawShape(ctx: CanvasRenderingContext2D, shape: string, x: number, y: number, color: string) {
  ctx.fillStyle = color;
  ctx.strokeStyle = color;
  ctx.beginPath();
  switch (shape) {
    case 'square':
      ctx.rect(x - 7, y - 7, 14, 14);
      break;
    case 'snow':
      for (let a = 0; a < 6; a++) {
        const an = (a * Math.PI) / 3;
        ctx.moveTo(x, y);
        ctx.lineTo(x + Math.cos(an) * 9, y + Math.sin(an) * 9);
      }
      ctx.lineWidth = 2.5;
      ctx.stroke();
      return;
    case 'triangle':
      ctx.moveTo(x, y - 10);
      ctx.lineTo(x + 9, y + 7);
      ctx.lineTo(x - 9, y + 7);
      ctx.closePath();
      break;
    case 'coin':
      ctx.arc(x, y, 8, 0, Math.PI * 2);
      break;
    case 'bolt':
      ctx.moveTo(x + 2, y - 10);
      ctx.lineTo(x - 5, y + 1);
      ctx.lineTo(x + 1, y + 1);
      ctx.lineTo(x - 3, y + 10);
      ctx.lineTo(x + 6, y - 3);
      ctx.lineTo(x, y - 3);
      ctx.closePath();
      break;
    case 'drop':
      ctx.arc(x, y + 2, 7, 0, Math.PI * 2);
      ctx.moveTo(x, y - 10);
      ctx.lineTo(x + 5, y - 2);
      ctx.lineTo(x - 5, y - 2);
      ctx.closePath();
      break;
    case 'flag':
      ctx.rect(x - 8, y - 9, 2, 18);
      ctx.moveTo(x - 6, y - 9);
      ctx.lineTo(x + 9, y - 4);
      ctx.lineTo(x - 6, y + 1);
      ctx.closePath();
      break;
    default:
      ctx.arc(x, y, 6, 0, Math.PI * 2);
  }
  ctx.fill();
}

function line(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}

function circle(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
}

function strokePath(ctx: CanvasRenderingContext2D, pts: [number, number][]) {
  ctx.beginPath();
  ctx.moveTo(pts[0][0], pts[0][1]);
  pts.slice(1).forEach(([x, y]) => ctx.lineTo(x, y));
  ctx.stroke();
}

/** A small shape icon for lists (the same drawing as on the map). */
export function TowerIcon(props: { shape: string; color: string; size?: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const size = props.size ?? 34;
  useEffect(() => {
    const c = ref.current;
    const ctx = c?.getContext('2d');
    if (!c || !ctx) return;
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    c.width = size * dpr;
    c.height = size * dpr;
    ctx.setTransform((size * dpr) / 40, 0, 0, (size * dpr) / 40, 0, 0);
    ctx.fillStyle = '#0e1424';
    ctx.strokeStyle = props.color;
    ctx.lineWidth = 2;
    circle(ctx, 20, 20, 17);
    ctx.fill();
    ctx.stroke();
    drawShape(ctx, props.shape, 20, 20, props.color);
  }, [props.shape, props.color, size]);
  return <canvas ref={ref} style={{ width: size, height: size, flexShrink: 0 }} aria-hidden />;
}
