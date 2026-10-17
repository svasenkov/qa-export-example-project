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

/** @type {import("@qa-guru/allure-report-kit").OverviewPreset} */
export const OVERVIEW_PRESET = {
  ...KIT_OVERVIEW_PRESET,
  qualityGates: KIT_OVERVIEW_PRESET.qualityGates.filter((gate) => gate.id === "allureQualityGate"),
  titles: {
    currentStatus: TITLES.currentStatus,
    durationDynamics: TITLES.durationDynamics,
    testingPyramid: TITLES.testingPyramid,
    durations: TITLES.durationsByLayer,
  },
  pyramidLayers: [...PYRAMID_LAYERS],
};

export function buildLeadTiles({ sonarFile, ...options } = {}) {
  const gatePanels = buildGatePanels({ layout: "2x1", sonarFile });
  return presets.fromLead({
    preset: {
      ...OVERVIEW_PRESET,
      qualityGates: KIT_OVERVIEW_PRESET.qualityGates.filter((gate) => gatePanels[gate.id]),
    },
    layers: [...PYRAMID_LAYERS],
    gatePanels,
    ...options,
  });
}
