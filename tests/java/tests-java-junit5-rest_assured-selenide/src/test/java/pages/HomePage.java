package pages;

import static com.codeborne.selenide.ClickOptions.usingJavaScript;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.confirm;
import static com.codeborne.selenide.Selenide.dismiss;
import static com.codeborne.selenide.Selenide.open;

import com.codeborne.selenide.SelenideElement;
import io.qameta.allure.Step;

public class HomePage extends BasePage<HomePage> {

    /** Mirrors frontend DELETE_ACCOUNT_CONFIRM. */
    private static final String DELETE_ACCOUNT_CONFIRM =
            "Delete this account? This cannot be undone.";

    private final SelenideElement layout = $("[data-testid='multistack-layout']");
    private final SelenideElement healthStatus = $("[data-testid='health-status']");
    private final SelenideElement itemsList = $("[data-testid='items-list']");
    private final SelenideElement welcomeMessage = $("[data-testid='welcome-message']");
    private final SelenideElement logoutButton = $("[data-testid='logout-button']");
    private final SelenideElement deleteAccountButton = $("[data-testid='delete-account-button']");
    private final SelenideElement welcomePanel = $("[data-testid='welcome-panel']");

    @Step("Open home page")
    public HomePage openPage() {
        open("/");
        return shouldBeOpen();
    }

    @Step("Open home page with local storage authentication")
    public HomePage openPageWithLocalStorageAuthentication(String username, String password) {
        super.openPageWithLocalStorageAuthentication("/", username, password);
        return shouldBeOpen();
    }

    @Step("Open home page with invalid local storage token")
    public HomePage openPageWithInvalidToken() {
        openPageWithLocalStorageToken("/", "invalid-token");
        return shouldBeOpen();
    }

    @Override
    @Step("Verify home page is open")
    public HomePage shouldBeOpen() {
        layout.shouldBe(visible);
        return this;
    }

    @Step("Verify home layout is mounted")
    public HomePage shouldShowLayout() {
        layout.shouldBe(visible);
        itemsList.shouldBe(visible);
        return this;
    }

    @Step("Verify home layout and health are mounted")
    public HomePage shouldShowLayoutAndHealth() {
        layout.shouldBe(visible);
        healthStatus.shouldBe(visible);
        return this;
    }

    @Step("Home layout panel is visible")
    public SelenideElement layoutPanel() {
        return layout.shouldBe(visible);
    }

    @Step("Welcome panel is visible")
    public SelenideElement welcomePanelElement() {
        return welcomePanel.shouldBe(visible);
    }

    @Step("Verify welcome panel stays hidden")
    public HomePage shouldHideWelcomePanel() {
        // Panel uses the HTML hidden attribute (welcome === null); remote Chrome may still report isDisplayed().
        welcomePanel.shouldHave(attribute("hidden"));
        return this;
    }

    @Step("Verify auth token was cleared from localStorage")
    public HomePage shouldClearAuthToken() {
        waitForAuthTokenToBeCleared();
        return this;
    }

    @Step("Verify health status contains: {textFragment}")
    public HomePage shouldShowHealthText(String textFragment) {
        healthStatus.shouldHave(text(textFragment));
        return this;
    }

    @Step("Verify items list contains: {textFragment}")
    public HomePage shouldShowItemText(String textFragment) {
        itemsList.shouldHave(text(textFragment));
        return this;
    }

    @Step("Verify items panel shows a readable error: {textFragment}")
    public HomePage shouldShowItemsError(String textFragment) {
        itemsList.shouldHave(text(textFragment));
        return this;
    }

    @Step("Verify health panel shows a readable error: {textFragment}")
    public HomePage shouldShowHealthError(String textFragment) {
        healthStatus.shouldHave(text(textFragment));
        return this;
    }

    @Step("Verify welcome message: {message}")
    public HomePage shouldHaveWelcomeMessage(String message) {
        welcomePanel.shouldBe(visible);
        welcomeMessage.shouldHave(text(message));
        return this;
    }

    /**
     * Session offers two exits: logout ends the session, delete account removes the user.
     * Click delete only against a throwaway account — never the seeded user1.
     */
    @Step("Verify session panel offers logout and delete account")
    public HomePage shouldShowSessionActions() {
        logoutButton.shouldBe(visible).shouldHave(text("Logout"));
        deleteAccountButton.shouldBe(visible).shouldHave(text("Delete account"));
        return this;
    }

    @Step("Click logout button")
    public LoginPage clickLogoutButton() {
        logoutButton.shouldBe(visible).click(usingJavaScript());
        return new LoginPage().shouldBeOpen();
    }

    @Step("Click delete account and confirm")
    public LoginPage clickDeleteAccountAndConfirm() {
        deleteAccountButton.shouldBe(visible).click();
        confirm(DELETE_ACCOUNT_CONFIRM);
        return new LoginPage();
    }

    @Step("Click delete account and cancel the confirm")
    public HomePage clickDeleteAccountAndCancel() {
        deleteAccountButton.shouldBe(visible).click();
        dismiss(DELETE_ACCOUNT_CONFIRM);
        return this;
    }

    @Step("Verify auth token remains in localStorage")
    public HomePage shouldKeepAuthToken() {
        waitForAuthTokenToBePresent();
        return this;
    }
}
