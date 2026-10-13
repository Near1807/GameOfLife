import { useEffect, useState } from 'react';
import { MonitorCog } from 'lucide-react';
import { desktop } from '../api';
import { Card } from './Card';
import { Switch } from './Switch';

/** Windows desktop app only: start with Windows (off by default) and where the data lives. */
export function DesktopCard() {
  const [startup, setStartup] = useState<boolean | null>(null);
  useEffect(() => {
    desktop?.getStartWithWindows().then(setStartup).catch(() => setStartup(false));
  }, []);
  if (!desktop) return null;
  return (
    <Card title="Desktop app" icon={MonitorCog}>
      <div className="list-item">
        <div className="grow">
          <div className="item-name">Start with Windows</div>
          <div className="tiny faint">Opens GameLife when you sign in, so missed waves are worked out right away.</div>
        </div>
        <Switch label="Start with Windows" checked={!!startup}
          onChange={() => desktop!.setStartWithWindows(!startup).then(setStartup)} />
      </div>
      <div className="list-item">
        <div className="grow">
          <div className="item-name">Data folder</div>
          <div className="tiny faint" style={{ wordBreak: 'break-all' }}>{desktop.dataFolder}</div>
          <div className="tiny faint">Database, settings and backups. Reinstalling or updating never touches it.</div>
        </div>
        <button className="btn ghost" onClick={() => desktop!.openFolder(desktop!.dataFolder)}>Open</button>
      </div>
    </Card>
  );
}
