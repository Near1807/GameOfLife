// Draws the placeholder pixel art of the game and writes it into the content folders, with the
// folder-driven names of brief 12b. No dependencies.
// Run from the repo root:  node content/tools/make-sprites.mjs   (or: npm run sprites)
//
//   content/hero/hero_NN.png        hero looks (32x32 frames, 4-frame idle strip)
//   content/enemies/boss_NN.png     bosses (64x64 frames, 4-frame idle strip) + boss_NN.json
//   content/regions/region_NN.png   arena backgrounds (320x180) + region_NN.json
//   content/items/NAME.png          shop item icons (32x32); their NAME.json is hand-written
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

// ================================================================ BOSSES (64x64)
// Three body designs, recoloured with palette ramps into ten bosses.
// ramp: { out, dark, mid, light, eye, accent }

function beetle(r) {
  const k = new Canvas(64, 64);
  // legs (left side, mirrored later)
  k.line(18, 36, 6, 30, r.dark, 2); k.line(6, 30, 3, 40, r.dark, 2);
  k.line(17, 42, 5, 44, r.dark, 2); k.line(5, 44, 4, 53, r.dark, 2);
  k.line(19, 48, 9, 55, r.dark, 2); k.line(9, 55, 10, 61, r.dark, 2);
  // antennae
  k.line(27, 14, 17, 4, r.dark, 1); k.line(17, 4, 12, 6, r.dark, 1);
  k.ellipse(12, 6, 1.6, 1.6, r.accent);
  // shell: dark base, mid body, light highlight (top-left light)
  k.ellipse(32, 40, 18, 17, r.dark);
  k.ellipse(31, 38, 16, 15, r.mid, true);
  k.ellipse(24, 32, 6, 5, r.light, true);
  // shell split and circuit lines
  k.line(32, 25, 32, 56, r.out);
  k.line(31, 25, 31, 56, r.dark);
  k.line(20, 40, 27, 40, r.accent); k.set(27, 41, r.accent); k.line(27, 42, 27, 46, r.accent); k.ellipse(27, 47, 1, 1, r.accent);
  k.line(17, 47, 22, 47, r.accent); k.ellipse(22, 48, 0.8, 0.8, r.accent);
  // head
  k.ellipse(32, 21, 12, 8, r.dark);
  k.ellipse(31, 20, 10, 6, r.mid, true);
  // eyes and mandibles
  k.ellipse(26, 21, 3, 2.4, r.eye);
  k.set(25, 20, P.white); k.set(26, 20, P.white);
  k.poly([[25, 27], [29, 27], [28, 32], [26, 31]], r.light);
  k.set(27, 31, r.out);
  k.mirror();
  k.outline(r.out);
  return k;
}

function wraith(r) {
  const k = new Canvas(64, 64);
  // tattered robe
  k.poly([[32, 10], [14, 26], [10, 52], [14, 58], [18, 52], [22, 60], [27, 53], [32, 61], [32, 10]], r.dark);
  k.poly([[32, 12], [17, 27], [14, 50], [18, 50], [22, 56], [27, 50], [32, 56]], r.mid, true);
  k.poly([[30, 14], [20, 26], [18, 40], [22, 30]], r.light, true);
  // claws reaching out
  k.line(16, 34, 6, 40, r.dark, 2);
  k.line(6, 40, 3, 37, r.light); k.line(6, 40, 3, 42, r.light); k.line(7, 41, 5, 45, r.light);
  // hood opening: void face with glowing eyes
  k.ellipse(32, 25, 9, 10, P.black);
  k.ellipse(32, 25, 9, 10, r.out, true);
  k.ellipse(32, 26, 7, 8, P.black, true);
  k.poly([[25, 24], [30, 25], [29, 27], [25, 26]], r.eye);
  k.set(27, 25, P.white);
  // floating orbs
  k.ellipse(8, 18, 2, 2, r.accent); k.set(7, 17, P.white);
  k.ellipse(12, 10, 1.2, 1.2, r.accent);
  // chest rune
  k.line(32, 40, 28, 44, r.accent); k.line(28, 44, 32, 48, r.accent);
  k.mirror();
  k.outline(r.out);
  return k;
}

