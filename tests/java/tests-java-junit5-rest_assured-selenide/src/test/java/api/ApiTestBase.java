package api;

import allure.AllureRestAssuredFilters;
import tests.AllureMeta;
import config.ConfigReader;
import config.TestConfig;
import io.restassured.RestAssured;

/**
 * Rest Assured globals ({@code baseURI}, Allure filter) — not a dump request spec.
 * A spec is for a handle (path, auth, expected status), not Content-Type JSON.
 */
public class ApiTestBase extends AllureMeta {

    protected static final TestConfig config = ConfigReader.testConfig;

    static {
        RestAssured.baseURI = ConfigReader.resolveApiBaseUrl();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        if (AllureRestAssuredFilters.isEnabled(config)) {
            RestAssured.filters(AllureRestAssuredFilters.create(config));
        }
    }

    /** Forces class initialization so UI helpers see the same Rest Assured globals. */
    static void useRestAssured() {
    }
}
