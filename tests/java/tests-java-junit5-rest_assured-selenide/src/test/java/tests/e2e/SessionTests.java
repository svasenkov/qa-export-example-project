package tests.e2e;

import tests.TestBase;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Layer("e2e")
@Epic("Authentication")
@Feature("Session")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Session")
class SessionTests extends TestBase {

    @Test
    @Tag("e2e")
    @Tag("negative")
    @DisplayName("Invalid token clears session and hides welcome")
    void invalidTokenClearsSession() {
        homePage.openPageWithInvalidToken()
                .shouldHideWelcomePanel()
                .shouldClearAuthToken();
    }

    @Test
    @Tag("e2e")
    @Tag("positive")
    @DisplayName("Session survives a page reload (token in localStorage)")
    void sessionSurvivesReload() {
        homePage.openPageWithLocalStorageAuthentication("user1", "password1")
                .shouldHaveWelcomeMessage("Welcome, user1!")
                .reloadPage()
                .shouldHaveWelcomeMessage("Welcome, user1!");
    }
}
