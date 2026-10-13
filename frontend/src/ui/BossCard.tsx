import { useEffect, useRef, useState } from 'react';
import { Clock, Coins, Skull, Swords } from 'lucide-react';
import type { StageView } from '../api';
import { Bar } from './Bar';
import { Card } from './Card';
import { CountUp } from './CountUp';
import { Sprite } from './Sprite';

/**
 * This week's boss (brief 9.1): health bar drained by the XP validated this week.
 * When the damage goes up, the boss flashes (hit) and the bar drains.
 */
export function BossCard(props: { stage: StageView; scale?: number; onOpen?: () => void }) {
  const s = props.stage;
  const scale = props.scale ?? 2;
  const [hit, setHit] = useState(0);
  const lastDamage = useRef(s.damage);
  useEffect(() => {
    if (s.damage > lastDamage.current) setHit((h) => h + 1);
    lastDamage.current = s.damage;
  }, [s.damage]);

  if (s.ascendReady) {
    return (
      <Card title="Campaign complete" icon={Swords} iconColor="var(--gold)" glow>
        <p className="muted" style={{ margin: 0 }}>
          All {s.stageCount} bosses are down. Ascend (New Game+) on the Campaign screen for a permanent x
          {(s.ascensionMultiplier * 1.05).toFixed(2)} XP multiplier.
        </p>
      </Card>
    );
  }

  return (
    <Card className={'boss-card' + (s.cleared ? ' defeated' : '')}>
      <div className="row" style={{ alignItems: 'flex-start', gap: 14 }}>
        <button className="boss-frame" style={{ width: 64 * scale + 16, height: 64 * scale + 16 }} onClick={props.onOpen}
          aria-label="open campaign">
          <Sprite key={hit} src={s.boss.sprite} size={64} scale={scale} className={hit ? 'hit' : undefined} label={s.boss.name} />
          {s.cleared && <span className="defeated-stamp">Defeated</span>}
        </button>
        <div className="grow col" style={{ gap: 4 }}>
          <span className="label">
            Stage {s.stage} / {s.stageCount}
            {s.ascensions > 0 && ` · NG+${s.ascensions}`}
          </span>
          <div className="class-name" style={{ fontSize: 20 }}>{s.boss.name}</div>
          <div className="small muted">{s.boss.title}</div>
        </div>
      </div>
      <div style={{ marginTop: 14 }}>
        <div className="row between small" style={{ marginBottom: 6 }}>
          <span className="row" style={{ gap: 6 }}>
            <Skull size={14} style={{ color: 'var(--boss)' }} />
            <span className="tabular">
              <CountUp value={s.hp} /> / {Math.round(s.target).toLocaleString('en-US')} HP
            </span>
          </span>
          <span className="faint row" style={{ gap: 5 }}>
            <Clock size={13} /> {timeLeft(s.endsAt)}
          </span>
        </div>
        <Bar value={s.hp / s.target} color="var(--boss)" thick label="boss health" />
        <div className="tiny faint" style={{ marginTop: 6 }}>
          {s.cleared ? (
            <span className="gold row" style={{ gap: 5 }}>
              <Coins size={12} /> +{s.rewardCoins} coins
              {s.overkill > 0 && ` · overkill ${Math.round(s.overkill)}`}
              {' · next boss on Monday'}
            </span>
          ) : (
            'Every XP you earn this week is damage. If it survives, it comes back healed next week.'
          )}
        </div>
      </div>
    </Card>
  );
}

/** "4d 6h", "5h 20m" until the week ends (Monday 04:00). */
export function timeLeft(endsAt: string) {
  const ms = Math.max(0, new Date(endsAt).getTime() - Date.now());
  const d = Math.floor(ms / 86_400_000);
  const h = Math.floor((ms % 86_400_000) / 3_600_000);
  const m = Math.floor((ms % 3_600_000) / 60_000);
  return d > 0 ? `${d}d ${h}h left` : `${h}h ${m}m left`;
}
