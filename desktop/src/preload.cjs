// The only bridge between the page and the desktop (desktop brief 6 and 4): start with Windows,
// folder choice, open folders. The page sees window.gamelifeDesktop; in the browser version it is
// undefined and these settings are hidden.
'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('gamelifeDesktop', {
  // asked once per page load; the app page loads after the data folder is prepared
  dataFolder: ipcRenderer.sendSync('gamelife:data-folder-sync'),
  getStartWithWindows: () => ipcRenderer.invoke('gamelife:get-start-with-windows'),
  setStartWithWindows: (on) => ipcRenderer.invoke('gamelife:set-start-with-windows', on),
  chooseFolder: () => ipcRenderer.invoke('gamelife:choose-folder'),
  openFolder: (p) => ipcRenderer.invoke('gamelife:open-folder', p),
  openLog: () => ipcRenderer.invoke('gamelife:open-log'),
  quit: () => ipcRenderer.invoke('gamelife:quit'),
});
