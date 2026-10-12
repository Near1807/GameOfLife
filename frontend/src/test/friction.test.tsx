/**
 * Friction tests (brief 3.1 and 14): count the taps needed for the daily actions.
 *   - a known task (already on the list) needs 1 tap
 *   - a new task needs its name plus 1 chip
 *   - validating a timer needs 1 tap
 * plus: a habit 1 tap, the mood check-in 1 tap, starting a timer 1 tap, re-adding a known task 1 tap.
 * The API is mocked: these tests only look at the interface.
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { askingTimer, meta, paid } from './fixtures';

vi.mock('../api', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api')>();
  return {
    ...original,
    api: {
      tasks: vi.fn(),
      suggestions: vi.fn(),
      createTask: vi.fn(),
      tickTask: vi.fn(),
      untickTask: vi.fn(),
      archiveTask: vi.fn(),
      habits: vi.fn(),
      habitDone: vi.fn(),
      checkinToday: vi.fn(),
      checkin: vi.fn(),
      startTimer: vi.fn(),
    },
  };
});

import { api } from '../api';
import { TasksScreen } from '../screens/TasksScreen';
import { QuickAdd } from '../components/QuickAdd';
import { TimerQuestion } from '../components/TimerQuestion';
import { HabitsScreen } from '../screens/HabitsScreen';
import { CheckinRow } from '../components/CheckinRow';
import { TimerScreen } from '../screens/TimerScreen';

const mocked = api as unknown as Record<string, ReturnType<typeof vi.fn>>;

/** userEvent wrapper that counts taps. Typing is not a tap. */
function tapCounter() {
  const user = userEvent.setup();
  let taps = 0;
  return {
    tap: async (el: Element) => {
      taps++;
      await user.click(el);
    },
    type: (el: Element, text: string) => user.type(el, text),
    get taps() {
      return taps;
    },
  };
}

const act = vi.fn(async (p: Promise<unknown>) => (await p) as never);

beforeEach(() => {
  vi.clearAllMocks();
  mocked.tasks.mockResolvedValue([]);
  mocked.suggestions.mockResolvedValue([]);
  mocked.createTask.mockResolvedValue({});
  mocked.tickTask.mockResolvedValue(paid);
  mocked.habits.mockResolvedValue([]);
  mocked.habitDone.mockResolvedValue(paid);
  mocked.checkinToday.mockResolvedValue({ mood: null });
  mocked.checkin.mockResolvedValue(paid);
  mocked.startTimer.mockResolvedValue(askingTimer);
});

describe('friction: tap counts', () => {
  it('a known task on the list is done with 1 tap', async () => {
    mocked.tasks.mockResolvedValue([
      { id: 3, name: 'Dishes', expectedMinutes: 15, tags: [], done: false, xpPaid: 0, viaTimer: false },
    ]);
    const u = tapCounter();
    render(<TasksScreen meta={meta} act={act} version={0} refresh={() => {}} onError={() => {}} onFocus={() => {}} />);
    await u.tap(await screen.findByLabelText('done: Dishes'));
    expect(u.taps).toBe(1);
    expect(mocked.tickTask).toHaveBeenCalledExactlyOnceWith(3);
  });

  it('a new task needs its name plus 1 chip', async () => {
    const u = tapCounter();
    render(<QuickAdd meta={meta} onAdded={() => {}} onError={() => {}} />);
    await u.type(screen.getByLabelText('task name'), 'Read paper');
    await u.tap(screen.getByRole('button', { name: '30m' }));
    expect(u.taps).toBe(1);
    expect(mocked.createTask).toHaveBeenCalledExactlyOnceWith('Read paper', 30, undefined);
  });

  it('a new task can also be just its name plus Enter (preselected chip)', async () => {
    const u = tapCounter();
    render(<QuickAdd meta={meta} onAdded={() => {}} onError={() => {}} />);
    await u.type(screen.getByLabelText('task name'), 'Call bank{Enter}');
    expect(u.taps).toBe(0);
    expect(mocked.createTask).toHaveBeenCalledExactlyOnceWith('Call bank', 15, undefined);
  });

  it('a known task is put back on the list with 1 tap, and that pays nothing', async () => {
    mocked.suggestions.mockResolvedValue([{ name: 'Gym', expectedMinutes: 60, tags: ['STRENGTH'], timesUsed: 4 }]);
    const u = tapCounter();
    render(<TasksScreen meta={meta} act={act} version={0} refresh={() => {}} onError={() => {}} onFocus={() => {}} />);
    await u.tap(await screen.findByRole('button', { name: /Gym/ }));
    expect(u.taps).toBe(1);
    expect(mocked.createTask).toHaveBeenCalledExactlyOnceWith('Gym');
    expect(mocked.tickTask).not.toHaveBeenCalled();
  });

  it('validating a timer needs 1 tap', async () => {
    const onAnswer = vi.fn();
    const u = tapCounter();
    render(<TimerQuestion timer={askingTimer} onAnswer={onAnswer} />);
    await u.tap(screen.getByRole('button', { name: 'Yes' }));
    expect(u.taps).toBe(1);
    expect(onAnswer).toHaveBeenCalledExactlyOnceWith(true);
  });

  it('starting a timer needs 1 tap', async () => {
    const u = tapCounter();
    render(<TimerScreen meta={meta} timer={null} taskId={null} clearTask={() => {}} onTimer={() => {}} onError={() => {}} />);
    await u.tap(screen.getByRole('button', { name: '45m' }));
    expect(u.taps).toBe(1);
    expect(mocked.startTimer).toHaveBeenCalledExactlyOnceWith(45, undefined, undefined, undefined);
  });

  it('a habit is ticked with 1 tap', async () => {
    mocked.habits.mockResolvedValue([
      { id: 5, name: 'Stretch', days: ['MONDAY'], tags: [], scheduledToday: true, doneToday: false },
    ]);
    const u = tapCounter();
    render(<HabitsScreen meta={meta} act={act} version={0} refresh={() => {}} onError={() => {}} />);
    await u.tap(await screen.findByLabelText('yes: Stretch'));
    expect(u.taps).toBe(1);
    expect(mocked.habitDone).toHaveBeenCalledExactlyOnceWith(5);
  });

  it('the mood check-in is 1 tap', async () => {
    const u = tapCounter();
    render(<CheckinRow act={act} version={0} />);
    await u.tap(await screen.findByLabelText('mood 4'));
    expect(u.taps).toBe(1);
    expect(mocked.checkin).toHaveBeenCalledExactlyOnceWith(4);
  });
});

describe('no payout without the act', () => {
  it('typing a name and choosing tags never ticks anything', async () => {
    const u = tapCounter();
    render(<QuickAdd meta={meta} onAdded={() => {}} onError={() => {}} />);
    await u.type(screen.getByLabelText('task name'), 'Essay');
    await u.tap(screen.getByRole('button', { name: 'INT' }));
    expect(mocked.tickTask).not.toHaveBeenCalled();
    expect(mocked.createTask).not.toHaveBeenCalled();
  });
});
