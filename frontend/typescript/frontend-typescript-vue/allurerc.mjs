import { createAllureConfig } from "../../../tools/allure/create-config.mjs";

export default createAllureConfig({
  slug: "frontend-typescript-vue",
  resultsDir: new URL("./allure-results/", import.meta.url),
  historyFile: new URL("./history.jsonl", import.meta.url),
  variables: { Framework: "Vitest + Testing Library", Report: "Allure 3" },
});
