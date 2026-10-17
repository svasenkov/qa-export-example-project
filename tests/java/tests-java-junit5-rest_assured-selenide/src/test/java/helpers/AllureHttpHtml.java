package helpers;

import allure.AllureRestAssuredFilters;
import io.qameta.allure.attachment.FreemarkerAttachmentRenderer;
import io.qameta.allure.attachment.http.HttpRequestAttachment;
import io.qameta.allure.attachment.http.HttpResponseAttachment;

/**
 * Renders colored Rest Assured Allure HTML from {@code tpl/*.ftl} — same path
 * {@link io.qameta.allure.restassured.AllureRestAssured} uses at runtime.
 * <p>
 * Unit-test the template, then copy to {@code _tests-meta}. Do not iterate via
 * a full {@code allureReport} first.
 */
public final class AllureHttpHtml {

    private AllureHttpHtml() {
    }

    public static String renderRequest(HttpRequestAttachment data) {
        return new FreemarkerAttachmentRenderer(AllureRestAssuredFilters.REQUEST_TEMPLATE)
                .render(data)
                .getContent();
    }

    public static String renderResponse(HttpResponseAttachment data) {
        return new FreemarkerAttachmentRenderer(AllureRestAssuredFilters.RESPONSE_TEMPLATE)
                .render(data)
                .getContent();
    }
}
