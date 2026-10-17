/** Quality-gate panels backed by actual report inputs; no demo fallback. */
import fs from "node:fs";

import { sonarProjectStatusToQualityGateOptions } from "@qa-guru/allure-report-kit/runtime";
import {
  QUALITY_GATE_LABELS,
  REPORT_LANGUAGE,
  SONAR_QUALITY_GATE_LABELS,
  SONAR_QUALITY_GATE_SOURCE,
  TITLES,
} from "./constants.mjs";

function loadSonarProjectStatus(filePath) {
  if (!filePath) return null;
  if (!fs.existsSync(filePath)) {
    throw new Error("Configured Sonar result file does not exist");
  }
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    throw new Error("Cannot read Sonar result JSON");
  }
  if (!parsed || !["OK", "ERROR", "WARN", "NONE"].includes(parsed.status)
      || (parsed.conditions !== undefined && !Array.isArray(parsed.conditions))) {
    throw new Error("Invalid Sonar project status");
  }
  if (parsed.status === "NONE") return null;
  return parsed;
}

export function buildGatePanels({
  layout = "2x1",
  sonarFile,
} = {}) {
  const panels = {
    allureQualityGate: {
      title: TITLES.qualityGate,
      layout,
      labels: QUALITY_GATE_LABELS,
    },
  };
  const projectStatus = loadSonarProjectStatus(sonarFile);
  if (!projectStatus) return panels;

  panels.sonarQualityGate = {
    title: TITLES.sonarQualityGate,
    layout,
    dots: false,
    data: sonarProjectStatusToQualityGateOptions(projectStatus, {
      lang: REPORT_LANGUAGE,
      passed: projectStatus.status === "OK",
      source: { ...SONAR_QUALITY_GATE_SOURCE },
      labels: SONAR_QUALITY_GATE_LABELS,
      barTitle: TITLES.sonarQualityGate,
    }),
  };
  return panels;
}
