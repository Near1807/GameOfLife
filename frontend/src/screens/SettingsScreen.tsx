import { useState } from 'react';
import { Download, HardDrive, SlidersHorizontal, Upload } from 'lucide-react';
import { api } from '../api';
import type { Settings } from '../settings';
import { Card } from '../ui/Card';
import { Switch } from '../ui/Switch';

/** Backup (brief 3.3), effects and sound switches (brief 3.1). */
export function SettingsScreen(props: {
  settings: Settings;
  onSettings: (s: Settings) => void;
  refresh: () => void;
  onMessage: (m: string) => void;
}) {
  const [pending, setPending] = useState<{ name: string; text: string } | null>(null);
  const s = props.settings;

  const row = (key: keyof Settings, label: string, hint: string) => (
    <div className="list-item">
      <div className="grow">
        <div className="item-name">{label}</div>
        <div className="tiny faint">{hint}</div>
      </div>
      <Switch label={label} checked={s[key]} onChange={() => props.onSettings({ ...s, [key]: !s[key] })} />
    </div>
  );

  return (
    <div className="page">
      <h1 className="page-title">Settings</h1>
      <p className="page-sub">Stored on this device only.</p>
      <div className="grid two">
        <Card title="Display" icon={SlidersHorizontal}>
          {row('reducedEffects', 'Reduced effects', 'No animations, no motion.')}
          {row('sound', 'Sound', 'A short chime when you earn XP or a block ends.')}
        </Card>
        <Card title="Backup" icon={HardDrive}>
          <p className="small muted" style={{ marginTop: 0 }}>
            One file with the whole ledger and your lists.
          </p>
          <a className="btn primary block" href="/api/backup/export" download style={{ textDecoration: 'none' }}>
            <Download size={18} /> Export backup
          </a>
          <p className="small muted">Restore replaces all current data, after checking the file's hash chain.</p>
          <label className="btn block" style={{ cursor: 'pointer' }}>
            <Upload size={18} /> Choose a backup file
            <input
              type="file"
              accept="application/json,.json"
              aria-label="backup file"
              style={{ display: 'none' }}
              onChange={async (e) => {
                const f = e.target.files?.[0];
                if (f) setPending({ name: f.name, text: await f.text() });
              }}
            />
          </label>
          {pending && (
            <div className="row" style={{ marginTop: 10 }}>
              <button
                className="btn primary grow"
                onClick={() =>
                  api
                    .importBackup(pending.text)
                    .then((r) => {
                      props.onMessage('Restored. ' + r.message);
                      setPending(null);
                      props.refresh();
                    })
                    .catch((err) => props.onMessage(err.message))
                }
              >
                Confirm restore of {pending.name}
              </button>
              <button className="btn ghost" onClick={() => setPending(null)}>
                Cancel
              </button>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
