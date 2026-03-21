#!/usr/bin/env node
/**
 * ZyntaPOS Docs — Static build script.
 *
 * Generates a single-page Scalar API reference from the OpenAPI spec
 * and copies guide pages into dist/ for static serving.
 */
const fs = require('fs');
const path = require('path');

const distDir = path.join(__dirname, 'dist');

// Clean and create dist/
if (fs.existsSync(distDir)) {
  fs.rmSync(distDir, { recursive: true });
}
fs.mkdirSync(distDir, { recursive: true });

// Copy OpenAPI specs
const openapiDir = path.join(distDir, 'openapi');
fs.mkdirSync(openapiDir, { recursive: true });
const specSrc = path.join(__dirname, 'openapi');
for (const file of fs.readdirSync(specSrc)) {
  if (file.endsWith('.yaml') || file.endsWith('.json')) {
    fs.copyFileSync(path.join(specSrc, file), path.join(openapiDir, file));
  }
}

// Multi-spec Scalar configuration with a dropdown selector for all 4 API specs.
// Scalar sources config: https://github.com/scalar/scalar#multiple-specs
const scalarConfig = JSON.stringify({
  sources: [
    { url: './openapi/api-v1.yaml',     title: 'POS API (v1)' },
    { url: './openapi/admin-v1.yaml',   title: 'Admin API (v1)' },
    { url: './openapi/license-v1.yaml', title: 'License API (v1)' },
    { url: './openapi/sync-v1.yaml',    title: 'Sync WebSocket (v1)' },
  ],
  theme: 'default',
  darkMode: true,
  defaultHttpClient: {
    targetKey: 'shell',
    clientKey: 'curl',
  },
});

// Generate index.html with Scalar CDN reference (multi-spec mode)
const indexHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>ZyntaPOS API Documentation</title>
  <meta name="description" content="Complete API reference for the ZyntaPOS Point of Sale system — POS API, Admin API, License API, and Sync WebSocket." />
  <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>Z</text></svg>" />
</head>
<body>
  <script id="api-reference" data-configuration='${scalarConfig}'></script>
  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
</body>
</html>`;

fs.writeFileSync(path.join(distDir, 'index.html'), indexHtml);

// Copy guides into dist/guides/ for static serving
const guidesDir = path.join(distDir, 'guides');
fs.mkdirSync(guidesDir, { recursive: true });
const guidesSrc = path.join(__dirname, 'guides');
if (fs.existsSync(guidesSrc)) {
  for (const file of fs.readdirSync(guidesSrc)) {
    fs.copyFileSync(path.join(guidesSrc, file), path.join(guidesDir, file));
  }
}

console.log('Build complete — output in dist/');
console.log('  - index.html (Scalar API reference — 4 specs with dropdown selector)');
console.log('  - openapi/api-v1.yaml     (POS API)');
console.log('  - openapi/admin-v1.yaml   (Admin API)');
console.log('  - openapi/license-v1.yaml (License API)');
console.log('  - openapi/sync-v1.yaml    (Sync WebSocket)');
console.log('  - guides/                 (Markdown guides)');
