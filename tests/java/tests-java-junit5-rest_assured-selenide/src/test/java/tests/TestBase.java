package tests;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;
import com.codeborne.selenide.logevents.SimpleReport;

import allure.AllureSelenideListeners;
import allure.Attachments;
import annotations.Framework;
import annotations.Scope;
import config.ConfigReader;
import config.TestConfig;
import helpers.BrowserSessionHelper;
import helpers.HarCapture;
import helpers.LocalChromePin;
import pages.HomePage;
import pages.LoginPage;
import pages.RegisterPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.chrome.ChromeOptions;

import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.sleep;


@Scope("browser")
@Framework("selenide")
public class TestBase extends AllureMeta {

    protected final HomePage homePage = new HomePage();
    protected final LoginPage loginPage = new LoginPage();
    protected final RegisterPage registerPage = new RegisterPage();

    protected static final TestConfig config = ConfigReader.testConfig;
    private static final SimpleReport selenideReport = new SimpleReport();

    private static final int SESSION_ATTEMPTS = 3;
    private static final long SESSION_RETRY_DELAY_MS = 3_000;

    private static boolean allureResultsEnabled() {
        return !"none".equals(config.allureReportMode());
    }

    @BeforeAll
    static void setup() {
        if (config.logToConsole()) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", config.rootLogLevel());
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off");
        }

        configureBrowser(config);

        if (AllureSelenideListeners.isGloballyEnabled(config)) {
            AllureSelenideListeners.setEnabled(true);
        }
    }

    public static void configureBrowser(TestConfig browserConfig) {
        Configuration.baseUrl = browserConfig.baseUrl();
        Configuration.browser = browserConfig.browser();
        Configuration.browserSize = browserConfig.browserSize();
        Configuration.remote = browserConfig.remoteUrl().isBlank() ? null : browserConfig.remoteUrl();
        Configuration.browserVersion = browserConfig.browserVersion();
        Configuration.headless = browserConfig.headless();
        Configuration.timeout = 5_000;
        if (browserConfig.remoteUrl().isBlank() && "chrome".equals(browserConfig.browser())) {
            LocalChromePin.apply(browserConfig.browserVersion());
            Configuration.browserCapabilities = localChromeCapabilities(browserConfig);
        } else {
            Configuration.browserCapabilities = new MutableCapabilities();
        }
    }

    // Linux CI runners cannot create Chrome's kernel sandbox (unprivileged
    // user namespaces are restricted), so the browser exits before DevTools
    // comes up — sibling lanes pass the same flag for local Chrome.
    private static ChromeOptions localChromeCapabilities(TestConfig browserConfig) {
        var chrome = new ChromeOptions();
        chrome.addArguments("--no-sandbox");
        boolean captureHar = browserConfig.enableHar() || browserConfig.attachHarLogs();
        if (captureHar && HarCapture.supportsBrowser(browserConfig.browser())) {
            HarCapture.enablePerformanceLogging(chrome);
        }
        return chrome;
    }

    /**
     * A shared Selenoid hub can refuse a session when it has no free slot, which
     * surfaces as {@link SessionNotCreatedException} from the first browser call
     * inside a test. Claiming the session here — with retries — keeps that hub
     * hiccup out of the test body.
     */
    private static void ensureBrowserSession() {
        if (WebDriverRunner.hasWebDriverStarted()) {
            return;
        }
        for (int attempt = 1; ; attempt++) {
            try {
                open();
                return;
            } catch (SessionNotCreatedException hubRefusedSession) {
                closeWebDriver();
                if (attempt >= SESSION_ATTEMPTS) {
                    throw hubRefusedSession;
                }
                sleep(SESSION_RETRY_DELAY_MS);
            }
        }
    }

    @BeforeEach
    void beforeEach() {
        if (config.logToConsole() && config.selenideLogToConsole()) {
            selenideReport.start();
        }
        boolean reusedSession = WebDriverRunner.hasWebDriverStarted();
        if (!config.skipBlankOpen()) {
            ensureBrowserSession();
        }
        if (!config.closeBrowserAfterEach() && reusedSession) {
            BrowserSessionHelper.resetPageState();
        }
    }

    @AfterEach
    void afterEach(TestInfo testInfo) {
        try {
            if (config.logToConsole() && config.selenideLogToConsole()) {
                selenideReport.finish(testInfo.getDisplayName());
            }
            if (allureResultsEnabled() && WebDriverRunner.hasWebDriverStarted()) {
                if (config.attachBrowserConsoleLogs()) {
                    Attachments.browserConsoleLogs();
                }
                if (config.attachPageSource()) {
                    Attachments.pageSource();
                }
                if (config.attachLastScreenshot()) {
                    Attachments.screenshot("Last screenshot");
                }
                if (config.enableVideo() && config.attachVideo()) {
                    Attachments.video();
                }
            }
        } finally {
            // HAR from Chrome Performance logs — attach while the session is still
            // alive, even if screenshot/source/video threw. Close the hub slot last.
            try {
                if (allureResultsEnabled() && config.attachHarLogs()
                        && WebDriverRunner.hasWebDriverStarted()) {
                    Attachments.harLogs();
                }
            } finally {
                if (config.closeBrowserAfterEach()) {
                    closeWebDriver();
                }
            }
        }
    }

    @AfterAll
    static void afterAll() {
        if (config.closeBrowserAfterAll() && WebDriverRunner.hasWebDriverStarted()) {
            closeWebDriver();
        }
    }

}
