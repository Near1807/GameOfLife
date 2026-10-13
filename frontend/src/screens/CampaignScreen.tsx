import { useEffect, useState } from 'react';
import { BarChart3, CalendarRange, ChevronLeft, ChevronRight, Crown, Flame, ListChecks, Map as MapIcon, Moon, Repeat, Skull, Smile, Swords, Timer, TrendingUp, Trophy } from 'lucide-react';
import { api, type Dashboard, type GameState, type Meta, type WeekReview } from '../api';
import { statStyle } from '../stats';
import type { Act } from '../types';
import { Bar } from '../ui/Bar';
import { CampaignMap } from '../ui/CampaignMap';
import { VARIANT_FILTER } from '../ui/Arena';
import { Sprite } from '../ui/Sprite';
import { Card } from '../ui/Card';
import { StreakLine, XpBars } from '../ui/Charts';

type Tab = 'boss' | 'review' | 'stats';

/** Campaign: this week's boss and the map, the weekly review, and the stats dashboard. */
export function CampaignScreen(props: { state: GameState; meta: Meta; act: Act; version: number }) {
  const [tab, setTab] = useState<Tab>('boss');
  return (
    <div className="page">
      <h1 className="page-title">Campaign</h1>
      <p className="page-sub">One boss at a time. Your effort decides how long the fight lasts, and you can lose.</p>
      <div className="segmented" role="tablist">
        {(['boss', 'review', 'stats'] as Tab[]).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'on' : ''} onClick={() => setTab(t)}>
            {t === 'boss' ? 'Boss' : t === 'review' ? 'Weekly review' : 'Stats'}
          </button>
        ))}
      </div>
      {tab === 'boss' && <BossTab state={props.state} act={props.act} />}
      {tab === 'review' && <ReviewTab meta={props.meta} version={props.version} />}
      {tab === 'stats' && <StatsTab meta={props.meta} version={props.version} />}
    </div>
  );
}

function BossTab(props: { state: GameState; act: Act }) {
  const f = props.state.fight;
  const [confirm, setConfirm] = useState(false);
  return (
    <div className="grid two">
      <Card className="boss-card">
        <div className="row" style={{ alignItems: 'flex-start', gap: 14 }}>
          <div className="boss-frame" style={{ width: 64 * 3 + 16, height: 64 * 3 + 16 }}>
            <Sprite src={f.bossSprite} frames={f.bossFrames} size={64} scale={3} filter={f.variant ? VARIANT_FILTER : undefined} label={f.bossName} />
          </div>
          <div className="grow col" style={{ gap: 4, minWidth: 140 }}>
            <span className="label">Boss {f.boss}{f.ascensions > 0 && ` · NG+${f.ascensions}`}{f.variant && ' · stronger variant'}</span>
            <div className="class-name" style={{ fontSize: 20 }}>{f.bossName}</div>
            <div className="small muted">{f.bossTitle}</div>
            {f.defeatsByThisBoss > 0 && <span className="pill violet" style={{ alignSelf: 'flex-start' }}>Defeated you {f.defeatsByThisBoss}×</span>}
          </div>
        </div>
        <div style={{ marginTop: 14 }}>
          <div className="row between small" style={{ marginBottom: 6 }}>
            <span className="row" style={{ gap: 6 }}><Skull size={14} style={{ color: 'var(--boss)' }} /> {Math.round(f.bossHp).toLocaleString('en-US')} / {Math.round(f.bossMaxHp).toLocaleString('en-US')} HP</span>
            <span className="faint">hits {Math.round(f.bossAtk)} / day</span>
          </div>
          <Bar value={f.bossHp / f.bossMaxHp} color="var(--boss)" thick label="boss health" />
        </div>
        <div className="tiles" style={{ marginTop: 14 }}>
          <Tile icon={Trophy} label="Bosses beaten" value={f.kills} />
          <Tile icon={Skull} label="Falls" value={f.falls} />
          <Tile icon={Swords} label="Hit per day" value={Math.round(f.bossAtk)} />
          <Tile icon={Crown} label="Kill reward" value={`${f.killReward} coins`} gold />
        </div>
      </Card>
      <div className="grid">
        <Card title="Campaign map" icon={MapIcon}>
          <CampaignMap fight={f} />
        </Card>
        <Card title="Pause" icon={Moon}>
          <p className="small muted" style={{ marginTop: 0 }}>
            Ill or travelling? A paused day is fully shielded and keeps your streak. {f.pausesLeft} of 7 left in 30 days.
          </p>
          {f.pausedToday ? <span className="pill cyan"><Moon size={13} /> Today is paused</span> : (
            <button className="btn block" disabled={f.pausesLeft === 0} onClick={() => props.act(api.pause())}>
              <Moon size={16} /> Pause today
            </button>
          )}
        </Card>
        {f.ascendReady && (
          <Card title="Ascend: New Game+" icon={Crown} iconColor="var(--gold)" glow>
            <p className="small muted" style={{ marginTop: 0 }}>
              You beat all {f.bossFiles} bosses. Start again at boss 1 with stronger variants and a permanent x1.05 XP
              multiplier. Your weapon goes back to level 0; stats, armour, charms, looks and achievements stay.
            </p>
            {confirm ? (
              <div className="row">
                <button className="btn primary grow" onClick={() => props.act(api.ascend()).then(() => setConfirm(false))}>
                  <Crown size={18} /> Yes, ascend
                </button>
                <button className="btn ghost" onClick={() => setConfirm(false)}>Cancel</button>
              </div>
            ) : (
              <button className="btn primary block" onClick={() => setConfirm(true)}><Crown size={18} /> Ascend</button>
            )}
          </Card>
        )}
        <Card title="Rules of the fight" icon={Swords}>
          <ul className="small muted" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.7 }}>
            <li>Every XP you validate is damage, right away. Extra damage on a kill is lost.</li>
            <li>Each night at 04:00 the boss hits you for {Math.round(f.bossAtk)}, minus your shield (that day's XP over the daily goal of {Math.round(f.dailyGoal)}) and your armour.</li>
            <li>A fully shielded day heals you. Rest days, freezes and pauses shield you fully.</li>
            <li>At 0 HP you fall: the boss heals, you get back up. You keep your coins, gear and stats.</li>
            <li>A kill pays {f.killReward} coins and the next boss appears at once.</li>
          </ul>
        </Card>
      </div>
    </div>
  );
}

