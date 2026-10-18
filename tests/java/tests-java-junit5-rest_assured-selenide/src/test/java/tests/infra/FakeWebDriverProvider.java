package tests.infra;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.SessionNotCreatedException;
import com.codeborne.selenide.WebDriverProvider;
import org.openqa.selenium.WebDriver;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebDriverProvider wired via {@code Configuration.browser = <fqcn>} in
 * isolated harness tests: fails {@link #remainingFailures} session attempts
 * with {@link SessionNotCreatedException} (shared-hub slot refusal), then
 * hands out a {@link FakeWebDriver}.
 */
public class FakeWebDriverProvider implements WebDriverProvider {

    private static final AtomicInteger REMAINING_FAILURES = new AtomicInteger();
    private static volatile String browserName = "chrome";

    public static void failNextSessions(int times) {
        REMAINING_FAILURES.set(times);
    }

    public static void useBrowser(String browser) {
        browserName = browser;
    }

    @Override
    public WebDriver createDriver(Capabilities capabilities) {
        if (REMAINING_FAILURES.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            throw new SessionNotCreatedException("fake hub refused the session");
        }
        // FakeCdpDriver: viewport setup after open() routes through CDP, like a
        // real chromium session; without it the augmented dispatch hits a dead
        // endpoint and the Selenide open() contract fails.
        return new FakeWebDriver.FakeCdpDriver(browserName);
    }
}
