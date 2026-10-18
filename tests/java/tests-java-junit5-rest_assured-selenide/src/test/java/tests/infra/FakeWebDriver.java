package tests.infra;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.SessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RemoteWebDriver stub for harness unit tests — a {@code RemoteWebDriver}
 * subclass so checks like {@code instanceof RemoteWebDriver} / capabilities
 * see a real contract. Elements are scripted per selector, scripts return
 * scripted values, and every external boundary (logs, url, alert) can fail
 * on demand. Never talks to a real browser.
 */
public class FakeWebDriver extends RemoteWebDriver {

    private final Map<String, FakeWebElement> elements = new ConcurrentHashMap<>();
    private final Map<String, Object> scriptResults = new ConcurrentHashMap<>();
    private final List<String> visitedUrls = new ArrayList<>();
    private final FakeOptions options = new FakeOptions();
    private final FakeTargetLocator targetLocator = new FakeTargetLocator();
    private String currentUrl = "http://stand.local/";
    private String pageSource = "<html><body>fake</body></html>";
    private byte[] screenshotBytes = new byte[0];
    private RuntimeException onScript;
    private RuntimeException onUrl;
    private RuntimeException onPageSource;
    private RuntimeException onScreenshot;
    private RuntimeException onSessionId;
    private LogEntries performanceLogs = new LogEntries(List.of());
    private RuntimeException onPerformanceLogs;
    private SessionId sessionId = new SessionId("fake-session");
    private boolean closed;

    public FakeWebDriver(String browserName) {
        capabilities = new ImmutableCapabilities("browserName", browserName);
    }

    public FakeWebDriver() {
        this("chrome");
    }

    /** Adds extra capabilities — e.g. {@code se:cdp} to reach the Augmenter path. */
    FakeWebDriver withCapability(String name, Object value) {
        var merged = new org.openqa.selenium.MutableCapabilities(capabilities);
        merged.setCapability(name, value);
        capabilities = new ImmutableCapabilities(merged);
        return this;
    }

    FakeWebElement element(String cssSelector) {
        return elements.computeIfAbsent(cssSelector, key -> new FakeWebElement());
    }

    FakeWebDriver url(String url) {
        currentUrl = url;
        return this;
    }

    FakeWebDriver pageSource(String source) {
        pageSource = source;
        return this;
    }

    FakeWebDriver screenshot(byte[] png) {
        screenshotBytes = png;
        return this;
    }

    FakeWebDriver scriptResult(String scriptFragment, Object result) {
        scriptResults.put(scriptFragment, result);
        return this;
    }

    /** Results consumed one-per-call — for polling branches (Wait().until). */
    FakeWebDriver scriptResultSequence(String scriptFragment, Object... results) {
        // LinkedList (not ArrayDeque): null is a legitimate scripted result.
        scriptResults.put(scriptFragment,
                new java.util.LinkedList<>(java.util.Arrays.asList(results)));
        return this;
    }

    FakeWebDriver performanceLogs(LogEntries entries) {
        performanceLogs = entries;
        return this;
    }

    FakeWebDriver failOnScript(RuntimeException failure) {
        onScript = failure;
        return this;
    }

    FakeWebDriver failOnUrl(RuntimeException failure) {
        onUrl = failure;
        return this;
    }

    FakeWebDriver failOnPageSource(RuntimeException failure) {
        onPageSource = failure;
        return this;
    }

    FakeWebDriver failOnScreenshot(RuntimeException failure) {
        onScreenshot = failure;
        return this;
    }

    FakeWebDriver failOnSessionId(RuntimeException failure) {
        onSessionId = failure;
        return this;
    }

    FakeWebDriver failOnPerformanceLogs(RuntimeException failure) {
        onPerformanceLogs = failure;
        return this;
    }

    FakeAlert alert() {
        return targetLocator.alert;
    }

    boolean closed() {
        return closed;
    }

    List<String> visitedUrls() {
        return visitedUrls;
    }

    private static String selectorOf(By by) {
        var raw = by.toString();
        var quote = raw.indexOf('\'');
        var end = raw.indexOf('\'', quote + 1);
        if (quote >= 0 && end > quote) {
            return raw.substring(quote + 1, end);
        }
        var colon = raw.indexOf(": ");
        return colon >= 0 ? raw.substring(colon + 2) : raw;
    }

    @Override
    public void get(String url) {
        visitedUrls.add(url);
        currentUrl = url;
    }

    @Override
    public String getCurrentUrl() {
        if (onUrl != null) {
            throw onUrl;
        }
        return currentUrl;
    }

    @Override
    public String getTitle() {
        return "fake";
    }

    @Override
    public String getPageSource() {
        if (onPageSource != null) {
            throw onPageSource;
        }
        return pageSource;
    }

    @Override
    public WebElement findElement(By by) {
        return element(selectorOf(by));
    }

    @Override
    public List<WebElement> findElements(By by) {
        return List.of(element(selectorOf(by)));
    }

    @Override
    public Object executeScript(String script, Object... args) {
        if (onScript != null) {
            throw onScript;
        }
        var key = scriptResults.keySet().stream()
                .filter(script::contains)
                .findFirst();
        var value = key.map(scriptResults::get).orElse(null);
        if (value instanceof java.util.Queue<?> queue) {
            return queue.isEmpty() ? null : queue.poll();
        }
        return value;
    }

