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
@Epic("Authentication")
@Feature("Login form")
@Suite("Login")
@SubSuite("screenshot")
@ResourceLock(value = "screenshot-compare", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Login form screenshot")
class LoginScreenshotTests extends TestBase {

    private static final int VIEWPORT_HEIGHT = 900;

    @ParameterizedTest(name = "Login form matches screenshot at {0}px")
    @ValueSource(ints = {390, 768, 1280})
    @DisplayName("Login form matches screenshot")
    void loginFormMatchesScreenshot(int viewportWidth) {
        ViewportHelper.setViewport(viewportWidth, VIEWPORT_HEIGHT);
        loginPage.openPage();

        ScreenshotHelper.captureAndCompare(
                loginPage.loginFormPanel(),
                "login",
                viewportWidth,
                "login-" + viewportWidth);
    }
}
