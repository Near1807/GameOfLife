// Palette snap (brief 12): converts any PNG to the shared 32-colour palette, so a generated or
// drawn picture drops into the game.
//
//   node content/tools/palette-snap.mjs input.png output.png [--kind hero|item]
//
// Every pixel becomes the nearest palette colour (by perceived distance); pixels less than half
// opaque become fully transparent, the others fully opaque. It does NOT resize: it reports wrong
// dimensions so you can fix them in your image tool (scaling would blur pixel art).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from './png.mjs';

const SIZES = { hero: [32, 32], item: [32, 32] };

export function snapToPalette(img, paletteHex) {
  const pal = paletteHex.map((h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16)));
  const out = new Uint8Array(img.rgba.length);
  let changed = 0;
  for (let i = 0; i < img.rgba.length; i += 4) {
    if (img.rgba[i + 3] < 128) continue; // stays fully transparent (0,0,0,0)
    const [r, g, b] = [img.rgba[i], img.rgba[i + 1], img.rgba[i + 2]];
    let best = pal[0];
    let bestD = Infinity;
    for (const p of pal) {
      // weighted RGB distance: close to how the eye sees it, simple enough to read
      const dr = r - p[0], dg = g - p[1], db = b - p[2];
      const d = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
      if (d < bestD) { bestD = d; best = p; }
    }
    if (bestD > 0 || img.rgba[i + 3] !== 255) changed++;
    out[i] = best[0]; out[i + 1] = best[1]; out[i + 2] = best[2]; out[i + 3] = 255;
  }
  return { width: img.width, height: img.height, rgba: out, changed };
}

export function sizeProblem(kind, width, height) {
  const s = SIZES[kind];
  if (!s) return null;
  const ok = height === s[1] && width % s[0] === 0 && width > 0;
  return ok ? null : `size ${width}x${height}, a ${kind} must be ${s[0]}x${s[1]} (or a strip of such frames)`;
}

function guessKind(file) {
  const n = path.basename(file);
  if (n.startsWith('hero_') || n.startsWith('outfit_')) return 'hero';
  return 'item';
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const [input, output] = process.argv.slice(2).filter((a) => !a.startsWith('--'));
  const kindArg = process.argv.indexOf('--kind');
  if (!input || !output) {
    console.error('usage: node content/tools/palette-snap.mjs input.png output.png [--kind hero|item]');
    process.exit(2);
  }
  const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
  const palette = JSON.parse(fs.readFileSync(path.join(root, 'palette.json'), 'utf8')).colors;
  const img = decodePng(fs.readFileSync(input));
  const snapped = snapToPalette(img, palette);
  fs.writeFileSync(output, encodePng(snapped.width, snapped.height, snapped.rgba));
  console.log(`wrote ${output}: ${snapped.changed} pixel(s) adjusted to the palette`);
  const kind = kindArg > 0 ? process.argv[kindArg + 1] : guessKind(output);
  const problem = sizeProblem(kind, img.width, img.height);
  if (problem) {
    console.warn(`WARNING: wrong dimensions: ${problem}. Resize by a whole factor (nearest neighbour) in your image tool.`);
    process.exitCode = 1;
  }
}
