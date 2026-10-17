import '@testing-library/jest-dom/vitest';
import { displayName, feature, suite } from 'allure-js-commons';
import { beforeEach } from 'vitest';

/** Suite/feature/name for TestOps Suites (epic/layer come from ALLURE_LABEL_* in npm test). */
beforeEach(async (ctx) => {
  const suiteName = ctx.task.suite?.name?.trim();
  if (suiteName) {
    await suite(suiteName);
    await feature(suiteName);
  }

  const testName = ctx.task.name?.trim();
  if (testName) {
    await displayName(testName);
  }
});
