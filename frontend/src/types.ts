import type { ActionResult } from './api';

/** Runs an action that may pay XP, shows the feedback and refreshes the state. */
export type Act = (action: Promise<ActionResult>) => Promise<ActionResult | undefined>;

export type Screen = 'home' | 'tasks' | 'timer' | 'habits' | 'campaign' | 'hero' | 'shop' | 'settings';
