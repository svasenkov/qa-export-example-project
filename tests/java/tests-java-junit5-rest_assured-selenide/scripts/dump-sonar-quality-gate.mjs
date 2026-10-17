#!/usr/bin/env node
/**
 * Dump Sonar project_status JSON for the Allure QG panel.
 *
 * Reads ceTaskId from report-task.txt, waits for CE SUCCESS, then polls
 * /api/qualitygates/project_status?analysisId=… (never by projectKey).
 *
 * Env: SONAR_TOKEN (required), SONAR_HOST_URL (fallback if report-task has no serverUrl)
 *
 * Usage:
 *   node scripts/dump-sonar-quality-gate.mjs --report-task PATH --out allure/sonar-quality-gate.json
 */
import fs from "node:fs";
import path from "node:path";

const GATE_TERMINAL = new Set(["OK", "PASSED", "FAILED", "ERROR"]);
const CE_TERMINAL_FAIL = new Set(["FAILED", "CANCELED"]);

function fail(message) {
  console.error(`dump-sonar-quality-gate: ${message}`);
  process.exit(1);
}

function parseArgs(argv) {
  const out = {
    reportTask: null,
    out: null,
    projectKey: null,
    timeout: 600,
    poll: 5,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    const value = argv[i + 1];
    if (key === "--report-task") {
      out.reportTask = value;
      i += 1;
    } else if (key === "--out") {
      out.out = value;
      i += 1;
    } else if (key === "--project-key") {
      out.projectKey = value;
      i += 1;
    } else if (key === "--timeout") {
      out.timeout = Number(value);
      i += 1;
    } else if (key === "--poll") {
      out.poll = Number(value);
      i += 1;
    }
  }
  return out;
}

function parseReportTask(filePath) {
  const fields = {};
  for (const raw of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }
    const eq = line.indexOf("=");
    fields[line.slice(0, eq).trim()] = line.slice(eq + 1).trim();
  }
  return fields;
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function apiGet(baseUrl, apiPath, params, token) {
  const url = new URL(apiPath, `${baseUrl.replace(/\/$/, "")}/`);
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) {
    throw new Error(`${url.pathname} HTTP ${response.status}`);
  }
  return response.json();
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.reportTask || !args.out) {
    fail("--report-task and --out are required");
  }
  if (!fs.existsSync(args.reportTask)) {
    fail(`report-task not found: ${args.reportTask}`);
  }
  const token = process.env.SONAR_TOKEN || "";
  if (!token) {
    fail("SONAR_TOKEN not set");
  }

  const report = parseReportTask(args.reportTask);
  const ceTaskId = report.ceTaskId;
  if (!ceTaskId) {
    fail("ceTaskId missing in report-task.txt");
  }
  const baseUrl = report.serverUrl || process.env.SONAR_HOST_URL || "https://sonar.qa.guru";
  const reportKey = report.projectKey || "";
  if (args.projectKey && reportKey && args.projectKey !== reportKey) {
    fail(`project-key mismatch: argv=${args.projectKey} report-task=${reportKey}`);
  }

  const deadline = Date.now() + args.timeout * 1000;
  let analysisId = "";
  let projectKey = args.projectKey || reportKey;
  while (Date.now() < deadline) {
    const payload = await apiGet(baseUrl, "/api/ce/task", { id: ceTaskId }, token);
    const task = payload.task || {};
    const status = task.status || "UNKNOWN";
    if (status === "SUCCESS") {
      analysisId = task.analysisId || "";
      projectKey = task.componentKey || projectKey;
      break;
    }
    if (CE_TERMINAL_FAIL.has(status)) {
      fail(`CE task ended with status ${status}`);
    }
    await sleep(args.poll * 1000);
  }
  if (!analysisId) {
    fail("CE task did not yield analysisId before timeout");
  }

  let gate;
  while (Date.now() < deadline) {
    const payload = await apiGet(
      baseUrl,
      "/api/qualitygates/project_status",
      { analysisId },
      token,
    );
    const projectStatus = payload.projectStatus || {};
    const status = projectStatus.status || "UNKNOWN";
    const key = projectStatus.projectKey || projectKey;
    gate = {
      status,
      project_key: key,
      analysis_id: analysisId,
      dashboard_url: `${baseUrl.replace(/\/$/, "")}/dashboard?id=${encodeURIComponent(key)}`,
      conditions: projectStatus.conditions || [],
    };
    if (GATE_TERMINAL.has(status)) {
      break;
    }
    await sleep(args.poll * 1000);
  }
  if (!gate) {
    fail("no quality gate response");
  }

  const outPath = path.resolve(args.out);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, `${JSON.stringify(gate, null, 2)}\n`);
  console.log(
    `dump-sonar-quality-gate: wrote ${outPath} status=${gate.status} key=${gate.project_key}`,
  );
}

main().catch((error) => fail(error?.stack || String(error)));
