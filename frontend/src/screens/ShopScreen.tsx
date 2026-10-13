import { useEffect, useState } from 'react';
import { Coins, Gem, Lock, Palette, Shield, Shirt, Sparkles, Sword, Swords } from 'lucide-react';
import { api, type ShopItem, type ShopView, type Upgrade } from '../api';
import type { Act } from '../types';
import { Card } from '../ui/Card';
import { Sprite } from '../ui/Sprite';

type Tab = 'power' | 'charms' | 'looks' | 'themes';

/**
 * The shop (brief 9.2, 9.3, 10): the owner decides where coins go. Damage (weapon) kills
 * faster, armour survives lazy days, charms give utility, looks and themes are only fun.
 * Every effect is spelled out, buying takes two taps, and new items appear from the content
 * folder with no code change.
 */
export function ShopScreen(props: { act: Act; version: number }) {
  const [view, setView] = useState<ShopView | null>(null);
  const [tab, setTab] = useState<Tab>('power');
  useEffect(() => {
    api.shop().then(setView).catch(() => {});
  }, [props.version]);
  if (!view) return <p className="muted">Loading…</p>;

  const of = (kinds: string[]) => view.items.filter((i) => kinds.includes(i.item.kind));
  const charmsWorn = view.items.filter((i) => i.item.kind === 'charm' && i.status === 'EQUIPPED').length;

  return (
    <div className="page">
      <h1 className="page-title">Shop</h1>
      <p className="page-sub">
        <Coins size={14} style={{ verticalAlign: '-2px', color: 'var(--gold)' }} /> <b className="gold">{Math.round(view.balance).toLocaleString('en-US')}</b> coins.
        Predictions use your last 14 days: about {Math.round(view.averageDailyXp)} XP a day.
      </p>
      <div className="choice-strip">
        <span><Swords size={14} /> Damage: kill faster</span>
        <span><Shield size={14} /> Armour: survive lazy days</span>
        <span><Sparkles size={14} /> Charms: utility</span>
        <span><Palette size={14} /> Looks: just fun</span>
      </div>
      <div className="segmented" role="tablist">
        {([['power', 'Weapon & armour'], ['charms', 'Charms'], ['looks', 'Looks'], ['themes', 'Themes']] as [Tab, string][]).map(([t, label]) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'on' : ''} onClick={() => setTab(t)}>{label}</button>
        ))}
      </div>

      {tab === 'power' && (
        <div className="grid two">
          <UpgradeCard u={view.weapon} icon={Sword} title="Weapon" act={props.act} />
          <UpgradeCard u={view.armor} icon={Shield} title="Armour" act={props.act} />
          {of(['weapon', 'armor']).map((i) => <ItemCard key={i.item.id} s={i} act={props.act} />)}
        </div>
      )}
      {tab === 'charms' && (
        <>
          <p className="small muted">Wear up to {view.maxCharms} charms. Now wearing {charmsWorn}.</p>
          <div className="grid two">{of(['charm']).map((i) => <ItemCard key={i.item.id} s={i} act={props.act} />)}</div>
        </>
      )}
      {tab === 'looks' && <div className="grid two">{of(['outfit', 'skin']).map((i) => <ItemCard key={i.item.id} s={i} act={props.act} />)}</div>}
      {tab === 'themes' && <div className="grid two">{of(['theme']).map((i) => <ItemCard key={i.item.id} s={i} act={props.act} />)}</div>}
    </div>
  );
}

function UpgradeCard(props: { u: Upgrade; icon: typeof Sword; title: string; act: Act }) {
  const [confirm, setConfirm] = useState(false);
  const u = props.u;
  const can = u.status === 'AFFORDABLE';
  return (
    <Card title={`${props.title} · level ${u.level}`} icon={props.icon} iconColor="var(--gold)" glow={can}>
      <p className="small" style={{ marginTop: 0 }}>{u.effectText}</p>
      {confirm ? (
        <div className="row">
          <button className="btn primary grow" onClick={() => props.act(api.upgrade(u.slot)).finally(() => setConfirm(false))}>
            Confirm: pay {Math.round(u.cost)}
          </button>
          <button className="btn ghost" onClick={() => setConfirm(false)}>Cancel</button>
        </div>
      ) : (
        <button className={'btn block' + (can ? ' primary' : '')} disabled={!can} onClick={() => setConfirm(true)} aria-label={`upgrade ${u.slot}`}>
          <Coins size={16} /> Level {u.level + 1} for {Math.round(u.cost)}
        </button>
      )}
    </Card>
  );
}

