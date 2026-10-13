import { useEffect, useState } from 'react';
import { Castle, Coins, Eye, Heart, Lock, Play, RotateCcw, TrendingUp, X } from 'lucide-react';
import { api, type DefenseView, type SpotView, type Watch } from '../api';
import type { Act } from '../types';
import { loadSeenAttempt, saveSeenAttempt } from '../seen';
import { Card } from './Card';
import { DefenseMap, TowerIcon } from './DefenseMap';

/**
 * Home screen game (v4 brief 4): the map with the current build, the wave and its status, the
 * income bonus, the last attempt, and the build / upgrade / sell panel of the tapped spot.
 *
 * Friction (brief 4): build = tap a spot, tap a tower. Upgrade = tap the tower, tap a path.
 * Sell = tap the tower, tap Sell, tap again to confirm.
 */
export function DefenseCard(props: { act: Act; version: number; filter?: string | null; onView?: (v: DefenseView) => void }) {
  const [view, setView] = useState<DefenseView | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [watch, setWatch] = useState<Watch | null>(null);
  const [speed, setSpeed] = useState(3);
  const [ended, setEnded] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [away, setAway] = useState<string | null>(null);
  const onView = props.onView;

  useEffect(() => {
    api.game().then((v) => {
      setView(v);
      onView?.(v);
      setAway((prev) => awayText(v) ?? prev);
    }).catch(() => {});
  }, [props.version, onView]);

  // the countdown to the next attempt
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(id);
  }, []);

  if (!view) return <Card><p className="muted">Loading the map…</p></Card>;

  const play = async (key?: string) => {
    try {
      setEnded(false);
      setWatch(await api.replay(key));
      setSelected(null);
    } catch (e) {
      /* no attempt yet */
    }
  };

  const runNow = async () => {
    const r = await props.act(api.runWave());
    if (r?.paid && r.event) play(r.event.key);
  };

  const spot = selected === null ? null : view.spots[selected];
  const last = view.last;

  return (
    <Card className="defense-card" glow>
      <div className="row wrap defense-hud">
        <span className="pill violet"><Castle size={13} /> Wave <b className="num">{view.wave}</b></span>
        <span className="pill"><Heart size={13} /> {view.livesMax} lives</span>
        <span className="pill gold"><Coins size={13} /> <span className="num">{Math.floor(view.balance)}</span></span>
        <span className="pill gold" title="Multiplies the coins of every real action (Markets and Social)">
          <TrendingUp size={13} /> Income bonus x{view.incomeMultiplier.toFixed(2)}
        </span>
        {view.discount > 0 && <span className="pill cyan">-{Math.round(view.discount * 100)}% prices</span>}
      </div>

      {away && (
        <div className="away row" role="status">
          <span className="grow small">{away}</span>
          <button className="btn ghost" onClick={() => { setAway(null); play(); }}><Eye size={15} /> Watch</button>
          <button className="icon-btn" onClick={() => setAway(null)} aria-label="dismiss"><X size={15} /></button>
        </div>
      )}
      <div className="defense-wrap">
        <DefenseMap view={view} selected={selected} onSelect={(s) => { setSelected(s); }} watch={watch} speed={speed}
          onEnd={() => setEnded(true)} filter={props.filter} />
        {watch && ended && (
          <div className={'defense-result ' + (watch.result.won ? 'won' : 'lost')}>
            <b>Wave {watch.attempt.wave} {watch.result.won ? 'cleared' : 'failed'}</b>
            <span className="small">{watch.result.killed}/{watch.result.enemies} defeated · {watch.result.lives}/{watch.result.livesMax} lives left</span>
            <button className="btn ghost" onClick={() => { setWatch(null); setEnded(false); }}>Back to the map</button>
          </div>
        )}
      </div>

      <div className="small" style={{ marginTop: 10 }}>
          <StatusLine view={view} now={now} />
          {last && (
            <div className="tiny muted">
              Last: wave {last.wave} {last.won ? 'cleared' : 'failed'} · {last.killed}/{last.enemies} defeated ·{' '}
              {last.lives}/{last.livesMax} lives{last.manual ? ' · run now' : ''}
            </div>
          )}
      </div>
      <div className="row wrap defense-controls">
        {watch ? (
          <>
            {[1, 3, 10].map((x) => (
              <button key={x} className={'btn' + (speed === x ? ' primary' : '')} onClick={() => setSpeed(x)} aria-pressed={speed === x}>
                x{x}
              </button>
            ))}
            <button className="btn ghost" onClick={() => { setWatch(null); setEnded(false); }} aria-label="stop watching"><X size={16} /></button>
          </>
        ) : (
          <>
            {last && <button className="btn" onClick={() => play()}><Eye size={16} /> Watch last attempt</button>}
            <button className="btn primary" onClick={runNow} disabled={view.status === 'idle' || view.status === 'stuck' || view.status === 'capped'}
              title="An extra attempt now. Same daily limit, and only if something changed since a failure.">
              <Play size={16} /> Run now
            </button>
          </>
        )}
      </div>

      {spot && !watch && (
        <SpotPanel key={spot.index + ':' + (spot.tower?.type ?? '') + ':' + (spot.tower ? spot.tower.a + spot.tower.b : -1)}
          view={view} spot={spot} act={props.act} onClose={() => setSelected(null)} />
      )}
      {!spot && !watch && view.status === 'idle' && (
        <p className="small muted" style={{ marginBottom: 0 }}>Tap a <b>+</b> spot on the map to build your first tower. Waves then run on their own every few hours.</p>
      )}
    </Card>
  );
}

