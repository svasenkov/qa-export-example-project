/// <reference types="vitest/config" />
import AllureReporter from 'allure-vitest/reporter';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  base: './',
  plugins: [vue()],
  server: {
    fs: {
      allow: [__dirname],
    },
  },
  test: {
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,js,vue}', 'vendor/ds/js/app-base.js'],
      exclude: ['src/test/**', '**/*.d.ts'],
      reporter: ['text', 'lcov', 'json', 'json-summary'],
      reportsDirectory: process.env.COVERAGE_DIR || 'coverage',
      reportOnFailure: true,
      thresholds: { lines: 100, statements: 100, functions: 100, branches: 100 },
    },
    environment: 'jsdom',
    globals: true,
    // Instrumented runs queue microtasks behind v8 serialization; the default
    // 5s is not enough for user-event flows on a loaded runner.
    testTimeout: 15000,
    setupFiles: ['./src/test/setup.ts', 'allure-vitest/setup'],
    include: ['src/test/**/*.test.{ts,tsx}'],
    css: true,
    // Reporter instance, not the `['allure-vitest/reporter', …]` string form:
    // that specifier can resolve to an allure-vitest hoisted above this module,
    // which then injects a second Vitest runtime (setup + runner) into the worker.
    reporters: ['default', new AllureReporter({ resultsDir: 'allure-results' })],
  },
});
