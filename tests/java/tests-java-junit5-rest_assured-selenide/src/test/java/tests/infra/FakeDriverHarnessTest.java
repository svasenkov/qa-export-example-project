package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;
import helpers.BrowserSessionHelper;
import helpers.ViewportHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import tests.AllureMeta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness contracts that need a live {@link WebDriverRunner} session but no real
 * browser: a scripted {@link FakeWebDriver} is installed per test. The fake
 * reproduces the observable WebDriver contract (elements, alerts, storage,
 * CDP), so PageObjects and helpers run their real code paths.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Driver-backed helpers")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("Driver-backed harness")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class FakeDriverHarnessTest extends AllureMeta {

    @AfterEach
    void closeFake() {
        if (WebDriverRunner.hasWebDriverStarted()) {
            WebDriverRunner.closeWebDriver();
        }
    }

    private static <T extends WebDriver> T install(T driver) {
        WebDriverRunner.setWebDriver(driver);
        return driver;
    }

    // --- BrowserSessionHelper -------------------------------------------------

    @Test
    @DisplayName("resetPageState no-ops without a driver")
    void resetPageStateWithoutDriver() {
        BrowserSessionHelper.resetPageState();
    }

    @Test
    @DisplayName("resetPageState skips web storage for non-http urls and failing drivers")
    void resetPageStateGuards() {
        install(new FakeWebDriver().url("about:blank"));
        BrowserSessionHelper.resetPageState();

        install(new FakeWebDriver().url("chrome://settings"));
        BrowserSessionHelper.resetPageState();

        install(new FakeWebDriver().url(null));
        BrowserSessionHelper.resetPageState();

        // https → full path incl. viewport reset: needs a CDP-capable fake
        install(new FakeWebDriver.FakeCdpDriver().url("https://secure.local/"));
        BrowserSessionHelper.resetPageState();

        install(new FakeWebDriver().failOnUrl(new RuntimeException("dead")));
        BrowserSessionHelper.resetPageState();
    }

    @Test
    @DisplayName("resetPageState clears storage, cookies and refreshes on http pages")
    void resetPageStateFullPath() {
        var driver = install(new FakeWebDriver.FakeCdpDriver());
        driver.url("http://stand.local/page");
        BrowserSessionHelper.resetPageState();
        assertTrue(driver.cdpCommands().contains("Emulation.clearDeviceMetricsOverride"),
                "viewport reset goes through CDP");
    }

    @Test
    @DisplayName("storage helpers execute against the live driver")
    void storageHelpers() {
        install(new FakeWebDriver());
        BrowserSessionHelper.clearLocalStorage();
        BrowserSessionHelper.clearSessionStorage();
        BrowserSessionHelper.clearCookies();
        BrowserSessionHelper.switchToDefaultContent();
    }

    @Test
    @DisplayName("switchToDefaultContent tolerates drivers that fail")
    void switchToDefaultContentFailure() {
        var driver = install(new FakeWebDriver() {
            @Override
            public TargetLocator switchTo() {
                throw new RuntimeException("no frames");
            }
        });
        BrowserSessionHelper.switchToDefaultContent();
    }

    // --- ViewportHelper --------------------------------------------------------

    @Test
    @DisplayName("resetViewport no-ops without a driver")
    void resetViewportWithoutDriver() {
        ViewportHelper.resetViewport();
    }

    @Test
    @DisplayName("setViewport and resetViewport drive CDP on a cdp-capable driver")
    void viewportViaCdp() {
        var driver = install(new FakeWebDriver.FakeCdpDriver());
        ViewportHelper.setViewport(390, 844);
        ViewportHelper.resetViewport();
        assertEquals(
                java.util.List.of(
                        "Emulation.clearDeviceMetricsOverride",
                        "Emulation.setDeviceMetricsOverride",
                        "Emulation.clearDeviceMetricsOverride"),
                driver.cdpCommands());
    }

    @Test
    @DisplayName("setViewport falls back to window resize on a plain driver")
    void viewportFallsBackToWindowResize() {
        var driver = install(new PlainFakeWebDriver());
        var original = Configuration.browserSize;
        try {
            Configuration.browserSize = "1920x1280";
            ViewportHelper.setViewport(390, 844);
            assertEquals(new Dimension(390, 844), driver.windowSize);
            ViewportHelper.resetViewport();
            assertEquals(new Dimension(1920, 1280), driver.windowSize,
                    "reset uses Configuration.browserSize");
        } finally {
            Configuration.browserSize = original;
        }
    }

    @Test
    @DisplayName("setViewport opens a session when no driver is running")
    void setViewportOpensSession() {
        var original = Configuration.browser;
        try {
            Configuration.browser = FakeWebDriverProvider.class.getName();
            ViewportHelper.setViewport(390, 844);
            assertTrue(WebDriverRunner.hasWebDriverStarted(),
                    "open(\"about:blank\") launches the provider driver");
        } finally {
            Configuration.browser = original;
        }
    }

    @Test
    @DisplayName("resolveCdp finds HasCdp on an augmented remote driver")
    void viewportViaAugmenter() {
        // Remote driver WITH se:cdp but no HasCdp — Augmenter wraps it into a
        // HasCdp proxy; dispatch then fails on the dead endpoint.
        var driver = install(new FakeWebDriver()
                .withCapability("se:cdp", "ws://127.0.0.1:1/devtools"));
        assertThrows(RuntimeException.class,
                () -> ViewportHelper.setViewport(390, 844),
                "augmented CDP dispatch reaches the dead endpoint and fails");

        // capabilities themselves unreadable → augment throws → window resize fallback
        var broken = install(new FakeWebDriver() {
            @Override
            public org.openqa.selenium.Capabilities getCapabilities() {
                throw new RuntimeException("no caps");
            }
        });
        ViewportHelper.setViewport(1024, 768);
        assertEquals(new Dimension(1024, 768), broken.manage().window().getSize());
    }

    @Test
    @DisplayName("resetViewport rejects a malformed browserSize")
    void resetViewportRejectsBadSize() {
        install(new PlainFakeWebDriver());
        var original = Configuration.browserSize;
        try {
            Configuration.browserSize = "wide";
            assertThrows(IllegalStateException.class, ViewportHelper::resetViewport);
        } finally {
            Configuration.browserSize = original;
        }
    }

    /**
     * Plain WebDriver — no RemoteWebDriver, no CDP. Implements HasCapabilities so
     * the Augmenter evaluates the caps (matching no provider) and returns the
     * driver unchanged — the augmenter path must degrade to window resize.
     */
    private static final class PlainFakeWebDriver implements WebDriver,
            org.openqa.selenium.JavascriptExecutor, org.openqa.selenium.TakesScreenshot,
            org.openqa.selenium.HasCapabilities {
        Dimension windowSize = new Dimension(1920, 1280);

        @Override
        public org.openqa.selenium.Capabilities getCapabilities() {
            return new org.openqa.selenium.ImmutableCapabilities();
        }

        @Override
        public void get(String url) {
        }

        @Override
        public String getCurrentUrl() {
            return "http://stand.local/";
        }

        @Override
        public String getTitle() {
            return "";
        }

        @Override
        public java.util.List<org.openqa.selenium.WebElement> findElements(org.openqa.selenium.By by) {
            return java.util.List.of();
        }

        @Override
        public org.openqa.selenium.WebElement findElement(org.openqa.selenium.By by) {
            return new FakeWebElement();
        }

        @Override
        public String getPageSource() {
            return "";
        }

        @Override
        public void close() {
        }

        @Override
        public void quit() {
        }

        @Override
        public java.util.Set<String> getWindowHandles() {
            return java.util.Set.of();
        }

        @Override
        public String getWindowHandle() {
            return "w";
        }

        @Override
        public TargetLocator switchTo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Navigation navigate() {
            return new Navigation() {
                @Override public void back() { }
                @Override public void forward() { }
                @Override public void to(String url) { }
                @Override public void to(java.net.URL url) { }
                @Override public void refresh() { }
            };
        }

        @Override
        public Options manage() {
            return new Options() {
                @Override public void addCookie(org.openqa.selenium.Cookie cookie) { }
                @Override public void deleteCookieNamed(String name) { }
                @Override public void deleteCookie(org.openqa.selenium.Cookie cookie) { }
                @Override public void deleteAllCookies() { }
                @Override public java.util.Set<org.openqa.selenium.Cookie> getCookies() { return java.util.Set.of(); }
                @Override public org.openqa.selenium.Cookie getCookieNamed(String name) { return null; }
                @Override public Timeouts timeouts() { throw new UnsupportedOperationException(); }
                @Override public Window window() {
                    return new Window() {
                        @Override public Dimension getSize() { return windowSize; }
                        @Override public void setSize(Dimension targetSize) { windowSize = targetSize; }
                        @Override public org.openqa.selenium.Point getPosition() { return new org.openqa.selenium.Point(0, 0); }
                        @Override public void setPosition(org.openqa.selenium.Point p) { }
                        @Override public void maximize() { }
                        @Override public void minimize() { }
                        @Override public void fullscreen() { }
                    };
                }
                @Override public org.openqa.selenium.logging.Logs logs() { throw new UnsupportedOperationException(); }
            };
        }

        @Override
        public Object executeScript(String script, Object... args) {
            return null;
        }

        @Override
        public Object executeAsyncScript(String script, Object... args) {
            return null;
        }

        @Override
        public <X> X getScreenshotAs(org.openqa.selenium.OutputType<X> target) {
            return target.convertFromPngBytes(new byte[0]);
        }
    }
}
