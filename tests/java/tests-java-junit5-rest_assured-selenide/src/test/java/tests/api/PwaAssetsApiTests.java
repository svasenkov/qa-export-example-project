package tests.api;

import java.util.List;

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
import static org.hamcrest.Matchers.containsString;

/**
 * Delivery of the PWA artifacts through the app gateway ({@code baseUrl}):
 * manifest, service worker and icons must reach the browser on a deployed
 * stand. Registration/update semantics are covered by frontend unit tests.
 */
@Layer("api")
@Epic("PWA")
@Feature("Static assets")
@Severity(SeverityLevel.NORMAL)
@DisplayName("PWA assets")
class PwaAssetsApiTests extends ApiTestBase {

    private static String appUrl(String path) {
        return config.baseUrl().trim() + path;
    }

    @Test
    @Tag("api")
    @Tag("smoke")
    @DisplayName("GET /manifest.webmanifest returns the install manifest")
    void manifestIsServed() {
        // The gateway serves .webmanifest as application/octet-stream — assert the
        // manifest contract as text instead of registering a global JSON parser.
        given()
                .when()
                .get(appUrl("/manifest.webmanifest"))
                .then()
                .statusCode(200)
                .body(containsString("\"name\":\"Multistack\""))
                .body(containsString("\"short_name\":\"Multistack\""))
                .body(containsString("\"display\":\"standalone\""))
                .body(containsString("icons/pwa-192.png"))
                .body(containsString("icons/pwa-512.png"))
                .body(containsString("icons/pwa-maskable-512.png"));
    }

    @Test
    @Tag("api")
    @Tag("smoke")
    @DisplayName("GET /sw.js returns the service worker with precache and navigation fallback")
    void serviceWorkerIsServed() {
        given()
                .when()
                .get(appUrl("/sw.js"))
                .then()
                .statusCode(200)
                .contentType(containsString("javascript"))
                .body(containsString("precacheAndRoute"))
                .body(containsString("NavigationRoute"));
    }

    @Test
    @Tag("api")
    @Tag("smoke")
    @DisplayName("PWA icons are served from the app mount")
    void iconsAreServed() {
        List<String> icons = List.of(
                "/icons/pwa-192.png",
                "/icons/pwa-512.png",
                "/icons/pwa-maskable-512.png");
        for (String icon : icons) {
            given()
                    .when()
                    .get(appUrl(icon))
                    .then()
                    .statusCode(200)
                    .contentType(containsString("image/png"));
        }
    }
}
