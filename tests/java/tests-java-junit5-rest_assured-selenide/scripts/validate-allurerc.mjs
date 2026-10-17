#!/usr/bin/env node
/**
 * Validate Allure ethalon / consumer allurerc.mjs:
 * - import succeeds
 * - configured quality gates followed by overview charts (awesome + dashboard)
 * - Sonar gate is optional when no measured result exists
 *
 * Usage:
 *   node scripts/validate-allurerc.mjs [path/to/allurerc.mjs]
 * Default: allurerc.mjs in the current directory or this module.
 */
import { pathToFileURL } from "node:url";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { presets } from "@qa-guru/allure-report-kit";
import { OVERVIEW_PRESET as KIT_OVERVIEW_PRESET } from "@qa-guru/allure-report-kit/presets/overview-preset";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function fail(message) {
  console.error(`validate-allurerc: FAIL — ${message}`);
  process.exit(1);
}

async function loadOverviewPreset(configDir) {
  const candidates = [
    path.join(configDir, "allure", "overview-preset.mjs"),
    path.resolve(__dirname, "../allure/overview-preset.mjs"),
  ];
  for (const file of candidates) {
    if (!fs.existsSync(file)) continue;
    try {
      const mod = await import(pathToFileURL(file).href);
      if (mod.OVERVIEW_PRESET && Array.isArray(mod.OVERVIEW_PRESET.tiles)) {
        return mod.OVERVIEW_PRESET;
      }
    } catch {
      /* try next */
    }
  }
  fail("allure/overview-preset.mjs with OVERVIEW_PRESET export not found");
}

function assertPresetDerivedFromKit(preset) {
  const gates = preset.qualityGates ?? [];
  if (gates[0]?.id !== "allureQualityGate" || gates.some((gate) =>
    !KIT_OVERVIEW_PRESET.qualityGates.some((expected) => JSON.stringify(expected) === JSON.stringify(gate)))) {
    fail("qualityGates must contain Allure and optionally the measured Sonar gate");
  }
  for (const field of ["tiles", "renderers"]) {
    if (JSON.stringify(preset[field]) !== JSON.stringify(KIT_OVERVIEW_PRESET[field])) {
      fail(
        `OVERVIEW_PRESET.${field} must match kit SSOT (@qa-guru/allure-report-kit/presets/overview-preset) — override titles/pyramidLayers only`,
      );
    }
  }
}

function assertLeadLayout(tiles, label, preset) {
  const gateCount = preset.qualityGates?.length ?? 0;
  const minLen = gateCount + preset.tiles.length;
  if (!Array.isArray(tiles) || tiles.length < minLen) {
    fail(`${label}: expected array with at least ${minLen} lead tiles`);
  }
  const leadIds = tiles.slice(0, gateCount).map((tile) => tile?.id);
  if (new Set(leadIds).size !== leadIds.length) {
    fail(`${label}: duplicate quality-gate panel ids: ${leadIds.join(", ")}`);
  }
  if (!presets.matchesLeadLayout?.(tiles, preset)) {
    fail(
      `${label}: lead section does not match OVERVIEW_PRESET from overview-preset.mjs`,
    );
  }
}

async function loadConfig(configPath) {
  if (!fs.existsSync(configPath)) {
    fail(`file not found: ${configPath}`);
  }
  const mod = await import(pathToFileURL(path.resolve(configPath)).href);
  if (!mod.default || typeof mod.default !== "object") {
    fail(`${configPath}: export default object required`);
  }
  return mod.default;
}

async function main() {
  const arg = process.argv[2];
  const packageRoot = path.resolve(__dirname, "..");
  const configPath = arg
    ? path.resolve(process.cwd(), arg)
    : fs.existsSync(path.join(process.cwd(), "allurerc.mjs"))
      ? path.join(process.cwd(), "allurerc.mjs")
      : path.join(packageRoot, "allurerc.mjs");

  const config = await loadConfig(configPath);
  const preset = await loadOverviewPreset(path.dirname(configPath));
  assertPresetDerivedFromKit(preset);
  const charts = config.plugins?.awesome?.options?.charts;
  const layout = config.plugins?.dashboard?.options?.layout;

  assertLeadLayout(charts, "plugins.awesome.options.charts", preset);
  assertLeadLayout(layout, "plugins.dashboard.options.layout", preset);

  if (!config.name || typeof config.name !== "string") {
    fail("name: required string");
  }
  if (!config.qualityGate?.rules?.length) {
    fail("qualityGate.rules: required non-empty");
  }

  const useIds = new Set(
    (config.qualityGate.use ?? []).map((item) => item?.rule).filter(Boolean),
  );
  const customKeys = ["minStepsForLayers", "minAttachmentsForLayers"];
  for (const rule of config.qualityGate.rules) {
    for (const key of customKeys) {
      if (rule[key] !== undefined && !useIds.has(key)) {
        fail(`qualityGate.use missing implementation for "${key}"`);
      }
    }
  }

  const catGroupBy = config.categories?.rules?.flatMap((r) => r.groupBy ?? []) ?? [];
  const allowed = new Set([
    "flaky",
    "owner",
    "severity",
    "transition",
    "status",
    "environment",
    "layer",
  ]);
  for (const selector of catGroupBy) {
    if (typeof selector === "string" && !allowed.has(selector)) {
      fail(
        `categories.groupBy invalid selector "${selector}" (Allure 3.13 builtins: ${[...allowed].join(", ")})`,
      );
    }
  }

  const layers = preset.pyramidLayers ?? [];
  const chartOffset = preset.qualityGates?.length ?? 0;
  console.log(`validate-allurerc: OK — ${configPath}`);
  console.log(`  name=${config.name}`);
  console.log(`  awesome.charts=${charts.length}, dashboard.layout=${layout.length}`);
  console.log(
    `  gates: ${(preset.qualityGates ?? []).map((gate) => gate.id).join(", ")}`,
  );
  console.log(`  pyramid@${chartOffset + 2} layers=[${layers.join(", ")}]`);
}

main().catch((err) => {
  fail(err?.stack || String(err));
});
