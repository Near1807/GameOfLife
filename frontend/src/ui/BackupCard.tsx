import { useEffect, useState } from 'react';
import { AlertTriangle, Check, FolderOpen, HardDriveDownload, RotateCcw, ShieldCheck } from 'lucide-react';
import { api, desktop, type BackupStatus } from '../api';
import { Card } from './Card';

/**
 * Automatic backups (desktop brief 4): where they go, when the last one was made, whether each one
 * verifies, and one button to restore (the server checks the hash chain before touching anything
 * and saves the current data first). Restore asks one confirmation tap.
 */
export function BackupCard(props: { onMessage: (m: string) => void; refresh: () => void }) {
  const [s, setS] = useState<BackupStatus | null>(null);
  const [folder, setFolder] = useState('');
  const [sure, setSure] = useState<string | null>(null);
  const load = () => api.backupStatus().then((x) => { setS(x); setFolder(x.folder); }).catch(() => {});
  useEffect(() => { load(); }, []);
  if (!s) return <Card title="Automatic backups" icon={ShieldCheck}><p className="muted small">Loading…</p></Card>;

  const save = (f: string) => api.setBackupFolder(f).then((x) => { setS(x); setFolder(x.folder); props.onMessage('Backup folder saved'); })
    .catch((e) => props.onMessage(e.message));
  const last = s.backups[0];
  return (
    <Card title="Automatic backups" icon={ShieldCheck}>
      <p className="small muted" style={{ marginTop: 0 }}>
        A copy of everything when the app closes and once a day. The 14 newest are kept, plus one per week for 8 weeks.
      </p>
      <div className={'small row' + (s.stale ? ' warn-line' : '')} style={{ gap: 6, marginBottom: 8 }}>
        {s.stale ? <AlertTriangle size={14} /> : <Check size={14} style={{ color: 'var(--success)' }} />}
        {last ? <>Last backup {ago(last.at)}{last.valid ? ', verified' : ', DAMAGED: ' + last.problem}</> : 'No backup yet'}
      </div>
      {s.lastError && <div className="small warn-line" style={{ marginBottom: 8 }}><AlertTriangle size={14} /> {s.lastError}</div>}

      <label className="label" htmlFor="backup-folder">Folder{s.defaultFolder ? ' (default, in the data folder)' : ''}</label>
      <div className="row" style={{ gap: 6, marginTop: 4 }}>
        <input id="backup-folder" className="input grow" value={folder} onChange={(e) => setFolder(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && save(folder)} spellCheck={false} />
        {desktop && (
          <button className="btn" onClick={() => desktop!.chooseFolder().then((f) => { if (f) save(f); })} aria-label="choose a folder">
            <FolderOpen size={16} />
          </button>
        )}
      </div>
      <div className="row wrap" style={{ gap: 6, marginTop: 8 }}>
        {folder !== s.folder && <button className="btn primary" onClick={() => save(folder)}>Save folder</button>}
        {!s.defaultFolder && <button className="btn ghost" onClick={() => save('')}>Use the default</button>}
        {desktop && <button className="btn ghost" onClick={() => desktop!.openFolder(s.folder)}>Open folder</button>}
        <button className="btn" onClick={() => api.backupNow().then(() => { load(); props.onMessage('Backup written and verified'); })
          .catch((e) => props.onMessage(e.message))}>
          <HardDriveDownload size={16} /> Back up now
        </button>
      </div>

      {s.backups.length > 0 && <div className="label" style={{ marginTop: 14 }}>Backups ({s.backups.length})</div>}
      {s.backups.slice(0, 8).map((b) => (
        <div key={b.file} className="list-item">
          <div className="grow">
            <div className="small">{new Date(b.at).toLocaleString()} <span className="faint">· {b.reason}</span></div>
            <div className="tiny faint">{b.valid ? `${b.events} events, chain verified` : `damaged: ${b.problem}`}</div>
          </div>
          <button className={'btn' + (sure === b.file ? ' primary' : ' ghost')} disabled={!b.valid}
            onClick={() => {
              if (sure !== b.file) return setSure(b.file);
              setSure(null);
              api.restoreBackup(b.file).then(() => { props.onMessage('Restored. Your data before the restore was backed up first.'); load(); props.refresh(); })
                .catch((e) => props.onMessage(e.message));
            }}>
            <RotateCcw size={15} /> {sure === b.file ? 'Tap again to restore' : 'Restore'}
          </button>
        </div>
      ))}
    </Card>
  );
}

export function ago(iso: string) {
  const h = (Date.now() - new Date(iso).getTime()) / 3600000;
  if (h < 1) return 'less than an hour ago';
  if (h < 48) return `${Math.floor(h)} hour${Math.floor(h) === 1 ? '' : 's'} ago`;
  return `${Math.floor(h / 24)} days ago`;
}
