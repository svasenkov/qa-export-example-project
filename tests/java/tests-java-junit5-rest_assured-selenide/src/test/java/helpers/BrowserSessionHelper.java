package helpers;

import com.codeborne.selenide.WebDriverRunner;
import io.qameta.allure.Step;

import static com.codeborne.selenide.Selenide.cookies;
import static com.codeborne.selenide.Selenide.localStorage;
import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.Selenide.sessionStorage;
import static com.codeborne.selenide.Selenide.switchTo;

public final class BrowserSessionHelper {

    private BrowserSessionHelper() {
    }

    @Step("Reset browser page state")
    public static void resetPageState() {
        if (!WebDriverRunner.hasWebDriverStarted() || !currentUrlAllowsWebStorage()) {
            return;
        }
        switchToDefaultContent();
        clearLocalStorage();
        clearSessionStorage();
        clearCookies();
        refresh();
        ViewportHelper.resetViewport();
    }

    private static boolean currentUrlAllowsWebStorage() {
        try {
            var url = WebDriverRunner.url();
            return url != null && (url.startsWith("http://") || url.startsWith("https://"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Step("Switch to default content")
    public static void switchToDefaultContent() {
        try {
            switchTo().defaultContent();
        } catch (RuntimeException ignored) {
            // not inside a frame
        }
    }

    @Step("Clear browser local storage")
    public static void clearLocalStorage() {
        localStorage().clear();
    }

    @Step("Clear browser session storage")
    public static void clearSessionStorage() {
        sessionStorage().clear();
    }

    @Step("Clear browser cookies")
    public static void clearCookies() {
        cookies().clear();
    }
}
