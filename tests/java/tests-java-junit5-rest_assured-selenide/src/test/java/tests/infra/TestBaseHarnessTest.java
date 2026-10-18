package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;
import config.TestConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.SessionNotCreatedException;
import tests.TestBase;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TestBase} lifecycle under the default config: session claim retries go
 * through a scripted provider ({@link FakeWebDriverProvider}), so no real
 * browser is launched here.
 * Isolated + same-thread: the test mutates global Selenide Configuration.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Test base")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("TestBase session lifecycle")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class TestBaseHarnessTest extends TestBase {

    @BeforeAll
    static void routeBrowserToFakeProvider() {
        var stub = ConfigFactory.create(TestConfig.class, Map.of(
                "browser", FakeWebDriverProvider.class.getName(),
                "baseUrl", "http://fake.local",
                "remoteUrl", "",
                "browserVersion", "148",
                "browserSize", "1920x1280",
                "headless", "true"));
        configureBrowser(stub);
        assertEquals(FakeWebDriverProvider.class.getName(), Configuration.browser);
    }

    @Test
    @DisplayName("beforeEach claims a session through the configured provider")
    void sessionClaimedByProvider() {
        assertTrue(WebDriverRunner.hasWebDriverStarted(), "fake provider driver installed");
    }

    @Test
    @DisplayName("a refused session is retried before the test sees the driver")
    void sessionRetryThenSuccess() throws Exception {
        FakeWebDriverProvider.failNextSessions(1);
        WebDriverRunner.closeWebDriver();
        var before = System.currentTimeMillis();
        invokeEnsureBrowserSession();
        assertTrue(WebDriverRunner.hasWebDriverStarted(), "second attempt succeeds");
        assertTrue(System.currentTimeMillis() - before >= 3000, "retry sleeps before the next attempt");
    }

    @Test
    @DisplayName("session claim gives up after the configured attempts")
    void sessionRetryExhausted() {
        try {
            FakeWebDriverProvider.failNextSessions(10);
            WebDriverRunner.closeWebDriver();
            var error = assertThrows(InvocationTargetException.class,
                    TestBaseHarnessTest::invokeEnsureBrowserSession);
            assertInstanceOf(SessionNotCreatedException.class, error.getCause());
        } finally {
            FakeWebDriverProvider.failNextSessions(0);
        }
    }

    @Test
    @DisplayName("afterEach runs with no live session (attach guards see a closed driver)")
    void afterEachWithoutDriver() {
        WebDriverRunner.closeWebDriver();
        assertFalse(WebDriverRunner.hasWebDriverStarted(), "session closed before afterEach");
    }

    private static void invokeEnsureBrowserSession() throws Exception {
        var method = TestBase.class.getDeclaredMethod("ensureBrowserSession");
        method.setAccessible(true);
        method.invoke(null);
    }
}
