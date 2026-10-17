import { maxFailuresRule } from "allure/rules";
import {
  LAYERS_REQUIRE_ATTACHMENTS,
  LAYERS_REQUIRE_STEPS,
  minStepsForLayersRule,
  minAttachmentsForLayersRule,
} from "./quality-gate-custom.mjs";

/** Rules for test outcomes and reporting metadata. CI job status is separate. */
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

export const qualityGateUse = [maxFailuresRule, minStepsForLayersRule, minAttachmentsForLayersRule];
