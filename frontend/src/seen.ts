// What this device has already shown, so the recap and the boss intro play once (brief 11).
// Stored in the browser: it is display state, not game data. attackId -1 means "never opened".

export interface Seen {
  attackId: number;
  fallId: number;
  fights: string[];
}

const KEY = 'gamelife.seen';

export function loadSeen(): Seen {
  try {
    const s = JSON.parse(localStorage.getItem(KEY) ?? 'null');
    if (s && typeof s.attackId === 'number') return { attackId: s.attackId, fallId: s.fallId ?? 0, fights: s.fights ?? [] };
  } catch {
    // ignore
  }
  return { attackId: -1, fallId: 0, fights: [] };
}

export function saveSeen(s: Seen) {
  try {
    localStorage.setItem(KEY, JSON.stringify(s));
  } catch {
    // private mode: the recap may show again, harmless
  }
}
