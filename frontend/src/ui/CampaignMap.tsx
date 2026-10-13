import { Check, Lock } from 'lucide-react';
import type { FightView } from '../api';
import { VARIANT_FILTER } from './Arena';

/**
 * Every boss picture in content/enemies as a node (brief 11): beaten, current, or a locked
 * silhouette. Past the last picture, bosses loop as tinted "stronger variants".
 */
export function CampaignMap(props: { fight: FightView }) {
  const f = props.fight;
  return (
    <div className="map" role="list" aria-label="campaign map">
      {f.map.map((n) => (
        <div key={n.number} className={'map-node ' + n.status} role="listitem" aria-label={`boss ${n.number}: ${n.name}, ${n.status.toLowerCase()}`}>
          <div className="map-thumb">
            <div className="sprite" style={{ width: 64, height: 64, backgroundImage: `url('/${n.sprite}')`, backgroundSize: `${64 * n.frames}px 64px`,
              filter: n.status !== 'LOCKED' && (f.ascensions > 0 || n.number > f.bossFiles) ? VARIANT_FILTER : undefined }} />
            {n.status === 'CLEARED' && <span className="map-badge"><Check size={14} /></span>}
            {n.status === 'LOCKED' && <span className="map-badge lock"><Lock size={12} /></span>}
          </div>
          <div className="tiny" style={{ fontWeight: 600 }}>{n.number}. {n.name}</div>
          {n.defeats > 0 && <div className="tiny" style={{ color: 'var(--violet-soft)' }}>beat you {n.defeats}×</div>}
        </div>
      ))}
    </div>
  );
}
