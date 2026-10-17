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
@Epic("Header")
@Feature("Active nav")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Header active nav")
class HeaderActiveNavTests extends TestBase {

    @Test
    @Tag("ui")
    @Tag("smoke")
    @DisplayName("Header always has Stage and Prod env switchers")
    void headerHasStageAndProdEnvSwitchers() {
        loginPage.openPage()
                .header.shouldHaveNav("header-nav-stage")
                .shouldHaveNav("header-nav-prod");
    }

    @Test
    @Tag("ui")
    @Tag("smoke")
    @DisplayName("Login page marks Login as the active header nav")
    void loginPageMarksActiveLogin() {
        loginPage.openPage()
                .header.shouldHaveActiveNav("header-nav-login");
    }

    @Test
    @Tag("ui")
    @DisplayName("Register page marks Register as the active header nav")
    void registerPageMarksActiveRegister() {
        registerPage.openPage()
                .header.shouldHaveActiveNav("header-nav-register");
    }

    @Test
    @Tag("ui")
    @DisplayName("Home page marks Home as the active header nav")
    void homePageMarksActiveHome() {
        homePage.openPage()
                .header.shouldHaveActiveNav("header-nav-home");
    }

    @Test
    @Tag("ui")
    @DisplayName("In-form Register link syncs the active header nav")
    void inFormRegisterLinkSyncsActiveNav() {
        loginPage.openPage()
                .header.shouldHaveActiveNav("header-nav-login");
        loginPage.clickRegisterLink()
                .shouldBeOpen()
                .header.shouldHaveActiveNav("header-nav-register");
    }

    @Test
    @Tag("ui")
    @DisplayName("In-form Login link syncs the active header nav")
    void inFormLoginLinkSyncsActiveNav() {
        registerPage.openPage()
                .header.shouldHaveActiveNav("header-nav-register");
        registerPage.clickLoginLink()
                .shouldBeOpen()
                .header.shouldHaveActiveNav("header-nav-login");
    }

    @Test
    @Tag("ui")
    @DisplayName("Header nav Register opens register and marks it active")
    void headerNavRegisterOpensRegister() {
        loginPage.openPage()
                .header.clickNav("header-nav-register");
        registerPage.shouldBeOpen()
                .header.shouldHaveActiveNav("header-nav-register");
    }

    @Test
    @Tag("ui")
    @DisplayName("Header nav Login opens login and marks it active")
    void headerNavLoginOpensLogin() {
        registerPage.openPage()
                .header.clickNav("header-nav-login");
        loginPage.shouldBeOpen()
                .header.shouldHaveActiveNav("header-nav-login");
    }
}
