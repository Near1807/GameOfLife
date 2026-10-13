'use strict';
// Desktop brief 3 and 9: the data folder is outside the install folder; the config merge keeps
// your edits and takes new defaults.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { dataDirFor, isInside, assertOutsideInstall, mergeConfig, prepareDataDir, resourcesFor } = require('../src/paths.cjs');

test('the data folder is %APPDATA%\\GameLife, outside the per-user install folder', () => {
  // the real Windows layout: install in %LOCALAPPDATA%\Programs\GameLife, data in %APPDATA%\GameLife
  const appData = path.join('C:', 'Users', 'thomas', 'AppData', 'Roaming');
  const install = path.join('C:', 'Users', 'thomas', 'AppData', 'Local', 'Programs', 'GameLife');
  const res = resourcesFor({ packaged: true, resourcesPath: path.join(install, 'resources'), repoRoot: '/', platform: 'win32' });
  assert.strictEqual(res.installDir, install);
  const data = dataDirFor(appData);
  assert.strictEqual(data, path.join(appData, 'GameLife'));
  assert.strictEqual(isInside(data, install), false);
  assert.doesNotThrow(() => assertOutsideInstall(data, install));
  assert.throws(() => assertOutsideInstall(path.join(install, 'data'), install), /inside the install folder/);
  assert.notStrictEqual(dataDirFor(appData, true), data); // development never uses the real data
});

test('preparing the data folder writes only inside it, and keeps the database location there', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-paths-'));
  const defaults = path.resolve(__dirname, '..', '..', 'config', 'gamelife.json');
  const before = fs.readFileSync(defaults, 'utf8');
  const p = prepareDataDir(dir, defaults);
  for (const f of [p.configFile, p.dbFile, p.logFile]) assert.ok(isInside(f, dir), f);
  assert.ok(fs.existsSync(p.configFile));
  assert.strictEqual(fs.readFileSync(defaults, 'utf8'), before); // the shipped defaults are never written
});

test('an update takes new defaults but keeps the values you changed', () => {
  const oldDefaults = { _comment: 'old', game: { spotCostGrowth: 1.8, enemyHpGrowth: 1.1, _comment: 'x' }, home: { dailyXpGoal: 100 } };
  const user = { _comment: 'old', game: { spotCostGrowth: 1.8, enemyHpGrowth: 1.2 }, home: { dailyXpGoal: 150 }, extra: 1 };
  const newDefaults = { _comment: 'new', game: { spotCostGrowth: 1.3, enemyHpGrowth: 1.1, maxSlow: 0.9, _comment: 'y' }, home: { dailyXpGoal: 100 } };
  const merged = mergeConfig(newDefaults, user, oldDefaults);
  assert.deepStrictEqual(merged, {
    _comment: 'new',
    game: { spotCostGrowth: 1.3, enemyHpGrowth: 1.2, maxSlow: 0.9, _comment: 'y' }, // untouched → new; edited → kept; new key added
    home: { dailyXpGoal: 150 },
  });
});

test('a second start with the same defaults changes nothing in your config', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-paths-'));
  const defaults = path.resolve(__dirname, '..', '..', 'config', 'gamelife.json');
  const p = prepareDataDir(dir, defaults);
  const cfg = JSON.parse(fs.readFileSync(p.configFile, 'utf8'));
  cfg.home.dailyXpGoal = 222;
  fs.writeFileSync(p.configFile, JSON.stringify(cfg));
  prepareDataDir(dir, defaults);
  assert.strictEqual(JSON.parse(fs.readFileSync(p.configFile, 'utf8')).home.dailyXpGoal, 222);
});
