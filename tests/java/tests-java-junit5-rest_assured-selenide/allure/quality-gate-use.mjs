import {
  minAttachmentsForLayersRule,
  minStepsForLayersRule,
} from "./quality-gate-custom.mjs";

/** Resolved when `allure` npm package is available (CI / npx allure quality-gate). */
let qualityGateUse;

try {
  const { maxFailuresRule } = await import("allure/rules");
  qualityGateUse = [
    maxFailuresRule,
    minStepsForLayersRule,
    minAttachmentsForLayersRule,
  ];
} catch {
  qualityGateUse = undefined;
}

export { qualityGateUse };
