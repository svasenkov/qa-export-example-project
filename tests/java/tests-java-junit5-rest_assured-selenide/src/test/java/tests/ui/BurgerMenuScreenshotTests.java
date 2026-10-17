package tests.ui;

import tests.TestBase;
import annotations.Layer;
import annotations.SubSuite;
import annotations.Suite;
import helpers.ScreenshotHelper;
import helpers.ViewportHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Layer("ui")
@Severity(SeverityLevel.MINOR)
@Tag("ui")
@Tag("screenshot")
@Epic("Header")
@Feature("Burger menu")
@Suite("Burger menu")
@SubSuite("screenshot")
@ResourceLock(value = "screenshot-compare", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Burger menu screenshot")
class BurgerMenuScreenshotTests extends TestBase {

    private static final int VIEWPORT_HEIGHT = 900;

    @AfterEach
    void resetViewport() {
        ViewportHelper.resetViewport();
    }

    @ParameterizedTest(name = "Open burger menu matches screenshot at {0}px")
    @ValueSource(ints = {390, 768})
    @DisplayName("Open burger menu matches screenshot")
    void openMenuMatchesScreenshot(int viewportWidth) {
        ViewportHelper.setViewport(viewportWidth, VIEWPORT_HEIGHT);
        loginPage.openPage().header.openMenu();

        ScreenshotHelper.captureAndCompare(
                loginPage.header.menuPanel(),
                "burger-menu",
                viewportWidth,
                "burger-menu-" + viewportWidth);
    }
}
