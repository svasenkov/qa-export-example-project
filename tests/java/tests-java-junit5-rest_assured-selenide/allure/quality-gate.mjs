import {
  LAYERS_REQUIRE_ATTACHMENTS,
  LAYERS_REQUIRE_STEPS,
} from "./quality-gate-custom.mjs";

/**
 * Ethalon quality gate rules for `npx allure quality-gate`.
 *
 * Custom keys require matching implementations in `qualityGate.use`
 * (`quality-gate-use.mjs`). GitHub layer-job `failure` is not a CLI rule:
 * after generate, `attach-ci-jobs-quality-gate.mjs` folds those into the widget.
 * Expected `skipped` does not fail the gate.
 */
export const qualityGateRules = [
  {
    id: "failures",
    maxFailures: 0,
    fastFail: true,
  },
  {
    id: "reporting",
    minStepsForLayers: LAYERS_REQUIRE_STEPS,
    minAttachmentsForLayers: LAYERS_REQUIRE_ATTACHMENTS,
  },
];
