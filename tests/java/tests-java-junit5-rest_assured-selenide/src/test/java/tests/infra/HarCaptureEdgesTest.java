package tests.infra;

import com.codeborne.selenide.WebDriverRunner;
import helpers.HarCapture;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import annotations.Layer;
import tests.AllureMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases of {@link HarCapture}: capability merge, driver guards and the
 * defensive parse branches of {@code toHar}. Driver-dependent paths run
 * against {@link FakeWebDriver} — no real browser.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("HAR capture")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("HarCapture edge cases")
class HarCaptureEdgesTest extends AllureMeta {

    @AfterEach
    void closeFakeDriver() {
        if (WebDriverRunner.hasWebDriverStarted()) {
            WebDriverRunner.closeWebDriver();
        }
    }

    private static LogEntry logEntry(String message) {
        return new LogEntry(Level.INFO, System.currentTimeMillis(), message);
    }

    @Test
    @DisplayName("supportsBrowser accepts chromium family only")
    void supportsBrowserMatrix() {
        assertFalse(HarCapture.supportsBrowser(null));
        assertFalse(HarCapture.supportsBrowser("   "));
        assertFalse(HarCapture.supportsBrowser("firefox"));
        assertFalse(HarCapture.supportsBrowser("safari"));
        assertTrue(HarCapture.supportsBrowser("chrome"));
        assertTrue(HarCapture.supportsBrowser("CHROME-headless"));
        assertTrue(HarCapture.supportsBrowser("msedge"));
        assertTrue(HarCapture.supportsBrowser("chromium"));
    }

    @Test
    @DisplayName("enablePerformanceLogging merges goog:loggingPrefs on plain and chromium caps")
    void enablePerformanceLoggingOnBothCapabilityKinds() {
        var plain = new MutableCapabilities();
        HarCapture.enablePerformanceLogging(plain);
        var plainPrefs = plain.getCapability("goog:loggingPrefs");
        assertTrue(plainPrefs instanceof org.openqa.selenium.logging.LoggingPreferences);

        var chrome = new ChromeOptions();
        HarCapture.enablePerformanceLogging(chrome);
        var chromePrefs = chrome.getCapability("goog:loggingPrefs");
        assertTrue(chromePrefs instanceof org.openqa.selenium.logging.LoggingPreferences);
    }

    @Test
    @DisplayName("collectHarJson returns empty without a driver, on non-remote and non-chromium drivers")
    void collectHarJsonGuards() {
        assertTrue(HarCapture.collectHarJson().isEmpty(), "no driver");

        var plainDriver = new PlainWebDriver();
        WebDriverRunner.setWebDriver(plainDriver);
        assertTrue(HarCapture.collectHarJson().isEmpty(), "non-RemoteWebDriver");
        WebDriverRunner.closeWebDriver();

        WebDriverRunner.setWebDriver(new FakeWebDriver("firefox"));
        assertTrue(HarCapture.collectHarJson().isEmpty(), "non-chromium remote driver");
        WebDriverRunner.closeWebDriver();
    }

    @Test
    @DisplayName("collectHarJson reads performance logs and swallows driver errors")
    void collectHarJsonReadsAndFailsSoft() {
        var driver = new FakeWebDriver("chrome").performanceLogs(new LogEntries(List.of(
                logEntry("{\"message\":{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"r1\",\"timestamp\":1.0,\"request\":{\"url\":\"https://a/\",\"method\":\"GET\",\"headers\":{}}}}}"))));
        WebDriverRunner.setWebDriver(driver);
        var har = HarCapture.collectHarJson();
        assertTrue(har.isPresent());
        var request = requestOf(harEntries(new String(har.get())).get(0));
        assertEquals("https://a/", request.get("url"));
        WebDriverRunner.closeWebDriver();

        var broken = new FakeWebDriver("chrome")
                .failOnPerformanceLogs(new RuntimeException("logs unavailable"));
        WebDriverRunner.setWebDriver(broken);
        assertTrue(HarCapture.collectHarJson().isEmpty(), "log read failure → empty");
    }

