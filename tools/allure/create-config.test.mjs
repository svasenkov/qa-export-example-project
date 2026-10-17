import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath, pathToFileURL } from "node:url";
import { test } from "node:test";
import { createAllureConfig } from "./create-config.mjs";

function fixture(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "shared-allure-"));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  return dir;
}

function consumer(dir, slug) {
  const base = path.join(dir, slug);
  const resultsDir = path.join(base, "results");
  fs.mkdirSync(resultsDir, { recursive: true });
  fs.writeFileSync(path.join(resultsDir, "one-result.json"), JSON.stringify({
    uuid: slug, historyId: slug, name: slug, fullName: slug, status: "passed",
    start: 1, stop: 10, labels: [{ name: "layer", value: "component" }],
  }));
  const historyFile = path.join(base, "history.jsonl");
  fs.writeFileSync(historyFile, JSON.stringify({ uuid: "previous", testResults: {
    [slug]: { historyId: slug, name: slug, fullName: slug, status: "failed", duration: 10 },
  } }) + "\n");
  return { slug, resultsDir: pathToFileURL(resultsDir), historyFile: pathToFileURL(historyFile) };
}

test("consumers keep their results and histories separate in one process", (t) => {
  const dir = fixture(t);
  for (const slug of ["java", "vue"]) {
    const input = consumer(dir, slug);
    const config = createAllureConfig(input);
    assert.equal(config.historyPath, fileURLToPath(input.historyFile));
    assert.equal(config.variables.Framework, undefined);
    for (const id of ["awesome", "dashboard"]) {
      assert.ok(path.isAbsolute(config.plugins[id].import));
      const options = config.plugins[id].options;
      const rows = (options.charts ?? options.layout).find((tile) => tile.id === "testsTable").data.rows;
      assert.deepEqual(rows.map((row) => row.name), [slug]);
      assert.deepEqual(rows[0].history.map((point) => point.status), ["failed", "passed"]);
    }
  }
});

test("CLI generates both report views from another working directory", (t) => {
  const dir = fixture(t);
  const input = consumer(dir, "another-stack");
  const configPath = path.join(dir, "allurerc.mjs");
  const factoryUrl = new URL("./create-config.mjs", import.meta.url).href;
  fs.writeFileSync(configPath, `import { createAllureConfig } from ${JSON.stringify(factoryUrl)};
export default createAllureConfig({
  slug: "another-stack",
  resultsDir: new URL(${JSON.stringify(input.resultsDir.href)}),
  historyFile: new URL(${JSON.stringify(input.historyFile.href)})
});\n`);
  const output = path.join(dir, "report");
  execFileSync(process.execPath, [
    fileURLToPath(new URL("./node_modules/allure/cli.js", import.meta.url)),
    "generate", "--config", configPath, "--output", output, fileURLToPath(input.resultsDir),
  ], { cwd: os.tmpdir(), timeout: 60000, stdio: "pipe" });
  for (const id of ["awesome", "dashboard"]) {
    // Inline panels are embedded in the kit manifest; only run-derived panels
    // (such as qualityGate) are emitted as separate widget files.
    const html = fs.readFileSync(path.join(output, id, "index.html"), "utf8");
    const manifest = JSON.parse(html.match(/<script>window\.allureReportKit = (.*?)<\/script>/s)[1]);
    const widget = manifest.tiles.find((tile) => tile.panel?.id === "testsTable").panel.data;
    assert.deepEqual(widget.rows.map((row) => row.name), [input.slug]);
    assert.equal(widget.rows[0].status, "passed");
    const gate = JSON.parse(fs.readFileSync(path.join(output, id, "widgets/kit-panels/allureQualityGate.json"), "utf8"));
    assert.equal(gate.passed, true);
  }
});
