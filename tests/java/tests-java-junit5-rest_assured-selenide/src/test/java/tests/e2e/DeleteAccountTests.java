package tests.e2e;

import tests.TestBase;
import annotations.Layer;
import api.AuthApiClient;
import helpers.DataFaker;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Layer("e2e")
@Epic("Authentication")
@Feature("Delete account")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Delete account")
class DeleteAccountTests extends TestBase {

    private static final String PASSWORD = "password123";

    /** Throwaway account — never seeded user1. Deleted through the API if the UI did not. */
    private String throwawayUsername;

    @AfterEach
    void cleanupThrowawayUser() {
        if (throwawayUsername != null) {
            AuthApiClient.deleteAccountQuietly(throwawayUsername, PASSWORD);
            throwawayUsername = null;
        }
    }

    @Test
    @Tag("e2e")
    @Tag("positive")
    @DisplayName("Confirming delete account clears the session and navigates to login")
    void confirmingDeleteClearsSessionAndNavigatesToLogin() {
        throwawayUsername = DataFaker.username();
        AuthApiClient.register(throwawayUsername, PASSWORD);

        homePage.openPageWithLocalStorageAuthentication(throwawayUsername, PASSWORD)
                .shouldHaveWelcomeMessage("Welcome, " + throwawayUsername + "!")
                .shouldShowSessionActions()
                .clickDeleteAccountAndConfirm()
                .shouldHaveFormTitle("Login Form");

        homePage.shouldClearAuthToken();
        throwawayUsername = null;
    }

    @Test
    @Tag("e2e")
    @Tag("positive")
    @DisplayName("Cancelling the confirm keeps the session and sends no delete request")
    void cancellingConfirmKeepsSession() {
        throwawayUsername = DataFaker.username();
        AuthApiClient.register(throwawayUsername, PASSWORD);

        homePage.openPageWithLocalStorageAuthentication(throwawayUsername, PASSWORD)
                .shouldHaveWelcomeMessage("Welcome, " + throwawayUsername + "!")
                .clickDeleteAccountAndCancel()
                .shouldHaveWelcomeMessage("Welcome, " + throwawayUsername + "!")
                .shouldKeepAuthToken();

        AuthApiClient.login(throwawayUsername, PASSWORD);
    }
}