    @Test
    @DisplayName("toHar tolerates malformed entries and bare CDP events")
    void toHarSkipsMalformedEntries() {
        LogEntries entries = new LogEntries(List.of(
                logEntry("not json at all"),
                logEntry("{\"message\":\"{not valid json"),
                logEntry("{\"message\":42}"),
                logEntry("{\"message\":{\"params\":{}}}"),
                logEntry("{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"\",\"request\":{}}}"),
                logEntry("{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"r2\"}}"),
                logEntry("{\"method\":\"Network.responseReceived\",\"params\":{\"requestId\":\"\",\"response\":{}}}"),
                logEntry("{\"method\":\"Network.responseReceived\",\"params\":{\"requestId\":\"r9\",\"response\":\"nope\"}}"),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"requestId\":\"\",\"timestamp\":2.0}}"),
                logEntry("{\"method\":\"Network.dataReceived\",\"params\":{\"requestId\":\"x\",\"dataLength\":3}}"),
                logEntry("{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"r9\",\"timestamp\":2.0,\"request\":{\"url\":\"https://b/\",\"method\":\"\",\"headers\":\"junk\"}}}"),
                logEntry("{\"method\":\"Network.responseReceived\",\"params\":{\"requestId\":\"r9\",\"response\":{\"status\":204,\"protocol\":\"h2\"}}}"),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"requestId\":\"r9\",\"timestamp\":1.0,\"encodedDataLength\":\"abc\"}}")
        ));

        String har = HarCapture.toHar(entries);

        var harEntries = harEntries(har);
        assertEquals(1, harEntries.size(), "only the valid entry survives: " + har);
        var entry = harEntries.get(0);
        var request = requestOf(entry);
        assertEquals("https://b/", request.get("url"));
        assertEquals(204, ((Number) responseOf(entry).get("status")).intValue(),
                "response joined by requestId");
        assertEquals("h2", responseOf(entry).get("httpVersion"),
                "non-empty protocol passes through");
        assertEquals(0.0, ((Number) entry.get("time")).doubleValue(),
                "negative duration clamps to 0");
        assertEquals("GET", request.get("method"), "empty method defaults to GET");
        assertEquals(List.of(), request.get("headers"), "non-map headers → empty list");
    }

    @Test
    @DisplayName("toHar deduplicates request ids and prefers loadingFinished sizes")
    void toHarDedupesAndReadsEncodedDataLength() {
        String req = "{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"r1\",\"timestamp\":1.0,"
                + "\"request\":{\"url\":\"https://dup/\",\"method\":\"POST\",\"headers\":{\"a\":\"b\"}}}}";
        LogEntries entries = new LogEntries(List.of(
                logEntry(req), logEntry(req),
                logEntry("{\"method\":\"Network.responseReceived\",\"params\":{\"requestId\":\"r1\",\"response\":{\"status\":301,\"statusText\":\"Moved\",\"protocol\":\"\",\"mimeType\":\"x/y\",\"encodedDataLength\":7,\"headers\":{\"x\":null}}}}"),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"requestId\":\"r1\",\"timestamp\":1.5}}")
        ));

        String har = HarCapture.toHar(entries);

        var harEntries = harEntries(har);
        assertEquals(1, harEntries.size(), "one entry per requestId: " + har);
        var entry = harEntries.get(0);
        var request = requestOf(entry);
        var response = responseOf(entry);
        assertEquals("https://dup/", request.get("url"));
        assertEquals(301, ((Number) response.get("status")).intValue(),
                "numeric status passes through");
        var content = (Map<String, Object>) response.get("content");
        assertEquals(7L, ((Number) content.get("size")).longValue(),
                "response encodedDataLength fallback");
        var headers = (List<Map<String, Object>>) response.get("headers");
        assertEquals("", headers.get(0).get("value"), "null header value renders empty");
        assertEquals("HTTP/1.1", response.get("httpVersion"), "empty protocol defaults");
        assertEquals("x/y", content.get("mimeType"));
        assertEquals(500.0, ((Number) entry.get("time")).doubleValue(),
                "loadingFinished timestamp − request timestamp");
    }

    @Test
    @DisplayName("toHar uses wallTime and falls back to wall-clock estimate")
    void toHarTimestampBranches() {
        String wallTimed = "{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"w\",\"timestamp\":1.0,"
                + "\"wallTime\":1700000000.0,\"request\":{\"url\":\"https://w/\",\"method\":\"GET\",\"headers\":{}}}}";
        String noWall = "{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"n\",\"timestamp\":1.5,"
                + "\"request\":{\"url\":\"https://n/\",\"method\":\"GET\",\"headers\":{}}}}";
        String noTs = "{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"z\",\"timestamp\":\"bad\","
                + "\"request\":{\"url\":\"https://z/\",\"method\":\"GET\",\"headers\":{}}}}";
        String validStartBadEnd = "{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"m\",\"timestamp\":3.0,"
                + "\"request\":{\"url\":\"https://m/\",\"method\":\"GET\",\"headers\":{}}}}";
        LogEntries entries = new LogEntries(List.of(
                logEntry(noTs),
                logEntry(wallTimed),
                logEntry(noWall),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"requestId\":\"n\",\"timestamp\":\"2.0\"}}"),
                logEntry(validStartBadEnd),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"requestId\":\"m\",\"timestamp\":\"bad\"}}"),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"requestId\":\"z\",\"timestamp\":\"bad\"}}")
        ));

        String har = HarCapture.toHar(entries);

        var harEntries = harEntries(har);
        assertEquals(4, harEntries.size(), "all four ids become entries: " + har);
        var byUrl = new java.util.HashMap<String, Map<String, Object>>();
        for (var entry : harEntries) {
            byUrl.put(String.valueOf(requestOf(entry).get("url")), entry);
        }
        assertTrue(String.valueOf(byUrl.get("https://w/").get("startedDateTime"))
                        .startsWith("2023-11-14T22:13:20"),
                "wallTime drives startedDateTime");
        assertEquals(500.0, ((Number) byUrl.get("https://n/").get("time")).doubleValue(),
                "duration from timestamps");
        assertEquals(0.0, ((Number) byUrl.get("https://z/").get("time")).doubleValue(),
                "NaN timestamps still produce an entry with time 0");
        assertEquals(0.0, ((Number) byUrl.get("https://m/").get("time")).doubleValue(),
                "NaN end timestamp still produces an entry with time 0");

        String lone = HarCapture.toHar(new LogEntries(List.of(logEntry(noTs))));
        assertEquals(1, harEntries(lone).size(), "orphan bad-timestamp request still yields an entry");
    }

    @Test
    @DisplayName("toHar handles string-typed messages and remaining guard arms")
    void toHarMessageShapes() {
        // Real chromedriver wraps the CDP event in a JSON *string* under "message".
        String inner = "{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"s\","
                + "\"timestamp\":9.0,\"wallTime\":null,"
                + "\"request\":{\"url\":\"https://s/\",\"method\":\"GET\",\"headers\":{}}}}";
        var quoted = inner.replace("\\", "\\\\").replace("\"", "\\\"");
        LogEntries entries = new LogEntries(List.of(
                logEntry("{\"message\":\"" + quoted + "\"}"),          // valid string-message
                logEntry("{\"message\":\"{broken\"}"),                // unparseable string-message
                logEntry("{\"method\":\"Some.event\"}"),              // method without params
                logEntry("{\"message\":{\"method\":\"x\",\"params\":1}}"), // params not a map
                logEntry("{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"p\",\"request\":\"x\"}}"),
                logEntry("{\"method\":\"Network.requestWillBeSent\",\"params\":{\"requestId\":\"n2\",\"timestamp\":null,\"request\":{\"url\":\"https://n/\",\"method\":\"GET\",\"headers\":{}}}}"),
                logEntry("{\"method\":\"Network.responseReceived\",\"params\":{\"requestId\":\"p\",\"response\":[]}}"),
                logEntry("{\"method\":\"Network.loadingFinished\",\"params\":{\"timestamp\":1.5}}")
        ));

        String har = HarCapture.toHar(entries);

        var harEntries = harEntries(har);
        assertEquals(2, harEntries.size(), "string-message + null-timestamp entries survive: " + har);
        var byUrl = new java.util.HashMap<String, Map<String, Object>>();
        for (var entry : harEntries) {
            byUrl.put(String.valueOf(requestOf(entry).get("url")), entry);
        }
        assertEquals("https://s/", byUrl.get("https://s/").get("request") != null
                ? requestOf(byUrl.get("https://s/")).get("url") : null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> harEntries(String har) {
        var parsed = (Map<String, Object>) new org.openqa.selenium.json.Json()
                .toType(har, Map.class);
        var log = (Map<String, Object>) parsed.get("log");
        return (List<Map<String, Object>>) log.get("entries");
    }

    private static Map<String, Object> requestOf(Map<String, Object> entry) {
        @SuppressWarnings("unchecked")
        var request = (Map<String, Object>) entry.get("request");
        return request;
    }

    private static Map<String, Object> responseOf(Map<String, Object> entry) {
        @SuppressWarnings("unchecked")
        var response = (Map<String, Object>) entry.get("response");
        return response;
    }

    /** Minimal non-remote WebDriver — collectHarJson must not classify it as chromium. */
    private static final class PlainWebDriver implements org.openqa.selenium.WebDriver {
        @Override public void get(String url) { }
        @Override public String getCurrentUrl() { return "http://x/"; }
        @Override public String getTitle() { return ""; }
        @Override public List<org.openqa.selenium.WebElement> findElements(org.openqa.selenium.By by) { return List.of(); }
        @Override public org.openqa.selenium.WebElement findElement(org.openqa.selenium.By by) { return new FakeWebElement(); }
        @Override public String getPageSource() { return ""; }
        @Override public void close() { }
        @Override public void quit() { }
        @Override public java.util.Set<String> getWindowHandles() { return java.util.Set.of(); }
        @Override public String getWindowHandle() { return "w"; }
        @Override public TargetLocator switchTo() { throw new UnsupportedOperationException(); }
        @Override public Navigation navigate() { throw new UnsupportedOperationException(); }
        @Override public Options manage() { throw new UnsupportedOperationException(); }
    }
}