const KIND_ICON = { weapon: Sword, armor: Shield, charm: Gem, outfit: Shirt, skin: Palette, theme: Palette } as const;

function ItemCard(props: { s: ShopItem; act: Act }) {
  const [confirm, setConfirm] = useState(false);
  const { item, status } = props.s;
  const Icon = KIND_ICON[item.kind];
  return (
    <Card>
      <div className="row" style={{ alignItems: 'flex-start', gap: 14 }}>
        <div className={'item-frame' + (status === 'LOCKED' ? ' locked' : '')}>
          {item.kind === 'theme' ? <ThemeSwatch tokens={item.tokens} />
            : item.sprite && <Sprite src={item.sprite} frames={item.frames} size={32} scale={2} label={item.name} />}
        </div>
        <div className="grow">
          <div className="row between" style={{ alignItems: 'baseline' }}>
            <span style={{ fontWeight: 650 }}><Icon size={14} style={{ verticalAlign: '-2px' }} className="faint" /> {item.name}</span>
            <span className="reward">{item.price === 0 ? 'free' : Math.round(item.price)}</span>
          </div>
          <div className="small muted">{item.description}</div>
          <div className="small" style={{ marginTop: 6 }}>{props.s.effectText}</div>
          {status === 'LOCKED' && <div className="tiny" style={{ color: 'var(--violet-soft)', marginTop: 4 }}><Lock size={11} /> Locked: {props.s.lockReason}</div>}
        </div>
      </div>
      <div style={{ marginTop: 12 }}>
        {status === 'EQUIPPED' && (item.kind === 'theme'
          ? <span className="pill cyan">In use</span>
          : <button className="btn block" onClick={() => props.act(api.equip(item.id, false))}>Take off</button>)}
        {status === 'OWNED' && <button className="btn block primary" onClick={() => props.act(api.equip(item.id, true))}>{item.kind === 'theme' ? 'Use theme' : 'Equip'}</button>}
        {(status === 'AFFORDABLE' || status === 'TOO_EXPENSIVE') && (confirm ? (
          <div className="row">
            <button className="btn primary grow" onClick={() => props.act(api.buy(item.id)).finally(() => setConfirm(false))}>Confirm: pay {Math.round(item.price)}</button>
            <button className="btn ghost" onClick={() => setConfirm(false)}>Cancel</button>
          </div>
        ) : (
          <button className={'btn block' + (status === 'AFFORDABLE' ? ' primary' : '')} disabled={status !== 'AFFORDABLE'}
            onClick={() => setConfirm(true)} aria-label={`buy ${item.name}`}>
            <Coins size={16} /> Buy for {Math.round(item.price)}
          </button>
        ))}
      </div>
    </Card>
  );
}

/** A theme preview from its own tokens: background, accent gradient, two accents. */
function ThemeSwatch(props: { tokens: Record<string, string> }) {
  const t = props.tokens;
  return (
    <div style={{ width: 64, height: 64, borderRadius: t['--radius-sm'] ?? 12, background: t['--bg'] ?? 'var(--bg)', padding: 8, display: 'grid', gap: 5 }}>
      <div style={{ height: 14, borderRadius: 6, background: t['--accent-grad'] ?? 'var(--accent-grad)' }} />
      <div className="row" style={{ gap: 5 }}>
        <span style={{ flex: 1, height: 14, borderRadius: 6, background: t['--violet'] ?? 'var(--violet)' }} />
        <span style={{ flex: 1, height: 14, borderRadius: 6, background: t['--cyan'] ?? 'var(--cyan)' }} />
      </div>
    </div>
  );
}
