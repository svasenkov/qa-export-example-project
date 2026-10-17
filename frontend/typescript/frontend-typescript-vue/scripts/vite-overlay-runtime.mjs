/**
 * Vite plugin: serve design-system runtime from `vendor/ds` in dev/preview.
 * Docker already copies that tree over `dist/`; without this, `/js/header.js`
 * and `/templates/header.html` 404 and agents copy a fake `.stand-overlay`.
 *
 * Default is `js` + `templates` only. Do not overlay `/css/` on Vite apps that
 * `import` those files — the plugin would steal the request and return raw CSS
 * as a module (MIME error, app never mounts). Pass `{ css: true }` for MPAs
 * that load overlay CSS via `<link rel="stylesheet">` (typescript-vanilla).
 */
import { createReadStream, statSync } from 'node:fs';
import { extname, join, resolve } from 'node:path';

const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
};

function pathnameOf(url) {
  const raw = (url ?? '/').split('?')[0];
  const stripped = raw
    .replace(/^\/stack\/(?:backend-[^/]+\/)?frontend-[^/]+/, '')
    .replace(/^\/backend-[^/]+\/frontend-[^/]+/, '');
  return stripped || '/';
}

/**
 * @param {string} overlayRoot absolute path to the cell's `vendor/ds`
 * @param {{ css?: boolean }} [opts]
 * @returns {import('vite').Plugin}
 */
export function overlayRuntime(overlayRoot, opts = {}) {
  const root = resolve(overlayRoot);
  const dirs = opts.css ? 'css|js|templates' : 'js|templates';
  const OVERLAY_PATH_RE = new RegExp(`^/((?:${dirs})/[\\w.-]+)$`);

  function serveOverlayFile(req, res, next) {
    const match = OVERLAY_PATH_RE.exec(pathnameOf(req.url));
    if (!match) {
      next();
      return;
    }
    const file = resolve(join(root, match[1]));
    if (file !== root && !file.startsWith(`${root}/`)) {
      next();
      return;
    }
    if (!statSync(file, { throwIfNoEntry: false })?.isFile()) {
      next();
      return;
    }
    res.setHeader('Content-Type', MIME_TYPES[extname(file)] ?? 'application/octet-stream');
    createReadStream(file).pipe(res);
  }

  return {
    name: 'overlay-runtime',
    configureServer(server) {
      server.middlewares.use(serveOverlayFile);
    },
    configurePreviewServer(server) {
      server.middlewares.use(serveOverlayFile);
    },
  };
}