/**
 * "While you were away": the attempts made since this device last looked (shown once).
 * The first visit only remembers where things stand.
 */
function awayText(v: DefenseView): string | null {
  const seen = loadSeenAttempt();
  const latest = v.recent[0];
  if (!latest) {
    if (seen === null) saveSeenAttempt(''); // visited before any attempt: everything later is news
    return null;
  }
  saveSeenAttempt(latest.key);
  if (seen === null || seen === latest.key) return null;
  const idx = v.recent.findIndex((a) => a.key === seen);
  const fresh = (idx < 0 ? v.recent : v.recent.slice(0, idx)).filter((a) => !a.manual).reverse();
  if (fresh.length === 0) return null;
  const won = fresh.filter((a) => a.won).map((a) => a.wave);
  const lost = fresh.filter((a) => !a.won);
  const parts = [];
  if (won.length === 1) parts.push(`wave ${won[0]} cleared`);
  if (won.length > 1) parts.push(`waves ${won[0]} to ${won[won.length - 1]} cleared`);
  if (lost.length > 0) parts.push(`wave ${lost[lost.length - 1].wave} failed`);
  return `While you were away: ${parts.join(', ')}.`;
}

function StatusLine(props: { view: DefenseView; now: number }) {
  const v = props.view;
  if (v.status === 'idle') return <div>Build a tower to start the waves.</div>;
  if (v.status === 'stuck') {
    return (
      <div style={{ color: 'var(--gold)' }}>
        <RotateCcw size={13} /> Stuck on wave {v.wave}: the same build would fail again. Build, upgrade or level up a stat.
      </div>
    );
  }
  const wait = v.nextAt ? Math.max(0, new Date(v.nextAt).getTime() - props.now) : 0;
  if (v.status === 'capped') return <div>Daily limit of {v.maxAttemptsPerDay} attempts reached. Next try in {duration(wait)}.</div>;
  return <div>Next attempt of wave {v.wave} in {duration(wait)}.</div>;
}

export function duration(ms: number) {
  const min = Math.ceil(ms / 60000);
  const h = Math.floor(min / 60);
  return h > 0 ? `${h} h ${min % 60} min` : `${min} min`;
}

