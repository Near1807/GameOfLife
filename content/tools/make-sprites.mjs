// Draws the game's pixel art and writes it as PNG sprite sheets. No dependencies.
// Run from the repo root:  node content/tools/make-sprites.mjs   (or: npm run sprites)
//
// Contract (brief 12): hero 32x32, bosses 64x64, only the 32 palette colours plus full
// transparency, no anti-aliasing, one horizontal sheet per animation (idle = 4 frames).
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
  const file = path.join(root, 'sprites', rel);
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

// The ten bosses: must match content/bosses.json (sprite file names).
const BOSSES = [
  ['enemy_bug_queen', beetle, RAMPS.green],
  ['enemy_doomscroll_wraith', wraith, RAMPS.violet],
  ['enemy_null_pointer', core, RAMPS.cyan],
  ['enemy_merge_conflict', beetle, RAMPS.red],
  ['enemy_burnout', wraith, RAMPS.ember],
  ['enemy_kernel_panic', core, RAMPS.red],
  ['enemy_infinite_loop', beetle, RAMPS.teal],
  ['enemy_distraction_phantom', wraith, RAMPS.ghost],
  ['enemy_overclock', core, RAMPS.gold],
  ['enemy_entropy', wraith, RAMPS.entropy],
];

// ---------------------------------------------------------------- write

write('hero/hero_hacker_idle.png', sheet(heroFrames()));
for (const [name, draw, ramp] of BOSSES) {
  write(`enemies/${name}_idle.png`, sheet(bossFrames(draw, ramp)));
}
