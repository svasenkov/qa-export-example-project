package tests.api;

import annotations.Layer;
import api.ApiTestBase;
import api.AuthApiClient;
import api.model.LoginRequest;
import api.model.RegisterRequest;
import helpers.DataFaker;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * HTTP contract of {@code /api/auth/*}: status codes, response schemas, error envelopes.
 * Deployed-stand wiring facts (seed catalogue, DB round-trips) live in sibling {@code *ApiTests}.
 */
@Layer("api")
@Epic("Authentication")
@Feature("Authentication")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Auth API")
class AuthApiTests extends ApiTestBase {

    private static final String WRONG_CREDENTIALS_MESSAGE = "Wrong login or password";

    @Test
    @Tag("api")
    @Tag("smoke")
    @DisplayName("POST /api/auth/login returns the auth contract for a seeded user")
    void loginWithValidCredentials() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("user1", "password1"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/auth-response.json"))
                .body("username", equalTo("user1"))
                .body("redirectUrl", equalTo("/"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/login rejects a wrong password with 401")
    void loginWithInvalidPassword() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("user1", "wrongpassword"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", equalTo(WRONG_CREDENTIALS_MESSAGE));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/login answers an unknown user with the same 401 (no enumeration)")
    void loginWithUnknownUsername() {
        given()
                .contentType(JSON)
                .body(new LoginRequest(DataFaker.username(), "password123"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("message", equalTo(WRONG_CREDENTIALS_MESSAGE));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/login joins both field errors into one 400 message")
    void loginRejectsEmptyCredentials() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("", ""))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", allOf(
                        containsString("username"),
                        containsString("password"),
                        containsString("; ")));
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("POST /api/auth/login rejects a short username with 400")
    void loginRejectsShortUsername() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("ab", "password1"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("username"));
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("POST /api/auth/login rejects a short password with 400")
    void loginRejectsShortPassword() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("user1", "123"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("password"));
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("POST /api/auth/login rejects an empty username with 400")
    void loginRejectsEmptyUsername() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("", "password1"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("username"));
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("POST /api/auth/login rejects an empty password with 400")
    void loginRejectsEmptyPassword() {
        given()
                .contentType(JSON)
                .body(new LoginRequest("user1", ""))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("password"));
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("POST /api/auth/login answers a malformed JSON body with 400, not 401")
    void loginRejectsMalformedJson() {
        given()
                .contentType(JSON)
                .body("not json")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400)
                .body("message", equalTo("Request body is not valid JSON"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register creates a user, returns the auth contract, and cleans up")
    void registerNewUser() {
        String username = DataFaker.username();

        String token = given()
                .contentType(JSON)
                .body(new RegisterRequest(username, "password123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/auth-response.json"))
                .body("username", equalTo(username))
                .body("redirectUrl", equalTo("/"))
                .extract()
                .path("token");

        AuthApiClient.deleteAccount(token);
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register accepts a 3-character username and 6-character password")
    void registerAcceptsMinimumLengthCredentials() {
        String username = DataFaker.usernameAtMinLength();
        String token = given()
                .contentType(JSON)
                .body(new RegisterRequest(username, DataFaker.passwordAtMinLength()))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body(matchesJsonSchemaInClasspath("schemas/auth-response.json"))
                .body("username", equalTo(username))
                .extract()
                .path("token");

        AuthApiClient.deleteAccount(token);
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/login with min-length unknown user is 401, not 400")
    void loginMinLengthUnknownUserIsUnauthorized() {
        given()
                .contentType(JSON)
                .body(new LoginRequest(DataFaker.usernameAtMinLength(), DataFaker.passwordAtMinLength()))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", equalTo(WRONG_CREDENTIALS_MESSAGE));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register rejects a duplicate username with 409")
    void registerDuplicateUsername() {
        given()
                .contentType(JSON)
                .body(new RegisterRequest("user1", "password123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", equalTo("Username already taken"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register rejects a short password with 400")
    void registerRejectsShortPassword() {
        given()
                .contentType(JSON)
                .body(new RegisterRequest("shortuser", "abc"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("password"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register rejects a short username with 400")
    void registerRejectsShortUsername() {
        given()
                .contentType(JSON)
                .body(new RegisterRequest("ab", "password123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("username"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register rejects an empty username with 400")
    void registerRejectsEmptyUsername() {
        given()
                .contentType(JSON)
                .body(new RegisterRequest("", "password123"))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("username"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register rejects an empty password with 400")
    void registerRejectsEmptyPassword() {
        given()
                .contentType(JSON)
                .body(new RegisterRequest("newuser", ""))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", containsString("password"));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register joins both field errors into one 400 message")
    void registerRejectsEmptyCredentials() {
        given()
                .contentType(JSON)
                .body(new RegisterRequest("", ""))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body(matchesJsonSchemaInClasspath("schemas/error.json"))
                .body("message", allOf(
                        containsString("username"),
                        containsString("password")));
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/register answers a malformed JSON body with 400, not 401")
    void registerRejectsMalformedJson() {
        given()
                .contentType(JSON)
                .body("not json")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .body("message", equalTo("Request body is not valid JSON"));
    }

    @Test
    @Tag("api")
    @DisplayName("GET /api/auth/me returns the profile contract for a bearer token")
    void profileWithBearerToken() {
        String token = AuthApiClient.login("user1", "password1");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/profile.json"))
                .body("username", equalTo("user1"));
    }

    @Test
    @Tag("api")
    @DisplayName("GET /api/auth/me without a token returns 401")
    void profileWithoutToken() {
        given()
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    @Tag("api")
    @DisplayName("GET /api/auth/me with a garbage token returns 401")
    void profileWithGarbageToken() {
        given()
                .header("Authorization", "Bearer not-a-jwt")
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    @Tag("api")
    @DisplayName("POST /api/auth/logout returns 204")
    void logoutReturnsNoContent() {
        given()
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(204);
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("DELETE /api/auth/me without a token returns 401")
    void deleteWithoutToken() {
        given()
                .when()
                .delete("/api/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    @Tag("api")
    @Tag("negative")
    @DisplayName("DELETE /api/auth/me with a garbage token returns 401")
    void deleteWithGarbageToken() {
        given()
                .header("Authorization", "Bearer not-a-jwt")
                .when()
                .delete("/api/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    @Tag("api")
    @DisplayName("DELETE /api/auth/me removes the account: repeated login is rejected")
    void deleteRemovesAccount() {
        String username = DataFaker.username();
        String token = AuthApiClient.register(username, "password123");

        AuthApiClient.deleteAccount(token);

        given()
                .contentType(JSON)
                .body(new LoginRequest(username, "password123"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("message", equalTo(WRONG_CREDENTIALS_MESSAGE));
    }

    @Test
    @Tag("api")
    @DisplayName("unmapped /api/* path requires authentication (security catch-all)")
    void unmappedApiPathRequiresAuthentication() {
        given()
                .when()
                .get("/api/nope")
                .then()
                .statusCode(401);
    }
}
