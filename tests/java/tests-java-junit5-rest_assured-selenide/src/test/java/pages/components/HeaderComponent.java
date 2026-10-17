package pages.components;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.hidden;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

import com.codeborne.selenide.SelenideElement;
import helpers.ViewportHelper;
import io.qameta.allure.Step;

public class HeaderComponent {

    private final SelenideElement root = $("[data-testid='header']");
    private final SelenideElement burger = $("[data-testid='header-burger']");
    private final SelenideElement menu = $("[data-testid='header-menu']");
    private final SelenideElement langToggle =
            $("[data-testid='header-tools'] [data-testid='header-lang-toggle']");
    private final SelenideElement langLabel =
            $("[data-testid='header-tools'] [data-testid='header-lang-label']");
    private final SelenideElement themeToggle =
            $("[data-testid='header-tools'] [data-testid='header-theme-toggle']");
    private final SelenideElement html = $("html");

    @Step("Emulate mobile viewport (375x812)")
    public HeaderComponent setMobileViewport() {
        ViewportHelper.setViewport(375, 812);
        return this;
    }

    @Step("Reset viewport to default")
    public HeaderComponent resetViewport() {
        ViewportHelper.resetViewport();
        return this;
    }

    @Step("Desktop nav '{navTestid}' is the active item")
    public HeaderComponent shouldHaveActiveNav(String navTestid) {
        $("[data-testid='" + navTestid + "']")
                .shouldBe(visible)
                .shouldHave(cssClass("is-active"))
                .shouldHave(attribute("aria-current", "page"));
        $$("[data-testid='header-nav'] a[aria-current='page']").shouldHave(size(1));
        return this;
    }

    @Step("Desktop nav '{navTestid}' is present")
    public HeaderComponent shouldHaveNav(String navTestid) {
        $("[data-testid='" + navTestid + "']").shouldBe(visible);
        return this;
    }

    @Step("Click header nav '{navTestid}'")
    public HeaderComponent clickNav(String navTestid) {
        $("[data-testid='" + navTestid + "']").shouldBe(visible).click();
        return this;
    }

    @Step("Open the burger menu")
    public HeaderComponent openMenu() {
        burger.shouldBe(visible).click();
        menu.shouldBe(visible);
        burger.shouldHave(attribute("aria-expanded", "true"));
        return this;
    }

    @Step("Menu nav '{menuNavTestid}' is the active item")
    public HeaderComponent shouldHaveActiveMenuNav(String menuNavTestid) {
        $("[data-testid='" + menuNavTestid + "']")
                .shouldBe(visible)
                .shouldHave(cssClass("is-active"))
                .shouldHave(attribute("aria-current", "page"));
        return this;
    }

    @Step("Click menu nav link '{menuNavTestid}'")
    public HeaderComponent clickMenuNav(String menuNavTestid) {
        $("[data-testid='" + menuNavTestid + "']").shouldBe(visible).click();
        return this;
    }

    @Step("Menu is closed")
    public HeaderComponent shouldHaveClosedMenu() {
        menu.shouldBe(hidden);
        burger.shouldHave(attribute("aria-expanded", "false"));
        return this;
    }

    @Step("Burger menu panel is visible")
    public SelenideElement menuPanel() {
        return menu.shouldBe(visible);
    }

    @Step("Header bar is visible")
    public SelenideElement headerPanel() {
        return root.shouldBe(visible);
    }

    @Step("Verify embedded header is mounted")
    public HeaderComponent shouldShowEmbeddedHeader() {
        root.shouldBe(visible);
        return this;
    }

    @Step("Click language toggle")
    public HeaderComponent clickLangToggle() {
        langToggle.shouldBe(visible).click();
        return this;
    }

    @Step("Click theme toggle")
    public HeaderComponent clickThemeToggle() {
        themeToggle.shouldBe(visible).click();
        return this;
    }

    @Step("Verify language label: {label}")
    public HeaderComponent shouldHaveLangLabel(String label) {
        langLabel.shouldHave(text(label));
        return this;
    }

    @Step("Verify html lang: {lang}")
    public HeaderComponent shouldHaveHtmlLang(String lang) {
        html.shouldHave(attribute("lang", lang));
        return this;
    }

    @Step("Verify theme: {theme}")
    public HeaderComponent shouldHaveTheme(String theme) {
        if ("light".equals(theme)) {
            html.shouldHave(cssClass("theme-light"));
        } else {
            html.shouldNotHave(cssClass("theme-light"));
        }
        return this;
    }
}
