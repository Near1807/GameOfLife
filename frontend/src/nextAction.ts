import type { Character, HabitView, TaskView } from './api';

/** One suggestion for the home screen. It never pays by itself: its button is the explicit act. */
export type NextAction =
  | { kind: 'checkin'; title: string; why: string }
  | { kind: 'habit'; id: number; title: string; why: string }
  | { kind: 'task'; id: number; title: string; why: string }
  | { kind: 'focus'; title: string; why: string };

/**
 * "Next best action" (v2 section 4.3), in this order:
 * 1. the mood check-in, if not done (fastest XP of the day);
 * 2. a habit scheduled today and not done (keeps its streak);
 * 3. an open task, preferring one that trains the lowest stat, then the shortest;
 * 4. otherwise a focus block.
 */
export function nextAction(input: {
  checkedIn: boolean;
  habits: HabitView[];
  tasks: TaskView[];
  character: Character | null;
}): NextAction {
  if (!input.checkedIn) {
    return { kind: 'checkin', title: 'Check in your mood', why: 'One tap, and today starts counting.' };
  }
  const habit = input.habits.find((h) => h.scheduledToday && !h.doneToday);
  if (habit) {
    return {
      kind: 'habit',
      id: habit.id,
      title: habit.name,
      why: habit.streak > 0 ? `Keep its ${habit.streak}-day streak alive.` : 'Scheduled for today.',
    };
  }
  const open = input.tasks.filter((t) => !t.done);
  if (open.length > 0) {
    const lowest = input.character
      ? [...input.character.stats].sort((a, b) => a.xp - b.xp)[0]
      : undefined;
    const trainsLowest = lowest ? open.filter((t) => t.tags.includes(lowest.id)) : [];
    const pool = trainsLowest.length > 0 ? trainsLowest : open;
    const task = [...pool].sort((a, b) => a.expectedMinutes - b.expectedMinutes)[0];
    return {
      kind: 'task',
      id: task.id,
      title: task.name,
      why: trainsLowest.length > 0 && lowest ? `Trains ${lowest.name}, your lowest stat.` : `Quickest open quest: ${task.expectedMinutes} min.`,
    };
  }
  return { kind: 'focus', title: 'Start a focus block', why: 'Nothing open. 25 focused minutes is a solid hit.' };
}
