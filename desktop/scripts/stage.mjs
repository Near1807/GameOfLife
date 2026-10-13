// Copies what the installer ships into desktop/stage/ (desktop brief 7):
//   stage/server/gamelife-backend.jar   (build it first: npm run build at the repo root)
//   stage/frontend/                     the built frontend (frontend/dist)
//   stage/content/                      towers, waves, maps, hero art, looks, themes
//   stage/defaults/gamelife.json        the default config (copied to %APPDATA%\GameLife on first start)
// The Java runtime is made by make-runtime.mjs into stage/jre.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..');
const stage = path.join(here, '..', 'stage');

const target = path.join(root, 'backend', 'target');
const jar = fs.existsSync(target) && fs.readdirSync(target).find((f) => /^gamelife-backend-.*\.jar$/.test(f));
if (!jar) throw new Error('No backend jar in backend/target. Run "npm run build" at the repository root first.');
if (!fs.existsSync(path.join(root, 'frontend', 'dist', 'index.html'))) throw new Error('No frontend/dist. Run "npm run build" first.');

for (const d of ['server', 'frontend', 'content', 'defaults']) fs.rmSync(path.join(stage, d), { recursive: true, force: true });
fs.mkdirSync(path.join(stage, 'server'), { recursive: true });
fs.mkdirSync(path.join(stage, 'defaults'), { recursive: true });
fs.copyFileSync(path.join(target, jar), path.join(stage, 'server', 'gamelife-backend.jar'));
fs.cpSync(path.join(root, 'frontend', 'dist'), path.join(stage, 'frontend'), { recursive: true });
fs.cpSync(path.join(root, 'content'), path.join(stage, 'content'), {
  recursive: true,
  filter: (src) => !src.includes(`${path.sep}tools`) && !src.includes(`${path.sep}styleguide`),
});
fs.copyFileSync(path.join(root, 'config', 'gamelife.json'), path.join(stage, 'defaults', 'gamelife.json'));
console.log(`staged ${jar}, frontend, content and default config in desktop/stage`);
