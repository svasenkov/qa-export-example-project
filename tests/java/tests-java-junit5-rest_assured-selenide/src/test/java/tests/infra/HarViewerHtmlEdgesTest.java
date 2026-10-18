package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import helpers.HarViewerHtml;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HarViewerHtml} render matrix: invalid input, empty logs, status/timing/
 * size formatting branches. Everything flows through the public {@code render}.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("HAR viewer")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("HarViewerHtml edge cases")
class HarViewerHtmlEdgesTest extends AllureMeta {

    private static byte[] har(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("render rejects null and empty input")
    void renderRejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> HarViewerHtml.render(null));
        assertThrows(IllegalArgumentException.class, () -> HarViewerHtml.render(new byte[0]));
    }

    @Test
    @DisplayName("render reports invalid JSON and a missing log section")
    void renderReportsBrokenInput() {
        var parseError = HarViewerHtml.render(har("this is not json"));
        assertTrue(parseError.contains("Parse error"), "invalid JSON → parse error page");
        assertTrue(parseError.contains("Failed to render HAR"), "error detail embedded");

        var noLog = HarViewerHtml.render(har("{\"entries\":[]}"));
        assertTrue(noLog.contains("Invalid HAR"), "no log section → invalid HAR page");
        assertTrue(noLog.contains("Missing log section"), "reason shown");

        var nullRoot = HarViewerHtml.render(har("null"));
        assertTrue(nullRoot.contains("Parse error"), "non-object JSON → parse error");
    }

    @Test
    @DisplayName("render handles missing and non-map entries")
    void renderHandlesEmptyAndNonMapEntries() {
        var noEntries = HarViewerHtml.render(har("{\"log\":{\"version\":\"1.2\"}}"));
        assertTrue(noEntries.contains("0 requests"), "summary without entries");
        assertTrue(noEntries.contains("No network entries captured"), "empty body");

        var emptyList = HarViewerHtml.render(har("{\"log\":{\"entries\":[]}}"));
        assertTrue(emptyList.contains("No network entries captured"));

        var nonMap = HarViewerHtml.render(har("{\"log\":{\"entries\":[\"x\",42]}}"));
        assertTrue(nonMap.contains("No network entries captured"), "non-map entries skipped");
        assertTrue(nonMap.contains("2 requests"), "summary counts raw entries");
    }

    @Test
    @DisplayName("render maps status codes to labels and classes")
    void renderStatusClasses() {
        String entry = """
                {"time":%s,"request":{"method":"%s","url":"https://u%s/"},"response":{"status":%s,"statusText":"%s","content":{"size":%s,"mimeType":"%s","text":%s},"headers":[%s]},"timings":{"blocked":%s}}
                """;
        StringBuilder sb = new StringBuilder("{\"log\":{\"entries\":[");
        // statuses: 0 (muted), 301 (redir), 404 (warn), 503 (err), 200 (ok)
        sb.append(entry.formatted("0", "", "/a", "0", "", "0", "", "null", "", "-1"));
        sb.append(',');
        sb.append(entry.formatted("1234.5", "post", "/b", "301", "Found", "2048", "", "\"<b>body</b>\"", "{\"n\":\"v\"}", "0"));
        sb.append(',');
        sb.append(entry.formatted("\"notanum\"", "GET", "/c", "404", "Not Found", "1048576", "text/plain", "\"\"", "", "1"));
        sb.append(',');
        sb.append(entry.formatted("5", "GET", "/d", "503", "Bad", "1", "text/plain", "null", "", "2.5"));
        sb.append(',');
        sb.append(entry.formatted("5", "GET", "/e", "200", "OK", "-1", "text/plain", "null", "", "2.5"));
        sb.append("]}}");

        String html = HarViewerHtml.render(har(sb.toString()));

        assertTrue(html.contains("5 requests"), "summary counts all entries");
        assertTrue(html.contains("har-status--muted"), "status 0 muted");
        assertTrue(html.contains("har-status--redir"), "3xx redir");
        assertTrue(html.contains("har-status--warn"), "4xx warn");
        assertTrue(html.contains("har-status--err"), "5xx err");
        assertTrue(html.contains("har-status--ok"), "2xx ok");
        assertTrue(html.contains("color:#f48771"), "5xx color");
        assertTrue(html.contains("color:#cca700"), "4xx color");
        assertTrue(html.contains("color:#6cb6ff"), "3xx color");
        assertTrue(html.contains("color:#999"), "status 0 color");
        assertTrue(html.contains("2.0 KB"), "KB formatted");
        assertTrue(html.contains("1.0 MB"), "MB formatted");
        assertTrue(html.contains(">—</span>"), "status 0 label and empty mime");
        assertTrue(html.contains("1 B"), "bytes formatted");
        assertTrue(html.contains("&lt;b&gt;body&lt;/b&gt;"), "body text escaped");
        assertTrue(html.contains("Body not captured"), "null body note for another row");
        assertTrue(html.contains("POST"), "method uppercased");
        assertTrue(html.contains(">GET</span>"), "missing method defaults to GET");
        assertTrue(html.contains("—"), "negative timing rendered as dash");
    }

    @Test
    @DisplayName("render tolerates string-typed numbers and non-map header items")
    void renderToleratesTypedJunk() {
        var html = HarViewerHtml.render(har(
                "{\"log\":{\"entries\":[{"
                        + "\"time\":\"2.5\","
                        + "\"request\":{\"method\":\"GET\",\"url\":\"https://f/\"},"
                        + "\"response\":{\"status\":\"503\",\"statusText\":\"Bad\","
                        + "\"content\":{\"size\":\"1024\",\"mimeType\":\"text/plain\",\"text\":null},"
                        + "\"headers\":[\"stray\",{\"name\":\"n\",\"value\":\"\"},{\"name\":\"x\",\"value\":\"v\"}]},"
                        + "\"timings\":{}}]}}"));
        assertTrue(html.contains("1 requests"), "summary");
        assertTrue(html.contains("har-status--muted"), "string status parses as 0 → muted");
        assertTrue(html.contains(">n</div>") || html.contains("n</"), "header name rendered");
    }

    @Test
    @DisplayName("render escapes html in values and marks absent header lists")
    void renderEscapesAndMarksEmptyHeaders() {
        var html = HarViewerHtml.render(har(
                "{\"log\":{\"entries\":[{\"request\":{\"method\":\"GET\",\"url\":\"https://x/?a=<b>&c=\\\"d\\\"\"},"
                        + "\"response\":{\"status\":200,\"headers\":\"junk\"},\"timings\":{}}]}}"));
        assertFalse(html.contains("<b>\"d\""), "url escaped");
        assertTrue(html.contains("&lt;b&gt;"), "angle brackets escaped");
        assertTrue(html.contains("No headers captured"), "non-list headers → muted note");
    }

    // --- template resource failure modes (shadow class loader) -------------------

    /** Child-first loader for HarViewerHtml; the template resource is injected. */
    private static java.net.URLClassLoader templateLoader(java.net.URL template)
            throws java.io.IOException {
        java.net.URL classesRoot;
        try {
            classesRoot = new java.io.File(HarViewerHtml.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toURI().toURL();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        var parent = HarViewerHtmlEdgesTest.class.getClassLoader();
        return new java.net.URLClassLoader(new java.net.URL[]{classesRoot}, parent) {
            @Override
            public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("helpers.HarViewerHtml")) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        c = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            }

            @Override
            public java.net.URL getResource(String name) {
                if ("allure/har-viewer-template.html".equals(name)) {
                    return template;
                }
                return super.getResource(name);
            }
        };
    }

    @Test
    @DisplayName("class init fails closed when the template resource is missing")
    void templateMissing() throws Exception {
        var error = assertThrows(ExceptionInInitializerError.class, () ->
                Class.forName("helpers.HarViewerHtml", true, templateLoader(null)));
        assertTrue(error.getCause() instanceof IllegalStateException);
        assertTrue(error.getCause().getMessage().contains("Missing classpath resource"),
                error.getCause().getMessage());
    }

    @Test
    @DisplayName("class init wraps a template read failure")
    void templateUnreadable() throws Exception {
        var handler = new java.net.URLStreamHandler() {
            @Override
            protected java.net.URLConnection openConnection(java.net.URL u) {
                return new java.net.URLConnection(u) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public java.io.InputStream getInputStream() {
                        return new java.io.InputStream() {
                            @Override
                            public int read() throws java.io.IOException {
                                throw new java.io.IOException("simulated read failure");
                            }
                        };
                    }
                };
            }
        };
        var broken = new java.net.URL("faketpl", null, -1, "virtual", handler);
        var error = assertThrows(ExceptionInInitializerError.class, () ->
                Class.forName("helpers.HarViewerHtml", true, templateLoader(broken)));
        assertTrue(error.getCause() instanceof IllegalStateException);
        assertTrue(error.getCause().getMessage().contains("Failed to load"),
                error.getCause().getMessage());
    }
}
