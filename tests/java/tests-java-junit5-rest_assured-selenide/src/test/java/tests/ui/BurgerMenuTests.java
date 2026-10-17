package tests.ui;

import tests.TestBase;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Layer("ui")
@Epic("Header")
@Feature("Burger menu")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Burger menu")
class BurgerMenuTests extends TestBase {

    @BeforeEach
    void setMobileViewport() {
        loginPage.header.setMobileViewport();
    }

    @AfterEach
    void resetViewport() {
        loginPage.header.resetViewport();
    }

    @Test
    @Tag("ui")
    @DisplayName("Menu nav marks Login active on the login page")
    void menuNavMarksActiveLogin() {
        loginPage.openPage()
                .header.openMenu()
                .shouldHaveActiveMenuNav("header-menu-nav-login");
    }

    @Test
    @Tag("ui")
    @DisplayName("Menu Register opens the register page and closes the menu")
    void clickingRegisterOpensRegisterAndClosesMenu() {
        loginPage.openPage()
                .header.openMenu()
                .shouldHaveActiveMenuNav("header-menu-nav-login")
                .clickMenuNav("header-menu-nav-register");
        registerPage.shouldBeOpen()
                .header.shouldHaveClosedMenu();
    }

    @Test
    @Tag("ui")
    @DisplayName("Menu Login opens the login page and closes the menu")
    void clickingLoginOpensLoginAndClosesMenu() {
        registerPage.openPage()
                .header.openMenu()
                .clickMenuNav("header-menu-nav-login");
        loginPage.shouldBeOpen()
                .header.shouldHaveClosedMenu();
    }
}
