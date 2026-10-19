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
 * Empty-catalog state injected through the mock stand's WireMock scenario —
 * the seeded live backend always returns rows. On stands without the admin API
 * the test is skipped by assumption instead of failing.
 */
@Layer("ui")
@Epic("Home")
@Feature("Empty states")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Home empty items state (mock)")
@Isolated("WireMock scenarios are process-global")
@Execution(ExecutionMode.SAME_THREAD)
class HomeEmptyItemsTests extends TestBase {

    private boolean mockStandAvailable;

    @BeforeEach
    void requireMockStand() {
        mockStandAvailable = MockScenarios.available();
        Assumptions.assumeTrue(
                mockStandAvailable,
                "WireMock admin API is not exposed on this stand — state injection needs the mock profile");
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
    @DisplayName("Empty items response shows the empty state text")
    void emptyItemsResponseShowsEmptyState() {
        MockScenarios.setState("items", "empty");

        homePage.openPage()
                .shouldShowItemText("No items found.");
    }
}
