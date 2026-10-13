// Where things live in the desktop app (desktop brief 3).
//
//   install folder (read-only, replaced on update): the app, the Java runtime, the server jar,
//                                                   the built frontend, the content, default config
//   data folder %APPDATA%\GameLife (never touched by an install, update or uninstall):
//       gamelife.db            the database (the ledger)
//       gamelife.json          your config (numbers you may edit)
//       gamelife.defaults.json the defaults your config was last merged with (see mergeConfig)
//       settings.json          backup folder choice
//       backups\               automatic backups (default folder)
//       logs\server.log        the server's output
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** %APPDATA%\GameLife on Windows. dev = a separate folder, so development never touches real data. */
function dataDirFor(appDataDir, dev = false) {
  return path.join(appDataDir, dev ? 'GameLife-dev' : 'GameLife');
}

/** True when child is parent itself or inside it (compares resolved, case-insensitive on Windows). */
function isInside(child, parent) {
  const norm = (p) => (process.platform === 'win32' ? path.resolve(p).toLowerCase() : path.resolve(p));
  const rel = path.relative(norm(parent), norm(child));
  return rel === '' || (!rel.startsWith('..') && !path.isAbsolute(rel));
}

/** The data must never be inside the install folder: an update or uninstall would delete it. */
function assertOutsideInstall(dataDir, installDir) {
  if (isInside(dataDir, installDir)) {
    throw new Error(`The data folder ${dataDir} is inside the install folder ${installDir}. Refusing to start.`);
  }
}

/**
 * Three-way merge of the config on every start, so an update can retune numbers without losing
 * your own edits:
 *   - a key you never changed (your value equals the previous default) takes the new default;
 *   - a key you changed keeps your value;
 *   - a new key is added; a key the new defaults removed is dropped;
 *   - "_comment" texts always come from the new defaults.
 */
function mergeConfig(newDefaults, user, oldDefaults) {
  const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
  if (!isObj(newDefaults)) {
    if (user === undefined) return newDefaults;
    const untouched = JSON.stringify(user) === JSON.stringify(oldDefaults);
    return untouched ? newDefaults : user;
  }
  const out = {};
  for (const [k, def] of Object.entries(newDefaults)) {
    if (k === '_comment') {
      out[k] = def;
    } else if (isObj(def)) {
      out[k] = mergeConfig(def, isObj(user?.[k]) ? user[k] : undefined, isObj(oldDefaults?.[k]) ? oldDefaults[k] : undefined);
    } else {
      out[k] = mergeConfig(def, user?.[k], oldDefaults?.[k]);
    }
  }
  return out;
}

/**
 * Creates the data folder and writes the merged config. Never writes outside dataDir.
 * Returns the file paths the server is started with.
 */
function prepareDataDir(dataDir, defaultsFile) {
  fs.mkdirSync(path.join(dataDir, 'logs'), { recursive: true });
  const configFile = path.join(dataDir, 'gamelife.json');
  const oldDefaultsFile = path.join(dataDir, 'gamelife.defaults.json');
  const defaults = JSON.parse(fs.readFileSync(defaultsFile, 'utf8'));
  let config = defaults;
  if (fs.existsSync(configFile)) {
    const user = JSON.parse(fs.readFileSync(configFile, 'utf8'));
    const old = fs.existsSync(oldDefaultsFile) ? JSON.parse(fs.readFileSync(oldDefaultsFile, 'utf8')) : user;
    config = mergeConfig(defaults, user, old);
  }
  writeIfChanged(configFile, JSON.stringify(config, null, 2) + '\n');
  writeIfChanged(oldDefaultsFile, JSON.stringify(defaults, null, 2) + '\n');
  return {
    dataDir,
    configFile,
    dbFile: path.join(dataDir, 'gamelife.db'),
    logFile: path.join(dataDir, 'logs', 'server.log'),
  };
}

function writeIfChanged(file, text) {
  if (!fs.existsSync(file) || fs.readFileSync(file, 'utf8') !== text) fs.writeFileSync(file, text);
}

/**
 * The files the app ships with. Packaged: in the install folder's resources. Development
 * (npm start in desktop/): straight from the repository, with the java on the PATH.
 */
function resourcesFor({ packaged, resourcesPath, repoRoot, platform = process.platform }) {
  if (packaged) {
    return {
      java: path.join(resourcesPath, 'jre', 'bin', platform === 'win32' ? 'java.exe' : 'java'),
      jar: path.join(resourcesPath, 'server', 'gamelife-backend.jar'),
      frontend: path.join(resourcesPath, 'frontend'),
      content: path.join(resourcesPath, 'content'),
      defaults: path.join(resourcesPath, 'defaults', 'gamelife.json'),
      installDir: path.dirname(resourcesPath),
    };
  }
  const target = path.join(repoRoot, 'backend', 'target');
  const jar = fs.existsSync(target) ? fs.readdirSync(target).find((f) => /^gamelife-backend-.*\.jar$/.test(f)) : null;
  return {
    java: process.env.GAMELIFE_JAVA || 'java',
    jar: jar ? path.join(target, jar) : path.join(target, 'gamelife-backend.jar'),
    frontend: path.join(repoRoot, 'frontend', 'dist'),
    content: path.join(repoRoot, 'content'),
    defaults: path.join(repoRoot, 'config', 'gamelife.json'),
    installDir: path.join(repoRoot, 'desktop'),
  };
}

module.exports = { dataDirFor, isInside, assertOutsideInstall, mergeConfig, prepareDataDir, resourcesFor };