/** Build, upgrade, unlock or sell, depending on what the tapped spot holds. */
function SpotPanel(props: { view: DefenseView; spot: SpotView; act: Act; onClose: () => void }) {
  const { view, spot, act } = props;
  const [sure, setSure] = useState(false);
  const balance = view.balance;

  const head = (title: string, sub?: string) => (
    <div className="row" style={{ marginBottom: 10 }}>
      <div className="grow">
        <div style={{ fontWeight: 650 }}>{title}</div>
        {sub && <div className="tiny muted">{sub}</div>}
      </div>
      <button className="icon-btn" onClick={props.onClose} aria-label="close"><X size={16} /></button>
    </div>
  );

  if (!spot.open) {
    return (
      <div className="spot-panel">
        {head('Locked spot', 'Unlocking is permanent. Each spot costs more than the last.')}
        <button className="opt" disabled={balance < view.nextSpotCost} onClick={() => act(api.unlockSpot(spot.index))}>
          <span><Lock size={14} /> Unlock this spot</span>
          <span className="price num">{view.nextSpotCost}</span>
        </button>
      </div>
    );
  }

  if (!spot.tower) {
    return (
      <div className="spot-panel">
        {head('Build a tower', 'Selling later gives back everything you paid.')}
        <div className="opt-list">
          {view.towers.map((t) => (
            <button key={t.id} className="opt" disabled={!!t.locked || balance < t.price}
              onClick={() => act(api.buildTower(spot.index, t.id))} aria-label={`build ${t.name} for ${t.price}`}>
              <TowerIcon shape={t.shape} color={t.color} />
              <span className="grow">
                {t.name}
                <small>{t.locked ?? t.kind}</small>
              </span>
              <span className="price num">{t.price}</span>
            </button>
          ))}
        </div>
      </div>
    );
  }

  const tw = spot.tower;
  const s = tw.stats;
  const facts = tw.role === 'support'
    ? `Towers within ${Math.round(s.range)} get fire rate +${Math.round(s.rbuff * 100)}% and damage +${Math.round(s.dbuff * 100)}%.`
    : tw.role === 'income'
      ? `Earnings bonus +${Math.round(s.coin * 100)}%, build discount ${Math.round(s.disc * 100)}%.`
      : [`Damage ${s.dmg.toFixed(1)}`, `${s.rate.toFixed(2)}/s`, `range ${Math.round(s.range)}`,
          s.rad ? `area ${Math.round(s.rad)}` : '', s.slow ? `slow ${Math.round(s.slow * 100)}%` : '',
          s.burn ? `burn ${s.burn}/s` : '', s.pois ? `poison ${s.pois}/s` : '', s.chain ? `chain ${s.chain}` : '',
          s.multi > 1 ? `${s.multi} targets` : '', s.pierce ? `pierce ${Math.round(s.pierce * 100)}%` : '']
          .filter(Boolean).join(' · ');
  return (
    <div className="spot-panel">
      {head(tw.name, tw.kind)}
      <p className="tiny muted num" style={{ marginTop: 0 }}>{facts}</p>
      {tw.a + tw.b === 0 && <p className="tiny faint">Choosing a path locks the other. Sell the tower to choose again.</p>}
      <div className="opt-list">
        {tw.paths.map((p, i) => (
          <button key={p.name} className="opt" disabled={p.locked || p.price === null || balance < (p.price ?? 0)}
            onClick={() => act(api.upgradeTower(spot.index, i, p.tier + 1))}>
            <span className="pips" aria-label={`${p.tier} of 3`}>{[0, 1, 2].map((k) => (k < p.tier ? '●' : '○')).join('')}</span>
            <span className="grow">
              {p.name}
              <small>{p.locked ? 'Locked: you chose the other path' : p.next ?? 'Path complete'}</small>
            </span>
            <span className="price num">{p.price ?? ''}</span>
          </button>
        ))}
        <button className={'opt' + (sure ? ' danger' : '')} onClick={() => (sure ? act(api.sellTower(spot.index)) : setSure(true))}>
          <span className="grow">
            {sure ? 'Tap again to sell' : 'Sell'}
            <small>Full refund of everything you paid</small>
          </span>
          <span className="price num">+{Math.round(tw.paid)}</span>
        </button>
      </div>
    </div>
  );
}
