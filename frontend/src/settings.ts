// Per-device display settings (brief 3.1: reduced-effects switch and sound switch).
// Stored in the browser only: they are preferences of this screen, not game data.

export interface Settings {
  reducedEffects: boolean;
  sound: boolean;
}

const KEY = 'gamelife.settings';
const DEFAULTS: Settings = { reducedEffects: false, sound: false }; // sound off by default (brief 11)

export function loadSettings(): Settings {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}');
    return { reducedEffects: !!(saved.reducedEffects ?? DEFAULTS.reducedEffects), sound: !!(saved.sound ?? DEFAULTS.sound) };
  } catch {
    return DEFAULTS;
  }
}

export function saveSettings(s: Settings) {
  try {
    localStorage.setItem(KEY, JSON.stringify(s));
  } catch {
    // private mode: settings simply do not persist
  }
}

/** A short soft chime made with the Web Audio API (no sound files needed yet). */
export function chime(kind: 'gain' | 'alarm') {
  try {
    const ctx = new AudioContext();
    const notes = kind === 'gain' ? [784, 1046.5, 1318.5] : [880, 1174.7, 880, 1174.7];
    notes.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      const t = ctx.currentTime + i * 0.09;
      gain.gain.setValueAtTime(0, t);
      gain.gain.linearRampToValueAtTime(0.08, t + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.35);
      osc.connect(gain).connect(ctx.destination);
      osc.start(t);
      osc.stop(t + 0.4);
    });
    setTimeout(() => ctx.close(), 1500);
  } catch {
    // no audio available
  }
}
