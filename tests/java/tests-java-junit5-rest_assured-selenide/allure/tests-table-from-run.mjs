/**
 * Build `panels.testsTable` data from allure-results + history.jsonl.
 *
 * Production dashboards must not ship the kit dogfood fixture (fake failed rows
 * next to a 100% currentStatus).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_COLUMNS = {
  ru: ["Тест", "Статус", "Тренд", "Стабильность"],
  en: ["Test", "Status", "Trend", "Stability"],
};

const STATUS_RANK = {
  failed: 0,
  broken: 1,
  skipped: 2,
  unknown: 3,
  passed: 4,
};

export function stableTestKey(result) {
  const fullName = result.fullName || result.testCaseName || "";
  const name = result.name || "";
  if (fullName && name) return `${fullName}\0${name}`;
  if (result.historyId) return String(result.historyId);
  return result.uuid || name || "unknown";
}

function durationMs(result) {
  if (typeof result.duration === "number") return result.duration;
  if (typeof result.start === "number" && typeof result.stop === "number") {
    return Math.max(0, result.stop - result.start);
  }
  return 0;
}

function durationSec(result) {
  return Math.round((durationMs(result) / 1000) * 100) / 100;
}

function statusOf(result) {
  return String(result.status || "unknown").toLowerCase();
}

export function countFlakyFlips(history) {
  let flips = 0;
  for (let index = 1; index < history.length; index += 1) {
    const prev = history[index - 1].status;
    const curr = history[index].status;
    const prevOutcome = prev === "passed" || prev === "failed" || prev === "broken";
    const currOutcome = curr === "passed" || curr === "failed" || curr === "broken";
    if (!prevOutcome || !currOutcome) continue;
    if ((prev === "passed") !== (curr === "passed")) flips += 1;
  }
  return flips;
}

function listResultFiles(resultsDir) {
  if (!resultsDir || !fs.existsSync(resultsDir)) return [];
  return fs
    .readdirSync(resultsDir)
    .filter((name) => name.endsWith("-result.json") && !name.includes("-retry-"))
    .map((name) => path.join(resultsDir, name));
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function loadCurrentResults(resultsDir) {
  const byKey = new Map();
  for (const filePath of listResultFiles(resultsDir)) {
    let result;
    try {
      result = readJson(filePath);
    } catch {
      continue;
    }
    const key = stableTestKey(result);
    const prev = byKey.get(key);
    if (!prev || (result.stop ?? 0) >= (prev.stop ?? 0)) {
      byKey.set(key, result);
    }
  }
  return [...byKey.values()];
}

function indexHistoryTimelines(historyFile) {
  const timelines = new Map();
  if (!historyFile || !fs.existsSync(historyFile)) return timelines;

  const lines = fs
    .readFileSync(historyFile, "utf8")
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  for (const line of lines) {
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      continue;
    }
    const runId = entry.uuid;
    for (const [rawKey, result] of Object.entries(entry.testResults ?? {})) {
      const point = {
        status: statusOf(result),
        durationSec: durationSec(result),
        runId,
      };
      const keys = new Set([stableTestKey(result), rawKey]);
      if (result.historyId) keys.add(String(result.historyId));
      for (const key of keys) {
        if (!timelines.has(key)) timelines.set(key, []);
        timelines.get(key).push(point);
      }
    }
  }
  return timelines;
}

function historyForResult(timelines, result) {
  const candidates = [stableTestKey(result)];
  if (result.historyId) {
    candidates.push(String(result.historyId));
  }
  for (const key of candidates) {
    if (timelines.has(key)) return [...timelines.get(key)];
  }
  const historyId = result.historyId ? String(result.historyId) : "";
  if (historyId) {
    for (const [key, points] of timelines) {
      if (key.startsWith(`${historyId}.`)) return [...points];
    }
  }
  return [];
}

function appendCurrentPoint(history, result, runId) {
  const point = {
    status: statusOf(result),
    durationSec: durationSec(result),
    runId,
  };
  const last = history[history.length - 1];
  if (last && last.runId && runId && last.runId === runId) {
    history[history.length - 1] = point;
    return history;
  }
  history.push(point);
  return history;
}

function compareRows(left, right) {
  const rankDelta =
    (STATUS_RANK[left.status] ?? STATUS_RANK.unknown) -
    (STATUS_RANK[right.status] ?? STATUS_RANK.unknown);
  if (rankDelta !== 0) return rankDelta;
  const flakyDelta = (right.flakyFlips ?? 0) - (left.flakyFlips ?? 0);
  if (flakyDelta !== 0) return flakyDelta;
  return String(left.name).localeCompare(String(right.name));
}

function toPath(value) {
  if (!value) return "";
  if (value instanceof URL) return fileURLToPath(value);
  if (typeof value === "string" && value.startsWith("file:")) return fileURLToPath(value);
  return path.resolve(value);
}

/**
 * @param {object} [options]
 * @param {string | URL} [options.resultsDir]
 * @param {string | URL} [options.historyFile]
 * @param {"ru" | "en"} [options.lang]
 * @param {string} [options.runId]
 * @returns {import("@qa-guru/allure-report-kit").KitTestsTableData}
 */
export function loadTestsTableFromRun({
  resultsDir,
  historyFile,
  lang = "ru",
  runId = "current",
} = {}) {
  const resultsPath = toPath(resultsDir);
  const historyPath = toPath(historyFile);
  const results = loadCurrentResults(resultsPath);
  const timelines = indexHistoryTimelines(historyPath);
  const rows = results
    .map((result) => {
      const history = appendCurrentPoint(historyForResult(timelines, result), result, runId);
      return {
        id: result.historyId || result.uuid || stableTestKey(result),
        name: result.name || result.testCaseName || result.fullName || "—",
        fullName: result.fullName || result.testCaseName || result.name,
        status: statusOf(result),
        history: history.map(({ status, durationSec: sec }) => ({
          status,
          durationSec: sec,
        })),
        flakyFlips: countFlakyFlips(history),
      };
    })
    .sort(compareRows);

  return {
    columns: [...DEFAULT_COLUMNS[lang]],
    lang,
    rows,
  };
}

export function writeTestsTableWidgets(reportRoot, data) {
  const payload = `${JSON.stringify(data)}\n`;
  for (const plugin of ["awesome", "dashboard"]) {
    const dest = path.join(reportRoot, plugin, "widgets/kit-panels/testsTable.json");
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, payload);
  }
}

function parseArgs(argv) {
  const options = { results: "", history: "", report: "", lang: "ru" };
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (token === "--results") options.results = argv[++index] ?? "";
    else if (token === "--history") options.history = argv[++index] ?? "";
    else if (token === "--report") options.report = argv[++index] ?? "";
    else if (token === "--lang") options.lang = argv[++index] ?? "ru";
  }
  return options;
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isMain) {
  const options = parseArgs(process.argv.slice(2));
  if (!options.results) {
    console.error("tests-table-from-run: --results <allure-results-dir> is required");
    process.exit(1);
  }
  const data = loadTestsTableFromRun({
    resultsDir: options.results,
    historyFile: options.history || undefined,
    lang: options.lang === "en" ? "en" : "ru",
  });
  if (options.report) {
    writeTestsTableWidgets(path.resolve(options.report), data);
    console.log(
      `tests-table-from-run: ${data.rows.length} rows → ${options.report}/{awesome,dashboard}/widgets/kit-panels/testsTable.json`,
    );
  } else {
    process.stdout.write(`${JSON.stringify(data)}\n`);
  }
}
