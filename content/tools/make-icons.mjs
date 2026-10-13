// Draws the app icons (PWA install, favicon) from the first hero picture: the sprite scaled by a
// whole number on the dark background. Run: node content/tools/make-icons.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from './png.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const hero = decodePng(fs.readFileSync(path.join(here, '../hero/hero_01.png')));
const out = path.join(here, '../../frontend/public/icons');
fs.mkdirSync(out, { recursive: true });

for (const size of [192, 512]) {
  const scale = Math.floor((size * 0.8) / 32);
  const rgba = new Uint8Array(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      // dark background with a soft violet glow in the middle
      const d = Math.hypot(x - size / 2, y - size * 0.55) / (size * 0.6);
      const glow = Math.max(0, 1 - d);
      rgba.set([Math.round(6 + 60 * glow), Math.round(7 + 30 * glow), Math.round(12 + 110 * glow), 255], (y * size + x) * 4);
    }
  }
  const ox = Math.floor((size - 32 * scale) / 2);
  const oy = Math.floor((size - 32 * scale) / 2);
  for (let y = 0; y < 32; y++) {
    for (let x = 0; x < 32; x++) {
      const i = (y * hero.width + x) * 4; // first frame only
      if (hero.rgba[i + 3] === 0) continue;
      for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) {
        rgba.set(hero.rgba.subarray(i, i + 4), ((oy + y * scale + dy) * size + ox + x * scale + dx) * 4);
      }
    }
  }
  fs.writeFileSync(path.join(out, `icon-${size}.png`), encodePng(size, size, rgba));
  console.log(`wrote frontend/public/icons/icon-${size}.png`);
}
