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
const item = (id: string, extra = {}) => ({ id, name: id, kind: 'skin', price: 100, ...extra });
const copyGame = () => {
  for (const f of ['towers.json', 'waves.json']) fs.copyFileSync(path.join(REAL, f), path.join(dir, f));
  fs.cpSync(path.join(REAL, 'maps'), path.join(dir, 'maps'), { recursive: true });
};

beforeEach(() => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-content-'));
  fs.copyFileSync(path.join(REAL, 'palette.json'), path.join(dir, 'palette.json'));
  fs.cpSync(path.join(REAL, 'schemas'), path.join(dir, 'schemas'), { recursive: true });
  copyGame();
  png('hero/hero_01.png', 32, 32);
});
afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

describe('content validator (brief 12)', () => {
  it('accepts valid content, including the real content folder', () => {
    png('hero/hero_02.png', 128, 32);
    png('items/skin_ok.png', 32, 32);
    json('items/skin_ok.json', item('skin_ok', { tokens: { filter: 'hue-rotate(10deg)' } }));
    expect(validateContent(dir).errors).toEqual([]);
    expect(validateContent(REAL).errors).toEqual([]);
  });

  it('rejects a wrong size', () => {
    png('hero/hero_02.png', 30, 32);
    png('items/skin_big.png', 64, 64);
    json('items/skin_big.json', item('skin_big'));
    expect(errors()).toContain('hero/hero_02.png: wrong size 30x32');
    expect(errors()).toContain('items/skin_big.png: wrong size 64x64');
  });

  it('rejects an off-palette colour', () => {
    png('hero/hero_02.png', 32, 32, '#123457');
    expect(errors()).toContain('hero_02.png: 1 off-palette colour(s), e.g. #123457');
  });

  it('rejects semi-transparent pixels', () => {
    png('hero/hero_02.png', 32, 32, PALETTE[3], 128);
    expect(errors()).toContain('hero_02.png: 1024 semi-transparent pixel(s)');
  });

  it('rejects a bad name', () => {
    png('hero/Hero_02.png', 32, 32);
    png('hero/knight.png', 32, 32);
    png('items/Big Hat.png', 32, 32);
    expect(errors()).toContain('hero/Hero_02.png: bad name');
    expect(errors()).toContain('hero/knight.png: bad name');
    expect(errors()).toContain('items/Big Hat.png: bad name');
  });

  it('rejects a missing sprite', () => {
    json('items/skin_ghost.json', item('skin_ghost'));
    json('hero/hero_07.json', { name: 'No picture' });
    expect(errors()).toContain('items/skin_ghost.json: missing sprite skin_ghost.png');
    expect(errors()).toContain('hero/hero_07.json: sidecar without picture hero_07.png');
  });

  it('rejects duplicate numbers and duplicate ids', () => {
    png('hero/hero_1.png', 32, 32);
    png('items/skin_a.png', 32, 32);
    json('items/skin_a.json', item('skin_same'));
    png('items/skin_b.png', 32, 32);
    json('items/skin_b.json', item('skin_same'));
    expect(errors()).toContain('duplicate number 1');
    expect(errors()).toContain('duplicate id "skin_same"');
  });

  it('rejects schema errors, including the old power items', () => {
    png('items/skin_bad.png', 32, 32);
    json('items/skin_bad.json', { id: 'skin_bad', name: 'Bad', kind: 'hat', price: -5 });
    png('items/charm_old.png', 32, 32);
    json('items/charm_old.json', { id: 'charm_old', name: 'Old', kind: 'charm', price: 100 });
    png('items/skin_broken.png', 32, 32);
    json('items/skin_broken.json', '{ not json');
    json('themes/theme_x.json', { id: 'theme_x', name: 'X', kind: 'theme', price: 300 });
    const e = errors();
    expect(e).toContain('skin_bad.json: schema error: kind must be one of');
    expect(e).toContain('skin_bad.json: schema error: price must be >= 0');
    expect(e).toContain('charm_old.json: schema error: kind must be one of outfit, skin');
    expect(e).toContain('skin_broken.json: invalid JSON');
    expect(e).toContain('theme_x.json: schema error: tokens is required');
  });

  it('rejects an unreadable PNG', () => {
    json('hero/hero_02.png', 'not a picture');
    expect(errors()).toContain('hero_02.png: unreadable PNG');
  });
});

describe('content validator: tower-defense data (v4)', () => {
  const edit = (file: string, change: (d: any) => void) => { // eslint-disable-line @typescript-eslint/no-explicit-any
    const f = path.join(dir, file);
    const d = JSON.parse(fs.readFileSync(f, 'utf8'));
    change(d);
    fs.writeFileSync(f, JSON.stringify(d));
  };

  it('rejects a tower with a wrong path, tier, stat or requirement', () => {
    edit('towers.json', (d) => {
      d.towers[0].paths[0].tiers.splice(1);
      d.towers[1].paths.pop();
      d.towers[2].paths[0].tiers[0].set = { damage: { mul: 2 } };
      d.towers[3].requires = { stat: 'LUCK', level: 3 };
      d.towers[5].paths[1].tiers[3].requires = { stat: 'LUCK', level: 10 };
      d.towers.push({ ...d.towers[4] });
    });
    const e = errors();
    expect(e).toContain('towers.json (archer): path Sniper needs 3 or 4 tiers');
    expect(e).toContain('towers.json (cannon): needs exactly 2 upgrade paths');
    expect(e).toContain('towers.json (frost): path Deep freeze: unknown stat "damage"');
    expect(e).toContain('towers.json (mage): requires needs a stat id');
    expect(e).toContain('towers.json (venom): path Toxin: a tier\'s requires needs a stat id');
    expect(e).toContain('duplicate id "tesla"');
  });

  it('rejects a wave of an unknown enemy and a spot outside the map', () => {
    edit('waves.json', (d) => d.groups.push({ type: 'dragon', from: 1, base: 1, per: 0, gap: 1 }));
    edit('maps/map_01.json', (d) => d.spots.push([2000, 10]));
    const e = errors();
    expect(e).toContain('waves.json: group of unknown enemy "dragon"');
    expect(e).toContain('maps/map_01.json: spot 15 is outside the map');
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
    expect(sizeProblem('hero', 32, 32)).toBeNull();
    expect(sizeProblem('hero', 128, 32)).toBeNull();
    expect(sizeProblem('hero', 100, 32)).toContain('must be 32x32');
    expect(sizeProblem('item', 64, 64)).toContain('must be 32x32');
  });
});
