package allure;

import config.TestConfig;
import io.qameta.allure.restassured.AllureRestAssured;

public final class AllureRestAssuredFilters {

    /** Classpath name under {@code tpl/}; keep in sync with {@code helpers.AllureHttpHtml}. */
    public static final String REQUEST_TEMPLATE = "request.ftl";
    /** Classpath name under {@code tpl/}; keep in sync with {@code helpers.AllureHttpHtml}. */
    public static final String RESPONSE_TEMPLATE = "response.ftl";

    private AllureRestAssuredFilters() {
    }

    public static boolean isEnabled(TestConfig config) {
        return !"none".equals(config.allureReportMode()) && config.enableAllureRestAssuredListener();
    }

    public static AllureRestAssured create(TestConfig config) {
        // allure-java 3.x dropped freemarker templates (setRequestTemplate/setResponseTemplate);
        // the structured HttpExchange attachment is rendered by the Allure 3 report itself.
        // tpl/*.ftl still feeds helpers.AllureHttpHtml (allure-attachments 2.35.5 pin).
        return new AllureRestAssured();
    }
}
