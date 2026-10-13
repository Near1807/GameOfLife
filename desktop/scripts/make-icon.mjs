// The app icon is the hero sprite (desktop brief 1): the first frame of content/hero/hero_01.png,
// scaled up with hard pixel edges onto the dark app background, as build/icon.png (256x256) and a
// multi-size build/icon.ico. No dependencies: the PNG codec of the content tools is reused.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng, encodePng } from '../../content/tools/png.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const out = path.join(here, '..', 'build');
fs.mkdirSync(out, { recursive: true });

const sprite = decodePng(fs.readFileSync(path.join(root, 'content', 'hero', 'hero_01.png')));
const FRAME = 32;

/** size x size: a rounded dark square with the hero frame scaled by an integer, centred. */
function render(size) {
  const rgba = new Uint8Array(size * size * 4);
  const r = size * 0.18;
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const dx = Math.max(r - x, 0, x - (size - 1 - r));
      const dy = Math.max(r - y, 0, y - (size - 1 - r));
      if (dx * dx + dy * dy <= r * r) rgba.set([16, 18, 34, 255], (y * size + x) * 4);
    }
  }
  // an integer scale-up from 32 px; below 32 px a nearest-neighbour shrink
  const drawn = size >= FRAME ? FRAME * Math.floor((size * 0.9) / FRAME) || FRAME : size;
  const off = Math.floor((size - drawn) / 2);
  for (let y = 0; y < drawn; y++) {
    for (let x = 0; x < drawn; x++) {
      const sx = Math.floor((x * FRAME) / drawn);
      const sy = Math.floor((y * FRAME) / drawn);
      const i = (sy * sprite.width + sx) * 4;
      if (sprite.rgba[i + 3] === 0) continue;
      rgba.set(sprite.rgba.subarray(i, i + 4), ((off + y) * size + off + x) * 4);
    }
  }
  return encodePng(size, size, rgba);
}

fs.writeFileSync(path.join(out, 'icon.png'), render(256));

// ICO with PNG-compressed images (supported since Windows Vista)
const sizes = [16, 24, 32, 48, 64, 128, 256];
const pngs = sizes.map(render);
const header = Buffer.alloc(6 + 16 * sizes.length);
header.writeUInt16LE(0, 0);
header.writeUInt16LE(1, 2);
header.writeUInt16LE(sizes.length, 4);
let offset = header.length;
sizes.forEach((s, i) => {
  const e = 6 + 16 * i;
  header.writeUInt8(s === 256 ? 0 : s, e);
  header.writeUInt8(s === 256 ? 0 : s, e + 1);
  header.writeUInt16LE(1, e + 4);   // colour planes
  header.writeUInt16LE(32, e + 6);  // bits per pixel
  header.writeUInt32LE(pngs[i].length, e + 8);
  header.writeUInt32LE(offset, e + 12);
  offset += pngs[i].length;
});
fs.writeFileSync(path.join(out, 'icon.ico'), Buffer.concat([header, ...pngs.map((p) => Buffer.from(p))]));
console.log('wrote build/icon.png and build/icon.ico from content/hero/hero_01.png');
