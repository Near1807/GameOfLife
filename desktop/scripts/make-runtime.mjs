// A minimal Java runtime for the installer (desktop brief 1), made with jlink from the JDK that
// builds the app (JAVA_HOME, or the jlink on the PATH). About 50 MB instead of a full JDK. The
// modules are those Spring Boot, SQLite and the content scanner (ImageIO) need; test/runtime.test.cjs
// starts the real server with this runtime to prove the list is complete.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const MODULES = [
  'java.base', 'java.compiler', 'java.desktop', 'java.instrument', 'java.logging', 'java.management',
  'java.naming', 'java.net.http', 'java.prefs', 'java.rmi', 'java.scripting', 'java.security.jgss',
  'java.sql', 'java.transaction.xa', 'java.xml', 'jdk.crypto.ec', 'jdk.jfr', 'jdk.management',
  'jdk.net', 'jdk.unsupported', 'jdk.zipfs', 'jdk.localedata',
];

export function makeRuntime(outDir) {
  const exe = process.platform === 'win32' ? 'jlink.exe' : 'jlink';
  const jlink = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', exe) : exe;
  fs.rmSync(outDir, { recursive: true, force: true });
  execFileSync(jlink, [
    '--add-modules', MODULES.join(','),
    '--include-locales', 'en,fr,nl,de',
    '--strip-debug', '--no-header-files', '--no-man-pages', '--compress', 'zip-6',
    '--output', outDir,
  ], { stdio: 'inherit' });
  return outDir;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const out = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'stage', 'jre');
  makeRuntime(out);
  console.log(`Java runtime written to ${out}`);
}
