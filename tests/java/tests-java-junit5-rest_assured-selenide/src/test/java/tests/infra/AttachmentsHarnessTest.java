package tests.infra;

import allure.Attachments;
import annotations.Layer;
import com.codeborne.selenide.WebDriverRunner;
import config.TestConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import tests.AllureMeta;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Attachments} — attachment guards and driver-error fallbacks against a
 * scripted driver. The video/har config branches that require a non-default
 * config run in the {@code infra-attach} Gradle fork. Isolated + same-thread:
 * {@code harLogsConfigBranches} swaps the global Allure lifecycle, which would
 * otherwise leak "simulated allure failure" into parallel classes.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Allure attachments")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("Attachments")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class AttachmentsHarnessTest extends AllureMeta {

    @AfterEach
    void closeFake() {
        if (WebDriverRunner.hasWebDriverStarted()) {
            WebDriverRunner.closeWebDriver();
        }
    }

    @Test
    @DisplayName("no driver → null attachments and no-op logs")
    void withoutDriver() {
        assertNull(Attachments.screenshot("s"));
        assertNull(Attachments.pageSource());
        assertNull(Attachments.video());
        Attachments.browserConsoleLogs();
        Attachments.harLogs();
    }

    @Test
    @DisplayName("screenshot and page source come from the driver")
    void driverBackedAttachments() {
        var driver = new FakeWebDriver()
                .screenshot(new byte[]{1, 2, 3})
                .pageSource("<p>hi</p>");
        WebDriverRunner.setWebDriver(driver);

        assertEquals(3, Attachments.screenshot("s").length);
        assertTrue(new String(Attachments.pageSource()).contains("hi"));
    }

    @Test
    @DisplayName("driver failures degrade to null instead of masking the test")
    void driverFailures() {
        var driver = new FakeWebDriver()
                .failOnScreenshot(new RuntimeException("no shot"))
                .failOnPageSource(new RuntimeException("no source"))
                .failOnPerformanceLogs(new RuntimeException("no logs"))
                .failOnSessionId(new RuntimeException("no session"));
        WebDriverRunner.setWebDriver(driver);

        assertNull(Attachments.screenshot("s"));
        assertNull(Attachments.pageSource());
        assertNull(Attachments.video());
        Attachments.browserConsoleLogs();
        Attachments.harLogs();
    }

    @Test
    @DisplayName("console logs attach via text, har via collect+viewer")
    void logAttachments() {
        var driver = new FakeWebDriver()
                .performanceLogs(new LogEntries(List.of(
                        new LogEntry(Level.INFO, 0L, "{\"message\":{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"r\",\"timestamp\":1.0,\"request\":{\"url\":\"https://x/\",\"method\":\"GET\",\"headers\":{}}}}}"))));
        WebDriverRunner.setWebDriver(driver);

        Attachments.browserConsoleLogs();
        Attachments.harLogs();
        assertNotNull(Attachments.text("t", "msg"));
        assertEquals("msg", Attachments.text("t", "msg"));
    }

    @Test
    @DisplayName("harLogs is skipped when the driver produces no har")
    void harLogsEmpty() {
        WebDriverRunner.setWebDriver(new FakeWebDriver().performanceLogs(new LogEntries(List.of())));
        Attachments.harLogs();
    }

    // --- config-seam coverage ---------------------------------------------------

    private static TestConfig config(java.util.Map<String, Object> overrides) {
        return org.aeonbits.owner.ConfigFactory.create(TestConfig.class, overrides);
    }

    private static TestConfig nullFolderConfig() {
        var defaults = org.aeonbits.owner.ConfigFactory.create(TestConfig.class);
        return (TestConfig) Proxy.newProxyInstance(
                AttachmentsHarnessTest.class.getClassLoader(),
                new Class<?>[]{TestConfig.class},
                (proxy, method, args) -> "videoFolder".equals(method.getName())
                        ? null : method.invoke(defaults, args));
    }

    @Test
    @DisplayName("video() guards null/blank folder and session failures")
    void videoConfigBranches() {
        // folder guards are only reached with a live driver
        WebDriverRunner.setWebDriver(new FakeWebDriver());
        assertNull(Attachments.video(nullFolderConfig()), "null folder → no attachment");
        assertNull(Attachments.video(config(java.util.Map.of("videoFolder", "  "))),
                "blank folder → no attachment");

        var html = Attachments.video(config(java.util.Map.of("videoFolder", "http://videos.test/")));
        assertNotNull(html);
        assertTrue(html.contains("videos.test"), html);
        assertTrue(html.contains(".mp4"), html);

        var noSlash = Attachments.video(config(java.util.Map.of("videoFolder", "http://videos.test")));
        assertTrue(noSlash.contains("videos.test/"), noSlash);

        var broken = new FakeWebDriver().failOnSessionId(new RuntimeException("no session"));
        WebDriverRunner.setWebDriver(broken);
        assertNull(Attachments.video(config(java.util.Map.of("videoFolder", "http://v/"))),
                "session failure degrades to null");
    }

    @Test
    @DisplayName("harLogs honors browser support and attaches raw+viewer")
    void harLogsConfigBranches() {
        // unsupported browser → early return
        WebDriverRunner.setWebDriver(new FakeWebDriver());
        Attachments.harLogs(config(java.util.Map.of("browser", "firefox")));

        // supported browser + scripted performance log → captures HAR
        var driver = new FakeWebDriver()
                .performanceLogs(new LogEntries(List.of(
                        new LogEntry(Level.INFO, 0L, "{\"message\":{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"r\",\"timestamp\":1.0,\"request\":{\"url\":\"https://x/\",\"method\":\"GET\",\"headers\":{}}}}}"))));
        WebDriverRunner.setWebDriver(driver);
        Attachments.harLogs(config(java.util.Map.of("browser", "chrome")));
        Attachments.harLogs(config(java.util.Map.of("browser", "edge")));

        // collect throwing → outer catch keeps the test green
        var broken = new FakeWebDriver().failOnPerformanceLogs(new RuntimeException("no logs"));
        WebDriverRunner.setWebDriver(broken);
        Attachments.harLogs(config(java.util.Map.of("browser", "chrome")));

        // attachment pipeline throwing → same outer catch
        // (Allure.attachment → addAttachmentAsStep → lifecycle.addAttachmentStep)
        var original = io.qameta.allure.Allure.getLifecycle();
        io.qameta.allure.Allure.setLifecycle(new io.qameta.allure.AllureLifecycle() {
            @Override
            public void addAttachmentStep(String name, String type,
                    java.io.InputStream stream, io.qameta.allure.AttachmentOptions options) {
                throw new RuntimeException("simulated allure failure");
            }
        });
        try {
            WebDriverRunner.setWebDriver(driver);
            Attachments.harLogs(config(java.util.Map.of("browser", "chrome")));
        } finally {
            io.qameta.allure.Allure.setLifecycle(original);
        }
    }

    @Test
    @DisplayName("class is instantiable (non-static contract)")
    void instantiable() {
        assertNotNull(new Attachments());
    }
}
