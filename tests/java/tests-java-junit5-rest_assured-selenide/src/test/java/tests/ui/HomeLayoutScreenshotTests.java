package tests.ui;

import annotations.Layer;
import helpers.ScreenshotHelper;
import helpers.ViewportHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import tests.TestBase;

@Layer("ui")
@Severity(SeverityLevel.MINOR)
@Tag("ui")
@Tag("screenshot")
@Epic("Home")
@Feature("Home layout")
@ResourceLock(value = "screenshot-compare", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Home layout screenshot")
class HomeLayoutScreenshotTests extends TestBase {

    private static final int VIEWPORT_WIDTH = 1280;
    private static final int VIEWPORT_HEIGHT = 900;

    @BeforeEach
    void openHome() {
        ViewportHelper.setViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
        homePage.openPage()
                .shouldShowLayoutAndHealth()
                .shouldShowHealthText("service: " + config.apiHealthService())
                .shouldShowItemText("Alpha");
        homePage.header.shouldShowEmbeddedHeader();
    }

    @Test
    @DisplayName("Home layout matches screenshot at 1280px")
    void homeLayoutMatchesScreenshot() {
        ScreenshotHelper.captureAndCompare(
                homePage.layoutPanel(),
                "home-layout",
                VIEWPORT_WIDTH,
                "home-layout-" + VIEWPORT_WIDTH);
    }
}
