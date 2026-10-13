import { Coins } from 'lucide-react';
import type { StageView } from '../api';
import { Sprite } from './Sprite';

/**
 * Full-screen celebration when a boss dies (brief 11). 1.4 s, pointer-events: none so it never
 * blocks the next tap; reduced effects shows it without motion.
 */
export function BossDefeated(props: { stage: StageView }) {
  const s = props.stage;
  return (
    <div className="victory" role="status" aria-live="assertive">
      <div className="victory-burst" />
      {Array.from({ length: 18 }, (_, i) => (
        <span key={i} className="spark" style={{ '--a': `${i * 20}deg`, '--d': `${120 + (i % 3) * 50}px` } as React.CSSProperties} />
      ))}
      <div className="victory-content">
        <Sprite src={s.boss.sprite} size={64} scale={3} className="dying" label={s.boss.name} />
        <div className="victory-title">Boss defeated</div>
        <div className="victory-name">{s.boss.name}</div>
        <div className="reward" style={{ fontSize: 16, padding: '6px 14px' }}>
          <Coins size={15} style={{ verticalAlign: '-2px' }} /> +{s.rewardCoins} coins
        </div>
      </div>
    </div>
  );
}
