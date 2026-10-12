import { whole, type LedgerEvent } from '../api';
import { short } from './TagChips';

/** Console-style log of the latest ledger events (brief 11). */
export function ConsoleLog(props: { events: LedgerEvent[] }) {
  return (
    <div className="panel console">
      <h2>log</h2>
      {props.events.length === 0 && <div className="dim">no events yet. do one thing.</div>}
      {props.events.slice(0, 10).map((e) => (
        <div key={e.id} className="line">
          <span className="dim">{e.occurredAt.slice(11, 16)} </span>
          {describe(e)}
        </div>
      ))}
      <div className="cursor dim">$ </div>
    </div>
  );
}

function describe(e: LedgerEvent) {
  const tags = Object.keys(e.statSplits).map(short).join(' ');
  switch (e.type) {
    case 'REST_DAY':
      return <span className="cyan">rest day planned: {String(e.meta.date)}</span>;
    case 'TASK_UNDONE':
      return <span className="dim">undo {e.label} ({whole(e.xp)})</span>;
    default:
      return (
        <>
          <span className="gold">+{whole(e.xp)}</span> {e.label}
          {tags && <span className="cyan"> [{tags}]</span>}
        </>
      );
  }
}
