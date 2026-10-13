// Content validator (brief 12): runs before every frontend build ("prebuild") and on demand:
//   node content/tools/validate-content.mjs [contentDir]
// Rejects (exit code 1): wrong size, off-palette colour, semi-transparent pixel, bad file name,
// missing sprite, duplicate number or id, JSON schema error, unreadable PNG.
// No dependencies. The JSON schemas are in content/schemas; a small checker below understands the
// parts of JSON Schema they use (type, required, properties, items, enum, minimum, maximum, pattern).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { decodePng } from './png.mjs';

const ORDERED = [
  { folder: 'hero', prefix: 'hero', w: 32, h: 32, strip: true, schema: 'hero' },
];

/** The tower numbers a tier may change (backend TowerStats). */
const TOWER_STATS = new Set(['dmg', 'rate', 'range', 'rad', 'slow', 'slowT', 'burn', 'pierce', 'multi', 'chain', 'pois',
  'pslow', 'rbuff', 'dbuff', 'coin', 'disc', 'phys', 'air', 'spread']);
const STAT_IDS = new Set(['STRENGTH', 'CREATIVITY', 'INTELLECT', 'DISCIPLINE', 'SOCIAL', 'VITALITY', 'SPIRIT']);

/** @returns {{ errors: string[], checked: number }} */
export function validateContent(root) {
  const errors = [];
  let checked = 0;
  const palette = loadPalette(root, errors);
  const schemas = {};
  for (const k of ['item', 'theme', 'hero', 'achievement', 'tower', 'waves', 'enemy', 'map']) {
    const f = path.join(root, 'schemas', `${k}.schema.json`);
    if (fs.existsSync(f)) schemas[k] = JSON.parse(fs.readFileSync(f, 'utf8'));
    else errors.push(`schemas/${k}.schema.json: missing schema`);
  }

  // ---------------------------------------------------------- ordered pictures
  for (const o of ORDERED) {
    const dir = path.join(root, o.folder);
    const numbers = new Map();
    for (const fn of list(dir)) {
      const rel = `${o.folder}/${fn}`;
      if (fn.endsWith('.json')) {
        const png = fn.replace(/\.json$/, '.png');
        if (!fs.existsSync(path.join(dir, png))) errors.push(`${rel}: sidecar without picture ${png}`);
        checkJson(path.join(dir, fn), rel, schemas[o.schema], errors);
        continue;
      }
      const m = fn.match(new RegExp(`^${o.prefix}_(\\d+)\\.png$`));
      if (!m) { errors.push(`${rel}: bad name, expected ${o.prefix}_NN.png (lowercase, PNG)`); continue; }
      const n = Number(m[1]);
      if (numbers.has(n)) errors.push(`${rel}: duplicate number ${n} (also ${numbers.get(n)})`);
      numbers.set(n, fn);
      checked++;
      checkPicture(path.join(dir, fn), rel, { w: o.w, h: o.h, strip: o.strip }, palette, errors);
    }
  }

  // ---------------------------------------------------------- shop items
  const ids = new Map();
  const itemsDir = path.join(root, 'items');
  for (const fn of list(itemsDir)) {
    const rel = `items/${fn}`;
    const base = fn.replace(/\.[^.]+$/, '');
    if (!/^[a-z0-9_]+\.(png|json)$/.test(fn)) { errors.push(`${rel}: bad name, expected lowercase NAME.png + NAME.json`); continue; }
    if (fn.endsWith('.png')) {
      if (!fs.existsSync(path.join(itemsDir, base + '.json'))) errors.push(`${rel}: shop picture without ${base}.json`);
      checked++;
      checkPicture(path.join(itemsDir, fn), rel, { w: 32, h: 32, strip: true }, palette, errors);
    } else {
      if (!fs.existsSync(path.join(itemsDir, base + '.png'))) errors.push(`${rel}: missing sprite ${base}.png`);
      const data = checkJson(path.join(itemsDir, fn), rel, schemas.item, errors);
      if (data?.id) addId(ids, data.id, rel, errors);
    }
  }

  // ---------------------------------------------------------- themes
  const themesDir = path.join(root, 'themes');
  for (const fn of list(themesDir)) {
    const rel = `themes/${fn}`;
    if (fn.endsWith('.png')) continue; // optional preview
    if (!/^[a-z0-9_]+\.json$/.test(fn)) { errors.push(`${rel}: bad name, expected lowercase NAME.json`); continue; }
    const data = checkJson(path.join(themesDir, fn), rel, schemas.theme, errors);
    if (data?.id) addId(ids, data.id, rel, errors);
  }

  // ---------------------------------------------------------- achievements
  const ach = path.join(root, 'achievements.json');
  if (fs.existsSync(ach)) {
    const data = checkJson(ach, 'achievements.json', schemas.achievement, errors);
    const seen = new Map();
    for (const a of data?.achievements ?? []) addId(seen, a.id, 'achievements.json', errors);
  }
  checkGame(root, schemas, errors);
  return { errors, checked };
}

