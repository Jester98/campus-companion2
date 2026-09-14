// Copies the browser-ready Capacitor runtime files out of node_modules and
// into www/vendor/, so the app can load them as plain local <script> tags
// instead of pulling them from a CDN at runtime.
//
// Why this matters: the native Android build (produced by `cap sync android`)
// bakes a specific, pinned version of these plugins into the app's native
// code. If index.html instead fetches "@latest" from a CDN every time the
// app opens, the JS bridge can silently drift out of sync with the native
// side, and the app stops working offline the moment there's no network —
// which defeats the point of *local* notifications. Self-hosting keeps the
// JS and native versions locked together and lets the feature work with no
// internet connection at all.
//
// Run via `npm run sync:vendor` after `npm install`, before `cap sync`.

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const VENDOR_DIR = path.join(ROOT, 'www', 'vendor');

const FILES = [
  {
    from: path.join(ROOT, 'node_modules', '@capacitor', 'core', 'dist', 'capacitor.js'),
    to: path.join(VENDOR_DIR, 'capacitor.js'),
  },
  {
    from: path.join(ROOT, 'node_modules', '@capacitor', 'local-notifications', 'dist', 'plugin.js'),
    to: path.join(VENDOR_DIR, 'local-notifications.js'),
  },
];

fs.mkdirSync(VENDOR_DIR, { recursive: true });

let failed = false;
for (const file of FILES) {
  if (!fs.existsSync(file.from)) {
    console.error(`[sync-vendor] Missing expected file: ${file.from}`);
    console.error('[sync-vendor] Did `npm install` run first?');
    failed = true;
    continue;
  }
  fs.copyFileSync(file.from, file.to);
  console.log(`[sync-vendor] Copied ${path.relative(ROOT, file.from)} -> ${path.relative(ROOT, file.to)}`);
}

if (failed) process.exit(1);
