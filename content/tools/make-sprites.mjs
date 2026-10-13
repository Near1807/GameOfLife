// Draws the placeholder pixel art of the game and writes it into the content folders, with the
// folder-driven names of brief 12b. No dependencies.
// Run from the repo root:  node content/tools/make-sprites.mjs   (or: npm run sprites)
//
//   content/hero/hero_NN.png        hero looks (32x32 frames, 4-frame idle strip)
//   content/items/NAME.png          shop item icons (32x32); their NAME.json is hand-written
//
// v4: the tower-defense map, towers and enemies are vector shapes drawn by the app, not sprites.
//
// Contract (brief 12): only the 32 palette colours plus full transparency, no anti-aliasing,
// one horizontal strip per animation (idle = 4 frames). Your own pictures replace these files:
// keep the names and sizes.
//
// Every sprite is a grid of palette INDICES (-1 = transparent), so an off-palette colour is
// impossible by construction. Shapes are drawn with a few small helpers, shaded with a top-left
// light, and finished with an automatic 1-pixel outline.

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const PALETTE = JSON.parse(fs.readFileSync(path.join(root, 'palette.json'), 'utf8')).colors;

// ---------------------------------------------------------------- PNG encoder

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
/** grid: rows of palette indices, -1 = transparent. */
function encodePng(grid) {
  const h = grid.length;
  const w = grid[0].length;
  const raw = Buffer.alloc(h * (1 + w * 4));
  grid.forEach((row, y) => {
    row.forEach((idx, x) => {
      if (idx < 0) return; // stays 0,0,0,0
      const hex = PALETTE[idx];
      const o = y * (1 + w * 4) + 1 + x * 4;
      raw[o] = parseInt(hex.slice(1, 3), 16);
      raw[o + 1] = parseInt(hex.slice(3, 5), 16);
      raw[o + 2] = parseInt(hex.slice(5, 7), 16);
      raw[o + 3] = 255;
    });
  });
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------------------------------------------------------------- canvas helpers

class Canvas {
  constructor(w, h) {
    this.w = w;
    this.h = h;
    this.px = Array.from({ length: h }, () => Array(w).fill(-1));
  }
  get(x, y) {
    return x >= 0 && y >= 0 && x < this.w && y < this.h ? this.px[y][x] : -1;
  }
  set(x, y, c) {
    x = Math.round(x);
    y = Math.round(y);
    if (x >= 0 && y >= 0 && x < this.w && y < this.h) this.px[y][x] = c;
  }
  /** Writes a string of pixel characters starting at (x, y). '.' leaves the pixel as is. */
  put(y, x, text, map) {
    [...text].forEach((ch, i) => {
      if (ch === '.') return;
      if (!(ch in map)) throw new Error(`unknown pixel char '${ch}'`);
      this.set(x + i, y, map[ch]);
    });
  }
  ellipse(cx, cy, rx, ry, c, onlyOver = false) {
    for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
      for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
        const dx = (x - cx) / rx;
        const dy = (y - cy) / ry;
        if (dx * dx + dy * dy <= 1 && (!onlyOver || this.get(x, y) >= 0)) this.set(x, y, c);
      }
    }
  }
  rect(x0, y0, x1, y1, c) {
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) this.set(x, y, c);
  }
  /** Filled polygon (even-odd rule on pixel centres). */
  poly(points, c, onlyOver = false) {
    const ys = points.map((p) => p[1]);
    for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++) {
      for (let x = 0; x < this.w; x++) {
        let inside = false;
        for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
          const [xi, yi] = points[i];
          const [xj, yj] = points[j];
          if (yi > y + 0.5 !== yj > y + 0.5 && x + 0.5 < ((xj - xi) * (y + 0.5 - yi)) / (yj - yi) + xi) inside = !inside;
        }
        if (inside && (!onlyOver || this.get(x, y) >= 0)) this.set(x, y, c);
      }
    }
  }
  /** Bresenham line, optional thickness 2 (adds the pixel to the right). */
  line(x0, y0, x1, y1, c, thick = 1) {
    x0 = Math.round(x0); y0 = Math.round(y0); x1 = Math.round(x1); y1 = Math.round(y1);
    const dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
    const dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      this.set(x0, y0, c);
      if (thick > 1) this.set(x0 + 1, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  }
  /** Copies the left half onto the right half, mirrored (symmetric creatures). */
  mirror() {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w / 2; x++) this.px[y][this.w - 1 - x] = this.px[y][x];
  }
  /** 1-pixel outline around everything drawn (only on transparent pixels). */
  outline(c) {
    const add = [];
    for (let y = 0; y < this.h; y++) {
      for (let x = 0; x < this.w; x++) {
        if (this.px[y][x] >= 0) continue;
        if ([[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => this.get(x + dx, y + dy) >= 0 && this.get(x + dx, y + dy) !== c)) add.push([x, y]);
      }
    }
    add.forEach(([x, y]) => (this.px[y][x] = c));
  }
  /** Replaces colour a by b everywhere. */
  swap(a, b) {
    this.px.forEach((row) => row.forEach((v, i) => { if (v === a) row[i] = b; }));
  }
  clone() {
    const c = new Canvas(this.w, this.h);
    c.px = this.px.map((r) => [...r]);
    return c;
  }
  /** Moves rows 0..untilY-1 down by dy pixels (negative = up), for the idle bob. Rows from untilY on stay. */
  shiftDown(dy, untilY = this.h) {
    const out = this.clone();
    for (let y = 0; y < untilY; y++) {
      const src = y - dy;
      for (let x = 0; x < this.w; x++) {
        out.px[y][x] = src >= 0 && src < untilY ? this.px[src][x] : -1;
      }
    }
    for (let y = untilY; y < this.h; y++) out.px[y] = [...this.px[y]];
    return out;
  }
}

