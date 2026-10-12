import { useEffect, useState } from 'react';
import { api, whole, type GameState, type Meta } from '../api';
import { CheckinRow } from '../components/CheckinRow';
import { ConsoleLog } from '../components/ConsoleLog';
import { Hero } from '../components/Hero';
import { QuickAdd } from '../components/QuickAdd';
import { StreakFlame } from '../components/StreakFlame';
import type { Act } from '../types';

/** Home: the hero, the streak flame, the balance, the check-in, a quick-add and the log. */
export function HomeScreen(props: {
  state: GameState;
  meta: Meta;
  act: Act;
  attackKey: number;
  version: number;
  refresh: () => void;
  onError: (m: string) => void;
}) {
  const { state } = props;
  const s = state.streak;
  const [rest, setRest] = useState<string[]>([]);

  useEffect(() => {
    api.restDays().then(setRest).catch(() => {});
  }, [props.version]);

  const tomorrow = addDays(state.today, 1);

  return (
    <>
      <div className="panel">
        <div className="home-top">
          <Hero attackKey={props.attackKey} />
          <div className="grow">
            <div className="row">
              <img src="/ui/icon_coin.png" className="pixel" width={32} height={32} alt="" />
              <span className="stat-big" aria-label="balance">
                {whole(state.balance)}
              </span>
            </div>
            <div className="dim small">today +{whole(state.xpToday)} XP</div>
            <div className="row" style={{ marginTop: 8 }}>
              <StreakFlame days={s.days} />
              <div>
                <div>
                  <b className="gold">{s.days}</b> day{s.days === 1 ? '' : 's'}
                  <span className="cyan"> x{s.multiplierToday.toFixed(2)}</span>
                </div>
                <div className="dim small">
                  {s.freezes > 0 && `❄ ${s.freezes} freeze${s.freezes > 1 ? 's' : ''} · `}
                  {s.nextMilestone > 0 && `next: ${s.nextMilestone}d`}
                </div>
              </div>
            </div>
          </div>
        </div>
        {s.comebackPending && <p className="gold small">Welcome back! Your next action pays double.</p>}
        <div className="row wrap" style={{ marginTop: 8 }}>
          {rest.includes(tomorrow) ? (
            <span className="dim small">💤 tomorrow is a rest day</span>
          ) : (
            <button className="chip" onClick={() => props.act(api.planRest(tomorrow))}>
              💤 rest tomorrow
            </button>
          )}
          {rest.includes(state.today) && <span className="cyan small">resting today: streak is safe</span>}
        </div>
      </div>

      <CheckinRow act={props.act} version={props.version} />
      <QuickAdd meta={props.meta} onAdded={props.refresh} onError={props.onError} />
      <ConsoleLog events={state.log} />
    </>
  );
}

function addDays(isoDate: string, days: number) {
  const d = new Date(isoDate + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
