#!/usr/bin/env node
/**
 * Fail if package.json dependencies do not match package-lock.json.
 *
 * CI installs with `npm ci` (the lockfile tree), not `npm install`.
 * After changing pins: `nvm use 26 && npm install --package-lock-only`
 * and commit package.json together with package-lock.json.
 *
 * Usage: node scripts/check-package-lock.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const pkgPath = path.join(root, "package.json");
const lockPath = path.join(root, "package-lock.json");

function fail(message) {
  console.error(`check-package-lock: FAIL — ${message}`);
  console.error(
    "npm ci installs the lockfile. After changing package.json pins:\n" +
      "  nvm use 26 && npm install --package-lock-only\n" +
      "Commit package.json and package-lock.json together.",
  );
  process.exit(1);
}

if (!fs.existsSync(pkgPath)) {
  fail(`missing ${pkgPath}`);
}
if (!fs.existsSync(lockPath)) {
  fail(`missing ${lockPath} — commit the lockfile; CI runs npm ci`);
}

const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf8"));
const lock = JSON.parse(fs.readFileSync(lockPath, "utf8"));
const declared = pkg.dependencies || {};
const lockRoot = lock.packages?.[""]?.dependencies || {};
const mismatches = [];

for (const [name, want] of Object.entries(declared)) {
  const locked = lockRoot[name];
  if (locked !== want) {
    mismatches.push(`${name}: package.json=${want} lock=${locked ?? "(missing)"}`);
    continue;
  }
  const resolved = lock.packages?.[`node_modules/${name}`]?.version;
  const exact = want && !String(want).startsWith("^") && !String(want).startsWith("~");
  if (exact && resolved && resolved !== want) {
    mismatches.push(`${name}: package.json=${want} resolved=${resolved}`);
  }
}

if (mismatches.length) {
  fail(mismatches.join("; "));
}

console.log(`check-package-lock: OK — ${Object.keys(declared).length} dependencies match ${path.basename(lockPath)}`);
