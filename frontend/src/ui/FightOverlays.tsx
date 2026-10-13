import { Coins, Skull } from 'lucide-react';
import { Sprite } from './Sprite';
import { VARIANT_FILTER } from './Arena';

/** A full-screen moment (brief 11): victory, defeat or a boss intro. 1.4 s, never blocks taps. */
export interface Moment {
  id: number;
  kind: 'victory' | 'defeat' | 'intro';
  name: string;
  title?: string;
  sprite: string;
  frames: number;
  variant: boolean;
  reward?: number;
  defeats?: number;
}

export function FightMoment(props: { m: Moment }) {
  const m = props.m;
  return (
    <div className={'victory ' + m.kind} role="status" aria-live="assertive">
      <div className="victory-burst" />
      {m.kind === 'victory' && Array.from({ length: 18 }, (_, i) => (
        <span key={i} className="spark" style={{ '--a': `${i * 20}deg`, '--d': `${120 + (i % 3) * 50}px` } as React.CSSProperties} />
      ))}
      <div className="victory-content">
        <Sprite src={m.sprite} frames={m.frames} size={64} scale={3} className={m.kind === 'victory' ? 'dying' : m.kind === 'intro' ? 'entering' : ''}
          filter={m.variant ? VARIANT_FILTER : undefined} label={m.name} />
        <div className="victory-title">
          {m.kind === 'victory' ? 'Boss defeated' : m.kind === 'defeat' ? 'You fell' : 'A new foe'}
        </div>
        <div className="victory-name">{m.name}</div>
        {m.kind === 'intro' && m.title && <div className="small muted">{m.title}</div>}
        {m.kind === 'victory' && (
          <div className="reward" style={{ fontSize: 16, padding: '6px 14px' }}>
            <Coins size={15} style={{ verticalAlign: '-2px' }} /> +{m.reward} coins
          </div>
        )}
        {m.kind === 'defeat' && (
          <div className="small muted" style={{ textAlign: 'center', maxWidth: 280 }}>
            <Skull size={13} style={{ verticalAlign: '-2px' }} /> It healed. You keep your coins, gear and stats.
            Defeated you {m.defeats}×.
          </div>
        )}
      </div>
    </div>
  );
}
