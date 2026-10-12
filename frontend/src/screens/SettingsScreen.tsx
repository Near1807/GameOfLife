import { useState } from 'react';
import { api } from '../api';
import type { Settings } from '../settings';

/** Backup (brief 3.3), effects and sound switches (brief 3.1). */
export function SettingsScreen(props: {
  settings: Settings;
  onSettings: (s: Settings) => void;
  refresh: () => void;
  onMessage: (m: string) => void;
}) {
  const [pending, setPending] = useState<{ name: string; text: string } | null>(null);
  const s = props.settings;

  const toggle = (key: keyof Settings, label: string) => (
    <label className="item" style={{ cursor: 'pointer' }}>
      <span className="grow">{label}</span>
      <input type="checkbox" checked={s[key]} onChange={() => props.onSettings({ ...s, [key]: !s[key] })} style={{ width: 24, height: 24 }} />
    </label>
  );

  return (
    <>
      <div className="panel">
        <h2>display</h2>
        {toggle('reducedEffects', 'reduced effects')}
        {toggle('sound', 'sound')}
        {toggle('scanlines', 'CRT scanlines')}
      </div>

      <div className="panel">
        <h2>backup</h2>
        <p className="dim small">Saves the whole ledger and your lists to a file.</p>
        <a className="btn primary" style={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }} href="/api/backup/export" download>
          export backup file
        </a>
        <p className="dim small" style={{ marginTop: 16 }}>
          Restore replaces ALL current data with the file, after checking its hash chain.
        </p>
        <input
          type="file"
          accept="application/json,.json"
          aria-label="backup file"
          onChange={async (e) => {
            const f = e.target.files?.[0];
            if (f) setPending({ name: f.name, text: await f.text() });
          }}
        />
        {pending && (
          <div className="row" style={{ marginTop: 10 }}>
            <button
              className="btn primary"
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
              confirm restore of {pending.name}
            </button>
            <button className="btn ghost" onClick={() => setPending(null)}>
              cancel
            </button>
          </div>
        )}
      </div>
    </>
  );
}
