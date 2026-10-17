import { charts } from "@qa-guru/allure-report-kit";

import {
  STABILITY_SKIP_STATUSES,
  STABILITY_STABILIZATION_PERIOD,
  STABILITY_THRESHOLD,
  TITLES,
} from "./constants.mjs";
import { buildLeadTiles } from "./overview-preset.mjs";
import { buildTestsTablePanels } from "./tests-table-panels.mjs";

/** Filter tests by Allure `component` label (hub README dashboard crops). */
export function componentLabelFilter(component) {
  return ({ labels }) =>
    labels.some(({ name, value }) => name === "component" && value === component);
}

/**
 * Compact lead dashboard for README PNG — same preset as main layout.
 * Per-widget filter applies to overview charts only (QG stays global).
 */
export function buildComponentReadmeDashboardLayout(component) {
  const filter = componentLabelFilter(component);

  return buildLeadTiles({ filter });
}

/**
 * Dashboard plugin layout.
 * Lead preset (QG + overview), then the rest.
 */
export function buildDashboardLayout({ epicCharts = [] } = {}) {
  const epicStatusDynamics = epicCharts.map((epic) =>
    charts.statusDynamics({
      title: `Динамика — ${epic}`,
      limit: 20,
      filter: ({ labels }) =>
        labels.some(({ name, value }) => name === "epic" && value === epic),
    }),
  );

  return [
    ...buildLeadTiles(),
    ...buildTestsTablePanels(),
    charts.statusDynamics({ title: TITLES.statusDynamics, limit: 20 }),
    charts.successRateDistribution({ title: TITLES.successRateDistribution }),
    charts.stabilityDistribution({
      title: TITLES.stabilityByComponent,
      threshold: STABILITY_THRESHOLD,
      stabilizationPeriod: STABILITY_STABILIZATION_PERIOD,
      skipStatuses: [...STABILITY_SKIP_STATUSES],
      groupBy: "label-name:component",
    }),
    charts.coverageDiff({ title: TITLES.coverageDiff }),
    charts.statusTransitions({ title: TITLES.statusTransitions, limit: 20 }),
    charts.testBaseGrowthDynamics({
      title: TITLES.testBaseGrowthDynamics,
      limit: 20,
    }),
    charts.problemsDistribution({ title: TITLES.problemsByEnvironment }),
    charts.stabilityDistribution({
      title: TITLES.stabilityByFeature,
      threshold: STABILITY_THRESHOLD,
      stabilizationPeriod: STABILITY_STABILIZATION_PERIOD,
      skipStatuses: [...STABILITY_SKIP_STATUSES],
      groupBy: "feature",
    }),
    charts.stabilityDistribution({
      title: TITLES.stabilityByEpic,
      threshold: STABILITY_THRESHOLD,
      stabilizationPeriod: STABILITY_STABILIZATION_PERIOD,
      skipStatuses: [...STABILITY_SKIP_STATUSES],
      groupBy: "epic",
    }),
    charts.stabilityDistribution({
      title: TITLES.stabilityByStory,
      threshold: STABILITY_THRESHOLD,
      stabilizationPeriod: STABILITY_STABILIZATION_PERIOD,
      skipStatuses: [...STABILITY_SKIP_STATUSES],
      groupBy: "story",
    }),
    charts.testResultSeverities({ title: TITLES.testResultSeverities }),
    charts.durations({ title: TITLES.durations, groupBy: "none" }),
    charts.statusAgePyramid({ title: TITLES.statusAgePyramid, limit: 20 }),
    ...epicStatusDynamics,
  ];
}
