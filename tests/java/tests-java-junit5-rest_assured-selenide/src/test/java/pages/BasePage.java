package pages;

import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.refresh;

import api.AuthApiClient;
import io.qameta.allure.Step;
import pages.components.HeaderComponent;

public abstract class BasePage<T extends BasePage<T>> {

    /** Mirrors frontend authTokenStorageKey (backend-scoped on matrix paths). */
    private static final String AUTH_TOKEN_KEY_JS =
            "var m=location.pathname.match(/\\/(backend-[^/]+)\\//);"
                    + "return m ? 'authToken:' + m[1] : 'authToken';";

    public final HeaderComponent header = new HeaderComponent();

    public abstract T shouldBeOpen();

    @Step("Reload current page")
    public T reloadPage() {
        refresh();
        return shouldBeOpen();
    }

    protected void openPageWithLocalStorageAuthentication(String pagePath, String username, String password) {
        openPageWithLocalStorageToken(pagePath, AuthApiClient.login(username, password));
    }

    protected void openPageWithLocalStorageToken(String pagePath, String token) {
        open("/icons/qa-guru-logo.svg");
        executeJavaScript(
                "localStorage.setItem(arguments[0], arguments[1]);",
                authTokenKey(),
                token
        );
        open(pagePath);
    }

    protected void waitForAuthTokenToBeCleared() {
        Wait().until(driver -> authToken() == null);
    }

    protected void waitForAuthTokenToBePresent() {
        Wait().until(driver -> authToken() != null);
    }

    private String authTokenKey() {
        return executeJavaScript(AUTH_TOKEN_KEY_JS);
    }

    private String authToken() {
        return executeJavaScript("return localStorage.getItem(arguments[0]);", authTokenKey());
    }
}
