package tests.ui;

import tests.TestBase;
import annotations.Layer;
import io.qameta.allure.AllureId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Layer("ui")
@Epic("Authentication")
@Feature("Login form")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Login form mount")
class LoginFormTests extends TestBase {

    @Test
    @AllureId("46592")
    @Issue("MUL-2")
    @Tag("ui")
    @Tag("mock")
    @DisplayName("Login form fields and submit are visible")
    void loginFormIsMounted() {
        loginPage.openPage()
                .shouldShowLoginForm()
                .shouldHaveFormTitle("Login Form");
    }
}
