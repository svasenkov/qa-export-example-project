#!/usr/bin/env bash
# JaCoCo union build: the measured support slice is exercised by several
# configuration variants, not one tag selection. Each variant run writes its
# own exec file into build/jacoco/union/ and the report/verification merge
# them — see build.gradle (unionExecFiles).
#
# Stand addresses come from the module config files; CI_BASE_URL/CI_API_URL
# and MOCK_BASE_URL/MOCK_API_URL override them for an isolated stand without
# editing config/*.properties.
#
# Usage from a clean build directory:
#   bash scripts/jacoco-union.sh
#   CI_BASE_URL=http://localhost:19821 CI_API_URL=http://localhost:18831/ \
#   MOCK_BASE_URL=http://localhost:19911 MOCK_API_URL=http://localhost:19911/ \
#     bash scripts/jacoco-union.sh
set -euo pipefail
cd "$(dirname "$0")/.."

UNION=build/jacoco/union
mkdir -p "$UNION"

CI_ARGS=(-Denv=ci "-DbaseUrl=${CI_BASE_URL:-http://localhost:9821}"
         "-DapiBaseUrl=${CI_API_URL:-http://localhost:8831/}")
MOCK_ARGS=(-Denv=mock "-DbaseUrl=${MOCK_BASE_URL:-http://localhost:9911}"
           "-DapiBaseUrl=${MOCK_API_URL:-http://localhost:9911/}")
# Smallest real-browser class exercising TestBase lifecycle hooks.
BROWSER_PROBE=(--tests tests.e2e.LoginTests)

save_exec() { cp build/jacoco/test.exec "$UNION/$1.exec"; }

# 1. ci — full CI set on the live stand: api + e2e + non-screenshot ui + infra
#    harness tests in one run (includeTags unset, screenshot/manual excluded).
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DexcludeTags=screenshot,manual
save_exec ci

# 2. mock — browser UI checks against the WireMock gateway.
./gradlew test --rerun-tasks "${MOCK_ARGS[@]}" -DincludeTags=ui -DexcludeTags=screenshot
save_exec mock

# 3. attach — every attachment + console logging + Selenide listener enabled.
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DincludeTags=e2e "${BROWSER_PROBE[@]}" \
  -DattachBrowserConsoleLogs=true -DattachPageSource=true -DattachLastScreenshot=true \
  -DenableVideo=true -DattachVideo=true -DenableHar=true -DattachHarLogs=true \
  -DenableAllureSelenideListener=true -DlogToConsole=true -DselenideLogToConsole=true
save_exec attach

# 4. quiet — Allure results disabled entirely (allureReportMode=none arm).
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DincludeTags=e2e "${BROWSER_PROBE[@]}" \
  -DallureReportMode=none
save_exec quiet

# 5. keep — one WebDriver session kept across tests, sequentially, so
#    ensureBrowserSession hits the reused-session early return.
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DincludeTags=e2e "${BROWSER_PROBE[@]}" \
  -DcloseBrowserAfterEach=false -Djunit.jupiter.execution.parallel.enabled=false
save_exec keep

# 6. skipopen — no blank open in beforeEach and no close in afterAll.
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DincludeTags=e2e "${BROWSER_PROBE[@]}" \
  -DskipBlankOpen=true -DcloseBrowserAfterAll=false
save_exec skipopen

# 7. harvideo — HAR requested without enableHar, video enabled but not
#    attached. The infra probe runs afterEach without a live driver, covering
#    the attachHarLogs && !hasWebDriverStarted arm.
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DexcludeTags=screenshot,manual \
  "${BROWSER_PROBE[@]}" --tests tests.infra.TestBaseHarnessTest \
  -DenableHar=false -DattachHarLogs=true -DenableVideo=true -DattachVideo=false
save_exec harvideo

# 8. conlog-off — logToConsole=false arm (default is true).
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DexcludeTags=screenshot,manual \
  "${BROWSER_PROBE[@]}" -DlogToConsole=false
save_exec conlog-off

# 9. report-off — selenideLogToConsole=false evaluated under logToConsole=true.
./gradlew test --rerun-tasks "${CI_ARGS[@]}" -DexcludeTags=screenshot,manual \
  "${BROWSER_PROBE[@]}" -DselenideLogToConsole=false
save_exec report-off

# Union report + 100% verification over all exec files; no tests re-run here.
./gradlew jacocoTestReport jacocoTestCoverageVerification -x test
echo "union exec files:" && ls -1 "$UNION"