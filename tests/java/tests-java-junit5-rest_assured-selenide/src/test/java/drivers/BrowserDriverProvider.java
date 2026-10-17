package drivers;

import com.codeborne.selenide.WebDriverProvider;
import config.ConfigReader;
import config.TestConfig;
import helpers.HarCapture;
import helpers.LocalChromePin;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BrowserDriverProvider implements WebDriverProvider {

    @Override
    public WebDriver createDriver(Capabilities ignored) {
        TestConfig config = ConfigReader.testConfig;
        boolean captureHar = config.enableHar() || config.attachHarLogs();
        if (!config.remoteUrl().isBlank()) {
            return remote(config, captureHar);
        }
        return local(config, captureHar);
    }

    private static WebDriver remote(TestConfig config, boolean captureHar) {
        MutableCapabilities caps = new MutableCapabilities();
        caps.setCapability("browserName", config.browser());
        caps.setCapability("browserVersion", config.browserVersion());
        Map<String, Object> selenoidOpts = new HashMap<>();
        selenoidOpts.put("enableVNC", config.enableVnc());
        selenoidOpts.put("enableVideo", config.enableVideo());
        caps.setCapability("selenoid:options", selenoidOpts);
        if (captureHar && HarCapture.supportsBrowser(config.browser())) {
            HarCapture.enablePerformanceLogging(caps);
        }
        try {
            return new RemoteWebDriver(URI.create(config.remoteUrl()).toURL(), caps);
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalStateException("Invalid remoteUrl: " + config.remoteUrl(), e);
        }
    }

    private static WebDriver local(TestConfig config, boolean captureHar) {
        String browser = config.browser();
        if ("chrome".equals(browser)) {
            var chromeBinary = LocalChromePin.apply(config.browserVersion());
            return new ChromeDriver(pinnedLocalChrome(chromeBinary, config.headless(), captureHar));
        }
        if ("firefox".equals(browser)) {
            FirefoxOptions firefox = new FirefoxOptions();
            firefox.setBrowserVersion(config.browserVersion());
            if (config.headless()) {
                firefox.addArguments("-headless");
            }
            return new FirefoxDriver(firefox);
        }
        throw new IllegalStateException(
                "Local BrowserDriverProvider supports chrome or firefox. Got: " + browser);
    }

    /**
     * ChromeDriver launched from a {@code WebDriverProvider} never reads
     * {@code Configuration.browserBinary}. Pin the CFT binary on the options
     * or chromedriver uses the runner's {@code /opt/google/chrome}.
     */
    public static ChromeOptions pinnedLocalChrome(Path chromeBinary, boolean headless, boolean captureHar) {
        ChromeOptions chrome = new ChromeOptions();
        chrome.setBinary(chromeBinary.toFile());
        if (headless) {
            chrome.addArguments(
                    "--headless=new",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--disable-dev-shm-usage");
        }
        if (captureHar && HarCapture.supportsBrowser("chrome")) {
            HarCapture.enablePerformanceLogging(chrome);
        }
        return chrome;
    }
}
