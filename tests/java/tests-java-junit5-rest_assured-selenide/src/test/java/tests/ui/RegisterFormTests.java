package tests.ui;

import tests.TestBase;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Layer("ui")
@Epic("Authentication")
@Feature("Register form")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Register form mount")
class RegisterFormTests extends TestBase {

    @Test
    @Tag("ui")
    @Tag("mock")
    @DisplayName("Register form fields and submit are visible")
    void registerFormIsMounted() {
        registerPage.openPage()
                .shouldShowRegisterForm()
                .shouldHaveFormTitle("Register");
    }
}
