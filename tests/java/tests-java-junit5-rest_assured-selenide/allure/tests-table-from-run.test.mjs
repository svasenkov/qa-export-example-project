import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";

import {
  countFlakyFlips,
  loadTestsTableFromRun,
  writeTestsTableWidgets,
} from "./tests-table-from-run.mjs";

test("countFlakyFlips counts pass/fail edges", () => {
  assert.equal(
    countFlakyFlips([
      { status: "passed" },
      { status: "failed" },
      { status: "passed" },
    ]),
    2,
  );
});

test("loadTestsTableFromRun uses current status, not fixture names", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "tests-table-"));
  const resultsDir = path.join(dir, "results");
  fs.mkdirSync(resultsDir);
  fs.writeFileSync(
    path.join(resultsDir, "a-result.json"),
    JSON.stringify({
      name: "shouldLogin",
      fullName: "auth.LoginTests.shouldLogin",
      status: "passed",
      historyId: "login",
      uuid: "1",
      start: 1,
      stop: 1100,
    }),
  );
  fs.writeFileSync(
    path.join(resultsDir, "b-result.json"),
    JSON.stringify({
      name: "checkoutFlowCompletes",
      fullName: "e2e.CheckoutTests.checkoutFlowCompletes",
      status: "passed",
      historyId: "checkout",
      uuid: "2",
      start: 2,
      stop: 4200,
    }),
  );
  fs.writeFileSync(
    path.join(dir, "history.jsonl"),
    `${JSON.stringify({
      uuid: "old",
      testResults: {
        "checkout.extra": {
          name: "checkoutFlowCompletes",
          fullName: "e2e.CheckoutTests.checkoutFlowCompletes",
          status: "failed",
          historyId: "checkout",
          duration: 5100,
        },
      },
    })}\n`,
  );

  const data = loadTestsTableFromRun({
    resultsDir,
    historyFile: path.join(dir, "history.jsonl"),
  });
  const checkout = data.rows.find((row) => row.name === "checkoutFlowCompletes");
  assert.ok(checkout);
  assert.equal(checkout.status, "passed");
  assert.equal(checkout.history.at(-1).status, "passed");
  assert.ok(checkout.history.some((point) => point.status === "failed"));
  assert.equal(data.rows.some((row) => row.status === "failed"), false);

  const report = path.join(dir, "report");
  writeTestsTableWidgets(report, data);
  const widget = JSON.parse(
    fs.readFileSync(path.join(report, "awesome/widgets/kit-panels/testsTable.json"), "utf8"),
  );
  assert.equal(widget.rows[0].name === "shouldLogin" || widget.rows[1].name === "shouldLogin", true);
});
