/**
 * Ethalon overview preset — kit SSOT + profile titles/layers only.
 *
 * Order: Allure and an optional measured Sonar gate, then overview charts.
 * Tiles/gates/renderers come from `@qa-guru/allure-report-kit/presets/overview-preset`.
 */
import { presets } from "@qa-guru/allure-report-kit";
import { OVERVIEW_PRESET as KIT_OVERVIEW_PRESET } from "@qa-guru/allure-report-kit/presets/overview-preset";

import { PYRAMID_LAYERS, TITLES } from "./constants.mjs";
import { buildGatePanels } from "./quality-gate-panels.mjs";

const gatePanels = buildGatePanels({ layout: "2x1" });

/** @type {import("@qa-guru/allure-report-kit").OverviewPreset} */
export const OVERVIEW_PRESET = {
  ...KIT_OVERVIEW_PRESET,
  qualityGates: KIT_OVERVIEW_PRESET.qualityGates.filter((gate) => gatePanels[gate.id]),
  titles: {
    currentStatus: TITLES.currentStatus,
    durationDynamics: TITLES.durationDynamics,
    testingPyramid: TITLES.testingPyramid,
    durations: TITLES.durationsByLayer,
  },
  pyramidLayers: [...PYRAMID_LAYERS],
};

/**
 * Overview charts without quality gates.
 * @param {import("@qa-guru/allure-report-kit").FromOverviewOptions} [options]
 */
export function buildOverviewTiles(options = {}) {
  return presets.fromOverviewCharts({
    preset: OVERVIEW_PRESET,
    layers: [...PYRAMID_LAYERS],
    ...options,
  });
}

/**
 * Lead section: quality gates, then overview charts.
 * @param {import("@qa-guru/allure-report-kit").FromLeadOptions} [options]
 */
export function buildLeadTiles(options = {}) {
  return presets.fromLead({
    preset: OVERVIEW_PRESET,
    layers: [...PYRAMID_LAYERS],
    gatePanels,
    ...options,
  });
}
