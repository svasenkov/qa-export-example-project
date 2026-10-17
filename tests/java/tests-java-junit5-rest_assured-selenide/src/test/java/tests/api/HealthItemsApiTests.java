package tests.api;

import annotations.Layer;
import api.ApiTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.equalTo;

/**
 * HTTP contract of {@code /api/health} and {@code /api/items}: shapes and types, not deployment facts.
 * Which service answers and where the data physically lives is asserted by
 * {@code BackendWiringApiTests} and {@code SeedDataApiTests}.
 */
@Layer("api")
@Epic("Home")
@Feature("Health and items")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Health and items API")
class HealthItemsApiTests extends ApiTestBase {

    @Test
    @Tag("api")
    @DisplayName("GET /api/health matches the health contract and reports ok")
    void healthMatchesContract() {
        given()
                .when()
                .get("/api/health")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/health.json"))
                .body("status", equalTo("ok"));
    }

    @Test
    @Tag("api")
    @DisplayName("GET /api/items matches the items contract (typed rows, named source)")
    void itemsMatchContract() {
        given()
                .when()
                .get("/api/items")
                .then()
                .statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/items.json"));
    }
}
