/**
 * Tests table panel (kit `panels.testsTable` pattern).
 */
import { panels } from "@qa-guru/allure-report-kit";

import { TITLES } from "./constants.mjs";
import { loadTestsTableFromRun } from "./tests-table-from-run.mjs";

/**
 * @returns {import("@qa-guru/allure-report-kit").KitCustomPanel[]}
 */
export function buildTestsTablePanels() {
  return [
    panels.testsTable({
      id: "testsTable",
      title: TITLES.testsTable,
      layout: "2x2",
      dots: false,
      data: loadTestsTableFromRun({
        resultsDir: new URL("../build/allure-results", import.meta.url),
        historyFile: new URL("../history.jsonl", import.meta.url),
      }),
    }),
  ];
}
