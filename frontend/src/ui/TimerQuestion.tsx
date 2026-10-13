import { Check, Timer, X } from 'lucide-react';
import type { TimerView } from '../api';
import { Card } from './Card';

/**
 * The only way a focus block pays: an explicit Yes after it ended (brief 3.2).
 * One tap to validate (brief 14 friction test). Shown on every screen.
 */
export function TimerQuestion(props: { timer: TimerView; previewXp: number; onAnswer: (yes: boolean) => void }) {
  const t = props.timer;
  const stopped = t.stoppedAt !== null && t.countedMinutes < t.plannedMinutes;
  return (
    <div className="modal-back">
      <div className="modal" role="dialog" aria-label="focus check">
        <Card title="Focus check" icon={Timer} iconColor="var(--cyan)" glow>
          <p className="muted" style={{ marginTop: 0 }}>
            {stopped ? `Stopped after ${t.countedMinutes} min` : `${t.plannedMinutes} min block complete`} ·{' '}
            <b style={{ color: 'var(--text)' }}>{t.label}</b>
          </p>
          <p className="class-name" style={{ margin: '8px 0 6px' }}>
            Did you really focus?
          </p>
          {t.wouldPay ? (
            <p className="small muted">
              Yes pays <span className="reward">+{Math.round(props.previewXp)} XP</span>
            </p>
          ) : (
            <p className="small muted">Under the minimum length: this block pays no XP either way.</p>
          )}
          <div className="row" style={{ marginTop: 16 }}>
            <button className="btn primary lg grow" onClick={() => props.onAnswer(true)}>
              <Check size={20} /> Yes
            </button>
            <button className="btn lg grow" onClick={() => props.onAnswer(false)}>
              <X size={20} /> No
            </button>
          </div>
        </Card>
      </div>
    </div>
  );
}
