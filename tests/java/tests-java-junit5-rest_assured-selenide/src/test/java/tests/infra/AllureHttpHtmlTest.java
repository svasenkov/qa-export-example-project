package tests.infra;

import annotations.Layer;
import helpers.AllureHtmlPreview;
import helpers.AllureHttpHtml;
import io.qameta.allure.attachment.http.HttpRequestAttachment;
import io.qameta.allure.attachment.http.HttpResponseAttachment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Layer("infra")
@Tag("infra")
@Tag("infra-backend")
class AllureHttpHtmlTest {

    private static final Path GALLERY = Path.of("build/http-attachment-preview");

    /**
     * Inline accents readable on Allure light {@code #fff} and dark {@code #1c1c1e}.
     * One palette — iframe has no {@code prefers-color-scheme} after sanitizer strip.
     */
    static final String COLOR_METHOD = "#0d9488";
    static final String COLOR_VALUE = "#db2777";
    static final String COLOR_KEY = "#0284c7";

    /** Backgrounds that paint an island over Allure's iframe theme. */
    private static final List<String> ISLAND_BACKGROUNDS = List.of(
            "#f4f4f5", "#f5f5f5", "#2c2c30", "#282a2e", "#1a1a1a",
            "background:#fff", "background:#ffffff", "background:#333",
            "color:#333", "color:#666");

    @Test
    void sanitizerStripsStyleAndScriptThenDarkThemeInjectsIntoHead() {
        String raw = "<html><head><style>body{color:red}</style></head>"
                + "<body><script>document.body.innerHTML='gone'</script><p>keep-me</p></body></html>";

        String light = AllureHtmlPreview.asIframe(raw, false);
        assertFalse(light.contains("<style"), light);
        assertFalse(light.contains("<script"), light);
        assertTrue(light.contains("keep-me"), light);
        assertFalse(light.contains("gone"), light);

        String dark = AllureHtmlPreview.asIframe(raw, true);
        assertTrue(dark.contains("data-allure-preview-theme"), dark);
        assertTrue(dark.contains("#1c1c1e"), dark);
        assertTrue(dark.contains("keep-me"), dark);
        assertFalse(dark.contains("document.body"), dark);
    }

    @Test
    void coloredFtlSurvivesAllureIframeWithoutThemeIslands() throws Exception {
        HttpRequestAttachment request = HttpRequestAttachment.Builder
                .create("Request", "https://autotests.ai/api/login")
                .setMethod("POST")
                .setHeader("Content-Type", "application/json")
                .setBody("{\"username\": \"user1\", \"password\": \"password1\"}")
                .build();
        HttpResponseAttachment response = HttpResponseAttachment.Builder
                .create("HTTP/1.1 200")
                .setUrl("https://autotests.ai/api/login")
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"token\": \"eyJhbGciOiJIUzI1NiJ9\", \"username\": \"user1\", \"redirectUrl\": \"/\"}")
                .build();

        String requestHtml = AllureHttpHtml.renderRequest(request);
        String responseHtml = AllureHttpHtml.renderResponse(response);

        assertRequestShape(requestHtml);
        assertResponseShape(responseHtml);
        assertIframeContract(requestHtml);
        assertIframeContract(responseHtml);

        AllureHtmlPreview.writeGallery(GALLERY, requestHtml, responseHtml);
        assertTrue(Files.isRegularFile(GALLERY.resolve("index.html")));
        String darkRequest = Files.readString(GALLERY.resolve("request-dark.html"));
        assertTrue(darkRequest.contains("data-allure-preview-theme"), darkRequest);
        assertTrue(darkRequest.contains("user1"), darkRequest);
        assertFalse(darkRequest.contains("<script"), darkRequest);
    }

    private static void assertRequestShape(String html) {
        assertTrue(html.contains("POST"), html);
        assertTrue(html.contains("https://autotests.ai/api/login"), html);
        assertTrue(html.contains("user1"), html);
        assertTrue(html.contains("password1"), html);
        assertTrue(html.contains("Content-Type"), html);
        assertTrue(html.contains(">Curl</h4>"), html);
        assertFalse(html.contains("${data"), html);
    }

    private static void assertResponseShape(String html) {
        assertTrue(html.contains("200"), html);
        assertTrue(html.contains("redirectUrl"), html);
        assertTrue(html.contains("token"), html);
        assertFalse(html.contains("${data"), html);
    }

    private static void assertIframeContract(String raw) {
        String sanitized = AllureHtmlPreview.stripUnsafe(raw);
        assertTrue(sanitized.contains("color:inherit"), () -> "code must inherit Allure body color: " + sanitized);
        assertTrue(sanitized.contains("background:transparent"), sanitized);
        for (String island : ISLAND_BACKGROUNDS) {
            assertFalse(sanitized.toLowerCase().contains(island.toLowerCase()),
                    () -> "island " + island + " in sanitizer view: " + sanitized);
        }
        assertFalse(sanitized.contains("color:#1a1a1a"), () -> "forced dark text: " + sanitized);
        assertFalse(sanitized.contains("<script"), sanitized);
        assertFalse(sanitized.contains("<style"), sanitized);
        assertTrue(sanitized.contains("style=\"color:" + COLOR_METHOD), sanitized);
        assertTrue(sanitized.contains("style=\"color:" + COLOR_VALUE), sanitized);
        assertFalse(sanitized.contains("style=\"color:" + COLOR_KEY),
                () -> "JSON key color is JS-only; iframe must inherit body color: " + sanitized);
    }
}
