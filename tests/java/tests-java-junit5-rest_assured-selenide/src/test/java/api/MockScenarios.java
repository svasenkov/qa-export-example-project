package api;

import io.qameta.allure.Step;
import io.restassured.http.ContentType;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * WireMock scenario switch for the mock stand (compose profile {@code mock}; the gateway
 * proxies {@code /__admin/} to WireMock). Lets UI tests inject API failures that a healthy
 * live backend can never produce.
 */
public final class MockScenarios {

    static {
        ApiTestBase.useRestAssured();
    }

    private MockScenarios() {
    }

    /** True when the stand under test exposes the WireMock admin API (mock stand only). */
    public static boolean available() {
        try {
            int status = given()
                    .when()
                    .get("/__admin/scenarios")
                    .statusCode();
            return status == 200;
        } catch (RuntimeException gatewayUnreachable) {
            return false;
        }
    }

    @Step("Mock: switch scenario {scenario} to state {state}")
    public static void setState(String scenario, String state) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("state", state))
                .when()
                .put("/__admin/scenarios/" + scenario + "/state")
                .then()
                .statusCode(200);
    }

    @Step("Mock: reset all scenarios")
    public static void resetAll() {
        given()
                .when()
                .post("/__admin/scenarios/reset")
                .then()
                .statusCode(200);
    }
}
