import type { TimerView } from '../api';

/**
 * The only way a focus block pays: an explicit Yes after it ended (brief 3.2).
 * One tap to validate (brief 14 friction test).
 */
export function TimerQuestion(props: { timer: TimerView; onAnswer: (yes: boolean) => void }) {
  const t = props.timer;
  const stopped = t.stoppedAt !== null && t.countedMinutes < t.plannedMinutes;
  return (
    <div className="modal-back">
      <div className="panel modal" role="dialog" aria-label="focus check">
        <h2>focus check</h2>
        <p>
          {stopped ? `Stopped after ${t.countedMinutes} min` : `${t.plannedMinutes} min block over`}:{' '}
          <b className="cyan">{t.label}</b>
        </p>
        <p className="big">Did you really focus?</p>
        {!t.wouldPay && <p className="dim small">Under the minimum length: this block pays no XP either way.</p>}
        <div className="row">
          <button className="btn big primary" onClick={() => props.onAnswer(true)}>
            Yes
          </button>
          <button className="btn big" onClick={() => props.onAnswer(false)}>
            No
          </button>
        </div>
      </div>
    </div>
  );
}
