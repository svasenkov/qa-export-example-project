import assert from "node:assert/strict";
import { test } from "node:test";

import {
  minAttachmentsForLayersRule,
  minStepsForLayersRule,
} from "./quality-gate-custom.mjs";

function gateState(initial = 0) {
  let value = initial;
  return {
    getResult: () => value,
    setResult: (next) => {
      value = next;
    },
  };
}

function tr({ layer, tags = [], steps = [], attachments = [], framework } = {}) {
  const labels = [{ name: "layer", value: layer }];
  if (framework) {
    labels.push({ name: "framework", value: framework });
  }
  for (const value of tags) {
    labels.push({ name: "tag", value });
  }
  return { labels, steps, attachments };
}

test("minStepsForLayers counts empty integration, skips unit", async () => {
  const result = await minStepsForLayersRule.validate({
    trs: [
      tr({ layer: "unit" }),
      tr({ layer: "api", steps: [{ name: "GET", steps: [] }] }),
      tr({ layer: "integration" }),
    ],
    expected: ["api", "integration", "e2e", "manual"],
    state: gateState(),
  });
  assert.equal(result.success, false);
  assert.equal(result.actual, 1);
});

test("minAttachmentsForLayers counts nested step PNGs on screenshot tests", async () => {
  const result = await minAttachmentsForLayersRule.validate({
    trs: [
      tr({
        layer: "e2e",
        tags: ["e2e", "screenshot"],
        steps: [{ name: "Compare", attachments: [{ name: "png" }], steps: [] }],
      }),
      tr({ layer: "e2e", tags: ["e2e"] }),
    ],
    expected: ["e2e"],
    state: gateState(),
  });
  assert.equal(result.success, true);
  assert.equal(result.actual, 0);
});

test("minAttachmentsForLayers counts Allure 3 attachment steps", async () => {
  const result = await minAttachmentsForLayersRule.validate({
    trs: [
      tr({
        layer: "e2e",
        tags: ["screenshot"],
        steps: [
          { name: "Compare", type: "step", steps: [{ type: "attachment", link: { name: "png" } }] },
        ],
      }),
    ],
    expected: ["e2e"],
    state: gateState(),
  });
  assert.equal(result.success, true);
  assert.equal(result.actual, 0);
});

test("minAttachmentsForLayers fails screenshot tests with no files", async () => {
  const result = await minAttachmentsForLayersRule.validate({
    trs: [tr({ layer: "e2e", tags: ["screenshot"], steps: [{ name: "Open", steps: [] }] })],
    expected: ["e2e"],
    state: gateState(),
  });
  assert.equal(result.success, false);
  assert.equal(result.actual, 1);
});