function ReviewTab(props: { meta: Meta; version: number }) {
  const [week, setWeek] = useState<string | undefined>(undefined);
  const [r, setR] = useState<WeekReview | null>(null);
  useEffect(() => {
    api.review(week).then(setR).catch(() => {});
  }, [week, props.version]);
  if (!r) return <p className="muted">Loading…</p>;

  const weekXp = r.days.reduce((a, d) => a + d.xp, 0);
  const statMax = Math.max(1, ...Object.values(r.statXp));
  return (
    <div className="grid two">
      <Card className="span-2">
        <div className="row between">
          <button className="icon-btn" disabled={!r.previousWeek} onClick={() => setWeek(r.previousWeek ?? undefined)} aria-label="previous week">
            <ChevronLeft size={20} />
          </button>
          <div style={{ textAlign: 'center' }}>
            <div className="label">{r.currentWeek ? 'This week' : 'Week of'}</div>
            <div style={{ fontWeight: 650 }}>{weekRange(r.weekStart)}</div>
          </div>
          <button className="icon-btn" disabled={!r.nextWeek} onClick={() => setWeek(r.nextWeek ?? undefined)} aria-label="next week">
            <ChevronRight size={20} />
          </button>
        </div>
        <div className="tiles" style={{ marginTop: 14 }}>
          <Tile icon={Swords} label="Damage dealt" value={Math.round(r.damageDealt).toLocaleString('en-US')} gold />
          <Tile icon={Skull} label="Damage taken" value={Math.round(r.damageTaken)} />
          <Tile icon={Trophy} label="Bosses beaten" value={r.bossesDefeated} />
          <Tile icon={Moon} label="Falls" value={r.falls} />
        </div>
        {r.bossNames.length > 0 && <p className="small muted" style={{ marginBottom: 0 }}>Beaten: {r.bossNames.join(', ')}</p>}
      </Card>

      <Card title={`XP per day · ${Math.round(weekXp)} this week`} icon={BarChart3}>
        <XpBars days={r.days} goal={props.meta.dailyXpGoal} label="XP per day this week" />
      </Card>

      <Card title="Stat XP this week" icon={TrendingUp}>
        {props.meta.stats.map((s) => {
          const st = statStyle(s.id);
          const v = r.statXp[s.id] ?? 0;
          return (
            <div key={s.id} className="row" style={{ padding: '5px 0' }}>
              <st.Icon size={16} style={{ color: st.color, flexShrink: 0 }} />
              <span className="small" style={{ width: 82 }}>{s.name}</span>
              <div className="grow"><Bar value={v / statMax} color={st.color} label={`${s.name} stat XP`} /></div>
              <span className="small tabular" style={{ width: 44, textAlign: 'right' }}>{Math.round(v)}</span>
            </div>
          );
        })}
      </Card>

      <Card title="The week in numbers" icon={CalendarRange}>
        <div className="tiles">
          <Tile icon={ListChecks} label="Quests done" value={r.tasksDone} />
          <Tile icon={Timer} label="Focus" value={`${Math.floor(r.focusMinutes / 60)}h ${r.focusMinutes % 60}m`} />
          <Tile icon={Repeat} label="Habits done" value={r.habitsDone} />
          <Tile icon={Smile} label="Avg mood" value={r.averageMood ?? '–'} />
          <Tile icon={Flame} label="Streak" value={`${r.streakAtEnd} d`} />
        </div>
      </Card>

      <Card title="Skipped habits and corrections" icon={Skull}>
        {r.skippedHabits.length === 0 && r.corrections.length === 0 && (
          <p className="small muted" style={{ margin: 0 }}>Nothing skipped, nothing corrected. Clean week.</p>
        )}
        {r.skippedHabits.map((h) => (
          <div key={h.name} className="list-item">
            <Repeat size={16} className="faint" />
            <span className="grow">{h.name}</span>
            <span className="small muted tabular">{h.missed} of {h.scheduled} skipped</span>
          </div>
        ))}
        {r.corrections.map((c, i) => (
          <div key={i} className="list-item">
            <span className="grow">{c.label} <span className="faint small">{c.reason}</span></span>
            <span className="reward">{c.xp > 0 ? '+' : ''}{Math.round(c.xp)}</span>
          </div>
        ))}
      </Card>
    </div>
  );
}

