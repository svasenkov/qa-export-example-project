#!/usr/bin/env node
/**
 * Fold GitHub layer-job `failure` into the Allure quality-gate widget.
 *
 * GitHub layer-job `failure` is not an Allure CLI / kit rule (those paths
 * only see Allure results — donut / tests table stay green). After generate,
 * this script folds those failures into the QG widget. Expected `skipped` (PR without prod e2e, manual on
 * push, lane `if:`) does not fail the gate.
 *
 * Usage:
 *   CI_LAYER_JOBS='{"frontend-unit-tests":{"result":"failure"}}' \
 *     node allure/attach-ci-jobs-quality-gate.mjs --report build/reports/allure-report/allureReport
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  QUALITY_GATE_LABELS,
  QUALITY_GATE_SOURCE,
  REPORT_LANGUAGE,
  TITLES,
} from "./constants.mjs";
import { qualityGateRules } from "./quality-gate.mjs";

/** Layer jobs `publish-allure-report` waits on. Not Sonar or deploy. */
export const LAYER_JOB_IDS = Object.freeze([
  "backend-unit-tests",
  "frontend-unit-tests",
  "infra-tests",
  "ui-tests",
  "integration-tests",
  "api-tests-stage",
  "e2e-tests-stage",
  "api-tests",
  "e2e-tests",
  "manual-tests",
]);

export const CI_JOBS_RULE_ID = "maxCiJobFailures";

const REPORT_PLUGINS = ["awesome", "dashboard"];

export function parseNeeds(raw) {
  if (raw == null || raw === "") {
    return {};
  }
  if (typeof raw === "object") {
    return raw;
  }
  return JSON.parse(String(raw));
}

/** Job ids in {@link LAYER_JOB_IDS} whose GitHub result is `failure`. */
export function failedLayerJobs(needs) {
  const failed = [];
  for (const id of LAYER_JOB_IDS) {
    if (needs?.[id]?.result === "failure") {
      failed.push(id);
    }
  }
  return failed;
}

function ciJobsRule(failedIds) {
  const actual = failedIds.length;
  return {
    id: CI_JOBS_RULE_ID,
    passed: actual === 0,
    message:
      actual === 0
        ? `Failed CI jobs ${actual} within threshold 0`
        : `The number of failed CI jobs ${actual} exceeds the allowed threshold value 0 (${failedIds.join(", ")})`,
    actual,
    expected: 0,
    comparator: "GT",
  };
}

function withCiJobsConfigRules(config = {}) {
  const rules = Array.isArray(config.rules) ? [...config.rules] : [];
  if (!rules.some((rule) => rule?.maxCiJobFailures !== undefined)) {
    const fromFile = qualityGateRules.find((rule) => rule.maxCiJobFailures !== undefined);
    rules.push(fromFile ? { ...fromFile } : { id: "ciJobs", maxCiJobFailures: 0 });
  }
  return {
    ...config,
    rules,
    source: config.source ?? { ...QUALITY_GATE_SOURCE },
  };
}

export function emptyAllureQualityGate() {
  return {
    kind: "allure",
    testId: "quality-gate",
    passed: true,
    rules: [],
    title: TITLES.qualityGate,
    barTitle: TITLES.qualityGate,
    config: withCiJobsConfigRules({ rules: qualityGateRules.map((rule) => ({ ...rule })) }),
    labels: QUALITY_GATE_LABELS,
    lang: REPORT_LANGUAGE,
  };
}

/**
 * @param {object} gateData KitQualityGateData
 * @param {string[]} failedIds
 * @returns {{ changed: boolean, data: object }}
 */
export function applyCiJobFailures(gateData, failedIds) {
  const base = gateData && typeof gateData === "object" ? gateData : emptyAllureQualityGate();
  if (!failedIds.length) {
    return { changed: false, data: base };
  }

  const without = (base.rules ?? []).filter((rule) => rule.id !== CI_JOBS_RULE_ID);
  const data = {
    ...base,
    passed: false,
    rules: [...without, ciJobsRule(failedIds)],
    config: withCiJobsConfigRules(base.config),
    ...(base.kind ? {} : { kind: "allure" }),
    ...(base.testId ? {} : { testId: "quality-gate" }),
    ...(base.labels ? {} : { labels: QUALITY_GATE_LABELS }),
    ...(base.lang ? {} : { lang: REPORT_LANGUAGE }),
  };
  return { changed: true, data };
}

export function qualityGateWidgetPath(reportRoot, plugin) {
  return path.join(reportRoot, plugin, "widgets/kit-panels/allureQualityGate.json");
}

function readGate(filePath) {
  if (!fs.existsSync(filePath)) {
    return emptyAllureQualityGate();
  }
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    if (parsed && typeof parsed === "object") {
      return parsed;
    }
  } catch {
    /* rewrite from empty */
  }
  return emptyAllureQualityGate();
}

export function attachCiJobsQualityGate(reportRoot, failedIds) {
  const written = [];
  if (!failedIds.length) {
    return { changed: false, failedIds, written };
  }

  for (const plugin of REPORT_PLUGINS) {
    const dest = qualityGateWidgetPath(reportRoot, plugin);
    const { data } = applyCiJobFailures(readGate(dest), failedIds);
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, `${JSON.stringify(data, null, 2)}\n`);
    written.push(dest);
  }
  return { changed: true, failedIds, written };
}

function parseArgs(argv) {
  const options = { report: "", needs: process.env.CI_LAYER_JOBS || "" };
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === "--report") options.report = argv[++index] ?? "";
    else if (token === "--needs") options.needs = argv[++index] ?? "";
    else if (token === "--needs-file") {
      options.needs = fs.readFileSync(argv[++index] ?? "", "utf8");
    }
  }
  return options;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isMain) {
  const options = parseArgs(process.argv.slice(2));
  if (!options.report) {
    console.error("attach-ci-jobs-quality-gate: --report <allure-report-root> is required");
    process.exit(1);
  }

  let needs;
  try {
    needs = parseNeeds(options.needs);
  } catch (error) {
    console.error(`attach-ci-jobs-quality-gate: invalid CI_LAYER_JOBS / --needs (${error.message})`);
    process.exit(1);
  }

  const failedIds = failedLayerJobs(needs);
  const result = attachCiJobsQualityGate(path.resolve(options.report), failedIds);
  if (!result.changed) {
    console.log("attach-ci-jobs-quality-gate: no layer-job failures — Allure QG unchanged");
  } else {
    console.log(
      `attach-ci-jobs-quality-gate: ${failedIds.join(", ")} → Allure QG not passed (${result.written.length} widgets)`,
    );
  }
}