function core(r) {
  const k = new Canvas(64, 64);
  // spikes around the ring (left half; mirrored)
  for (const a of [-160, -125, -90, 160, 125]) {
    const rad = (a * Math.PI) / 180;
    const cx = 32 + Math.cos(rad) * 22;
    const cy = 30 + Math.sin(rad) * 22;
    const px = 32 + Math.cos(rad) * 29;
    const py = 30 + Math.sin(rad) * 29;
    const nx = -Math.sin(rad) * 4;
    const ny = Math.cos(rad) * 4;
    k.poly([[cx + nx, cy + ny], [px, py], [cx - nx, cy - ny]], r.dark);
  }
  // hanging cables
  k.line(24, 48, 22, 58, r.dark, 2); k.line(22, 58, 25, 62, r.dark);
  k.line(29, 50, 29, 60, r.dark);
  k.ellipse(25, 62, 1, 1, r.accent);
  // outer ring, plating, iris, pupil
  k.ellipse(32, 30, 23, 23, r.dark);
  k.ellipse(31, 29, 21, 21, r.mid, true);
  k.ellipse(24, 21, 9, 7, r.light, true);
  k.ellipse(32, 30, 15, 15, r.out);
  k.ellipse(32, 30, 13.5, 13.5, r.eye);
  k.ellipse(30, 28, 9, 9, r.accent, true);
  k.ellipse(32, 30, 5.5, 5.5, P.black);
  k.ellipse(28, 26, 2, 2, P.white);
  // ring bolts
  for (const [x, y] of [[13, 30], [18, 16], [18, 44]]) k.ellipse(x, y, 1.2, 1.2, r.light);
  k.mirror();
  // keep the eye highlight on the left only (asymmetric reflection)
  k.ellipse(36, 26, 2, 2, r.accent, true);
  k.outline(r.out);
  return k;
}

const RAMPS = {
  green: { out: P.ink, dark: P.g0, mid: P.g1, light: P.g2, eye: P.r2, accent: P.c3 },
  violet: { out: P.ink, dark: P.v0, mid: P.v1, light: P.v2, eye: P.g3, accent: P.c3 },
  cyan: { out: P.ink, dark: P.c0, mid: P.c1, light: P.c2, eye: P.o3, accent: P.c4 },
  red: { out: P.ink, dark: P.r0, mid: P.r1, light: P.r2, eye: P.o3, accent: P.c3 },
  ember: { out: P.ink, dark: P.o0, mid: P.o1, light: P.o2, eye: P.c3, accent: P.o4 },
  ghost: { out: P.ink, dark: P.steel, mid: P.iron, light: P.silver, eye: P.r2, accent: P.c3 },
  steel: { out: P.ink, dark: P.slate, mid: P.steel, light: P.iron, eye: P.r2, accent: P.r2 },
  gold: { out: P.ink, dark: P.o0, mid: P.o1, light: P.o3, eye: P.r2, accent: P.o4 },
  teal: { out: P.ink, dark: P.c0, mid: P.g1, light: P.g2, eye: P.v2, accent: P.g4 },
  entropy: { out: P.black, dark: P.r0, mid: P.v0, light: P.v1, eye: P.o3, accent: P.r2 },
};

/** Idle: 4 frames at 6 fps. Bob up and down, the eyes and accents pulse. */
function bossFrames(draw, ramp) {
  const base = draw(ramp);
  const pulse = base.clone();
  pulse.swap(ramp.accent, P.white);
  const up = base.shiftDown(-1);
  const upPulse = pulse.shiftDown(-1);
  return [base, upPulse, up, pulse];
}

// The ten bosses, in ladder order. name and title go to the sidecar boss_NN.json.
const BOSSES = [
  [beetle, RAMPS.green, 'Bug Queen', 'Mother of all regressions'],
  [wraith, RAMPS.violet, 'Doomscroll Wraith', 'It feeds on lost evenings'],
  [core, RAMPS.cyan, 'Null Pointer', 'Stares into nothing, and nothing stares back'],
  [beetle, RAMPS.red, 'Merge Conflict', 'Two truths, one file'],
  [wraith, RAMPS.ember, 'Burnout', 'Burns bright, leaves ash'],
  [core, RAMPS.red, 'Kernel Panic', 'The core that halts everything'],
  [beetle, RAMPS.teal, 'Infinite Loop', 'Same day, again and again'],
  [wraith, RAMPS.ghost, 'Distraction Phantom', 'Just one more notification'],
  [core, RAMPS.gold, 'Overclock', 'Faster, hotter, until it breaks'],
  [wraith, RAMPS.entropy, 'Entropy', 'The last boss. Everything drifts toward it'],
];

