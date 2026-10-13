'use strict';
// End-to-end test of the real Electron shell (desktop brief 1 and 9): window on the local server,
// single instance, clean exit with the exit backup, error window when the server cannot start.
// Needs a display (run under xvfb-run on Linux, or on a Windows desktop) and the Electron binary;
// skipped otherwise. Data goes to a temp folder (GAMELIFE_DATA_DIR), never to the real one.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const net = require('node:net');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { dev } = require('./helpers.cjs');

const appDir = path.resolve(__dirname, '..');
let electronPath = null;
try { electronPath = require('electron'); } catch { /* not installed */ }
const hasDisplay = process.platform === 'win32' || process.platform === 'darwin' || !!process.env.DISPLAY;
const skip = (!electronPath || !fs.existsSync(electronPath) || !hasDisplay || !fs.existsSync(dev.jar)) && 'needs electron, a display and the jar';
// root (CI containers) cannot use Chromium's sandbox
const extra = process.getuid && process.getuid() === 0 ? ['--no-sandbox'] : [];

/** The environment for the app: node --test marks its children (NODE_TEST_CONTEXT); Electron embeds Node and must not see that. */
function appEnv(env) {
  const out = { ...process.env, ...env };
  delete out.NODE_TEST_CONTEXT;
  delete out.NODE_OPTIONS;
  return out;
}

/** verbose: Chromium logs every URL its network layer sees, including its own background requests. */
function launch(env, verbose = false) {
  const { _electron } = require('playwright-core');
  const log = verbose ? ['--enable-logging=stderr', '--v=1'] : [];
  return _electron.launch({ executablePath: electronPath, args: [...extra, ...log, appDir], env: appEnv(env), timeout: 60000 });
}

function canConnect(port) {
  return new Promise((resolve) => {
    const s = net.connect({ host: '127.0.0.1', port, timeout: 1000 });
    s.on('connect', () => { s.destroy(); resolve(true); });
    s.on('error', () => resolve(false));
    s.on('timeout', () => { s.destroy(); resolve(false); });
  });
}

test('the shell opens the app on a local port, stays single, and stops the server on exit', { skip, timeout: Number(process.env.E2E_TIMEOUT || 240000) }, async (t) => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-e2e-'));
  const app = await launch({ GAMELIFE_DATA_DIR: dataDir }, true);
  t.after(() => app.close().catch(() => {}));   // even when an assertion fails, never leave the app running
  const network = [];
  app.process().stderr.on('data', (d) => {
    for (const m of String(d).matchAll(/NotifyBeforeURLRequest: (\S+)/g)) network.push(m[1]);
  });
  const win = await app.firstWindow();
  await win.waitForURL(/^http:\/\/127\.0\.0\.1:\d+\/$/, { timeout: 120000 });
  await win.waitForSelector('nav');
  const port = Number(new URL(win.url()).port);
  assert.strictEqual(await win.title(), 'GameLife');
  assert.ok(fs.existsSync(path.join(dataDir, 'gamelife.db')), 'database in the data folder');
  // the desktop bridge is there, with the data folder
  assert.strictEqual(await win.evaluate(() => window.gamelifeDesktop && window.gamelifeDesktop.dataFolder), dataDir);
  // a request to the internet is refused by the shell
  const blocked = await win.evaluate(() => fetch('https://example.com/').then((r) => `loaded ${r.status}`, () => 'blocked'));
  assert.strictEqual(blocked, 'blocked');

  // a second start does not open a second app: it focuses the first and exits
  await app.evaluate(({ app: a }) => { globalThis.secondStarts = 0; a.on('second-instance', () => { globalThis.secondStarts++; }); });
  const second = spawn(app.process().spawnfile, [...extra, appDir], { env: appEnv({ GAMELIFE_DATA_DIR: dataDir }), stdio: 'ignore' });
  const code = await new Promise((r) => second.on('exit', r));
  assert.strictEqual(code, 0);
  // the first app hears about it a moment later (on Windows often after the second has exited)
  let starts = 0;
  for (let i = 0; i < 50 && starts === 0; i++) {
    starts = await app.evaluate(() => globalThis.secondStarts);
    if (starts === 0) await new Promise((r) => setTimeout(r, 200));
  }
  assert.strictEqual(starts, 1);

  // quit: the server stops cleanly and writes its exit backup
  const closed = new Promise((r) => app.process().on('exit', r));
  await app.evaluate(({ app: a }) => a.quit());
  await closed;
  assert.strictEqual(await canConnect(port), false, 'server stopped');
  const backups = fs.readdirSync(path.join(dataDir, 'backups'));
  assert.ok(backups.some((f) => f.endsWith('-exit.json')), `exit backup: ${backups}`);

  // not one request left this computer, Chromium's own included (desktop brief 2)
  assert.ok(network.length > 5, `network log seen: ${network.length}`);
  const outside = network.filter((u) => /^https?:/.test(u) && !/^https?:\/\/(127\.0\.0\.1|localhost)[:/]/.test(u))
    .filter((u) => u !== 'https://example.com/'); // the test's own request above, which the shell cancelled
  assert.deepStrictEqual(outside, []);
});

test('when the server cannot start, a clear error window names the log', { skip, timeout: 120000 }, async (t) => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gamelife-e2e-err-'));
  const app = await launch({ GAMELIFE_DATA_DIR: dataDir, GAMELIFE_JAVA: path.join(dataDir, 'no-such-java') });
  t.after(() => app.close().catch(() => {}));
  const win = await app.firstWindow();
  await win.waitForURL(/error\.html/, { timeout: 60000 });
  assert.match(await win.textContent('h1'), /could not start/);
  assert.strictEqual(await win.textContent('#log'), path.join(dataDir, 'logs', 'server.log'));
  await app.close();
});
