# vendor/ds — design-system runtime snapshot

**Vendor** lean design-system runtime snapshot — not the etalon.

SSOT lives upstream in `projects/design-system-home/design-system/` (`css/`,
`js/`, `templates/`). Do not edit this tree by hand. This assembly ships no sync
script — `frontend/scripts/sync-ds-runtime.sh` exists only in the upstream
layout. To refresh, copy `css/`, the runtime `js/` files and `templates/` from
an upstream checkout, then re-apply the product overlay below.

Product overlay (not copied from design-system): `js/app-base.js`,
`js/env-hosts.js` (generated upstream by `sync-stack-matrix.py`, also not part
of this repo).

The `/stack/` board lives on autotests.ai (landing React `/stack/`),
not in this snapshot.

Packed into this module's nginx image as `vendor/ds` (module `Dockerfile`);
`npm run dev` serves the same files via `scripts/vite-overlay-runtime.mjs`.
