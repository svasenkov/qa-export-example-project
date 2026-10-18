package tests.e2e;

import tests.TestBase;
import annotations.Layer;
import api.AuthApiClient;
import helpers.User;
import helpers.UserBuilder;
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
@Feature("Register")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Register")
class RegisterTests extends TestBase {

    private static final String DUPLICATE_USERNAME_MESSAGE = "Username already taken";

    private static final String REGISTER_PASSWORD = "password123";

    /** Throwaway registered by the test — deleted through the API afterwards. */
    private User registeredUser;

    @AfterEach
    void cleanupRegisteredUser() {
        if (registeredUser != null) {
            AuthApiClient.deleteAccountQuietly(registeredUser.username(), registeredUser.password());
            registeredUser = null;
        }
    }

    @Test
    @Tag("e2e")
    @Tag("positive")
    @DisplayName("New user can register and land on home")
    void shouldRegisterNewUser() {
        registeredUser = new UserBuilder().withUsername().withPassword().build();

        registerPage.openPage()
                .fillAndSubmitForm(
                        registeredUser.username(),
                        registeredUser.password(),
                        registeredUser.password())
                .shouldHaveWelcomeMessage(registeredUser.welcomeMessage());
    }

    @Test
    @Tag("e2e")
    @Tag("positive")
    @DisplayName("New user can register with 3-character login and 6-character password")
    void shouldRegisterWithMinimumLengthCredentials() {
        registeredUser = new User(DataFaker.usernameAtMinLength(), DataFaker.passwordAtMinLength());

        registerPage.openPage()
                .fillAndSubmitForm(
                        registeredUser.username(),
                        registeredUser.password(),
                        registeredUser.password())
                .shouldHaveWelcomeMessage(registeredUser.welcomeMessage());
    }

    @Test
    @Tag("e2e")
    @Tag("negative")
    @DisplayName("Duplicate username shows readable error")
    void shouldShowErrorWhenUsernameIsTaken() {
        registerPage.openPage()
                .typeUsername("user1")
                .typePassword(REGISTER_PASSWORD)
                .typeConfirmPassword(REGISTER_PASSWORD)
                .submitExpectingError()
                .shouldHaveErrorMessage(DUPLICATE_USERNAME_MESSAGE);
    }

}
