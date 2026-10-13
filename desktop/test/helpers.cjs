'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { prepareDataDir, resourcesFor } = require('../src/paths.cjs');

const repoRoot = path.resolve(__dirname, '..', '..');
const dev = resourcesFor({ packaged: false, repoRoot });

/** A fresh data folder in the system temp folder, prepared like the app does. */
function tempData(name) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), `gamelife-${name}-`));
  return prepareDataDir(dir, dev.defaults);
}

/** Non-loopback IPv4 addresses of this computer (to prove the server is not reachable on them). */
function lanAddresses() {
  return Object.values(os.networkInterfaces()).flat().filter((a) => a && a.family === 'IPv4' && !a.internal).map((a) => a.address);
}

module.exports = { repoRoot, dev, tempData, lanAddresses };