    @Override
    public Object executeAsyncScript(String script, Object... args) {
        return executeScript(script, args);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
        if (onScreenshot != null) {
            throw onScreenshot;
        }
        return (X) target.convertFromPngBytes(screenshotBytes);
    }

    @Override
    public SessionId getSessionId() {
        if (onSessionId != null) {
            throw onSessionId;
        }
        return sessionId;
    }

    @Override
    public Options manage() {
        return options;
    }

    @Override
    public Navigation navigate() {
        return new FakeNavigation();
    }

    @Override
    public TargetLocator switchTo() {
        return targetLocator;
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void quit() {
        closed = true;
    }

    @Override
    public Set<String> getWindowHandles() {
        return Set.of("fake");
    }

    @Override
    public String getWindowHandle() {
        return "fake";
    }

    class FakeOptions implements Options {
        private final Logs logs = new FakeLogs();
        private final FakeWindow window = new FakeWindow();

        @Override
        public void addCookie(Cookie cookie) {
        }

        @Override
        public void deleteCookieNamed(String name) {
        }

        @Override
        public void deleteCookie(Cookie cookie) {
        }

        @Override
        public void deleteAllCookies() {
        }

        @Override
        public Set<Cookie> getCookies() {
            return Set.of();
        }

        @Override
        public Cookie getCookieNamed(String name) {
            return null;
        }

        @Override
        public Timeouts timeouts() {
            return new FakeTimeouts();
        }

        @Override
        public Window window() {
            return window;
        }

        @Override
        public Logs logs() {
            return logs;
        }
    }

    class FakeLogs implements Logs {
        @Override
        public LogEntries get(String logType) {
            if (onPerformanceLogs != null) {
                throw onPerformanceLogs;
            }
            return performanceLogs;
        }

        @Override
        public Set<String> getAvailableLogTypes() {
            return Set.of("performance");
        }
    }

    class FakeTimeouts implements Timeouts {
        @Override
        public Timeouts implicitlyWait(java.time.Duration duration) {
            return this;
        }

        @Override
        public Timeouts scriptTimeout(java.time.Duration duration) {
            return this;
        }

        @Override
        public Timeouts pageLoadTimeout(java.time.Duration duration) {
            return this;
        }
    }

    class FakeWindow implements Window {
        private Dimension size = new Dimension(1920, 1280);

        @Override
        public Dimension getSize() {
            return size;
        }

        @Override
        public void setSize(Dimension targetSize) {
            size = targetSize;
        }

        @Override
        public Point getPosition() {
            return new Point(0, 0);
        }

        @Override
        public void setPosition(Point targetPosition) {
        }

        @Override
        public void maximize() {
        }

        @Override
        public void minimize() {
        }

        @Override
        public void fullscreen() {
        }
    }

    class FakeNavigation implements Navigation {
        @Override
        public void back() {
        }

        @Override
        public void forward() {
        }

        @Override
        public void to(String url) {
            get(url);
        }

        @Override
        public void to(java.net.URL url) {
            get(url.toString());
        }

        @Override
        public void refresh() {
        }
    }

    class FakeTargetLocator implements TargetLocator {
        private final FakeAlert alert = new FakeAlert();

        @Override
        public WebDriver frame(int index) {
            return FakeWebDriver.this;
        }

        @Override
        public WebDriver frame(String nameOrId) {
            return FakeWebDriver.this;
        }

        @Override
        public WebDriver frame(WebElement frameElement) {
            return FakeWebDriver.this;
        }

        @Override
        public WebDriver parentFrame() {
            return FakeWebDriver.this;
        }

        @Override
        public WebDriver defaultContent() {
            return FakeWebDriver.this;
        }

        @Override
        public WebDriver window(String nameOrHandle) {
            return FakeWebDriver.this;
        }

        @Override
        public WebDriver newWindow(WindowType typeHint) {
            return FakeWebDriver.this;
        }

        @Override
        public WebElement activeElement() {
            return new FakeWebElement();
        }

        @Override
        public FakeAlert alert() {
            return alert;
        }
    }

    /** FakeWebDriver with real CDP capability — records executed commands. */
    public static class FakeCdpDriver extends FakeWebDriver
            implements org.openqa.selenium.chromium.HasCdp {
        private final List<String> cdpCommands = new ArrayList<>();

        public FakeCdpDriver() {
        }

        public FakeCdpDriver(String browserName) {
            super(browserName);
        }

        List<String> cdpCommands() {
            return cdpCommands;
        }

        @Override
        public Map<String, Object> executeCdpCommand(String commandName,
                Map<String, Object> parameters) {
            cdpCommands.add(commandName);
            return Map.of();
        }
    }

    static class FakeAlert implements org.openqa.selenium.Alert {
        private String text = "";
        private boolean accepted;
        private boolean dismissed;

        FakeAlert text(String value) {
            text = value;
            return this;
        }

        boolean accepted() {
            return accepted;
        }

        boolean dismissed() {
            return dismissed;
        }

        @Override
        public void dismiss() {
            dismissed = true;
        }

        @Override
        public void accept() {
            accepted = true;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public void sendKeys(String keysToSend) {
        }
    }
}