// ---------------------------------------------------------- tower defense data (v4)

function checkGame(root, schemas, errors) {
  const towers = checkJson(path.join(root, 'towers.json'), 'towers.json', schemas.tower, errors);
  const ids = new Map();
  for (const t of towers?.towers ?? []) {
    const rel = `towers.json (${t.id})`;
    addId(ids, t.id, 'towers.json', errors);
    for (const k of Object.keys(t.base ?? {})) if (!TOWER_STATS.has(k)) errors.push(`${rel}: unknown base stat "${k}"`);
    if (t.requires && (!STAT_IDS.has(t.requires.stat) || !(t.requires.level >= 1))) {
      errors.push(`${rel}: requires needs a stat id (${[...STAT_IDS].join(', ')}) and a level >= 1`);
    }
    if ((t.paths ?? []).length !== 2) errors.push(`${rel}: needs exactly 2 upgrade paths`);
    for (const p of t.paths ?? []) {
      if ((p.tiers ?? []).length !== 3) errors.push(`${rel}: path ${p.name} needs exactly 3 tiers`);
      for (const tier of p.tiers ?? []) {
        for (const [k, op] of Object.entries(tier.set ?? {})) {
          if (!TOWER_STATS.has(k)) errors.push(`${rel}: path ${p.name}: unknown stat "${k}"`);
          const ops = Object.keys(op ?? {});
          if (ops.length !== 1 || !['mul', 'add', 'set'].includes(ops[0])) {
            errors.push(`${rel}: path ${p.name}: "${k}" needs exactly one of mul, add, set`);
          }
        }
      }
    }
  }
  const waves = checkJson(path.join(root, 'waves.json'), 'waves.json', schemas.waves, errors);
  for (const [id, e] of Object.entries(waves?.enemies ?? {})) {
    for (const problem of checkSchema(e, schemas.enemy ?? {}, id)) errors.push(`waves.json: schema error: ${problem}`);
  }
  for (const g of waves?.groups ?? []) {
    if (waves?.enemies && !(g.type in waves.enemies)) errors.push(`waves.json: group of unknown enemy "${g.type}"`);
  }
  const mapsDir = path.join(root, 'maps');
  const maps = list(mapsDir);
  if (maps.length === 0) errors.push('maps/: no map_NN.json found');
  for (const fn of maps) {
    const rel = `maps/${fn}`;
    if (!/^map_\d+\.json$/.test(fn)) { errors.push(`${rel}: bad name, expected map_NN.json`); continue; }
    const m = checkJson(path.join(mapsDir, fn), rel, schemas.map, errors);
    if (!m) continue;
    if ((m.path ?? []).length < 2) errors.push(`${rel}: the path needs at least 2 points`);
    if (m.freeSpots > (m.spots ?? []).length) errors.push(`${rel}: more free spots than spots`);
    (m.spots ?? []).forEach(([x, y], i) => {
      if (!(x >= 0 && x <= m.width && y >= 0 && y <= m.height)) errors.push(`${rel}: spot ${i} is outside the map`);
    });
  }
}

// ---------------------------------------------------------- pictures

function loadPalette(root, errors) {
  try {
    const colors = JSON.parse(fs.readFileSync(path.join(root, 'palette.json'), 'utf8')).colors;
    return new Set(colors.map((c) => c.toLowerCase()));
  } catch {
    errors.push('palette.json: missing or invalid');
    return new Set();
  }
}

