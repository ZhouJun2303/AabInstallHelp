'use strict';

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const root = path.resolve(__dirname, '..');
const staging = path.join(root, '.pack_staging');
const outDir = path.join(root, 'release');
const outAsar = path.join(outDir, 'app.asar');
const APP_FILES = [
  'index.html',
  'main.js',
  'process.js',
  'handle_drag.js',
  'style.css',
  'package.json'
];

function fail(message) {
  console.error(message);
  process.exit(1);
}

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    if (entry.name === '.bin') continue;
    const from = path.join(src, entry.name);
    const to = path.join(dest, entry.name);
    if (entry.isDirectory()) copyDir(from, to);
    else if (entry.isFile()) fs.copyFileSync(from, to);
  }
}

function moduleDir(name, fromDir) {
  const nested = path.join(fromDir, 'node_modules', name);
  if (fs.existsSync(path.join(nested, 'package.json'))) return nested;
  const top = path.join(root, 'node_modules', name);
  if (fs.existsSync(path.join(top, 'package.json'))) return top;
  return null;
}

function copyProdDeps() {
  const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
  const queue = Object.keys(pkg.dependencies || {}).map((name) => ({
    name,
    parent: root,
    destParent: staging
  }));
  const copied = new Set();

  while (queue.length) {
    const item = queue.pop();
    const key = item.name + '|' + item.destParent;
    if (copied.has(key)) continue;
    copied.add(key);

    const src = moduleDir(item.name, item.parent);
    if (!src) fail('missing production dependency: ' + item.name);

    const dest = path.join(item.destParent, 'node_modules', item.name);
    copyDir(src, dest);

    const depPkg = JSON.parse(fs.readFileSync(path.join(src, 'package.json'), 'utf8'));
    for (const dep of Object.keys(depPkg.dependencies || {})) {
      queue.push({ name: dep, parent: src, destParent: dest });
    }
  }
}

function packAsar() {
  fs.mkdirSync(outDir, { recursive: true });
  const quotedStaging = '"' + staging + '"';
  const quotedOut = '"' + outAsar + '"';
  const command = 'npx --yes @electron/asar pack ' + quotedStaging + ' ' + quotedOut;
  const result = spawnSync(command, { cwd: root, stdio: 'inherit', shell: true });
  if (result.error) fail('asar pack failed: ' + result.error.message);
  if (result.status !== 0) fail('asar pack failed');
}

function main() {
  for (const file of APP_FILES) {
    if (!fs.existsSync(path.join(root, file))) fail('missing file: ' + file);
  }

  fs.rmSync(staging, { recursive: true, force: true });
  fs.mkdirSync(staging, { recursive: true });

  for (const file of APP_FILES) {
    fs.copyFileSync(path.join(root, file), path.join(staging, file));
  }

  copyProdDeps();
  packAsar();

  const size = fs.statSync(outAsar).size;
  console.log('packed ' + outAsar + ' (' + size + ' bytes)');
}

main();
