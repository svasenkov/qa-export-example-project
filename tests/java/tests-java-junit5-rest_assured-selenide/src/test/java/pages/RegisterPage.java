package pages;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

import com.codeborne.selenide.SelenideElement;
import io.qameta.allure.Step;

public class RegisterPage extends BasePage<RegisterPage> {

    private final SelenideElement registerForm = $("[data-testid='register-form']");
    private final SelenideElement loginInput = $("[data-testid='register-login-input']");
    private final SelenideElement passwordInput = $("[data-testid='register-password-input']");
    private final SelenideElement confirmPasswordInput = $("[data-testid='confirm-password-input']");
    private final SelenideElement submitButton = $("[data-testid='register-submit-button']");
    private final SelenideElement formTitle = $("[data-testid='register-form-title']");
    private final SelenideElement errorMessage = $("[data-testid='register-error-message']");

    @Step("Open register page")
    public RegisterPage openPage() {
        open("/register");
        return shouldBeOpen();
    }

    @Step("Click Login link under the register form")
    public LoginPage clickLoginLink() {
        $("[data-testid='login-link']").shouldBe(visible).click();
        return new LoginPage();
    }

    @Step("Fill and submit register form")
    public HomePage fillAndSubmitForm(String username, String password, String confirmPassword) {
        typeUsername(username);
        typePassword(password);
        typeConfirmPassword(confirmPassword);
        return submit();
    }

    @Step("Type username: {username}")
    public RegisterPage typeUsername(String username) {
        loginInput.setValue(username);
        return this;
    }

    @Step("Type password")
    public RegisterPage typePassword(String password) {
        passwordInput.setValue(password);
        return this;
    }

    @Step("Type confirm password")
    public RegisterPage typeConfirmPassword(String confirmPassword) {
        confirmPasswordInput.setValue(confirmPassword);
        return this;
    }

    @Step("Submit register form")
    public HomePage submit() {
        submitButton.click();
        return new HomePage();
    }

    @Step("Submit register form expecting validation or API error")
    public RegisterPage submitExpectingError() {
        submitButton.click();
        errorMessage.shouldBe(visible);
        return this;
    }

    @Override
    @Step("Verify register page is open")
    public RegisterPage shouldBeOpen() {
        registerForm.shouldBe(visible);
        return this;
    }

    @Step("Verify register form is mounted")
    public RegisterPage shouldShowRegisterForm() {
        formTitle.shouldBe(visible);
        loginInput.shouldBe(visible);
        passwordInput.shouldBe(visible);
        confirmPasswordInput.shouldBe(visible);
        submitButton.shouldBe(visible);
        return this;
    }

    @Step("Verify form title message: {message}")
    public RegisterPage shouldHaveFormTitle(String message) {
        formTitle.shouldHave(text(message));
        return this;
    }

    @Step("Verify error message: {message}")
    public RegisterPage shouldHaveErrorMessage(String message) {
        errorMessage.shouldBe(visible);
        errorMessage.shouldHave(text(message));
        return this;
    }
}
