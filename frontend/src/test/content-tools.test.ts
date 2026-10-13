// @vitest-environment node
/**
 * Brief 12 and 14: the content validator rejects each kind of violation, and the palette-snap
 * tool fixes colours and flags wrong sizes. Each test builds a small content folder on disk.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { validateContent } from '../../../content/tools/validate-content.mjs';
import { decodePng, encodePng } from '../../../content/tools/png.mjs';
import { sizeProblem, snapToPalette } from '../../../content/tools/palette-snap.mjs';

const REAL = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../content');
const PALETTE: string[] = JSON.parse(fs.readFileSync(path.join(REAL, 'palette.json'), 'utf8')).colors;
let dir = '';

/** A PNG filled with one colour (hex) and alpha. */
function png(rel: string, w: number, h: number, color = PALETTE[12], alpha = 255) {
  const rgba = new Uint8Array(w * h * 4);
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(color.slice(i, i + 2), 16));
  for (let i = 0; i < w * h; i++) rgba.set([r, g, b, alpha], i * 4);
  const f = path.join(dir, rel);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, encodePng(w, h, rgba));
}
function json(rel: string, data: unknown) {
  const f = path.join(dir, rel);
  fs.mkdirSync(path.dirname(f), { recursive: true });
  fs.writeFileSync(f, typeof data === 'string' ? data : JSON.stringify(data));
}
const errors = () => validateContent(dir).errors.join('\n');
const item = (id: string, extra = {}) => ({ id, name: id, kind: 'charm', price: 100, ...extra });

beforeEach(() => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-content-'));
  fs.copyFileSync(path.join(REAL, 'palette.json'), path.join(dir, 'palette.json'));
  fs.cpSync(path.join(REAL, 'schemas'), path.join(dir, 'schemas'), { recursive: true });
  png('enemies/boss_01.png', 64, 64);
});
afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

describe('content validator (brief 12)', () => {
  it('accepts valid content, including the real content folder', () => {
    png('hero/hero_01.png', 128, 32);
    png('regions/region_01.png', 320, 180);
    png('items/charm_ok.png', 32, 32);
    json('items/charm_ok.json', item('charm_ok', { effect: 'shieldBonus', value: 0.05 }));
    expect(validateContent(dir).errors).toEqual([]);
    expect(validateContent(REAL).errors).toEqual([]);
  });

  it('rejects a wrong size', () => {
    png('enemies/boss_02.png', 60, 64);
    png('regions/region_01.png', 320, 200);
    expect(errors()).toContain('enemies/boss_02.png: wrong size 60x64');
    expect(errors()).toContain('regions/region_01.png: wrong size 320x200');
  });

  it('rejects an off-palette colour', () => {
    png('enemies/boss_02.png', 64, 64, '#123457');
    expect(errors()).toContain('boss_02.png: 1 off-palette colour(s), e.g. #123457');
  });

  it('rejects semi-transparent pixels', () => {
    png('enemies/boss_02.png', 64, 64, PALETTE[3], 128);
    expect(errors()).toContain('boss_02.png: 4096 semi-transparent pixel(s)');
  });

  it('rejects a bad name', () => {
    png('enemies/Boss_02.png', 64, 64);
    png('enemies/dragon.png', 64, 64);
    png('items/Big Sword.png', 32, 32);
    expect(errors()).toContain('enemies/Boss_02.png: bad name');
    expect(errors()).toContain('enemies/dragon.png: bad name');
    expect(errors()).toContain('items/Big Sword.png: bad name');
  });

  it('rejects a missing sprite', () => {
    json('items/charm_ghost.json', item('charm_ghost'));
    json('enemies/boss_07.json', { name: 'No picture' });
    expect(errors()).toContain('items/charm_ghost.json: missing sprite charm_ghost.png');
    expect(errors()).toContain('enemies/boss_07.json: sidecar without picture boss_07.png');
  });

  it('rejects duplicate numbers and duplicate ids', () => {
    png('enemies/boss_1.png', 64, 64);
    png('items/charm_a.png', 32, 32);
    json('items/charm_a.json', item('charm_same'));
    png('items/charm_b.png', 32, 32);
    json('items/charm_b.json', item('charm_same'));
    expect(errors()).toContain('duplicate number 1');
    expect(errors()).toContain('duplicate id "charm_same"');
  });

  it('rejects schema errors', () => {
    png('items/charm_bad.png', 32, 32);
    json('items/charm_bad.json', { id: 'charm_bad', name: 'Bad', kind: 'hat', price: -5 });
    png('items/charm_broken.png', 32, 32);
    json('items/charm_broken.json', '{ not json');
    json('themes/theme_x.json', { id: 'theme_x', name: 'X', kind: 'theme', price: 300 });
    const e = errors();
    expect(e).toContain('charm_bad.json: schema error: kind must be one of');
    expect(e).toContain('charm_bad.json: schema error: price must be >= 0');
    expect(e).toContain('charm_broken.json: invalid JSON');
    expect(e).toContain('theme_x.json: schema error: tokens is required');
  });

  it('rejects an unreadable PNG', () => {
    json('enemies/boss_02.png', 'not a picture');
    expect(errors()).toContain('boss_02.png: unreadable PNG');
  });
});

describe('palette snap', () => {
  it('maps every pixel to the palette and makes alpha all-or-nothing', () => {
    const rgba = new Uint8Array([250, 70, 100, 255, 10, 10, 10, 40, 130, 130, 170, 200]); // 3 pixels
    const snapped = snapToPalette({ width: 3, height: 1, rgba }, PALETTE);
    const out = decodePng(encodePng(3, 1, snapped.rgba));
    const hex = (i: number) => '#' + [0, 1, 2].map((k) => out.rgba[i * 4 + k].toString(16).padStart(2, '0')).join('');
    expect(PALETTE).toContain(hex(0));
    expect(out.rgba[7]).toBe(0);        // faint pixel became transparent
    expect(PALETTE).toContain(hex(2));
    expect(out.rgba[11]).toBe(255);     // mostly opaque became fully opaque
  });

  it('flags wrong dimensions', () => {
    expect(sizeProblem('boss', 64, 64)).toBeNull();
    expect(sizeProblem('boss', 256, 64)).toBeNull();
    expect(sizeProblem('boss', 100, 64)).toContain('must be 64x64');
    expect(sizeProblem('region', 640, 360)).toContain('must be 320x180');
  });
});
