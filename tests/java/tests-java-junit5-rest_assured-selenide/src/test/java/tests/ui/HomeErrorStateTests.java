package tests.ui;

import tests.TestBase;
import annotations.Layer;
import api.MockScenarios;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * UI error states that a healthy live backend can never produce — the mock stand's WireMock
 * scenarios inject them. On stands without the admin API (ci/prod) these tests are skipped
 * by assumption instead of failing: same suite, honest report.
 */
@Layer("ui")
@Epic("Home")
@Feature("Error states")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Home error states (mock)")
@Isolated("WireMock scenarios are process-global")
@Execution(ExecutionMode.SAME_THREAD)
class HomeErrorStateTests extends TestBase {

    private boolean mockStandAvailable;

    @BeforeEach
    void requireMockStand() {
        mockStandAvailable = MockScenarios.available();
        Assumptions.assumeTrue(
                mockStandAvailable,
                "WireMock admin API is not exposed on this stand — error injection needs the mock profile");
    }

    @AfterEach
    void resetScenarios() {
        // @AfterEach also runs when the assumption above aborted the test — guard the admin call.
        if (mockStandAvailable) {
            MockScenarios.resetAll();
        }
    }

    @Test
    @Tag("ui")
    @Tag("mock")
    @Tag("negative")
    @DisplayName("Items API failure shows a readable error, not a blank page")
    void itemsApiFailureShowsReadableError() {
        MockScenarios.setState("items", "error");

        homePage.openPage()
                .shouldShowItemsError("✗ items: HTTP 500");
    }

    @Test
    @Tag("ui")
    @Tag("mock")
    @Tag("negative")
    @DisplayName("Health API failure shows a readable error in the health panel")
    void healthApiFailureShowsReadableError() {
        MockScenarios.setState("health", "error");

        homePage.openPage()
                .shouldShowHealthError("✗ health: HTTP 500");
    }
}
