package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.Configuration;
import config.ConfigReader;
import config.TestConfig;
import helpers.LocalChromePin;
import org.aeonbits.owner.ConfigFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tests.AllureMeta;
import tests.TestBase;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Local browser pin (infra-frontend): the suite is not Chrome-only.
 * <p>
 * Living helper is {@link LocalChromePin} (Chrome for Testing). {@code TestBase}
 * applies it only when {@code remoteUrl} is empty and {@code browser=chrome}.
 * Selenoid uses the hub image tag; {@code -Dbrowser=firefox} skips the pin.
 * Do not grow this helper into a multi-browser installer until there is a
 * matching pin + screenshot folder (a sibling {@code firefox-*/} beside
 * {@code chrome-148/} — {@code ScreenshotHelper} still takes the major segment
 * from the Chrome pin).
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Local browser pin")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("Local browser pin")
@Execution(ExecutionMode.SAME_THREAD)
class LocalBrowserPinTest extends AllureMeta {

    private static String major(String version) {
        return version.split("\\.")[0];
    }

    @Test
    @DisplayName("remote browser setup applies the configured hub and browser version")
    void remoteBrowserSetupAppliesHubAndVersion() {
        var remoteConfig = ConfigFactory.create(TestConfig.class,
                Map.of("remoteUrl", "https://selenium.example.test/wd/hub"));
        var baseUrl = Configuration.baseUrl;
        var browser = Configuration.browser;
        var browserSize = Configuration.browserSize;
        var headless = Configuration.headless;
        var timeout = Configuration.timeout;
        var remote = Configuration.remote;
        var browserVersion = Configuration.browserVersion;
        try {
            TestBase.configureBrowser(remoteConfig);
            assertAll(
                    () -> assertEquals(remoteConfig.remoteUrl(), Configuration.remote),
                    () -> assertEquals(remoteConfig.browserVersion(), Configuration.browserVersion));
        } finally {
            Configuration.baseUrl = baseUrl;
            Configuration.browser = browser;
            Configuration.browserSize = browserSize;
            Configuration.headless = headless;
            Configuration.timeout = timeout;
            Configuration.remote = remote;
            Configuration.browserVersion = browserVersion;
        }
    }

    @Test
    @DisplayName("pinnedVersion is a full Chrome for Testing build number")
    void pinnedVersionIsFullBuildNumber() {
        assertTrue(LocalChromePin.pinnedVersion().matches("\\d+\\.\\d+\\.\\d+\\.\\d+"),
                "chrome-for-testing.properties must pin an exact build, got: " + LocalChromePin.pinnedVersion());
    }

    @ParameterizedTest(name = "{0} uses the pinned browser major")
    @ValueSource(strings = {"ci", "mock", "stage", "prod"})
    @DisplayName("configured browserVersion stays on the pinned major")
    void configuredBrowserVersionMatchesPin(String stand) throws IOException {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/config/" + stand + ".properties")) {
            assertNotNull(input, "Missing browser config for " + stand);
            properties.load(input);
        }
        var browserVersion = properties.getProperty("browserVersion", ConfigReader.testConfig.browserVersion());
        assertEquals(major(LocalChromePin.pinnedVersion()), major(browserVersion),
                "browserVersion and chrome-for-testing.properties drifted apart for " + stand);
    }

    @Test
    @DisplayName("apply rejects a browserVersion from another major")
    void applyRejectsForeignMajor() {
        var foreignMajor = Integer.parseInt(major(LocalChromePin.pinnedVersion())) + 1;
        var error = assertThrows(IllegalStateException.class,
                () -> LocalChromePin.apply(String.valueOf(foreignMajor)));
        assertTrue(error.getMessage().contains("pinned build is"), error.getMessage());
    }

    @Test
    @DisplayName("apply refuses to fall back to system Chrome")
    void applyRejectsBlankBrowserVersion() {
        var error = assertThrows(IllegalStateException.class, () -> LocalChromePin.apply(" "));
        assertTrue(error.getMessage().contains("browserVersion is required"), error.getMessage());
    }
}
