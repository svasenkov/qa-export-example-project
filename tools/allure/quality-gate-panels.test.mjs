import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { buildGatePanels } from "./quality-gate-panels.mjs";
import { buildLeadTiles } from "./overview-preset.mjs";

function resultFile(t, content) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "sonar-panel-test-"));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const file = path.join(dir, "result.json");
  fs.writeFileSync(file, typeof content === "string" ? content : JSON.stringify(content));
  return file;
}

test("no Sonar result creates neither a successful panel nor a Sonar lead tile", () => {
  assert.equal(process.env.SONAR_QUALITY_GATE_JSON, undefined, "run without a live Sonar input");
  assert.ok(buildGatePanels().allureQualityGate);
  assert.equal(buildGatePanels().sonarQualityGate, undefined);
  assert.equal(buildLeadTiles().some((tile) => tile.id === "sonarQualityGate"), false);
});

test("missing explicitly selected result fails instead of supplying demo success", (t) => {
  const file = resultFile(t, {});
  fs.unlinkSync(file);
  assert.throws(() => buildGatePanels({ sonarFile: file }), /does not exist/);
});

test("malformed or unsupported results cannot become a successful gate", (t) => {
  for (const content of ["{", {}, { status: "invented" }, { status: "OK", conditions: {} }]) {
    const file = resultFile(t, content);
    assert.throws(() => buildGatePanels({ sonarFile: file }), /Sonar/);
  }
});

test("Sonar NONE means no measured gate and omits the panel", (t) => {
  const file = resultFile(t, { status: "NONE", conditions: [] });
  assert.equal(buildGatePanels({ sonarFile: file }).sonarQualityGate, undefined);
});

test("a failed measured gate remains failed even with a contradictory passed field", (t) => {
  const file = resultFile(t, { status: "ERROR", passed: true, conditions: [
    { status: "ERROR", metricKey: "coverage", actualValue: "64", errorThreshold: "80", comparator: "LT" },
  ] });
  const data = buildGatePanels({ sonarFile: file }).sonarQualityGate.data;
  assert.equal(data.passed, false);
  assert.equal(data.rules[0].passed, false);
  assert.equal(Number(data.rules[0].actual), 64);
});

test("a successful measured gate preserves its supplied project and metrics", (t) => {
  const file = resultFile(t, { status: "OK", project_key: "local-example", conditions: [
    { status: "OK", metricKey: "coverage", actualValue: "87", errorThreshold: "80", comparator: "LT" },
  ] });
  const data = buildGatePanels({ sonarFile: file }).sonarQualityGate.data;
  assert.equal(data.passed, true);
  assert.equal(data.config.projectKey, "local-example");
  assert.equal(Number(data.rules[0].actual), 87);
});

