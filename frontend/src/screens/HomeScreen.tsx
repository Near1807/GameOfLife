import { useEffect, useState } from 'react';
import {
  Activity, CalendarCheck, Check, ChevronRight, Coins, Flame, Gift, Moon, Play, Skull, Smile, Snowflake, Sparkles, Sword, Timer, Undo2, Zap,
} from 'lucide-react';
import { api, type Character, type GameState, type HabitView, type LedgerEvent, type Meta, type TaskView } from '../api';
import { nextAction } from '../nextAction';
import { statStyle } from '../stats';
import type { Act, Screen } from '../types';
import { Bar } from '../ui/Bar';
import { Card } from '../ui/Card';
import { DefenseCard } from '../ui/DefenseCard';
import { CountUp } from '../ui/CountUp';
import { HeroPortrait } from '../ui/HeroPortrait';
import { MoodPicker } from '../ui/MoodPicker';
import { QuickAdd } from '../ui/QuickAdd';
import { StatTagPicker } from '../ui/StatTagPicker';

/** Home dashboard (v2 section 4.3). */
export function HomeScreen(props: {
  state: GameState;
  meta: Meta;
  character: Character | null;
  act: Act;
  hitKey: number;
  version: number;
  refresh: () => void;
  onError: (m: string) => void;
  go: (s: Screen) => void;
  arena: React.ReactNode;
}) {
  const { state, meta } = props;
  const s = state.streak;
  const m = state.multipliers;
  const mult = m.streak * m.weapon * m.ascension * m.comeback;

  const [mood, setMood] = useState<number | null>(null);
  const [habits, setHabits] = useState<HabitView[]>([]);
  const [tasks, setTasks] = useState<TaskView[]>([]);
  const [rest, setRest] = useState<string[]>([]);
  const [checkinTags, setCheckinTags] = useState<string[]>(meta.lastCheckinTags);
  const [editTags, setEditTags] = useState(false);

  useEffect(() => {
    api.checkinToday().then((r) => setMood(r.mood)).catch(() => {});
    api.habits().then(setHabits).catch(() => {});
    api.tasks().then(setTasks).catch(() => {});
    api.restDays().then(setRest).catch(() => {});
  }, [props.version]);

  const tomorrow = addDays(state.today, 1);
  const next = nextAction({ checkedIn: mood !== null, habits, tasks, character: props.character });
  const c = props.character;

  return (
    <div className="page">
    <DefenseCard act={props.act} version={props.version} filter={state.look.arenaFilter} />
    <div className="grid home">
      {/* hero */}
      <Card glow>
        <div className="row" style={{ alignItems: 'flex-start', gap: 16 }}>
          <button onClick={() => props.go('hero')} style={{ all: 'unset', cursor: 'pointer' }} aria-label="open character sheet">
            <HeroPortrait scale={3} hitKey={props.hitKey} sprite={state.look.heroSprite} frames={state.look.heroFrames} />
          </button>
          <div className="col grow">
            <span className="level-badge" style={{ alignSelf: 'flex-start' }}>
              Level {c?.overallLevel ?? 1}
            </span>
            <div className="class-name">{c?.className ?? '…'}</div>
            <div className="muted small">{c?.rank}</div>
          </div>
          <button className="icon-btn" onClick={() => props.go('hero')} aria-label="character sheet">
            <ChevronRight size={18} />
          </button>
        </div>
        {c && (
          <div style={{ marginTop: 16, display: 'grid', gap: 9 }}>
            {c.stats.map((st) => {
              const style = statStyle(st.id);
              return (
                <div key={st.id} className="row" style={{ gap: 8 }} data-stat={st.id}>
                  <style.Icon size={15} style={{ color: style.color, flexShrink: 0 }} />
                  <span className="tiny muted" style={{ width: 30 }}>
                    {style.short}
                  </span>
                  <div className="grow">
                    <Bar value={st.progress} color={style.color} label={`${st.name} progress`} />
                  </div>
                  <span className="num tiny" style={{ width: 34, textAlign: 'right', color: style.color }}>
                    Lv{st.level}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      <div className="grid">
        {/* today and wallet */}
        <Card>
          <div className="grid two" style={{ gap: 18 }}>
            <div>
              <div className="label row" style={{ gap: 6 }}>
                <Zap size={13} /> Today
              </div>
              <div className="row" style={{ alignItems: 'baseline', gap: 6, margin: '6px 0 10px' }}>
                <CountUp value={state.xpToday} className="big-num gold" />
                <span className="faint small">/ {meta.dailyXpGoal} XP</span>
              </div>
              <Bar value={state.xpToday / meta.dailyXpGoal} color="var(--gold)" thick label="today's XP" />
            </div>
            <div>
              <div className="label row" style={{ gap: 6 }}>
                <Coins size={13} /> Coins
              </div>
              <div className="row" style={{ alignItems: 'baseline', gap: 6, margin: '6px 0 10px' }}>
                <CountUp value={state.balance} className="big-num gold" />
              </div>
              <Bar value={state.balance / state.nextUpgradeCost} color="var(--violet-soft)" thick label="toward next weapon upgrade" />
              <button className="tiny faint" style={{ all: 'unset', cursor: 'pointer', marginTop: 6, display: 'block', fontSize: 11.5 }} onClick={() => props.go('shop')}>
                <Sword size={11} /> Weapon Lv {state.weaponLevel + 1} at {Math.round(state.nextUpgradeCost)} · open shop
              </button>
            </div>
          </div>
        </Card>

        {/* streak */}
        <Card>
          <div className="row" style={{ gap: 14 }}>
            <Flame size={s.days >= 365 ? 64 : s.days >= 100 ? 56 : s.days >= 30 ? 48 : s.days >= 7 ? 44 : 38}
              className={s.days > 0 ? 'flame live' : 'flame off'} />
            <div className="grow">
              <div className="row" style={{ alignItems: 'baseline', gap: 8 }}>
                <span className="big-num">{s.days}</span>
                <span className="muted">day streak</span>
              </div>
              <div className="row wrap" style={{ gap: 6, marginTop: 6 }}>
                <span className="pill gold">x{s.multiplierToday.toFixed(2)} XP</span>
                {s.freezes > 0 && (
                  <span className="pill cyan">
                    <Snowflake size={12} /> {s.freezes}
                  </span>
                )}
                {s.comebackPending && <span className="pill gold">Comeback x2 ready</span>}
              </div>
            </div>
            {rest.includes(tomorrow) ? (
              <span className="pill violet">
                <Moon size={12} /> Rest tomorrow
              </span>
            ) : (
              <button className="btn" onClick={() => props.act(api.planRest(tomorrow))} title="Plan a rest day tomorrow: keeps the streak">
                <Moon size={16} /> Rest
              </button>
            )}
          </div>
          {s.nextMilestone > 0 && (
            <div style={{ marginTop: 14 }}>
              <Bar value={s.days / s.nextMilestone} color="#fb923c" label="streak milestone" />
              <div className="tiny faint" style={{ marginTop: 5 }}>
                Next milestone: {s.nextMilestone} days
              </div>
            </div>
          )}
          {rest.includes(state.today) && <p className="small cyan" style={{ marginBottom: 0 }}>Rest day today: your streak is safe.</p>}
        </Card>

        {/* next best action */}
        <Card title="Next best action" icon={Sparkles} iconColor="var(--gold)">
          <div className="row">
            <div className="grow">
              <div style={{ fontWeight: 650, fontSize: 17 }}>{next.title}</div>
              <div className="small muted">{next.why}</div>
            </div>
            {next.kind === 'habit' && (
              <button className="btn primary" onClick={() => props.act(api.habitDone(next.id))} aria-label={`do now: ${next.title}`}>
                <Check size={18} /> Done
              </button>
            )}
            {next.kind === 'task' && (
              <button className="btn primary" onClick={() => props.act(api.tickTask(next.id))} aria-label={`do now: ${next.title}`}>
                <Check size={18} /> Done
              </button>
            )}
            {next.kind === 'focus' && (
              <button className="btn primary" onClick={() => props.go('timer')}>
                <Play size={18} /> Focus
              </button>
            )}
          </div>
        </Card>
      </div>

      {/* mood */}
      <Card title={mood === null ? 'How are you today?' : 'Mood logged'} icon={Smile} iconColor="var(--cyan)"
        action={mood === null && (
          <button className="btn ghost" onClick={() => setEditTags(!editTags)}>
            Feeds: {checkinTags.map((t) => statStyle(t).short).join(' ') || 'coins only'}
          </button>
        )}>
        <MoodPicker picked={mood} onPick={(v) => props.act(api.checkin(v, checkinTags))} />
        {editTags && mood === null && (
          <div style={{ marginTop: 14 }}>
            <StatTagPicker stats={meta.stats} selected={checkinTags} onChange={setCheckinTags} baseXp={meta.checkin.xp} />
          </div>
        )}
        {mood === null && (
          <div className="tiny faint" style={{ marginTop: 8 }}>
            +{Math.round(meta.checkin.xp * mult)} XP, once a day
          </div>
        )}
      </Card>

      <QuickAdd meta={meta} multiplier={mult} onAdded={props.refresh} onError={props.onError} />

      <Card title="Recent activity" icon={Activity} className="span-2">
        {state.log.length === 0 && <p className="muted small">Nothing yet. Your first action shows up here.</p>}
        {state.log.slice(0, 8).map((e) => (
          <FeedItem key={e.id} e={e} />
        ))}
      </Card>
    </div>
    </div>
  );
}

const MODULE_ICON = { todo: Check, timer: Timer, habit: CalendarCheck, checkin: Smile, rest: Moon, fight: Skull, shop: Coins, loot: Gift } as const;

function FeedItem(props: { e: LedgerEvent }) {
  const e = props.e;
  const Icon = e.type === 'TASK_UNDONE' ? Undo2 : MODULE_ICON[e.module as keyof typeof MODULE_ICON] ?? Zap;
  const tags = Object.keys(e.statSplits);
  return (
    <div className="feed-item">
      <span className="feed-icon" style={{ color: tags[0] ? statStyle(tags[0]).color : 'var(--text-2)' }}>
        <Icon size={16} />
      </span>
      <div className="grow">
        <div style={{ fontWeight: 550 }}>{e.type === 'REST_DAY' ? 'Rest day planned' : e.label}</div>
        {e.type === 'BOSS_ATTACK' && <div className="tiny muted">hit for {Math.round(Number(e.meta.damage))}, blocked {Math.round(Number(e.meta.blocked))}</div>}
        <div className="meta-row tiny faint">
          {new Date(e.occurredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          {tags.map((t) => {
            const st = statStyle(t);
            return <st.Icon key={t} size={12} style={{ color: st.color }} />;
          })}
        </div>
      </div>
      {e.xp !== 0 && <span className="reward">{e.xp > 0 ? '+' : ''}{Math.round(e.xp)}</span>}
      {e.xp === 0 && e.currencyDelta !== 0 && <span className="reward">{e.currencyDelta > 0 ? '+' : ''}{Math.round(e.currencyDelta)} coins</span>}
    </div>
  );
}

function addDays(isoDate: string, days: number) {
  const d = new Date(isoDate + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
