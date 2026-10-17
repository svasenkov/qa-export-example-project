package config;

import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "classpath:config/${env}.properties",
        "classpath:config/default.properties",
})
public interface TestConfig extends Config {

    @DefaultValue("allure3")
    String allureReportMode();

    @DefaultValue("none")
    String allureAgentMode();

    @DefaultValue("false")
    boolean attachBrowserConsoleLogs();

    @DefaultValue("false")
    boolean attachHarLogs();

    @DefaultValue("false")
    boolean attachLastScreenshot();

    @DefaultValue("false")
    boolean attachPageSource();

    @DefaultValue("false")
    boolean attachVideo();

    @DefaultValue("false")
    boolean enableAllureSelenideListener();

    @DefaultValue("false")
    boolean enableAllureRestAssuredListener();

    @DefaultValue("default")
    String allureRestAssuredListenerStyle();

    @DefaultValue("")
    String baseUrl();

    @DefaultValue("")
    String apiBaseUrl();

    @DefaultValue("backend-go-stdlib")
    String apiHealthService();

    /** Display name in the home welcome panel after seed login. Mock stand stubs {@code mock-user}. */
    @DefaultValue("user1")
    String welcomeUsername();

    @DefaultValue("")
    String remoteUrl();

    @DefaultValue("chrome")
    String browser();

    @DefaultValue("148")
    String browserVersion();

    @DefaultValue("1920x1280")
    String browserSize();

    @DefaultValue("false")
    boolean headless();

    @DefaultValue("true")
    boolean closeBrowserAfterEach();

    @DefaultValue("true")
    boolean closeBrowserAfterAll();

    @DefaultValue("false")
    boolean skipBlankOpen();

    @DefaultValue("false")
    boolean enableHar();

    @DefaultValue("false")
    boolean enableVnc();

    @DefaultValue("false")
    boolean enableVideo();

    @DefaultValue("")
    String videoFolder();

    @DefaultValue("false")
    boolean updateScreenshots();

    @DefaultValue("screenshots")
    String screenshotsDir();

    @DefaultValue("0.015")
    double screenshotDiffThreshold();

    @DefaultValue("true")
    boolean logToConsole();

    @DefaultValue("true")
    boolean selenideLogToConsole();

    @DefaultValue("info")
    String rootLogLevel();

}
