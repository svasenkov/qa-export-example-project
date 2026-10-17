/** Shared report defaults. */
export const REPORT_LANGUAGE = "ru";

export const PYRAMID_LAYERS = [
  "unit",
  "component",
  "integration",
  "api",
  "ui",
  "e2e",
  "manual",
];

export const STABILITY_THRESHOLD = 90;

export const STABILITY_SKIP_STATUSES = ["skipped", "unknown"];

// Min history data points required before stabilityDistribution renders bars
// (Allure default 5). Lowered so charts populate with a shorter history.
export const STABILITY_STABILIZATION_PERIOD = 3;

export const HISTORY_DEFAULTS = {
  appendHistory: true,
  historyLimit: 20,
};

export const QUALITY_GATE_SOURCE = {
  configFile: "allurerc.mjs",
  rulesFile: "tools/allure/quality-gate.mjs",
  knownIssuesFile: "./known.json",
};

export const QUALITY_GATE_LABELS = {
  passed: { ru: "Allure Quality Gate пройден", en: "Allure Quality Gate passed" },
  failed: { ru: "Allure Quality Gate не пройден", en: "Allure Quality Gate failed" },
};

export const SONAR_QUALITY_GATE_SOURCE = {
  configFile: "allurerc.mjs (sonarFile)",
};

export const SONAR_QUALITY_GATE_LABELS = {
  passed: { ru: "Sonar Quality Gate пройден", en: "Sonar Quality Gate passed" },
  failed: { ru: "Sonar Quality Gate не пройден", en: "Sonar Quality Gate failed" },
};


/** Default chart/layout titles shared by awesome + dashboard. */
export const TITLES = {
  currentStatus: "Текущий статус по сервисам",
  testingPyramid: "Пирамида тестирования",
  testResultSeverities: "Результаты по severity",
  statusDynamics: "Динамика статусов",
  statusTransitions: "Переходы статусов",
  testBaseGrowthDynamics: "Динамика роста тестовой базы",
  coverageDiff: "Карта изменений покрытия",
  successRateDistribution: "Распределение успешности",
  problemsByEnvironment: "Распределение проблем по environment",
  stabilityByComponent: "Стабильность по сервисам",
  stabilityByFeature: "Стабильность по feature",
  stabilityByEpic: "Стабильность по epic",
  stabilityByStory: "Стабильность по story",
  durations: "Гистограмма длительностей",
  durationsByLayer: "Длительности по layer",
  durationDynamics: "Динамика длительности",
  statusAgePyramid: "Пирамида возраста статусов",
  qualityGate: "Allure Quality Gate",
  sonarQualityGate: "Sonar Quality Gate",
  testsTable: "Таблица тестов",
};
