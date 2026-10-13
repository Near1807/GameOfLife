// What this device has already shown, so the "while you were away" recap plays once (brief 11).
// Stored in the browser: it is display state, not game data.

const KEY = 'gamelife.seenAttempt';

/** The key of the last wave attempt this device has shown, or null on a first visit. */
export function loadSeenAttempt(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function saveSeenAttempt(key: string) {
  try {
    localStorage.setItem(KEY, key);
  } catch {
    // private mode: the recap may show again, harmless
  }
}
