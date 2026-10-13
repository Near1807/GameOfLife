'use strict';
// Desktop brief 2: the app works with the network off. Opens every screen of the real app in a
// browser and records every request: all of them must go to 127.0.0.1. Uses Chromium from
// PLAYWRIGHT_CHROMIUM (or /opt/pw-browsers), else Microsoft Edge (present on Windows); skipped when
// neither exists.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const { findFreePort, startServer, waitUntilReady, stopServer } = require('../src/server.cjs');
const { dev, tempData } = require('./helpers.cjs');

function browserOptions() {
  const candidates = [process.env.PLAYWRIGHT_CHROMIUM, '/opt/pw-browsers/chromium'].filter(Boolean);
  const exe = candidates.find((p) => fs.existsSync(p));
  if (exe) return { executablePath: exe };
  if (process.platform === 'win32') return { channel: 'msedge' };
  return null;
}

test('no request leaves 127.0.0.1', { skip: (!browserOptions() || !fs.existsSync(dev.jar)) && 'no browser or no jar', timeout: 180000 }, async () => {
  const { chromium } = require('playwright-core');
  const paths = tempData('offline');
  const server = startServer({ java: dev.java, jar: dev.jar, port: await findFreePort(), paths, contentDir: dev.content, frontendDir: dev.frontend });
  await waitUntilReady(server);
  const browser = await chromium.launch(browserOptions());
  const requests = [];
  try {
    const page = await browser.newPage();
    page.on('request', (r) => requests.push(r.url()));
    // anything that is not local fails at once, like with the network off
    await page.route((url) => !['127.0.0.1', 'localhost'].includes(url.hostname) && url.protocol.startsWith('http'),
      (route) => route.abort('internetdisconnected'));
    await page.goto(server.url + '/');
    await page.waitForSelector('nav');
    for (const label of ['Quests', 'Focus', 'Habits', 'Review', 'Hero', 'Home']) {
      await page.click(`nav >> text=${label}`);
      await page.waitForTimeout(600);
    }
    await page.click('[aria-label="settings"]');
    await page.waitForTimeout(600);
    await page.click('[aria-label="coins, open the shop"]');
    await page.waitForTimeout(600);
    // fonts were loaded from the server itself
    const fonts = await page.evaluate(() => [...document.fonts].filter((f) => f.status === 'loaded').map((f) => f.family));
    assert.ok(fonts.some((f) => f.includes('Inter')), `fonts: ${fonts}`);
  } finally {
    await browser.close();
    await stopServer(server);
  }
  const http = requests.filter((u) => u.startsWith('http'));
  assert.ok(http.length > 10, `only ${http.length} requests`);
  const outside = http.filter((u) => new URL(u).hostname !== '127.0.0.1');
  assert.deepStrictEqual(outside, []);
});
