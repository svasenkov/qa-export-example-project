package tests.api;

import annotations.Layer;
import api.ApiTestBase;
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
import static org.hamcrest.Matchers.equalTo;

@Layer("api")
@Epic("Authentication")
@Feature("Account lifecycle")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Auth account lifecycle on deployed stand")
class AuthRoundTripApiTests extends ApiTestBase {

    /**
     * Full account lifecycle across separate HTTP requests — proves DB and JWT are wired
     * together on the deployed stand, and documents that logout is stateless: the JWT keeps
     * working until the account itself is gone. Deletes the user it registers, so the stand
     * does not accumulate test accounts.
     */
    @Test
    @Tag("api")
    @DisplayName("register → login → me → logout (stateless: token survives) → delete → me is 401")
    void accountLifecycleRoundTrip() {
        String username = DataFaker.username();
        String password = "password123";

        given()
                .contentType(JSON)
                .body(new RegisterRequest(username, password))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("username", equalTo(username));

        String token = given()
                .contentType(JSON)
                .body(new LoginRequest(username, password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(username));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(204);

        // Stateless JWT: logout does not invalidate the token server-side — by design.
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(username));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/auth/me")
                .then()
                .statusCode(204);

        // The token still verifies cryptographically, but the account is gone → 401.
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(401);
    }
}
