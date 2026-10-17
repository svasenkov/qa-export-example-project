import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { withKit, theme, renderers } from "@qa-guru/allure-report-kit";

import { buildCharts } from "./charts.mjs";
import { categoryRules } from "./categories.mjs";
import {
  HISTORY_DEFAULTS,
  QUALITY_GATE_SOURCE,
  REPORT_LANGUAGE,
} from "./constants.mjs";
import { qualityGateRules, qualityGateUse } from "./quality-gate.mjs";

// Allure 3 writes `logo` into <img src> as-is and does not copy the file into
// the report. A filesystem path 404s on GitHub Pages; embed as data URI.
const ALLURE_LOGO = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(
  readFileSync(fileURLToPath(new URL("./allure3-logo.svg", import.meta.url)), "utf8"),
)}`;

/** Shared report; every consumer supplies its own input and history paths. */
export function createAllureConfig({
  slug,
  resultsDir,
  historyFile,
  knownIssuesFile,
  sonarFile,
  variables,
} = {}) {
  if (!slug || typeof slug !== "string") {
    throw new Error("createAllureConfig: profile.slug is required");
  }

  if (!resultsDir || !historyFile) {
    throw new Error("createAllureConfig: resultsDir and historyFile are required");
  }
  const absolute = (value) => value instanceof URL ? fileURLToPath(value) : path.resolve(value);
  const inputs = {
    resultsDir: absolute(resultsDir),
    historyFile: absolute(historyFile),
    sonarFile,
  };

  const config = withKit({
    softFork: true,
    renderer: renderers.stock(),
    theme: {
      ...theme.qaGuru(),
      header: {
        enabled: true,
        source: "design-system",
        productName: `${slug} Tests`,
      },
    },
    name: `${slug} Tests`,
    ...HISTORY_DEFAULTS,
    historyPath: inputs.historyFile,
    knownIssuesPath: knownIssuesFile ? absolute(knownIssuesFile) : path.join(path.dirname(inputs.historyFile), "known.json"),
    variables: variables ?? {
      Report: "Allure 3",
    },
    qualityGate: {
      rules: structuredClone(qualityGateRules),
      use: qualityGateUse,
      source: { ...QUALITY_GATE_SOURCE },
    },
    categories: {
      rules: categoryRules.map((rule) => structuredClone(rule)),
    },
    plugins: {
      awesome: {
        options: {
          logo: ALLURE_LOGO,
          reportLanguage: REPORT_LANGUAGE,
          groupBy: ["parentSuite", "suite", "subSuite"],
          charts: buildCharts(inputs),
        },
      },
      dashboard: {
        options: {
          reportName: `${slug} Tests Dashboard`,
          reportLanguage: REPORT_LANGUAGE,
          layout: buildCharts(inputs),
        },
      },
      csv: {
        options: {
          fileName: `${slug}.csv`,
        },
      },
    },
  });

  // Gradle may run its own CLI installation. Resolve the UI plugins here,
  // beside our single package-lock, rather than relative to that CLI.
  for (const id of ["awesome", "dashboard"]) {
    config.plugins[id].import = fileURLToPath(import.meta.resolve(config.plugins[id].import));
  }
  return config;
}
