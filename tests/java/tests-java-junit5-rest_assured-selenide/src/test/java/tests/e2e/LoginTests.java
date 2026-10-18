package tests.e2e;

import tests.TestBase;
import annotations.Layer;
import api.AuthApiClient;
import helpers.DataFaker;
import helpers.User;
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
@Feature("Login")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Login")
class LoginTests extends TestBase {

    private static final String WRONG_CREDENTIALS_MESSAGE = "Wrong login or password";

    private User minLengthUser;

    @AfterEach
    void cleanupMinLengthUser() {
        if (minLengthUser != null) {
            AuthApiClient.deleteAccountQuietly(minLengthUser.username(), minLengthUser.password());
            minLengthUser = null;
        }
    }

    @Test
    @Tag("e2e")
    @Tag("smoke")
    @Tag("positive")
    @DisplayName("User is logged in with valid credentials")
    void shouldLoginWithValidCredentials() {
        loginPage.openPage()
                .fillAndSubmitForm("user1", "password1")
                .shouldHaveWelcomeMessage("Welcome, user1!");
    }

    @Test
    @Tag("e2e")
    @Tag("positive")
    @DisplayName("User is logged in with 3-character login and 6-character password")
    void shouldLoginWithMinimumLengthCredentials() {
        minLengthUser = new User(DataFaker.usernameAtMinLength(), DataFaker.passwordAtMinLength());
        AuthApiClient.register(minLengthUser.username(), minLengthUser.password());
        loginPage.openPage()
                .fillAndSubmitForm(minLengthUser.username(), minLengthUser.password())
                .shouldHaveWelcomeMessage(minLengthUser.welcomeMessage());
    }

    @Test
    @Tag("e2e")
    @Tag("negative")
    @DisplayName("Wrong password shows readable error")
    void shouldShowErrorWhenPasswordIsWrong() {
        loginPage.openPage()
                .typeUsername("user1")
                .typePassword("wrongpassword")
                .submitExpectingError()
                .shouldHaveErrorMessage(WRONG_CREDENTIALS_MESSAGE);
    }

}
