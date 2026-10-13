import { useEffect, useState } from 'react';
import { Coins, Lock, Palette, Shirt } from 'lucide-react';
import { api, type ShopItem, type ShopView } from '../api';
import type { Act } from '../types';
import { Card } from '../ui/Card';
import { Sprite } from '../ui/Sprite';

type Tab = 'looks' | 'themes';

/**
 * The shop (brief 10, v4 brief 4): looks only. Outfits, map skins and themes; power comes from
 * the towers on the map. Buying takes two taps, and new items appear from the content folder with
 * no code change.
 */
export function ShopScreen(props: { act: Act; version: number }) {
  const [view, setView] = useState<ShopView | null>(null);
  const [tab, setTab] = useState<Tab>('looks');
  useEffect(() => {
    api.shop().then(setView).catch(() => {});
  }, [props.version]);
  if (!view) return <p className="muted">Loading…</p>;

  const of = (kinds: string[]) => view.items.filter((i) => kinds.includes(i.item.kind));

  return (
    <div className="page">
      <h1 className="page-title">Shop</h1>
      <p className="page-sub">
        <Coins size={14} style={{ verticalAlign: '-2px', color: 'var(--gold)' }} /> <b className="gold">{Math.round(view.balance).toLocaleString('en-US')}</b> coins.
        Looks only: power comes from the towers on the map.
      </p>
      <div className="segmented" role="tablist">
        {([['looks', 'Looks'], ['themes', 'Themes']] as [Tab, string][]).map(([t, label]) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'on' : ''} onClick={() => setTab(t)}>{label}</button>
        ))}
      </div>

      {tab === 'looks' && <div className="grid two">{of(['outfit', 'skin']).map((i) => <ItemCard key={i.item.id} s={i} act={props.act} />)}</div>}
      {tab === 'themes' && <div className="grid two">{of(['theme']).map((i) => <ItemCard key={i.item.id} s={i} act={props.act} />)}</div>}
    </div>
  );
}

const KIND_ICON = { outfit: Shirt, skin: Palette, theme: Palette } as const;

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
