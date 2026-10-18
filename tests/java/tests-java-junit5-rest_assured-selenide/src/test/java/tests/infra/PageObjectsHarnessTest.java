package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.WebDriverRunner;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pages.HomePage;
import pages.LoginPage;
import tests.AllureMeta;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PageObject methods that need only the WebDriver contract — run against
 * {@link FakeWebDriver}, which answers element lookups, JS storage scripts and
 * alerts deterministically. Product-level flows stay in tests/ui + tests/e2e.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("PageObjects")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("PageObject methods")
class PageObjectsHarnessTest extends AllureMeta {

    @AfterEach
    void closeFake() {
        if (WebDriverRunner.hasWebDriverStarted()) {
            WebDriverRunner.closeWebDriver();
        }
    }

    @Test
    @DisplayName("HomePage panels and error checks")
    void homePagePanels() {
        var driver = install(new FakeWebDriver());
        driver.element("items-list").text("items failed");
        driver.element("health-status").text("health failed");
        var page = new HomePage();

        page.shouldShowLayoutAndHealth();
        assertNotNull(page.layoutPanel());
        assertNotNull(page.welcomePanelElement());
        page.shouldShowItemsError("items failed");
        page.shouldShowHealthError("health failed");
        page.shouldShowLayout();
        page.shouldBeOpen();
    }

    @Test
    @DisplayName("LoginPage exposes the form panel")
    void loginPagePanel() {
        install(new FakeWebDriver());
        assertNotNull(new LoginPage().loginFormPanel());
    }

    @Test
    @DisplayName("HeaderComponent exposes menu and header panels")
    void headerPanels() {
        install(new FakeWebDriver());
        var header = new HomePage().header;
        assertNotNull(header.headerPanel());
        assertNotNull(header.menuPanel());
    }

    @Test
    @DisplayName("BasePage auth-token waits observe scripted localStorage")
    void authTokenWaits() {
        var driver = install(new FakeWebDriver()
                .scriptResult("location.pathname.match", "authToken"));

        var page = new HomePage();
        page.shouldClearAuthToken();

        driver.scriptResult("localStorage.getItem", "token-value");
        page.shouldKeepAuthToken();
    }

    @Test
    @DisplayName("session panel and delete-account confirm/cancel")
    void sessionActions() {
        var driver = install(new FakeWebDriver());
        driver.element("logout-button").text("Logout");
        driver.element("delete-account-button").text("Delete account");
        driver.alert().text("Delete this account? This cannot be undone.");

        var page = new HomePage();
        page.shouldShowSessionActions();

        var afterDelete = page.clickDeleteAccountAndConfirm();
        assertNotNull(afterDelete);
        org.junit.jupiter.api.Assertions.assertTrue(driver.alert().accepted(), "confirm accepted");

        driver.alert().text("Delete this account? This cannot be undone.");
        var stillHome = page.clickDeleteAccountAndCancel();
        org.junit.jupiter.api.Assertions.assertTrue(driver.alert().dismissed(), "confirm dismissed");
        org.junit.jupiter.api.Assertions.assertSame(page, stillHome);
    }

    @Test
    @DisplayName("hidden welcome panel, health text, item text, reload, invalid token flow")
    void homePageRemaining() {
        var driver = install(new FakeWebDriver()
                .scriptResult("location.pathname.match", "authToken"));
        driver.element("health-status").text("down");
        driver.element("items-list").text("oops");
        driver.element("welcome-message").text("Welcome, user1!");

        var page = new HomePage();
        page.shouldHaveWelcomeMessage("Welcome, user1!");
        driver.element("welcome-panel").displayed(false).attribute("hidden", "");
        page.shouldHideWelcomePanel();
        page.shouldShowHealthText("down");
        page.shouldShowItemText("oops");
        page.clickLogoutButton();
        page.reloadPage();
        page.openPageWithInvalidToken();
    }

    private static FakeWebDriver install(FakeWebDriver driver) {
        WebDriverRunner.setWebDriver(driver);
        return driver;
    }

    /** Minimal concrete page to drive the protected BasePage token waits. */
    private static final class ProbePage extends pages.BasePage<ProbePage> {
        @Override
        public ProbePage shouldBeOpen() {
            return this;
        }

        void waitCleared() {
            waitForAuthTokenToBeCleared();
        }

        void waitPresent() {
            waitForAuthTokenToBePresent();
        }
    }

    @Test
    @DisplayName("auth token waits poll until the storage state flips")
    void authTokenWaitBranches() {
        // token present → cleared: both branches of `authToken() == null`
        install(new FakeWebDriver()
                .scriptResult("location.pathname.match", "authToken")
                .scriptResultSequence("localStorage.getItem", "tok", null, null, null, null));
        new ProbePage().waitCleared();

        // token absent → present: both branches of `authToken() != null`
        install(new FakeWebDriver()
                .scriptResult("location.pathname.match", "authToken")
                .scriptResultSequence("localStorage.getItem", null, "tok", "tok", "tok", "tok"));
        new ProbePage().waitPresent();
    }
}
