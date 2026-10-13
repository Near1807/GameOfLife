import { useState } from 'react';
import { AlertTriangle, Download, FolderSync, HardDrive, Palette, SlidersHorizontal, Upload } from 'lucide-react';
import { useEffect } from 'react';
import type { Catalog } from '../api';
import { desktop } from '../api';
import { BackupCard } from '../ui/BackupCard';
import { DesktopCard } from '../ui/DesktopCard';
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
  openShop: () => void;
}) {
  const [pending, setPending] = useState<{ name: string; text: string } | null>(null);
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  useEffect(() => {
    api.content().then(setCatalog).catch(() => {});
  }, []);
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
          {row('sound', 'Sound', 'A short chime when you earn XP or a block ends. Off by default.')}
          <div className="list-item">
            <div className="grow">
              <div className="item-name">Theme</div>
              <div className="tiny faint">Bought themes are switched in the shop, one tap.</div>
            </div>
            <button className="btn" onClick={props.openShop}><Palette size={16} /> Themes</button>
          </div>
        </Card>
        <BackupCard onMessage={props.onMessage} refresh={props.refresh} />
        {desktop && <DesktopCard />}
        <Card title="Content folder" icon={FolderSync}>
          <p className="small muted" style={{ marginTop: 0 }}>
            {catalog ? `${catalog.heroes.length} hero looks, ${catalog.items.length} shop items. Towers, waves and maps are read again too.` : '…'}
          </p>
          <button className="btn block" onClick={() => api.reloadContent().then((c) => {
            setCatalog(c);
            props.onMessage(c.warnings.length === 0 ? 'Content reloaded, no problems.' : `Content reloaded with ${c.warnings.length} warning(s).`);
            props.refresh();
          }).catch((e) => props.onMessage(e.message))}>
            <FolderSync size={16} /> Reload content
          </button>
          {catalog && catalog.warnings.length > 0 && (
            <div style={{ marginTop: 12 }}>
              {catalog.warnings.map((w) => (
                <div key={w} className="small row" style={{ gap: 8, alignItems: 'flex-start', padding: '4px 0', color: 'var(--violet-soft)' }}>
                  <AlertTriangle size={14} style={{ flexShrink: 0, marginTop: 2 }} /> {w}
                </div>
              ))}
            </div>
          )}
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
