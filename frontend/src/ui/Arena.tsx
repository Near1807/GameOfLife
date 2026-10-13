import { useEffect, useRef, useState } from 'react';
import { Heart, Moon, Shield, Skull, Swords } from 'lucide-react';
import type { FightView, Look } from '../api';
import { Bar } from './Bar';
import { CountUp } from './CountUp';
import { Sprite } from './Sprite';

/** One payout to animate: the hero attacks, the boss flashes, the damage floats up. */
export interface Hit {
  id: number;
  amount: number;
  crit: boolean;
}

/** "While you were away" (brief 11): the attacks since the app was last opened. */
export interface Recap {
  id: number;
  days: number;
  damage: number;
  blocked: number;
  heal: number;
  fell: boolean;
  heroHpBefore: number;
}

/** The variant tint of a boss met again after the last picture, or after ascension. */
export const VARIANT_FILTER = 'hue-rotate(160deg) saturate(1.4) drop-shadow(0 0 6px rgba(255, 77, 109, 0.7))';

/**
 * The battle arena at the top of Home (brief 11): region background, hero on the left, boss on
 * the right, both health bars, today's shield and the expected hit tonight. Idle animations
 * always run; payouts play an attack, a hit flash and floating damage, all under 1.5 s and
 * never blocking a tap (pointer-events: none on every effect).
 */
export function Arena(props: {
  fight: FightView;
  look: Look;
  hits: Hit[];
  recap: Recap | null;
  onDismissRecap: () => void;
  onOpen: () => void;
}) {
  const f = props.fight;
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(360);
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => setWidth(entries[0].contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // Whole-number scaling only (brief 12): the 320x180 background at x1 on phones, x2 on laptops.
  const s = width >= 640 ? 2 : 1;
  const HUD = 64; // a strip above the scene for the health panels, so they never cover a fighter
  const ground = HUD + 150 * s; // feet line of the backgrounds
  const lastHit = props.hits[props.hits.length - 1];
  const recapKey = props.recap?.id ?? 0;

  return (
    <section className="arena-card" aria-label="battle arena">
      <div
        ref={box}
        className="arena"
        style={{
          height: 180 * s + HUD,
          backgroundImage: f.region ? `url('/${f.region}')` : undefined,
          backgroundSize: `${320 * s}px ${180 * s}px`,
          filter: props.look.arenaFilter ?? undefined,
        }}
      >
        {/* HUD: hero and boss health */}
        <div className="hud hud-left">
          <div className="hud-name"><Heart size={12} /> You</div>
          <Bar value={f.heroHp / f.heroMaxHp} from={props.recap ? props.recap.heroHpBefore / f.heroMaxHp : undefined}
            color="var(--success)" label="hero health" key={'h' + recapKey} />
          <div className="hud-num tabular"><CountUp value={f.heroHp} /> / {Math.round(f.heroMaxHp)}</div>
        </div>
        <button className="hud hud-right" onClick={props.onOpen} aria-label="open the boss screen">
          <div className="hud-name"><Skull size={12} /> {f.bossName}{f.variant && ' +'}</div>
          <Bar value={f.bossHp / f.bossMaxHp} color="var(--boss)" label="boss health" />
          <div className="hud-num tabular"><CountUp value={f.bossHp} /> / {Math.round(f.bossMaxHp).toLocaleString('en-US')}</div>
          {f.defeatsByThisBoss > 0 && <div className="hud-defeats">Defeated you {f.defeatsByThisBoss}×</div>}
        </button>

        {/* the fighters */}
        <div className="fighter hero-fighter" style={{ top: ground - 32 * 2 * s, left: '12%' }}>
          <Sprite key={'a' + (lastHit?.id ?? 0) + '-' + recapKey} src={props.look.heroSprite} frames={props.look.heroFrames}
            size={32} scale={2 * s} className={(lastHit ? 'attack' : '') + (props.recap ? ' hurt' : '')} label="your hero" />
          {props.look.worn.length > 0 && (
            <div className="worn">
              {props.look.worn.map((i) => (
                <span key={i.id} className="worn-item" title={i.name}>
                  <span className="sprite" style={{ width: 32, height: 32, backgroundImage: `url('/${i.sprite}')`, backgroundSize: `${32 * i.frames}px 32px` }} />
                </span>
              ))}
            </div>
          )}
        </div>
        <div className="fighter boss-fighter" style={{ top: ground - 64 * 2 * s + 6 * s, right: '7%' }}>
          <Sprite key={'b' + (lastHit?.id ?? 0)} src={f.bossSprite} frames={f.bossFrames} size={64} scale={2 * s}
            className={(lastHit ? 'hit' : '') + (props.recap ? ' boss-strike' : '')}
            filter={f.variant ? VARIANT_FILTER : undefined} label={f.bossName} />
        </div>

        {/* effects: projectile and floating damage per payout */}
        {props.hits.map((h) => (
          <div key={h.id} className="fx-layer" style={{ '--travel': `${Math.round(width * 0.52)}px` } as React.CSSProperties}>
            <span className={'projectile' + (h.crit ? ' crit' : '')} style={{ top: ground - 40 * s }} />
            <span className={'dmg' + (h.crit ? ' crit' : '')} style={{ top: ground - 120 * s }}>
              {h.crit && <span className="crit-label">Critical</span>}-{Math.round(h.amount)}
            </span>
          </div>
        ))}
        {lastHit?.crit && <div key={'shake' + lastHit.id} className="crit-flash" />}

        {props.recap && (
          <div className="recap" role="status">
            <div className="small">
              <b>While you were away</b> ({props.recap.days} day{props.recap.days > 1 ? 's' : ''}):{' '}
              {f.bossName} hit for <b className="tabular">{Math.round(props.recap.damage)}</b>
              {props.recap.blocked > 0 && <>, your shield blocked <b className="tabular">{Math.round(props.recap.blocked)}</b></>}
              {props.recap.heal > 0 && <>, you healed <b className="tabular">{Math.round(props.recap.heal)}</b></>}.
              {props.recap.fell && ' You fell, the boss healed. Get back up.'}
            </div>
            <button className="btn" onClick={props.onDismissRecap}>OK</button>
          </div>
        )}
      </div>

      {/* tonight's forecast */}
      <div className="arena-foot">
        <div className="row" style={{ gap: 8 }}>
          <Shield size={16} className="cyan" />
          <div className="grow">
            <div className="row between tiny">
              <span className="muted">Shield today</span>
              <span className="tabular">{Math.round(f.shieldToday * 100)}% · {Math.round(f.todayXp)} / {Math.round(f.dailyGoal)} XP</span>
            </div>
            <Bar value={f.shieldToday} color="var(--cyan)" label="shield today" />
          </div>
        </div>
        <div className="tiny muted row" style={{ gap: 6, marginTop: 8 }}>
          {f.protectedToday ? (
            <><Moon size={13} style={{ flexShrink: 0 }} /> <span>Protected today (rest, pause or freeze): no hit tonight.</span></>
          ) : f.expectedHit > 0 ? (
            <><Swords size={13} style={{ color: 'var(--boss)', flexShrink: 0 }} />
              <span>Tonight {f.bossName} hits for <b className="tabular" style={{ color: 'var(--text)' }}>{Math.round(f.expectedHit)}</b> unless you raise your shield.</span></>
          ) : (
            <><Shield size={13} className="cyan" style={{ flexShrink: 0 }} /> <span>Fully shielded: no hit tonight, and you heal.</span></>
          )}
        </div>
      </div>
    </section>
  );
}
