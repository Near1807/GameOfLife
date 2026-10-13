'use strict';
// Desktop brief 1: the bundled minimal Java runtime (jlink) is enough to run the server. Builds the
// runtime into a temp folder with the module list of scripts/make-runtime.mjs and starts the real
// server with it. Skipped when no jlink is available.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { findFreePort, startServer, waitUntilReady, stopServer } = require('../src/server.cjs');
const { dev, tempData } = require('./helpers.cjs');

function hasJlink() {
  try {
    execFileSync(process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'jlink') : 'jlink', ['--version'], { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

test('the jlink runtime runs the server', { skip: (!hasJlink() || !fs.existsSync(dev.jar)) && 'no jlink or no jar', timeout: 300000 }, async () => {
  const { makeRuntime } = await import('../scripts/make-runtime.mjs');
  const jre = makeRuntime(path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-jre-')), 'jre'));
  const java = path.join(jre, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  const paths = tempData('runtime');
  const server = startServer({ java, jar: dev.jar, port: await findFreePort(), paths, contentDir: dev.content, frontendDir: dev.frontend });
  await waitUntilReady(server);
  // a real round trip: the content scanner (ImageIO), SQLite and the game simulation all run
  const content = await (await fetch(`${server.url}/api/content`)).json();
  assert.deepStrictEqual(content.warnings, []);
  assert.ok(content.heroes.length >= 1);
  const game = await (await fetch(`${server.url}/api/game`)).json();
  assert.strictEqual(game.wave, 1);
  assert.deepStrictEqual(await stopServer(server), { graceful: true, code: 0 });
  const size = du(jre);
  assert.ok(size < 120 * 1024 * 1024, `runtime is ${Math.round(size / 1048576)} MB`);
});

function du(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).reduce((a, e) => {
    const p = path.join(dir, e.name);
    return a + (e.isDirectory() ? du(p) : fs.statSync(p).size);
  }, 0);
}