// ================================================================ HERO LOOKS
// The look changes every fight.heroEveryNLevels overall levels: same body, new colours.

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

// ================================================================ REGIONS (320x180)

/** Small deterministic random generator, so the backgrounds are the same on every run. */
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

/** Sky in horizontal bands with a checkerboard dither row between bands (no blending). */
function sky(k, bands, height) {
  const step = height / bands.length;
  for (let y = 0; y < height; y++) {
    const i = Math.min(bands.length - 1, Math.floor(y / step));
    const edge = (y % step) > step - 3 && i + 1 < bands.length;
    for (let x = 0; x < k.w; x++) k.set(x, y, edge && (x + y) % 2 === 0 ? bands[i + 1] : bands[i]);
  }
}

function region(def) {
  const k = new Canvas(320, 180);
  const r = rng(def.seed);
  sky(k, def.sky, 150);
  for (let i = 0; i < def.stars; i++) k.set(Math.floor(r() * 320), Math.floor(r() * 90), r() < 0.2 ? P.white : def.star);
  if (def.moon) {
    k.ellipse(def.moon[0], def.moon[1], def.moon[2], def.moon[2], def.moon[3]);
    k.ellipse(def.moon[0] - 3, def.moon[1] - 3, def.moon[2] * 0.6, def.moon[2] * 0.6, def.moon[4], true);
  }
  def.far(k, r);
  // ground: a lit edge, then darker layers and a few floor lines
  k.rect(0, 148, 319, 179, def.ground[0]);
  k.rect(0, 148, 319, 149, def.ground[1]);
  for (let y = 156; y < 180; y += 7) for (let x = 0; x < 320; x++) if ((x + y) % 4 === 0) k.set(x, y, def.ground[2]);
  return k;
}

const REGIONS = [
  { name: 'Neon City', seed: 7, sky: [P.black, P.night, P.v0, P.v0, P.v1], stars: 90, star: P.c3,
    moon: [262, 34, 14, P.mist, P.white], ground: [P.night, P.c2, P.c0],
    far: (k, r) => {
      for (let x = 0; x < 320;) {
        const w = 14 + Math.floor(r() * 22), h = 30 + Math.floor(r() * 70);
        k.rect(x, 148 - h, x + w - 2, 147, r() < 0.5 ? P.slate : P.night);
        for (let wy = 148 - h + 4; wy < 144; wy += 6) for (let wx = x + 3; wx < x + w - 4; wx += 4) if (r() < 0.35) k.set(wx, wy, r() < 0.5 ? P.c3 : P.v2);
        x += w;
      }
    } },
  { name: 'Data Forest', seed: 11, sky: [P.night, P.c0, P.c0, P.g0, P.g1], stars: 40, star: P.g4,
    moon: [60, 30, 10, P.g4, P.white], ground: [P.g0, P.g2, P.g1],
    far: (k, r) => {
      for (let i = 0; i < 26; i++) {
        const x = Math.floor(r() * 330) - 5, h = 40 + Math.floor(r() * 60), w = 10 + Math.floor(r() * 10);
        k.poly([[x, 148 - h], [x - w, 148], [x + w, 148]], r() < 0.5 ? P.g0 : P.c0);
        k.line(x, 148 - h + 6, x, 148, P.night);
      }
      for (let i = 0; i < 40; i++) k.set(Math.floor(r() * 320), 90 + Math.floor(r() * 55), P.g4); // fireflies
    } },
  { name: 'Frozen Server Farm', seed: 23, sky: [P.night, P.c0, P.c1, P.c1, P.c2], stars: 70, star: P.white,
    moon: null, ground: [P.steel, P.mist, P.iron],
    far: (k, r) => {
      for (let x = 6; x < 320; x += 34) {
        const h = 60 + Math.floor(r() * 30);
        k.rect(x, 148 - h, x + 24, 147, P.slate);
        k.rect(x + 1, 148 - h + 1, x + 23, 146, P.steel);
        for (let y = 148 - h + 5; y < 144; y += 5) {
          k.line(x + 3, y, x + 21, y, P.slate);
          k.set(x + 19, y - 2, r() < 0.5 ? P.g3 : P.c3);
          if (r() < 0.3) k.set(x + 16, y - 2, P.r2);
        }
      }
      for (let i = 0; i < 120; i++) k.set(Math.floor(r() * 320), Math.floor(r() * 148), P.white); // snow
    } },
  { name: 'Ember Wastes', seed: 31, sky: [P.night, P.r0, P.r0, P.r1, P.o1], stars: 20, star: P.o3,
    moon: [240, 70, 24, P.o3, P.o4], ground: [P.o0, P.o2, P.r0],
    far: (k, r) => {
      let y = 110;
      const pts = [[0, 148]];
      for (let x = 0; x <= 320; x += 16) { y = Math.max(70, Math.min(135, y + Math.floor(r() * 40) - 20)); pts.push([x, y]); }
      pts.push([320, 148]);
      k.poly(pts, P.r0);
      for (let i = 0; i < 60; i++) k.set(Math.floor(r() * 320), Math.floor(r() * 140), r() < 0.5 ? P.o3 : P.o2); // embers
    } },
];

