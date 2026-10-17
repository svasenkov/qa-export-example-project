/**
 * Quality-gate popover paths — browser + allurerc SSOT.
 * stacks/.../constants.mjs re-exports Allure as QUALITY_GATE_SOURCE / LABELS.
 * Do not partial-copy in shell; import objects whole.
 */
export const ALLURE_QUALITY_GATE_SOURCE = {
  configFile: "allurerc.mjs",
  rulesFile: "allure/quality-gate.mjs",
  knownIssuesFile: "./known.json",
};

export const ALLURE_QUALITY_GATE_LABELS = {
  passed: { ru: "Allure Quality Gate пройден", en: "Allure Quality Gate passed" },
  failed: { ru: "Allure Quality Gate не пройден", en: "Allure Quality Gate failed" },
};

export const SONAR_QUALITY_GATE_SOURCE = {
  configFile: "build.gradle",
};

export const SONAR_QUALITY_GATE_LABELS = {
  passed: { ru: "Sonar Quality Gate пройден", en: "Sonar Quality Gate passed" },
  failed: { ru: "Sonar Quality Gate не пройден", en: "Sonar Quality Gate failed" },
};