function sheet(frames) {
  return frames[0].px.map((_, y) => frames.flatMap((f) => f.px[y]));
}

function write(rel, grid) {
  const file = path.join(root, rel);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, encodePng(grid));
  console.log('wrote', path.relative(process.cwd(), file), `${grid[0].length}x${grid.length}`);
}

// Palette indices by name (see content/palette.json).
const P = {
  black: 0, ink: 1, night: 2, slate: 3, steel: 4, iron: 5, silver: 6, mist: 7, white: 8,
  g0: 9, g1: 10, g2: 11, g3: 12, g4: 13,
  c0: 14, c1: 15, c2: 16, c3: 17, c4: 18,
  o0: 19, o1: 20, o2: 21, o3: 22, o4: 23,
  r0: 24, r1: 25, r2: 26,
  v0: 27, v1: 28, v2: 29,
  skinD: 30, skin: 31,
};

// ================================================================ HERO (32x32)
// A hooded hacker-mage: indigo robe with a glowing cyan visor, a cyan rune on the chest, a gold
// belt, and a wooden staff topped by a crystal orb. Light comes from the top left.

function heroBase() {
  const k = new Canvas(32, 32);
  const M = {
    o: P.ink, K: P.night, k: P.slate,
    D: P.v0, V: P.v1, L: P.v2,
    c: P.c1, C: P.c3, w: P.c4, x: P.c2,
    b: P.o0, B: P.o1, g: P.o2, G: P.o3,
    s: P.skinD, S: P.skin,
  };
  // staff: shaft first, so the hand can cover it
  for (let y = 7; y <= 29; y++) {
    k.set(26, y, P.o1);
    k.set(27, y, P.o0);
  }
  k.put(6, 25, 'gGg', M);              // gold cap under the orb
  k.put(1, 25, 'ooo', M);              // crystal orb
  k.put(2, 24, 'oCwCo', M);
  k.put(3, 24, 'oCwCo', M);
  k.put(4, 24, 'oxCxo', M);
  k.put(5, 25, 'ooo', M);

  // hood
  k.put(3, 13, 'ooooo', M);
  k.put(4, 11, 'ooVVLVVoo', M);
  k.put(5, 10, 'oVVLLVVVDo', M);
  k.put(6, 9, 'oVVLLVVVVDDo', M);
  k.put(7, 9, 'oVLLVVVVVVDDo', M);
  k.put(8, 9, 'oVLKKKKKKKVDo', M);
  k.put(9, 9, 'oVLKcCwCCcKDo', M);     // visor
  k.put(10, 9, 'oVLKkcxxckKDo', M);
  k.put(11, 9, 'oVVKKsSSsKKDo', M);    // lower face in shadow
  k.put(12, 9, 'oDVVKKssKKVDDo', M);
  // shoulders and robe
  k.put(13, 8, 'oDVVVLKKKLVVVDo', M);
  k.put(14, 7, 'oDVVVVLkkkLVVVVDo', M);
  k.put(15, 7, 'oDVVVVLkCkLVVVVDo', M);
  k.put(16, 7, 'oDVVVVLxCxLVVVVDo', M);
  k.put(17, 7, 'oDVVVVLkCkLVVVVDo', M);
  k.put(18, 6, 'oDDVVVVLkkkLVVVVDo', M);
  k.put(19, 6, 'oDVVVVVLkkkLVVVVDo', M);
  k.put(20, 6, 'oDgggggggGggggggDo', M); // belt with buckle
  k.put(21, 6, 'oDVVVVVLkkkLVVVVDo', M);
  k.put(22, 5, 'oDVVVVVVLkkkLVVVVVDo', M);
  k.put(23, 5, 'oDVVVVVVLkkkLVVVVVDo', M);
  k.put(24, 5, 'oDDVVVVVLkkkLVVVVDDo', M);
  k.put(25, 4, 'oDDVVVVVVLkkkLVVVVVDDo', M);
  k.put(26, 4, 'oDDDVVVVVLkkkLVVVVDDDo', M);
  k.put(27, 4, 'oDDDDVVVVLkkkLVVVDDDDo', M);
  k.put(28, 4, 'ooDDDDDDDDDDDDDDDDDDoo', M);
  k.put(29, 9, 'okkko', M);             // boots
  k.put(29, 17, 'okkko', M);
  k.put(30, 9, 'ooooo', M);
  k.put(30, 17, 'ooooo', M);
  // right arm reaching the staff
  k.put(14, 21, 'VVo', M);
  k.put(15, 21, 'VVVoo', M);
  k.put(16, 21, 'LVVVSS', M);
  k.put(17, 21, 'VVVDSS', M);
  k.put(18, 21, 'DDDoo', M);
  // left sleeve fold
  k.put(16, 6, 'oD', M);
  k.put(17, 6, 'oD', M);
  return k;
}

