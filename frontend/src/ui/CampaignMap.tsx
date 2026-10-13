import { Check, Lock } from 'lucide-react';
import type { StageView } from '../api';

/** The ten stages as nodes on a path (brief 11): cleared, current, locked. */
export function CampaignMap(props: { stage: StageView }) {
  return (
    <div className="map" role="list" aria-label="campaign map">
      {props.stage.map.map((n) => (
        <div key={n.stage} className={'map-node ' + n.status} role="listitem" aria-label={`stage ${n.stage}: ${n.name}, ${n.status.toLowerCase()}`}>
          <div className="map-thumb">
            <div className="sprite" style={{ width: 64, height: 64, backgroundImage: `url('/${n.sprite}')`, backgroundSize: '256px 64px' }} />
            {n.status === 'CLEARED' && <span className="map-badge"><Check size={14} /></span>}
            {n.status === 'LOCKED' && <span className="map-badge lock"><Lock size={12} /></span>}
          </div>
          <div className="tiny" style={{ fontWeight: 600 }}>{n.stage}. {n.name}</div>
          <div className="tiny faint tabular">{Math.round(n.target).toLocaleString('en-US')} HP</div>
        </div>
      ))}
    </div>
  );
}
