package helpers;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.WebDriverRunner;
import io.qameta.allure.Step;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.Augmenter;

import java.util.Map;
import java.util.Optional;

import static com.codeborne.selenide.Selenide.open;

public final class ViewportHelper {

    private ViewportHelper() {
    }

    @Step("Reset viewport to default browser size")
    public static void resetViewport() {
        if (!WebDriverRunner.hasWebDriverStarted()) {
            return;
        }

        var driver = WebDriverRunner.getWebDriver();
        resolveCdp(driver).ifPresentOrElse(
                cdp -> cdp.executeCdpCommand("Emulation.clearDeviceMetricsOverride", Map.of()),
                () -> driver.manage().window().setSize(parseBrowserSize(Configuration.browserSize))
        );
    }

    public static void setViewport(int width, int height) {
        if (!WebDriverRunner.hasWebDriverStarted()) {
            open("about:blank");
        }

        var driver = WebDriverRunner.getWebDriver();
        resolveCdp(driver).ifPresent(
                cdp -> cdp.executeCdpCommand("Emulation.clearDeviceMetricsOverride", Map.of())
        );

        var metrics = Map.<String, Object>of(
                "width", width,
                "height", height,
                "deviceScaleFactor", 1,
                "mobile", false
        );

        resolveCdp(driver).ifPresentOrElse(
                cdp -> cdp.executeCdpCommand("Emulation.setDeviceMetricsOverride", metrics),
                () -> driver.manage().window().setSize(new Dimension(width, height))
        );
    }

    /** Local Chrome is ChromiumDriver; Selenoid remote Chrome needs Augmenter for CDP. */
    private static Optional<HasCdp> resolveCdp(WebDriver driver) {
        if (driver instanceof HasCdp hasCdp) {
            return Optional.of(hasCdp);
        }
        try {
            var augmented = new Augmenter().augment(driver);
            if (augmented instanceof HasCdp hasCdp) {
                return Optional.of(hasCdp);
            }
        } catch (RuntimeException ignored) {
            // Selenoid without CDP — fall back to window resize below.
        }
        return Optional.empty();
    }

    private static Dimension parseBrowserSize(String browserSize) {
        var parts = browserSize.split("x");
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid browserSize: " + browserSize);
        }
        return new Dimension(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
    }
}
