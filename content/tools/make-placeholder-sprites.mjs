// Draws the two small UI icons of milestones 1-2 (flame, coin) from ASCII grids. The hero and the
// bosses are drawn by make-sprites.mjs.
// No dependencies: only Node's built-in zlib. Run from the repo root:
//   node content/tools/make-placeholder-sprites.mjs
//
// Contract (brief 12): hero 32x32, icons 16x16, palette colors only, no semi-transparency,
// one horizontal sprite sheet per animation (idle = 4 frames).
// Most sprites are symmetric, so each row is written as its LEFT half and mirrored.

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const palette = JSON.parse(fs.readFileSync(path.join(root, 'palette.json'), 'utf8')).colors;

// ---------------------------------------------------------------- tiny PNG encoder

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
/** pixels: array of rows, each row an array of "#rrggbb" or null (transparent). */
function encodePng(pixels) {
  const h = pixels.length;
  const w = pixels[0].length;
  const raw = Buffer.alloc(h * (1 + w * 4));
  pixels.forEach((row, y) => {
    raw[y * (1 + w * 4)] = 0; // filter: none
    row.forEach((hex, x) => {
      const o = y * (1 + w * 4) + 1 + x * 4;
      if (hex) {
        raw[o] = parseInt(hex.slice(1, 3), 16);
        raw[o + 1] = parseInt(hex.slice(3, 5), 16);
        raw[o + 2] = parseInt(hex.slice(5, 7), 16);
        raw[o + 3] = 255;
      } // else stays 0,0,0,0 = fully transparent
    });
  });
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------- grids

/** Mirrors left halves into full rows and maps characters to palette colors. */
function grid(leftHalves, colors, size) {
  return leftHalves.map((half, y) => {
    if (half.length !== size / 2) throw new Error(`row ${y} has ${half.length} chars, expected ${size / 2}`);
    const full = half + [...half].reverse().join('');
    return [...full].map((ch) => {
      if (ch === '.') return null;
      const idx = colors[ch];
      if (idx === undefined) throw new Error(`unknown pixel char '${ch}' in row ${y}`);
      return palette[idx];
    });
  });
}

// Streak flame icon, 16x16.
const FLAME_COLORS = { r: 26, o: 21, y: 22, w: 23 };
const FLAME = [
  '........',
  '........',
  '.......r',
  '......rr',
  '.....rro',
  '.....roo',
  '....rroo',
  '...rrooy',
  '...rooyy',
  '..rrooyw',
  '..rooyyw',
  '..rooyyw',
  '..rrooyy',
  '...rrooo',
  '....rrrr',
  '........',
];

// Coin icon (currency), 16x16.
const COIN_COLORS = { o: 20, y: 22, w: 23, l: 24 };
const COIN = [
  '........',
  '.....ooo',
  '....oyyy',
  '...oyyww',
  '..oyywll',
  '..oyywly',
  '..oyywly',
  '..oyywly',
  '..oyywly',
  '..oyywly',
  '..oyywly',
  '..oyywwy',
  '...oyyyy',
  '....oyyy',
  '.....ooo',
  '........',
];

// ---------------------------------------------------------------- write files

function write(rel, pixels) {
  const file = path.join(root, 'sprites', rel);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, encodePng(pixels));
  console.log('wrote', path.relative(process.cwd(), file), `${pixels[0].length}x${pixels.length}`);
}

// The hero is now drawn by make-sprites.mjs.

write('ui/icon_flame.png', grid(FLAME, FLAME_COLORS, 16));
write('ui/icon_coin.png', grid(COIN, COIN_COLORS, 16));
