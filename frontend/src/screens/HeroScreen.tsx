import { Crown, Gem, Radar as RadarIcon, Shield, Sword } from 'lucide-react';
import type { Character, GameState } from '../api';
import { statStyle } from '../stats';
import { Bar } from '../ui/Bar';
import { Card } from '../ui/Card';
import { HeroPortrait } from '../ui/HeroPortrait';
import { Radar } from '../ui/Radar';

/**
 * Character sheet (brief 8, v2 section 4.2): seven stats with level and XP bar, radar chart,
 * overall level, class from the two highest stats, rank, and the hero with its gear slots.
 * Every number comes from the backend, computed from the ledger.
 */
export function HeroScreen(props: { character: Character; state: GameState }) {
  const c = props.character;
  return (
    <div className="page">
      <h1 className="page-title">Hero</h1>
      <p className="page-sub">Who you are becoming. Stats only grow from real, confirmed actions.</p>
      <div className="grid two">
        <Card glow>
          <div className="row wrap" style={{ alignItems: 'flex-start', gap: 18 }}>
            <HeroPortrait scale={5} />
            <div className="col grow" style={{ minWidth: 190 }}>
              <span className="level-badge" style={{ alignSelf: 'flex-start' }}>
                <Crown size={14} /> Level {c.overallLevel}
              </span>
              <div className="class-name">{c.className}</div>
              <div className="muted small">
                {c.rank}
                {c.balanced && ' · Balanced'}
              </div>
              <div className="label" style={{ marginTop: 10 }}>
                Gear
              </div>
              <Slot icon={Sword} name="Weapon" value={`Lv ${props.state.weaponLevel} · x${props.state.multipliers.weapon.toFixed(2)} XP`} />
              <Slot icon={Shield} name="Armor" value="Empty" />
              <Slot icon={Gem} name="Charm" value="Empty" />
            </div>
          </div>
        </Card>
        <Card title="Balance" icon={RadarIcon}>
          <Radar stats={c.stats} />
        </Card>
        <Card title="Stats" className="span-2">
          <div className="grid two" style={{ gap: '6px 28px' }}>
            {c.stats.map((s) => {
              const st = statStyle(s.id);
              return (
                <div key={s.id} className="row" style={{ padding: '10px 0' }}>
                  <span className="card-icon" style={{ color: st.color, width: 42, height: 42 }}>
                    <st.Icon size={20} />
                  </span>
                  <div className="grow">
                    <div className="row between">
                      <span style={{ fontWeight: 600 }}>
                        {s.name} <span className="faint tiny">{s.hint}</span>
                      </span>
                      <span className="num" style={{ color: st.color, fontWeight: 700 }}>
                        Lv {s.level}
                      </span>
                    </div>
                    <div style={{ margin: '7px 0 4px' }}>
                      <Bar value={s.progress} color={st.color} label={`${s.name} progress`} />
                    </div>
                    <div className="tiny faint tabular">
                      {Math.floor(s.xpIntoLevel)} / {Math.round(s.xpForNext)} XP to level {s.level + 1}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      </div>
    </div>
  );
}

function Slot(props: { icon: typeof Sword; name: string; value: string }) {
  return (
    <div className="row small" style={{ padding: '4px 0' }}>
      <props.icon size={15} className="faint" />
      <span className="muted" style={{ width: 56 }}>
        {props.name}
      </span>
      <span>{props.value}</span>
    </div>
  );
}
