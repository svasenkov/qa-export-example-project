/** Shared Allure ethalon constants. */
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
  historyPath: "./history.jsonl",
  appendHistory: true,
  historyLimit: 20,
  knownIssuesPath: "./known.json",
};

/** Relative paths shown in the quality gate info popover — SSOT in design-system. */
export {
  ALLURE_QUALITY_GATE_SOURCE as QUALITY_GATE_SOURCE,
  ALLURE_QUALITY_GATE_LABELS as QUALITY_GATE_LABELS,
  SONAR_QUALITY_GATE_SOURCE,
  SONAR_QUALITY_GATE_LABELS,
} from "./quality-gate-source.mjs";

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

