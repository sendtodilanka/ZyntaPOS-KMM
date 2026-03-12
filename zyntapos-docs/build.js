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

// Generate index.html with Scalar CDN reference
const indexHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>ZyntaPOS API Documentation</title>
  <meta name="description" content="Complete API reference for the ZyntaPOS Point of Sale system." />
  <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>Z</text></svg>" />
</head>
<body>
  <script id="api-reference" data-url="./openapi/api-v1.yaml"></script>
  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
</body>
</html>`;

fs.writeFileSync(path.join(distDir, 'index.html'), indexHtml);

console.log('Build complete — output in dist/');
console.log('  - index.html (Scalar API reference)');
console.log('  - openapi/ (spec files)');
`;