// ================================================================ ITEM ICONS (32x32)

function icon(draw) {
  const k = new Canvas(32, 32);
  draw(k);
  k.outline(P.ink);
  return k;
}
const ICONS = {
  weapon_plasma_staff: (k) => { k.line(6, 28, 22, 10, P.o1, 2); k.ellipse(24, 8, 5, 5, P.r2); k.ellipse(23, 7, 2, 2, P.o4); },
  weapon_arc_blade: (k) => { k.poly([[8, 22], [25, 4], [28, 7], [11, 25]], P.c3); k.line(10, 22, 26, 6, P.c4); k.rect(5, 23, 10, 25, P.o2); k.line(4, 28, 8, 24, P.o1, 2); },
  armor_circuit_vest: (k) => { k.poly([[8, 6], [13, 6], [16, 10], [19, 6], [24, 6], [27, 12], [25, 28], [7, 28], [5, 12]], P.v1); k.line(16, 11, 16, 27, P.c3); k.line(10, 16, 14, 16, P.c3); k.line(18, 20, 22, 20, P.c3); },
  armor_aegis_plate: (k) => { k.poly([[6, 5], [26, 5], [26, 16], [16, 28], [6, 16]], P.o2); k.poly([[8, 7], [24, 7], [24, 15], [16, 25], [8, 15]], P.silver); k.line(16, 8, 16, 23, P.mist); k.line(9, 13, 23, 13, P.mist); },
  charm_ice_crystal: (k) => { k.poly([[16, 3], [25, 16], [16, 29], [7, 16]], P.c2); k.poly([[16, 6], [20, 16], [16, 26]], P.c4); },
  charm_phoenix_feather: (k) => { k.ellipse(17, 14, 6, 11, P.r2); k.ellipse(17, 12, 3, 8, P.o3, true); k.line(10, 29, 17, 16, P.o1, 2); },
  charm_moon_amulet: (k) => { k.line(8, 3, 16, 12, P.o2); k.line(24, 3, 16, 12, P.o2); k.ellipse(16, 20, 8, 8, P.mist); k.ellipse(19, 18, 7, 7, -1, true); },
  charm_aegis_rune: (k) => { k.poly([[16, 3], [27, 9], [27, 23], [16, 29], [5, 23], [5, 9]], P.v1); k.line(16, 8, 16, 24, P.c3); k.line(11, 12, 21, 20, P.c3); k.line(21, 12, 11, 20, P.c3); },
  charm_focus_lens: (k) => { k.ellipse(14, 14, 10, 10, P.o1); k.ellipse(14, 14, 8, 8, P.c2); k.ellipse(11, 11, 3, 3, P.c4); k.line(21, 21, 28, 28, P.o1, 2); },
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
BOSSES.forEach(([draw, ramp, name, title], i) => {
  write(`enemies/boss_${nn(i)}.png`, sheet(bossFrames(draw, ramp)));
  writeJson(`enemies/boss_${nn(i)}.json`, { name, title });
});
REGIONS.forEach((def, i) => {
  write(`regions/region_${nn(i)}.png`, region(def).px);
  writeJson(`regions/region_${nn(i)}.json`, { name: def.name });
});
for (const [name, draw] of Object.entries(ICONS)) write(`items/${name}.png`, icon(draw).px);
for (const [name, map] of OUTFITS) write(`items/${name}.png`, sheet(recolor(heroBaseFrames, map)));
