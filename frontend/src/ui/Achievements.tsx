import {
  Castle, Coins, Crown, Flame, Gem, Gift, HeartPulse, MapPin, Moon, Repeat, Shield, Skull, Smile, Sparkles, Star, Sword, Swords, Timer,
  TrendingUp, Trophy, Zap, type LucideIcon,
} from 'lucide-react';
import type { AchievementStatus } from '../api';
import { Bar } from './Bar';

/** Icon names the achievement data may use; anything else shows a trophy. */
const ICONS: Record<string, LucideIcon> = {
  Castle, Coins, Crown, Flame, Gem, Gift, HeartPulse, MapPin, Moon, Repeat, Shield, Skull, Smile, Sparkles, Star, Sword, Swords, Timer,
  TrendingUp, Trophy, Zap,
};

export const achievementIcon = (name: string) => ICONS[name] ?? Trophy;

/** Grid of achievement badges: unlocked ones glow, locked ones show their progress. */
export function AchievementGrid(props: { list: AchievementStatus[] }) {
  const done = props.list.filter((a) => a.unlocked).length;
  return (
    <>
      <p className="small muted" style={{ marginTop: 0 }}>{done} of {props.list.length} unlocked.</p>
      <div className="badges">
        {[...props.list].sort((a, b) => Number(b.unlocked) - Number(a.unlocked)).map((a) => {
          const Icon = achievementIcon(a.icon);
          return (
            <div key={a.id} className={'badge' + (a.unlocked ? ' on' : '')} title={a.description}>
              <span className="badge-icon"><Icon size={20} /></span>
              <div className="grow" style={{ minWidth: 0 }}>
                <div className="small" style={{ fontWeight: 650 }}>{a.name}</div>
                <div className="tiny muted">{a.description}</div>
                {!a.unlocked && (
                  <div style={{ marginTop: 6 }}>
                    <Bar value={a.current / a.target} color="var(--violet-soft)" label={`${a.name} progress`} />
                    <div className="tiny faint tabular" style={{ marginTop: 3 }}>{Math.floor(a.current)} / {a.target}</div>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </>
  );
}
