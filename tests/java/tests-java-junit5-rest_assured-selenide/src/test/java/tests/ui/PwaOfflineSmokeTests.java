package tests.ui;

import tests.TestBase;
import annotations.Layer;
import com.codeborne.selenide.WebDriverRunner;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.Augmenter;

import java.util.Map;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.refresh;

/**
 * Offline smoke per PWA-004: after the service worker controls the page and
 * precache is filled, an emulated-offline reload must still render the app
 * shell. CDP emulation is sent as a raw command so it works on both local
 * ChromiumDriver and augmented remote sessions.
 */
@Layer("ui")
@Epic("PWA")
@Feature("Offline shell")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("PWA offline shell (CDP)")
class PwaOfflineSmokeTests extends TestBase {

    @Test
    @Tag("ui")
    @DisplayName("Reload under emulated offline still renders the app shell")
    void offlineReloadStillRendersShell() {
        homePage.openPage();

        Wait().until(driver -> Boolean.TRUE.equals(executeJavaScript(
                "return !!(navigator.serviceWorker && navigator.serviceWorker.controller)")));

        emulateOffline(true);
        try {
            refresh();
            $("[data-testid='multistack-layout']").shouldBe(visible);
        } finally {
            emulateOffline(false);
        }
    }

    private static void emulateOffline(boolean offline) {
        WebDriver driver = WebDriverRunner.getWebDriver();
        if (!(driver instanceof HasCdp)) {
            driver = new Augmenter().augment(driver);
        }
        var cdp = (HasCdp) driver;
        cdp.executeCdpCommand("Network.enable", Map.of());
        cdp.executeCdpCommand("Network.emulateNetworkConditions", Map.of(
                "offline", offline,
                "latency", 0,
                "downloadThroughput", -1,
                "uploadThroughput", -1));
    }
}
