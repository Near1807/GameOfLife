// Per-device display settings (brief 3.1: reduced-effects switch and sound switch).
// Stored in the browser only: they are preferences of this screen, not game data.

export interface Settings {
  reducedEffects: boolean;
  sound: boolean;
  scanlines: boolean;
}

const KEY = 'gamelife.settings';
const DEFAULTS: Settings = { reducedEffects: false, sound: true, scanlines: true };

export function loadSettings(): Settings {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') };
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

/** A short 8-bit style blip made with the Web Audio API (no sound files needed yet). */
export function blip(kind: 'gain' | 'alarm') {
  try {
    const ctx = new AudioContext();
    const notes = kind === 'gain' ? [660, 880, 1320] : [880, 660, 880, 660];
    notes.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'square';
      osc.frequency.value = freq;
      gain.gain.value = 0.05;
      osc.connect(gain).connect(ctx.destination);
      const t = ctx.currentTime + i * 0.08;
      osc.start(t);
      osc.stop(t + 0.07);
    });
    setTimeout(() => ctx.close(), 1000);
  } catch {
    // no audio available
  }
}