function StatsTab(props: { meta: Meta; version: number }) {
  const [days, setDays] = useState(30);
  const [d, setD] = useState<Dashboard | null>(null);
  useEffect(() => {
    api.dashboard(days).then(setD).catch(() => {});
  }, [days, props.version]);
  if (!d) return <p className="muted">Loading…</p>;
  const statMax = Math.max(1, ...Object.values(d.statXpPeriod));
  return (
    <div className="grid two">
      <div className="span-2 row wrap" style={{ gap: 8 }}>
        {[7, 30, 90].map((n) => (
          <button key={n} className={'chip pill' + (days === n ? ' selected' : '')} onClick={() => setDays(n)}>
            Last {n} days
          </button>
        ))}
      </div>
      <Card className="span-2">
        <div className="tiles">
          <Tile icon={TrendingUp} label={`XP, ${d.days} days`} value={Math.round(d.xpPeriod).toLocaleString('en-US')} gold />
          <Tile icon={Flame} label="Best streak" value={`${d.bestStreak} d`} />
          <Tile icon={Timer} label="Focus, all time" value={`${d.focusHoursAllTime} h`} />
          <Tile icon={Trophy} label="Bosses defeated" value={d.bossesDefeated} />
        </div>
      </Card>
      <Card title="XP per day" icon={BarChart3}>
        <XpBars days={d.history} goal={props.meta.dailyXpGoal} label={`XP per day, last ${d.days} days`} />
      </Card>
      <Card title="Streak history" icon={Flame} iconColor="#fb923c">
        <StreakLine days={d.history} label={`streak per day, last ${d.days} days`} />
      </Card>
      <Card title={`Stat XP, last ${d.days} days`} icon={BarChart3} className="span-2">
        <div className="grid two" style={{ gap: '4px 28px' }}>
          {props.meta.stats.map((s) => {
            const st = statStyle(s.id);
            const v = d.statXpPeriod[s.id] ?? 0;
            return (
              <div key={s.id} className="row" style={{ padding: '5px 0' }}>
                <st.Icon size={16} style={{ color: st.color, flexShrink: 0 }} />
                <span className="small" style={{ width: 82 }}>{s.name}</span>
                <div className="grow"><Bar value={v / statMax} color={st.color} label={`${s.name} stat XP`} /></div>
                <span className="small tabular" style={{ width: 52, textAlign: 'right' }}>{Math.round(v)}</span>
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}

function Tile(props: { icon: typeof Flame; label: string; value: string | number; gold?: boolean }) {
  return (
    <div className="tile">
      <div className="label row" style={{ gap: 6 }}><props.icon size={13} /> {props.label}</div>
      <div className={'big-num' + (props.gold ? ' gold' : '')} style={{ marginTop: 6 }}>{props.value}</div>
    </div>
  );
}

function weekRange(monday: string) {
  const a = new Date(monday + 'T12:00:00Z');
  const b = new Date(a);
  b.setUTCDate(a.getUTCDate() + 6);
  const f = (d: Date) => d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
  return `${f(a)} – ${f(b)}`;
}
