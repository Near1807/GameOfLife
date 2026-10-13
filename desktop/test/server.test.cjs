'use strict';
// Desktop brief 1 and 9: the shell starts the server on a free 127.0.0.1 port, nothing else can
// reach it, and it stops cleanly (with its exit backup). Needs the backend jar (npm run build at
// the repository root) and a java on the PATH, or GAMELIFE_JAVA.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const net = require('node:net');
const path = require('node:path');
const { findFreePort, startServer, waitUntilReady, stopServer } = require('../src/server.cjs');
const { dev, tempData, lanAddresses } = require('./helpers.cjs');

const haveJar = fs.existsSync(dev.jar);

function canConnect(host, port) {
  return new Promise((resolve) => {
    const s = net.connect({ host, port, timeout: 1500 });
    s.on('connect', () => { s.destroy(); resolve(true); });
    s.on('error', () => resolve(false));
    s.on('timeout', () => { s.destroy(); resolve(false); });
  });
}

test('starts, answers on 127.0.0.1 only, and stops cleanly with an exit backup', { skip: !haveJar && 'no backend jar', timeout: 180000 }, async () => {
  const paths = tempData('server');
  const port = await findFreePort();
  const server = startServer({ java: dev.java, jar: dev.jar, port, paths, contentDir: dev.content, frontendDir: dev.frontend });
  await waitUntilReady(server);
  assert.strictEqual(server.url, `http://127.0.0.1:${port}`);

  // the API and the frontend are served
  const meta = await (await fetch(`${server.url}/api/meta`)).json();
  assert.ok(Array.isArray(meta.stats));
  assert.match(await (await fetch(`${server.url}/`)).text(), /<div id="root">/);

  // local only: no other address of this computer accepts the connection...
  for (const ip of lanAddresses()) assert.strictEqual(await canConnect(ip, port), false, `reachable on ${ip}`);
  // ...while a control listener on all interfaces is reachable there (the probe itself works)
  const control = net.createServer().listen(0, '0.0.0.0');
  await new Promise((r) => control.once('listening', r));
  for (const ip of lanAddresses()) assert.strictEqual(await canConnect(ip, control.address().port), true, `control on ${ip}`);
  control.close();
  assert.strictEqual(await canConnect('127.0.0.1', port), true);

  // the data went to the data folder
  assert.ok(fs.existsSync(paths.dbFile));

  // a wrong token cannot stop it
  const bad = await fetch(`${server.url}/api/system/shutdown`, { method: 'POST', headers: { 'X-GameLife-Token': 'nope' } });
  assert.strictEqual(bad.status, 403);

  const stopped = await stopServer(server);
  assert.deepStrictEqual(stopped, { graceful: true, code: 0 });
  assert.strictEqual(await canConnect('127.0.0.1', port), false);
  const backups = fs.readdirSync(path.join(paths.dataDir, 'backups'));
  assert.ok(backups.some((f) => /-exit\.json$/.test(f)), `exit backup written: ${backups}`);
});

test('a server that cannot start fails fast with the log path', { timeout: 60000 }, async () => {
  const paths = tempData('broken');
  const port = await findFreePort();
  const server = startServer({ java: dev.java, jar: path.join(paths.dataDir, 'missing.jar'), port, paths, contentDir: dev.content, frontendDir: dev.frontend });
  await assert.rejects(waitUntilReady(server, 30000), (e) => e.message.includes(paths.logFile));
  assert.ok(fs.readFileSync(paths.logFile, 'utf8').length > 0);
});

test('a missing java fails with a clear message too', { timeout: 30000 }, async () => {
  const paths = tempData('nojava');
  const server = startServer({ java: path.join(paths.dataDir, 'no-java'), jar: dev.jar, port: await findFreePort(), paths, contentDir: dev.content, frontendDir: dev.frontend });
  await assert.rejects(waitUntilReady(server, 20000), /stopped during start-up/);
  assert.match(fs.readFileSync(paths.logFile, 'utf8'), /cannot start java/);
});

test('if the shell dies, the server stops by itself (with its exit backup)', { skip: !haveJar && 'no backend jar', timeout: 120000 }, async () => {
  const paths = tempData('orphan');
  // a stand-in for the shell: a process we can kill
  const { spawn } = require('node:child_process');
  const shell = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
  const server = startServer({ java: dev.java, jar: dev.jar, port: await findFreePort(), paths, contentDir: dev.content,
    frontendDir: dev.frontend, parentPid: shell.pid });
  await waitUntilReady(server);
  shell.kill();
  const code = await Promise.race([server.exited, new Promise((r) => setTimeout(() => r('still running'), 30000))]);
  assert.strictEqual(code, 0);
  assert.ok(fs.readdirSync(path.join(paths.dataDir, 'backups')).some((f) => /-exit\.json$/.test(f)));
});