function heroFrames() {
  const base = heroBase();
  const shadow = (c) => c.put(31, 7, 'KKKKKKKKKKKKKKKKKK', { K: P.night });
  const f0 = base.clone();
  // f1: orb flares, sparkles appear
  const f1 = base.clone();
  f1.set(25, 3, P.c4); f1.set(27, 2, P.c4);
  f1.set(22, 1, P.c3); f1.set(30, 4, P.c3);
  // f2: body bobs down one pixel (the staff stays planted)
  const f2 = base.shiftDown(1, 31);
  for (let y = 7; y <= 29; y++) { f2.set(26, y, P.o1); f2.set(27, y, P.o0); }
  // f3: bob + visor scan line moves
  const f3 = f2.clone();
  f3.set(13, 10, P.c4); f3.set(16, 10, P.c3);
  f3.set(29, 3, P.c3); f3.set(23, 0, P.c3);
  [f0, f1, f2, f3].forEach(shadow);
  return [f0, f1, f2, f3];
}

// ================================================================ HERO LOOKS
// The look changes every character.heroEveryNLevels overall levels: same body, new colours.

function recolor(frames, map) {
  return frames.map((f) => {
    const c = f.clone();
    c.px = c.px.map((row) => row.map((v) => (v in map ? map[v] : v)));
    return c;
  });
}
const HEROES = [
  ['Initiate', {}],
  ['Netrunner', { [P.v0]: P.c0, [P.v1]: P.c1, [P.v2]: P.c2, [P.c1]: P.o1, [P.c2]: P.o2, [P.c3]: P.o3, [P.c4]: P.o4 }],
  ['Archmage', { [P.v0]: P.r0, [P.v1]: P.r1, [P.v2]: P.r2, [P.o2]: P.o3, [P.c1]: P.v1, [P.c2]: P.v2, [P.c3]: P.v2, [P.c4]: P.white }],
];
const OUTFITS = [
  ['outfit_neon_ronin', { [P.v0]: P.night, [P.v1]: P.slate, [P.v2]: P.steel, [P.o2]: P.r2, [P.o3]: P.r2, [P.c3]: P.r2, [P.c4]: P.o4 }],
  ['outfit_void_walker', { [P.v0]: P.black, [P.v1]: P.v0, [P.v2]: P.v1, [P.o2]: P.c2, [P.o3]: P.c3, [P.skin]: P.silver, [P.skinD]: P.iron }],
];

// ================================================================ ITEM ICONS (32x32)

function icon(draw) {
  const k = new Canvas(32, 32);
  draw(k);
  k.outline(P.ink);
  return k;
}
const ICONS = {
  skin_aurora: (k) => { k.rect(3, 3, 28, 28, P.night); k.rect(3, 3, 28, 12, P.g1); k.rect(3, 13, 28, 18, P.c1); for (let x = 3; x < 29; x += 2) k.set(x, 12, P.g4); k.rect(3, 22, 28, 28, P.g0); },
  skin_ember_night: (k) => { k.rect(3, 3, 28, 28, P.r0); k.ellipse(20, 12, 5, 5, P.o3); k.poly([[3, 28], [3, 18], [10, 13], [17, 20], [24, 14], [28, 19], [28, 28]], P.night); },
};

// ---------------------------------------------------------------- write

function writeJson(rel, data) {
  const file = path.join(root, rel);
  fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
}
const nn = (i) => String(i + 1).padStart(2, '0');

const heroBaseFrames = heroFrames();
HEROES.forEach(([name, map], i) => {
  write(`hero/hero_${nn(i)}.png`, sheet(recolor(heroBaseFrames, map)));
  writeJson(`hero/hero_${nn(i)}.json`, { name });
});
for (const [name, draw] of Object.entries(ICONS)) write(`items/${name}.png`, icon(draw).px);
for (const [name, map] of OUTFITS) write(`items/${name}.png`, sheet(recolor(heroBaseFrames, map)));
