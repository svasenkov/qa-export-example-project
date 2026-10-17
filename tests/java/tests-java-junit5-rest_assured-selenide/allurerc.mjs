import { createAllureConfig } from "../../../tools/allure/create-config.mjs";

export default createAllureConfig({
  slug: "autotests-ai-multistack-app",
  resultsDir: new URL("./build/allure-results/", import.meta.url),
  historyFile: new URL("./history.jsonl", import.meta.url),
  variables: { Framework: "JUnit + Rest Assured + Selenide", Report: "Allure 3" },
  sonarFile: process.env.SONAR_QUALITY_GATE_JSON,
});
