#!/usr/bin/env node
/**
 * Render notifications/config.runtime.json from template + explicitly supplied environment variables.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const testsDir = fileURLToPath(new URL("..", import.meta.url));
const templatePath = path.join(testsDir, "notifications/config.json");
const outPath = path.join(testsDir, "notifications/config.runtime.json");

const cfg = JSON.parse(fs.readFileSync(templatePath, "utf8"));

cfg.base.project = process.env.NOTIFICATION_PROJECT || cfg.base.project;
cfg.base.allureFolder = process.env.ALLURE_FOLDER || cfg.base.allureFolder;
cfg.base.allureResultsFolder =
  process.env.ALLURE_RESULTS_FOLDER || cfg.base.allureResultsFolder;
if (cfg.base.chart && process.env.ALLURE_FOLDER) {
  // Kit collage reads AQG / testsTable JSON from the generated soft-fork report.
  const folder = process.env.ALLURE_FOLDER.replace(/\/$/, "");
  cfg.base.chart.allureQualityGatePath = `${folder}/widgets/kit-panels/allureQualityGate.json`;
  cfg.base.chart.testsTablePath = `${folder}/widgets/kit-panels/testsTable.json`;
}
cfg.base.links = {
  report: process.env.ALLURE_REPORT_URL || "",
  dashboard: process.env.ALLURE_DASHBOARD_URL || "",
  testops: process.env.TESTOPS_LAUNCH_URL || "",
  build: process.env.BUILD_URL || "",
};

cfg.telegram.token = process.env.TELEGRAM_BOT_TOKEN || process.env.TELEGRAM_TOKEN || "";
cfg.telegram.chat = process.env.TELEGRAM_CHAT_ID || "";
cfg.telegram.topic = process.env.TELEGRAM_TOPIC_ID || "";

fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, `${JSON.stringify(cfg, null, 2)}\n`);
console.log(`render-notifications-config: ${outPath}`);