const hex = (r, g, b) => '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('');

function checkPicture(file, rel, size, palette, errors) {
  let img;
  try {
    img = decodePng(fs.readFileSync(file));
  } catch (e) {
    errors.push(`${rel}: unreadable PNG (${e.message})`);
    return;
  }
  const okSize = img.height === size.h && (size.strip ? img.width % size.w === 0 && img.width > 0 : img.width === size.w);
  if (!okSize) {
    errors.push(`${rel}: wrong size ${img.width}x${img.height}, expected ${size.w}x${size.h}${size.strip ? ' or a strip of such frames' : ''}`);
  }
  const off = new Set();
  let semi = 0;
  for (let i = 0; i < img.rgba.length; i += 4) {
    const a = img.rgba[i + 3];
    if (a === 0) continue;
    if (a !== 255) { semi++; continue; }
    const c = hex(img.rgba[i], img.rgba[i + 1], img.rgba[i + 2]);
    if (!palette.has(c)) off.add(c);
  }
  if (semi > 0) errors.push(`${rel}: ${semi} semi-transparent pixel(s); only fully opaque or fully transparent`);
  if (off.size > 0) {
    errors.push(`${rel}: ${off.size} off-palette colour(s), e.g. ${[...off].slice(0, 3).join(', ')} (run palette-snap)`);
  }
}

// ---------------------------------------------------------- JSON and schemas

function checkJson(file, rel, schema, errors) {
  let data;
  try {
    data = JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    errors.push(`${rel}: invalid JSON (${e.message})`);
    return null;
  }
  if (schema) for (const problem of checkSchema(data, schema, '')) errors.push(`${rel}: schema error: ${problem}`);
  return data;
}

/** The subset of JSON Schema used by content/schemas. Returns a list of problems. */
export function checkSchema(value, schema, where) {
  const out = [];
  const at = where || '(root)';
  const typeOf = (v) => (Array.isArray(v) ? 'array' : v === null ? 'null' : Number.isInteger(v) ? 'integer' : typeof v);
  if (schema.type) {
    const t = typeOf(value);
    const ok = schema.type === t || (schema.type === 'number' && t === 'integer');
    if (!ok) return [`${at} must be ${schema.type}, is ${t}`];
  }
  if (schema.enum && !schema.enum.includes(value)) out.push(`${at} must be one of ${schema.enum.join(', ')}`);
  if (typeof value === 'number') {
    if (schema.minimum !== undefined && value < schema.minimum) out.push(`${at} must be >= ${schema.minimum}`);
    if (schema.maximum !== undefined && value > schema.maximum) out.push(`${at} must be <= ${schema.maximum}`);
  }
  if (typeof value === 'string' && schema.pattern && !new RegExp(schema.pattern).test(value)) {
    out.push(`${at} must match ${schema.pattern}`);
  }
  if (schema.type === 'object') {
    for (const r of schema.required ?? []) if (!(r in value)) out.push(`${where ? where + '.' : ''}${r} is required`);
    for (const [k, sub] of Object.entries(schema.properties ?? {})) {
      if (k in value) out.push(...checkSchema(value[k], sub, where ? `${where}.${k}` : k));
    }
  }
  if (schema.type === 'array' && schema.items) {
    value.forEach((v, i) => out.push(...checkSchema(v, schema.items, `${at}[${i}]`)));
  }
  return out;
}

function addId(map, id, rel, errors) {
  if (map.has(id)) errors.push(`${rel}: duplicate id "${id}" (also in ${map.get(id)})`);
  else map.set(id, rel);
}

function list(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((f) => !f.startsWith('.') && f !== 'README.md' && fs.statSync(path.join(dir, f)).isFile()).sort();
}

// ---------------------------------------------------------- command line

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(process.argv[2] ?? path.join(path.dirname(fileURLToPath(import.meta.url)), '..'));
  const { errors, checked } = validateContent(root);
  if (errors.length > 0) {
    console.error(`Content check FAILED (${errors.length} problem${errors.length > 1 ? 's' : ''}):`);
    errors.forEach((e) => console.error('  - ' + e));
    process.exit(1);
  }
  console.log(`Content check passed: ${checked} pictures, palette and schemas OK.`);
}
