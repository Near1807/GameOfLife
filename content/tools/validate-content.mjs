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
  { folder: 'enemies', prefix: 'boss', w: 64, h: 64, strip: true, schema: 'boss' },
  { folder: 'hero', prefix: 'hero', w: 32, h: 32, strip: true, schema: 'hero' },
  { folder: 'regions', prefix: 'region', w: 320, h: 180, strip: false, schema: 'region' },
];

/** @returns {{ errors: string[], checked: number }} */
export function validateContent(root) {
  const errors = [];
  let checked = 0;
  const palette = loadPalette(root, errors);
  const schemas = {};
  for (const k of ['item', 'theme', 'boss', 'hero', 'region', 'achievement']) {
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
  return { errors, checked };
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
