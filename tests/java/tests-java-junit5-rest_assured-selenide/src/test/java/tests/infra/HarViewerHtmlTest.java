package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import helpers.HarViewerHtml;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Layer("infra")
@Epic("Test infra")
@Feature("HAR viewer")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
class HarViewerHtmlTest extends AllureMeta {

    @Test
    void renderBuildsSelenoidLikeTableWithDetailsWithoutEmbeddedHarDataUri() {
        byte[] har = """
                {"log":{"version":"1.2","entries":[{"startedDateTime":"2026-01-01T00:00:00.000Z","time":50,"request":{"method":"GET","url":"https://example.com/","headers":[{"name":"Accept","value":"*/*"}]},"response":{"status":200,"statusText":"OK","headers":[{"name":"Content-Type","value":"text/html"}],"content":{"size":42,"mimeType":"text/html"}},"timings":{"wait":40,"receive":10}}]}}
                """.trim().getBytes();

        String html = HarViewerHtml.render(har);

        assertTrue(html.contains("HAR Viewer"), () -> "missing title: " + html);
        assertTrue(html.contains("1 requests"), () -> "missing summary: " + html);
        assertTrue(html.contains("example.com"), () -> "missing url row: " + html);
        assertTrue(html.contains("<table class=\"har-table\""), () -> "missing har-table: " + html);
        assertTrue(html.contains(">Method</span>"), () -> "missing Method column: " + html);
        assertTrue(html.contains(">Status</span>"), () -> "missing Status column: " + html);
        assertTrue(html.contains(">Type</span>"), () -> "missing Type column: " + html);
        assertFalse(html.contains("cols-head"), () -> "must not use CSS-grid cols-head: " + html);
        assertFalse(html.contains("Waterfall"), () -> "must not use Waterfall column: " + html);
        assertFalse(html.contains("har-detail-row"), () -> "must not emit sibling detail rows: " + html);
        assertFalse(html.contains("Details — Headers"), () -> "must not show Details — junk rows: " + html);
        assertTrue(html.contains("<details"), () -> "missing expandable details: " + html);
        assertFalse(html.contains("<details open"), () -> "details must stay collapsed for Allure iframe clip: " + html);
        assertEquals(1, html.split("<tr class=\"har-row\"", -1).length - 1,
                () -> "exactly one data row per entry: " + html);
        assertTrue(html.contains("Response Headers"), () -> "missing response headers section: " + html);
        assertTrue(html.contains("Request Headers"), () -> "missing request headers section: " + html);
        assertTrue(html.contains("Content-Type"), () -> "missing response header value: " + html);
        assertTrue(html.contains("Accept"), () -> "missing request header value: " + html);
        assertTrue(html.contains("capture.har"), () -> "missing raw HAR attachment hint: " + html);
        assertTrue(html.contains("border-collapse:collapse"), () -> "missing inline table styles for DOMPurify: " + html);
        assertFalse(html.contains("data:application/json;base64,"),
                () -> "must not embed giant data URI: " + html);
        assertFalse(html.contains("__CONTENT__"), () -> "placeholder not replaced: " + html);
        assertFalse(html.contains("__SUMMARY__"), () -> "summary placeholder not replaced: " + html);
    }
}
