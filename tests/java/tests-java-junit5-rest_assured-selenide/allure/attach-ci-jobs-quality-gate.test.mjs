import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";

import {
  CI_JOBS_RULE_ID,
  applyCiJobFailures,
  attachCiJobsQualityGate,
  failedLayerJobs,
  parseNeeds,
  qualityGateWidgetPath,
} from "./attach-ci-jobs-quality-gate.mjs";

test("failedLayerJobs counts failure only, not skipped or missing", () => {
  assert.deepEqual(
    failedLayerJobs({
      "frontend-unit-tests": { result: "failure" },
      "backend-unit-tests": { result: "success" },
      "manual-tests": { result: "skipped" },
      "e2e-tests": { result: "skipped" },
      "sonar-backend": { result: "failure" },
    }),
    ["frontend-unit-tests"],
  );
});

test("failedLayerJobs includes infra-tests", () => {
  assert.deepEqual(failedLayerJobs({ "infra-tests": { result: "failure" } }), [
    "infra-tests",
  ]);
});

test("parseNeeds reads GitHub toJSON(needs) strings", () => {
  const needs = parseNeeds('{"ui-tests":{"result":"failure","outputs":{}}}');
  assert.deepEqual(failedLayerJobs(needs), ["ui-tests"]);
});

test("applyCiJobFailures keeps result rules and is idempotent", () => {
  const gate = {
    passed: true,
    rules: [
      {
        id: "maxFailures",
        passed: true,
        message: "Failed tests 0 within threshold 0",
        actual: 0,
        expected: 0,
      },
    ],
    config: { rules: [{ maxFailures: 0 }] },
  };
  const once = applyCiJobFailures(gate, ["frontend-unit-tests"]);
  assert.equal(once.changed, true);
  assert.equal(once.data.passed, false);
  assert.equal(once.data.rules[0].id, "maxFailures");
  assert.equal(once.data.rules[0].passed, true);
  const ci = once.data.rules.find((rule) => rule.id === CI_JOBS_RULE_ID);
  assert.equal(ci.passed, false);
  assert.equal(ci.actual, 1);
  assert.match(ci.message, /frontend-unit-tests/);
  assert.equal(
    once.data.config.rules.some((rule) => rule.maxCiJobFailures === 0),
    true,
  );

  const twice = applyCiJobFailures(once.data, ["frontend-unit-tests"]);
  assert.equal(twice.data.rules.filter((rule) => rule.id === CI_JOBS_RULE_ID).length, 1);
});

test("applyCiJobFailures is a no-op when every layer job succeeded or skipped", () => {
  const gate = { passed: true, rules: [{ id: "maxFailures", passed: true, message: "ok" }] };
  const result = applyCiJobFailures(gate, []);
  assert.equal(result.changed, false);
  assert.equal(result.data.passed, true);
});

test("attachCiJobsQualityGate writes awesome and dashboard widgets", () => {
  const report = fs.mkdtempSync(path.join(os.tmpdir(), "ci-qg-"));
  const awesome = qualityGateWidgetPath(report, "awesome");
  fs.mkdirSync(path.dirname(awesome), { recursive: true });
  fs.writeFileSync(
    awesome,
    JSON.stringify({
      passed: true,
      rules: [{ id: "maxFailures", passed: true, message: "ok", actual: 0, expected: 0 }],
    }),
  );

  const result = attachCiJobsQualityGate(report, ["infra-tests", "frontend-unit-tests"]);
  assert.equal(result.changed, true);
  assert.equal(result.written.length, 2);

  for (const plugin of ["awesome", "dashboard"]) {
    const widget = JSON.parse(fs.readFileSync(qualityGateWidgetPath(report, plugin), "utf8"));
    assert.equal(widget.passed, false);
    assert.equal(widget.rules.at(-1).actual, 2);
    assert.match(widget.rules.at(-1).message, /infra-tests/);
  }
});

test("attachCiJobsQualityGate does not rewrite a green gate", () => {
  const report = fs.mkdtempSync(path.join(os.tmpdir(), "ci-qg-green-"));
  const awesome = qualityGateWidgetPath(report, "awesome");
  fs.mkdirSync(path.dirname(awesome), { recursive: true });
  fs.writeFileSync(awesome, JSON.stringify({ passed: true, rules: [] }));
  const result = attachCiJobsQualityGate(report, []);
  assert.equal(result.changed, false);
  assert.equal(fs.existsSync(qualityGateWidgetPath(report, "dashboard")), false);
  assert.deepEqual(JSON.parse(fs.readFileSync(awesome, "utf8")), { passed: true, rules: [] });
});
