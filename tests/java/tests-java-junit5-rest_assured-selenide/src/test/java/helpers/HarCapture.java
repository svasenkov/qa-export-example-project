package helpers;

import com.codeborne.selenide.WebDriverRunner;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Client-side HAR from Chrome/Edge Performance logs (CDP Network events).
 * Hub {@code enableHAR} is a no-op — capture lives in the test process.
 */
public final class HarCapture {

    private static final Json JSON = new Json();

    private HarCapture() {
    }

    public static boolean supportsBrowser(String browser) {
        if (browser == null || browser.isBlank()) {
            return false;
        }
        String b = browser.toLowerCase(Locale.ROOT);
        return b.contains("chrome") || b.contains("edge") || b.equals("chromium");
    }

    /** Merge {@code goog:loggingPrefs} so Network.* CDP events land in PERFORMANCE logs. */
    public static void enablePerformanceLogging(MutableCapabilities caps) {
        LoggingPreferences prefs = new LoggingPreferences();
        prefs.enable(LogType.PERFORMANCE, Level.ALL);
        caps.setCapability("goog:loggingPrefs", prefs);
        if (caps instanceof ChromiumOptions<?> chromium) {
            chromium.setCapability("goog:loggingPrefs", prefs);
        }
    }

    public static Optional<byte[]> collectHarJson() {
        if (!WebDriverRunner.hasWebDriverStarted()) {
            return Optional.empty();
        }
        WebDriver driver = WebDriverRunner.getWebDriver();
        if (!isChromiumDriver(driver)) {
            return Optional.empty();
        }
        try {
            LogEntries entries = driver.manage().logs().get(LogType.PERFORMANCE);
            String har = toHar(entries);
            return Optional.of(har.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean isChromiumDriver(WebDriver driver) {
        if (!(driver instanceof RemoteWebDriver remote)) {
            return false;
        }
        Capabilities caps = remote.getCapabilities();
        return supportsBrowser(caps.getBrowserName());
    }

    @SuppressWarnings("unchecked")
    public static String toHar(LogEntries entries) {
        Map<String, Map<String, Object>> requests = new LinkedHashMap<>();
        Map<String, Map<String, Object>> responses = new LinkedHashMap<>();
        Map<String, Double> finishedMs = new LinkedHashMap<>();
        Map<String, Long> encodedBytes = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        double wallStart = Double.NaN;

        for (LogEntry entry : entries) {
            Map<String, Object> root;
            try {
                root = JSON.toType(entry.getMessage(), Map.class);
            } catch (RuntimeException ex) {
                continue;
            }
            Object messageObj = root.get("message");
            Map<String, Object> message;
            if (messageObj instanceof String messageJson) {
                try {
                    message = JSON.toType(messageJson, Map.class);
                } catch (RuntimeException ex) {
                    continue;
                }
            } else if (messageObj instanceof Map<?, ?> messageRaw) {
                message = (Map<String, Object>) messageRaw;
            } else if (root.containsKey("method") && root.containsKey("params")) {
                // bare CDP event (unit tests / some drivers)
                message = root;
            } else {
                continue;
            }
            Object methodObj = message.get("method");
            Object paramsObj = message.get("params");
            if (!(methodObj instanceof String method) || !(paramsObj instanceof Map<?, ?> paramsRaw)) {
                continue;
            }
            Map<String, Object> params = (Map<String, Object>) paramsRaw;

            if ("Network.requestWillBeSent".equals(method)) {
                String id = stringVal(params.get("requestId"));
                Object requestObj = params.get("request");
                if (id.isEmpty() || !(requestObj instanceof Map<?, ?> requestRaw)) {
                    continue;
                }
                Map<String, Object> req = (Map<String, Object>) requestRaw;
                double ts = doubleVal(params.get("timestamp"));
                if (Double.isNaN(wallStart) && !Double.isNaN(ts)) {
                    wallStart = ts;
                }
                Map<String, Object> stored = new LinkedHashMap<>();
                stored.put("url", stringVal(req.get("url")));
                stored.put("method", stringVal(req.get("method")));
                stored.put("headers", req.get("headers") instanceof Map ? req.get("headers") : Map.of());
                stored.put("timestamp", ts);
                if (params.containsKey("wallTime")) {
                    stored.put("wallTime", doubleVal(params.get("wallTime")));
                }
                if (!requests.containsKey(id)) {
                    order.add(id);
                }
                requests.put(id, stored);
            } else if ("Network.responseReceived".equals(method)) {
                String id = stringVal(params.get("requestId"));
                Object responseObj = params.get("response");
                if (id.isEmpty() || !(responseObj instanceof Map<?, ?> responseRaw)) {
                    continue;
                }
                responses.put(id, (Map<String, Object>) responseRaw);
            } else if ("Network.loadingFinished".equals(method)) {
                String id = stringVal(params.get("requestId"));
                if (!id.isEmpty()) {
                    finishedMs.put(id, doubleVal(params.get("timestamp")));
                    if (params.containsKey("encodedDataLength")) {
                        encodedBytes.put(id, longVal(params.get("encodedDataLength")));
                    }
                }
            }
        }

        List<Map<String, Object>> harEntries = new ArrayList<>();
        for (String id : order) {
            Map<String, Object> req = requests.get(id);
            if (req == null) {
                continue;
            }
            Map<String, Object> resp = responses.getOrDefault(id, Map.of());
            double start = doubleVal(req.get("timestamp"));
            double end = finishedMs.getOrDefault(id, start);
            double timeMs = (!Double.isNaN(start) && !Double.isNaN(end) && end >= start)
                    ? (end - start) * 1000.0
                    : 0.0;

            long startedDateTimeMs = System.currentTimeMillis();
            if (req.containsKey("wallTime")) {
                startedDateTimeMs = (long) (doubleVal(req.get("wallTime")) * 1000.0);
            } else if (!Double.isNaN(wallStart) && !Double.isNaN(end)) {
                startedDateTimeMs = Instant.now().toEpochMilli()
                        - (long) ((end - wallStart) * 1000.0);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("startedDateTime", Instant.ofEpochMilli(startedDateTimeMs).toString());
            entry.put("time", timeMs);
            entry.put("request", harRequest(req));
            entry.put("response", harResponse(resp, encodedBytes.get(id)));
            entry.put("cache", Map.of());
            entry.put("timings", timings(timeMs));
            harEntries.add(entry);
        }

        Map<String, Object> pageTimings = new LinkedHashMap<>();
        pageTimings.put("onContentLoad", -1);
        pageTimings.put("onLoad", -1);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("startedDateTime", Instant.now().toString());
        page.put("id", "page_1");
        page.put("title", "selenide-har");
        page.put("pageTimings", pageTimings);

        Map<String, Object> creator = new LinkedHashMap<>();
        creator.put("name", "zero-design-system HarCapture");
        creator.put("version", "1");

        Map<String, Object> log = new LinkedHashMap<>();
        log.put("version", "1.2");
        log.put("creator", creator);
        log.put("pages", List.of(page));
        log.put("entries", harEntries);

        return JSON.toJson(Map.of("log", log));
    }

    private static Map<String, Object> harRequest(Map<String, Object> req) {
        Map<String, Object> out = new LinkedHashMap<>();
        String method = stringVal(req.get("method"));
        out.put("method", method.isEmpty() ? "GET" : method);
        out.put("url", stringVal(req.get("url")));
        out.put("httpVersion", "HTTP/1.1");
        out.put("cookies", List.of());
        out.put("headers", headerList(req.get("headers")));
        out.put("queryString", List.of());
        out.put("headersSize", -1);
        out.put("bodySize", -1);
        return out;
    }

    private static Map<String, Object> harResponse(Map<String, Object> resp, Long finishedEncodedBytes) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", intVal(resp.get("status")));
        out.put("statusText", stringVal(resp.get("statusText")));
        String protocol = stringVal(resp.get("protocol"));
        out.put("httpVersion", protocol.isEmpty() ? "HTTP/1.1" : protocol);
        out.put("cookies", List.of());
        out.put("headers", headerList(resp.get("headers")));
        Map<String, Object> content = new LinkedHashMap<>();
        // loadingFinished.encodedDataLength is the reliable wire size; responseReceived often has 0
        long size = finishedEncodedBytes != null ? finishedEncodedBytes : longVal(resp.get("encodedDataLength"));
        content.put("size", size);
        content.put("mimeType", stringVal(resp.get("mimeType")));
        out.put("content", content);
        out.put("redirectURL", "");
        out.put("headersSize", -1);
        out.put("bodySize", -1);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> headerList(Object headersObj) {
        List<Map<String, String>> arr = new ArrayList<>();
        if (!(headersObj instanceof Map<?, ?> headers)) {
            return arr;
        }
        for (Map.Entry<?, ?> e : headers.entrySet()) {
            Map<String, String> h = new LinkedHashMap<>();
            h.put("name", String.valueOf(e.getKey()));
            h.put("value", e.getValue() == null ? "" : String.valueOf(e.getValue()));
            arr.add(h);
        }
        return arr;
    }

    private static Map<String, Object> timings(double totalMs) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("blocked", -1);
        t.put("dns", -1);
        t.put("connect", -1);
        t.put("ssl", -1);
        t.put("send", 0);
        t.put("wait", Math.max(0, totalMs));
        t.put("receive", 0);
        return t;
    }

    private static String stringVal(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double doubleVal(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static long longVal(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
