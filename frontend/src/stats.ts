// How each of the seven stats looks: icon, colour token and short label (v2 section 4.1).
// The list of stats itself comes from the backend config; an unknown stat falls back to a
// neutral style, so adding a stat to the config never breaks the interface.
import { Brain, Dumbbell, HeartPulse, Palette, Sparkles, Star, Target, Users, type LucideIcon } from 'lucide-react';

export interface StatStyle {
  Icon: LucideIcon;
  color: string; // a CSS value, usually a theme token
  short: string;
}

const STYLES: Record<string, StatStyle> = {
  STRENGTH: { Icon: Dumbbell, color: 'var(--stat-strength)', short: 'STR' },
  CREATIVITY: { Icon: Palette, color: 'var(--stat-creativity)', short: 'CRE' },
  INTELLECT: { Icon: Brain, color: 'var(--stat-intellect)', short: 'INT' },
  DISCIPLINE: { Icon: Target, color: 'var(--stat-discipline)', short: 'DIS' },
  SOCIAL: { Icon: Users, color: 'var(--stat-social)', short: 'SOC' },
  VITALITY: { Icon: HeartPulse, color: 'var(--stat-vitality)', short: 'VIT' },
  SPIRIT: { Icon: Sparkles, color: 'var(--stat-spirit)', short: 'SPI' },
};

export function statStyle(id: string): StatStyle {
  return STYLES[id] ?? { Icon: Star, color: 'var(--violet-soft)', short: id.slice(0, 3) };
}

/**
 * Equal split of the base XP over the tags, in cents, leftover cents to the first tags.
 * Same rule as the backend's StatSplitter (brief 8), used only for the preview.
 */
export function splitPreview(baseXp: number, tags: string[]): { id: string; xp: number }[] {
  if (tags.length === 0) return [];
  const cents = Math.round(baseXp * 100);
  const share = Math.floor(cents / tags.length);
  const left = cents - share * tags.length;
  return tags.map((id, i) => ({ id, xp: (share + (i < left ? 1 : 0)) / 100 }));
}

/** 15 -> "15", 7.5 -> "7.5" */
export const fmt = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(n * 10 === Math.round(n * 10) ? 1 : 2));
