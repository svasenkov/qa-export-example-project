package api;

import api.model.LoginRequest;
import api.model.RegisterRequest;
import io.qameta.allure.Step;
import io.restassured.http.ContentType;

import static io.restassured.RestAssured.given;

/**
 * Thin API client for test setup and cleanup. API and e2e tests use it to arrange
 * state through the product API instead of duplicating raw JSON strings.
 */
public final class AuthApiClient {

    static {
        ApiTestBase.useRestAssured();
    }

    private AuthApiClient() {
    }

    @Step("API: register user {username}")
    public static String register(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest(username, password))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .path("token");
    }

    @Step("API: login as {username}")
    public static String login(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest(username, password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    @Step("API: delete current account")
    public static void deleteAccount(String token) {
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/auth/me")
                .then()
                .statusCode(204);
    }

    /** Cleanup that must not mask the original test failure: logs in and deletes, best-effort. */
    public static void deleteAccountQuietly(String username, String password) {
        try {
            deleteAccount(login(username, password));
        } catch (AssertionError | RuntimeException ignored) {
            // The test that created the user is responsible for its own assertions;
            // a failed cleanup (user never created, stand down) must not re-fail it.
        }
    }
}
