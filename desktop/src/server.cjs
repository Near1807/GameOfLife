// Starts and stops the Spring Boot server as a child process (desktop brief 1). Plain Node, no
// Electron, so it can be tested on its own (test/server.test.cjs).
'use strict';

const fs = require('node:fs');
const net = require('node:net');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');

const HOST = '127.0.0.1';

/** A free TCP port on 127.0.0.1 (the OS picks one; the tiny race with another program is retried by the caller). */
function findFreePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.on('error', reject);
    srv.listen(0, HOST, () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
  });
}

/**
 * Starts the server. Its output goes to logFile. Bound to 127.0.0.1 only: nothing on the network
 * can reach it. The shutdown token goes through the environment, not the command line, so other
 * programs listing processes cannot read it.
 *
 * Returns { child, url, token, logFile, exited } where exited is a promise of the exit code.
 */
function startServer({ java, jar, port, paths, contentDir, frontendDir, parentPid = process.pid, extraArgs = [], env = {} }) {
  const token = crypto.randomBytes(24).toString('hex');
  const fwd = (p) => p.replace(/\\/g, '/'); // Spring reads file: URLs with forward slashes, also on Windows
  const args = [
    '-Xss2m',
    '-jar', jar,
    `--server.port=${port}`,
    `--server.address=${HOST}`,
    `--gamelife.db-path=${paths.dbFile}`,
    `--gamelife.data-dir=${paths.dataDir}`,
    `--gamelife.config-path=${paths.configFile}`,
    `--gamelife.content-dir=${contentDir}`,
    `--gamelife.frontend-dist=${fwd(frontendDir)}`,
    // if the shell ever dies without stopping the server (crash, killed), the server stops itself
    `--gamelife.parent-pid=${parentPid}`,
    ...extraArgs,
  ];
  const log = fs.openSync(paths.logFile, 'a');
  fs.writeSync(log, `\n==== ${new Date().toISOString()} starting: ${java} ${args.join(' ')}\n`);
  const child = spawn(java, args, {
    stdio: ['ignore', log, log],
    windowsHide: true,
    env: { ...process.env, ...env, GAMELIFE_SHUTDOWNTOKEN: token },
  });
  const exited = new Promise((resolve) => {
    child.on('exit', (code, signal) => resolve(code ?? signal));
    child.on('error', (err) => {
      fs.writeSync(log, `cannot start java: ${err.message}\n`);
      resolve(`error: ${err.message}`);
    });
  });
  exited.then(() => fs.closeSync(log)).catch(() => {});
  return { child, url: `http://${HOST}:${port}`, token, logFile: paths.logFile, exited };
}

/**
 * Waits until the server answers, or fails with a message that names the log file. Fails early
 * when the process exits.
 */
async function waitUntilReady(server, timeoutMs = 90000) {
  let gone = null;
  server.exited.then((code) => { gone = code; });
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    if (gone !== null) {
      throw new Error(`The GameLife server stopped during start-up (exit ${gone}). Details are in the log: ${server.logFile}`);
    }
    try {
      const r = await fetch(`${server.url}/api/meta`);
      if (r.ok) return;
    } catch {
      // not listening yet
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(`The GameLife server did not start within ${Math.round(timeoutMs / 1000)} s. Details are in the log: ${server.logFile}`);
}

/**
 * Stops the server cleanly: asks it to shut down (it writes the exit backup and closes the
 * database), waits, and only kills it if it does not stop in time. Returns { graceful, code }.
 */
async function stopServer(server, timeoutMs = 20000) {
  let done = false;
  server.exited.then(() => { done = true; });
  if (done || server.child.exitCode !== null) return { graceful: true, code: server.child.exitCode };
  try {
    await fetch(`${server.url}/api/system/shutdown`, { method: 'POST', headers: { 'X-GameLife-Token': server.token } });
  } catch {
    // already gone or not answering: the timeout below decides
  }
  const code = await Promise.race([server.exited, new Promise((r) => setTimeout(() => r(undefined), timeoutMs))]);
  if (code !== undefined) return { graceful: true, code };
  server.child.kill();
  const killed = await Promise.race([server.exited, new Promise((r) => setTimeout(() => r('timeout'), 5000))]);
  return { graceful: false, code: killed };
}

module.exports = { HOST, findFreePort, startServer, waitUntilReady, stopServer };
