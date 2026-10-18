package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.Configuration;
import config.TestConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;
import tests.AllureMeta;
import tests.TestBase;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TestBase#configureBrowser} matrix — remote / non-chrome / chrome+HAR
 * capability paths on stub configs. Isolated + restores: touches the global
 * Selenide {@link Configuration}.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Browser configuration")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("configureBrowser")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class ConfigureBrowserTest extends AllureMeta {

    private static TestConfig stubConfig(Map<String, Object> overrides) {
        return ConfigFactory.create(TestConfig.class, overrides);
    }

    @AfterEach
    void restoreConfiguration() {
        TestBase.configureBrowser(config.ConfigReader.testConfig);
    }

    @Test
    @DisplayName("remote hub config skips the local chrome pin")
    void remoteSkipsPin() {
        TestBase.configureBrowser(stubConfig(Map.of(
                "remoteUrl", "https://hub.example.test/wd/hub",
                "browser", "chrome",
                "browserVersion", "148")));
        assertEquals("https://hub.example.test/wd/hub", Configuration.remote);
        assertNotNull(Configuration.browserCapabilities);
    }

    @Test
    @DisplayName("non-chrome local browser gets plain capabilities and no pin")
    void nonChromeLocalSkipsPin() {
        TestBase.configureBrowser(stubConfig(Map.of(
                "remoteUrl", "",
                "browser", "firefox",
                "browserVersion", "1.0")));
        assertNull(Configuration.remote);
        assertNotNull(Configuration.browserCapabilities);
    }

    @Test
    @DisplayName("local chrome + HAR merges performance logging into capabilities")
    void localChromeWithHar() {
        var withHar = stubConfig(Map.of(
                "remoteUrl", "",
                "browser", "chrome",
                "browserVersion", "148",
                "enableHar", "true",
                "attachHarLogs", "false"));
        TestBase.configureBrowser(withHar);
        var caps = (ChromeOptions) Configuration.browserCapabilities;
        assertNotNull(caps.getCapability("goog:loggingPrefs"), "har logging enabled");

        var withoutHar = stubConfig(Map.of(
                "remoteUrl", "",
                "browser", "chrome",
                "browserVersion", "148",
                "enableHar", "false",
                "attachHarLogs", "false"));
        TestBase.configureBrowser(withoutHar);
        var caps2 = (ChromeOptions) Configuration.browserCapabilities;
        assertNull(caps2.getCapability("goog:loggingPrefs"), "no har logging");
        assertTrue(caps2.asMap().toString().contains("--no-sandbox"), "CI sandbox flag");
    }
}
