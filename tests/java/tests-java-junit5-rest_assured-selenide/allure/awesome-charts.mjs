import { charts } from "@qa-guru/allure-report-kit";

import {
  STABILITY_SKIP_STATUSES,
  STABILITY_STABILIZATION_PERIOD,
  STABILITY_THRESHOLD,
  TITLES,
} from "./constants.mjs";
import { buildLeadTiles } from "./overview-preset.mjs";
import { buildTestsTablePanels } from "./tests-table-panels.mjs";

/**
 * Awesome plugin charts.
 * Lead preset (QG + overview), then the rest.
 */
export function buildAwesomeCharts() {
  return [
    ...buildLeadTiles(),
    ...buildTestsTablePanels(),
    charts.testResultSeverities({ title: TITLES.testResultSeverities }),
    charts.statusDynamics({ title: TITLES.statusDynamics, limit: 20 }),
    charts.statusTransitions({ title: TITLES.statusTransitions, limit: 20 }),
    charts.testBaseGrowthDynamics({
      title: TITLES.testBaseGrowthDynamics,
      limit: 20,
    }),
    charts.coverageDiff({ title: TITLES.coverageDiff }),
    charts.successRateDistribution({ title: TITLES.successRateDistribution }),
    charts.problemsDistribution({ title: TITLES.problemsByEnvironment }),
    charts.stabilityDistribution({
      title: TITLES.stabilityByComponent,
      threshold: STABILITY_THRESHOLD,
      stabilizationPeriod: STABILITY_STABILIZATION_PERIOD,
      skipStatuses: [...STABILITY_SKIP_STATUSES],
      groupBy: "label-name:component",
    }),
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
    charts.durations({ title: TITLES.durations, groupBy: "none" }),
    charts.statusAgePyramid({ title: TITLES.statusAgePyramid, limit: 20 }),
  ];
}
