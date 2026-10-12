import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Act } from '../types';

const FACES = ['x_x', '-_-', 'o_o', '^_^', '*o*'];

/** Mood check-in: one tap on a 5-level scale, once per day (brief 6). */
export function CheckinRow(props: { act: Act; version: number }) {
  const [mood, setMood] = useState<number | null>(null);

  useEffect(() => {
    api.checkinToday().then((r) => setMood(r.mood)).catch(() => {});
  }, [props.version]);

  if (mood !== null) {
    return (
      <div className="panel">
        <h2>mood</h2>
        <span className="green">{FACES[mood - 1]}</span> <span className="dim">checked in today</span>
      </div>
    );
  }
  return (
    <div className="panel">
      <h2>how are you?</h2>
      <div className="row">
        {FACES.map((face, i) => (
          <button key={face} className="btn mood" aria-label={`mood ${i + 1}`} onClick={() => props.act(api.checkin(i + 1))}>
            {face}
          </button>
        ))}
      </div>
    </div>
  );
}
