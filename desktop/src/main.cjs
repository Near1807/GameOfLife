// GameLife desktop shell (desktop brief 1). Opens the existing frontend in its own window and runs
// the Spring Boot server next to it, on 127.0.0.1 only, with the bundled Java runtime.
'use strict';

const path = require('node:path');
const { app, BrowserWindow, dialog, ipcMain, shell, session } = require('electron');
const { dataDirFor, assertOutsideInstall, prepareDataDir, resourcesFor } = require('./paths.cjs');
const { findFreePort, startServer, waitUntilReady, stopServer } = require('./server.cjs');

const ICON = path.join(__dirname, '..', 'build', 'icon.png');
let win = null;
let server = null;
let paths = null;
let quitting = false;

// No traffic of Chromium's own either (component updates, pings, field trials, etc.): the app is
// fully offline. The page's requests are filtered separately in start().
for (const sw of ['disable-background-networking', 'disable-component-update', 'disable-domain-reliability',
  'no-pings', 'disable-sync', 'disable-breakpad', 'metrics-recording-only']) {
  app.commandLine.appendSwitch(sw);
}
app.commandLine.appendSwitch('disable-features', 'OptimizationHints,MediaRouter,Translate,AutofillServerCommunication,CertificateTransparencyComponentUpdater');

// No request may leave this computer (desktop brief 2): every session refuses non-local requests
// from the moment it exists, and its spell checker (which downloads dictionaries on Linux) is off.
app.on('session-created', (ses) => {
  ses.setSpellCheckerEnabled(false);
  if (process.platform !== 'darwin') {
    ses.setSpellCheckerLanguages([]);                                // nothing to download...
    ses.setSpellCheckerDictionaryDownloadURL('http://127.0.0.1:9/'); // ...and never from the internet
  }
  ses.webRequest.onBeforeRequest((details, callback) => callback({ cancel: !isLocal(details.url) }));
});

// One instance only: a second start focuses the window that is already open.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.show();
      win.focus();
    }
  });
  app.whenReady().then(start);
}

/** Only this computer: the server, local pages and Electron's own devtools. Everything else is refused. */
function isLocal(url) {
  try {
    const u = new URL(url);
    if (['file:', 'devtools:', 'data:', 'blob:', 'chrome-extension:'].includes(u.protocol)) return true;
    return u.hostname === '127.0.0.1' || u.hostname === 'localhost';
  } catch {
    return false;
  }
}

async function start() {
  const dev = !app.isPackaged;
  const res = resourcesFor({ packaged: app.isPackaged, resourcesPath: process.resourcesPath, repoRoot: path.resolve(__dirname, '..', '..') });
  const dataDir = process.env.GAMELIFE_DATA_DIR || dataDirFor(app.getPath('appData'), dev);
  // Chromium's own files (cache, local storage) go in a subfolder of the data folder, not next to the database.
  app.setPath('userData', path.join(dataDir, 'electron'));


  win = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 380,
    minHeight: 600,
    show: false,
    backgroundColor: '#06070c',
    title: 'GameLife',
    icon: ICON,
    autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, sandbox: true, spellcheck: false },
  });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e, url) => { if (!isLocal(url)) e.preventDefault(); });
  win.once('ready-to-show', () => win.show());
  await win.loadFile(path.join(__dirname, 'starting.html'));

  try {
    assertOutsideInstall(dataDir, res.installDir);
    paths = prepareDataDir(dataDir, res.defaults);
    const port = await findFreePort();
    server = startServer({ java: res.java, jar: res.jar, port, paths, contentDir: res.content, frontendDir: res.frontend });
    server.exited.then((code) => {
      if (!quitting) showError(`The GameLife server stopped unexpectedly (exit ${code}).`);
    });
    await waitUntilReady(server);
    await win.loadURL(server.url + '/');
  } catch (e) {
    showError(e.message);
  }
}

/** A clear error page instead of a blank window, with the log path and a button to open it. */
function showError(message) {
  if (!win || win.isDestroyed()) return;
  const logFile = paths?.logFile ?? '';
  win.loadFile(path.join(__dirname, 'error.html'), { query: { message, log: logFile } });
  win.show();
}

// Desktop-only settings, reached from the page through preload.cjs.
ipcMain.on('gamelife:data-folder-sync', (e) => { e.returnValue = paths?.dataDir ?? ''; });
ipcMain.handle('gamelife:get-start-with-windows', () => app.getLoginItemSettings().openAtLogin);
ipcMain.handle('gamelife:set-start-with-windows', (_e, on) => {
  app.setLoginItemSettings({ openAtLogin: !!on });
  return app.getLoginItemSettings().openAtLogin;
});
ipcMain.handle('gamelife:choose-folder', async () => {
  const r = await dialog.showOpenDialog(win, { title: 'Backup folder', properties: ['openDirectory', 'createDirectory'] });
  return r.canceled ? null : r.filePaths[0];
});
ipcMain.handle('gamelife:open-folder', (_e, p) => shell.openPath(String(p)));
ipcMain.handle('gamelife:open-log', () => paths && shell.showItemInFolder(paths.logFile));
ipcMain.handle('gamelife:quit', () => app.quit());

// Exit: stop the server cleanly first (it writes the exit backup), then quit for real.
app.on('before-quit', (e) => {
  if (quitting || !server) return;
  e.preventDefault();
  quitting = true;
  stopServer(server).finally(() => app.exit(0));
});
app.on('window-all-closed', () => app.quit());
