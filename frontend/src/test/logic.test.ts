import { describe, expect, it } from 'vitest';
import { nextAction } from '../nextAction';
import { splitPreview } from '../stats';
import type { Character, HabitView, TaskView } from '../api';

describe('splitPreview (same rule as the backend StatSplitter)', () => {
  it('splits equally and always sums to the base', () => {
    expect(splitPreview(3, ['A', 'B'])).toEqual([{ id: 'A', xp: 1.5 }, { id: 'B', xp: 1.5 }]);
    expect(splitPreview(10, ['A', 'B', 'C'])).toEqual([{ id: 'A', xp: 3.34 }, { id: 'B', xp: 3.33 }, { id: 'C', xp: 3.33 }]);
    for (let base = 0; base < 500; base += 7.13) {
      for (let n = 1; n <= 7; n++) {
        const parts = splitPreview(base, Array.from({ length: n }, (_, i) => `S${i}`));
        const sumCents = parts.reduce((s, p) => s + Math.round(p.xp * 100), 0);
        expect(sumCents).toBe(Math.round(base * 100));
      }
    }
    expect(splitPreview(30, [])).toEqual([]);
  });
});

const task = (id: number, minutes: number, tags: string[] = []): TaskView =>
  ({ id, name: `t${id}`, expectedMinutes: minutes, tags, done: false, xpPaid: 0, viaTimer: false });
const habit = (id: number, done: boolean): HabitView =>
  ({ id, name: `h${id}`, days: ['MONDAY'], tags: [], scheduledToday: true, doneToday: done, streak: 3, history: [] });
const character = {
  stats: [
    { id: 'INTELLECT', name: 'Intellect', xp: 500 },
    { id: 'SOCIAL', name: 'Social', xp: 0 },
  ],
} as unknown as Character;

describe('next best action', () => {
  it('check-in first, then habits, then tasks training the lowest stat, then focus', () => {
    expect(nextAction({ checkedIn: false, habits: [habit(1, false)], tasks: [], character }).kind).toBe('checkin');
    expect(nextAction({ checkedIn: true, habits: [habit(1, false)], tasks: [task(1, 5)], character })).toMatchObject({ kind: 'habit', id: 1 });
    const t = nextAction({ checkedIn: true, habits: [habit(1, true)], tasks: [task(1, 5, ['INTELLECT']), task(2, 60, ['SOCIAL'])], character });
    expect(t).toMatchObject({ kind: 'task', id: 2 }); // trains Social, the lowest stat
    expect(nextAction({ checkedIn: true, habits: [], tasks: [task(1, 30), task(2, 10)], character })).toMatchObject({ kind: 'task', id: 2 });
    expect(nextAction({ checkedIn: true, habits: [], tasks: [], character }).kind).toBe('focus');
  });
});
