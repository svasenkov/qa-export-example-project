package tests.infra;

import annotations.Layer;
import drivers.BrowserDriverProvider;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import tests.AllureMeta;

import config.TestConfig;
import org.aeonbits.owner.ConfigFactory;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BrowserDriverProvider} local-chrome path against the real pinned
 * Chrome (fast headless launch), plus {@code pinnedLocalChrome} options matrix.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Driver provider")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("BrowserDriverProvider")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class BrowserProviderHarnessTest extends AllureMeta {

    @Test
    @DisplayName("createDriver launches pinned local chrome headless")
    void createsLocalChromeDriver() {
        var driver = new BrowserDriverProvider().createDriver(new MutableCapabilities());
        try {
            assertNotNull(driver);
            var caps = ((org.openqa.selenium.HasCapabilities) driver).getCapabilities();
            assertEquals("chrome", caps.getBrowserName());
        } finally {
            driver.quit();
        }
    }

    @Test
    @DisplayName("pinnedLocalChrome sets binary, headless flags and optional perf logging")
    void pinnedLocalChromeOptions() {
        var chromeBinary = Path.of("/bin/true");

        var headed = BrowserDriverProvider.pinnedLocalChrome(chromeBinary, false, false);
        var headedOpts = chromeOpts(headed);
        assertEquals(chromeBinary.toString(), String.valueOf(headedOpts.get("binary")));
        assertTrue(argsOf(headedOpts).isEmpty(), "headed: no chrome args");
        assertTrue(headed.getCapability("goog:loggingPrefs") == null,
                "no logging prefs without captureHar");

        var headless = BrowserDriverProvider.pinnedLocalChrome(chromeBinary, true, true);
        var args = argsOf(chromeOpts(headless));
        assertTrue(args.contains("--headless=new"), "headless arg: " + args);
        assertTrue(args.contains("--no-sandbox"), "sandbox arg: " + args);
        assertNotNull(headless.getCapability("goog:loggingPrefs"), "logging prefs merged for chrome");
    }

    @Test
    @DisplayName("remote driver path completes a W3C session against a fake hub")
    void remotePathSuccess() throws IOException {
        // In-process W3C hub: RemoteWebDriver POSTs /session, quit() DELETEs it.
        var hub = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        hub.createContext("/session", exchange -> {
            var body = ("POST".equals(exchange.getRequestMethod()))
                    ? "{\"value\":{\"sessionId\":\"fake-hub-session\",\"capabilities\":"
                      + "{\"browserName\":\"chrome\",\"browserVersion\":\"148\"}}}"
                    : "{\"value\":null}";
            var bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        hub.start();
        try {
            var cfg = ConfigFactory.create(TestConfig.class, Map.of(
                    "remoteUrl", "http://127.0.0.1:" + hub.getAddress().getPort(),
                    "browser", "chrome",
                    "browserVersion", "148",
                    "enableVnc", "true",
                    "enableVideo", "false"));
            var driver = new BrowserDriverProvider().createDriver(cfg);
            try {
                assertEquals("fake-hub-session",
                        ((org.openqa.selenium.remote.RemoteWebDriver) driver).getSessionId().toString());
            } finally {
                driver.quit();
            }
        } finally {
            hub.stop(0);
        }
    }

    @Test
    @DisplayName("remote driver path builds caps, rejects bad urls and unreachable hubs")
    void remotePathBranches() {
        var provider = new BrowserDriverProvider();

        var badUrl = ConfigFactory.create(TestConfig.class, Map.of(
                "remoteUrl", "ht!tp://[",
                "browser", "chrome",
                "browserVersion", "148"));
        var invalid = assertThrows(IllegalStateException.class,
                () -> provider.createDriver(badUrl));
        assertTrue(invalid.getMessage().contains("Invalid remoteUrl"), invalid.getMessage());

        // Valid remote URL but unreachable hub — session creation fails after caps built.
        var unreachable = ConfigFactory.create(TestConfig.class, Map.of(
                "remoteUrl", "http://127.0.0.1:1/wd/hub",
                "browser", "chrome",
                "browserVersion", "148",
                "enableVnc", "true",
                "enableVideo", "true",
                "enableHar", "true"));
        assertThrows(RuntimeException.class, () -> provider.createDriver(unreachable),
                "connection refused surfaces from RemoteWebDriver");

        // remote + unsupported browser for HAR → logging prefs skipped
        var firefoxHub = ConfigFactory.create(TestConfig.class, Map.of(
                "remoteUrl", "http://127.0.0.1:1/wd/hub",
                "browser", "firefox",
                "browserVersion", "120",
                "attachHarLogs", "true"));
        assertThrows(RuntimeException.class, () -> provider.createDriver(firefoxHub));
    }

    @Test
    @DisplayName("local path rejects unsupported browsers and attempts firefox")
    void localPathBranches() throws IOException {
        var provider = new BrowserDriverProvider();

        var safari = ConfigFactory.create(TestConfig.class, Map.of(
                "remoteUrl", "", "browser", "safari", "browserVersion", "1"));
        var unsupported = assertThrows(IllegalStateException.class,
                () -> provider.createDriver(safari));
        assertTrue(unsupported.getMessage().contains("chrome or firefox"), unsupported.getMessage());

        // Firefox local: fake geckodriver speaks the W3C session protocol,
        // so the provider reaches a live driver without a real firefox install.
        var gecko = System.getProperty("webdriver.gecko.driver");
        var fakeGecko = fakeGeckoDriver();
        System.setProperty("webdriver.gecko.driver", fakeGecko.toString());
        try {
            var headless = ConfigFactory.create(TestConfig.class, Map.of(
                    "remoteUrl", "", "browser", "firefox", "browserVersion", "120",
                    "headless", "true"));
            var driver = provider.createDriver(headless);
            try {
                assertNotNull(((RemoteWebDriver) driver).getSessionId(),
                        "session through fake geckodriver");
            } finally {
                driver.quit();
            }

            // headed: the -headless arg branch must be absent
            var headed = ConfigFactory.create(TestConfig.class, Map.of(
                    "remoteUrl", "", "browser", "firefox", "browserVersion", "120",
                    "headless", "false"));
            var driver2 = provider.createDriver(headed);
            try {
                assertNotNull(((RemoteWebDriver) driver2).getSessionId());
            } finally {
                driver2.quit();
            }
        } finally {
            if (gecko == null) {
                System.clearProperty("webdriver.gecko.driver");
            } else {
                System.setProperty("webdriver.gecko.driver", gecko);
            }
            java.nio.file.Files.deleteIfExists(fakeGecko);
        }
    }

    /**
     * A real geckodriver is a W3C server the JVM execs with {@code --port}. This
     * executable script does the same over python's stdlib: GET /status for the
     * driver's readiness probe, POST /session, DELETE /session/{id}.
     */
    private static Path fakeGeckoDriver() throws IOException {
        var script = Files.createTempFile("geckodriver", ".py");
        Files.writeString(script, """
                #!/usr/bin/env python3
                import json, sys, http.server, socketserver

                def port_of(args):
                    for i, a in enumerate(args):
                        if a.startswith("--port="):
                            return int(a.split("=", 1)[1])
                        if a in ("--port", "-p"):
                            return int(args[i + 1])
                    return 4444

                class Handler(http.server.BaseHTTPRequestHandler):
                    def reply(self, obj):
                        body = json.dumps(obj).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)

                    def do_GET(self):
                        self.reply({"value": {"ready": True, "message": "fake gecko"}})

                    def do_POST(self):
                        self.reply({"value": {"sessionId": "fake-ff", "capabilities": {
                            "browserName": "firefox", "browserVersion": "120.0",
                            "moz:geckodriverVersion": "0.0"}}})

                    def do_DELETE(self):
                        self.reply({"value": None})

                    def log_message(self, *a):
                        pass

                socketserver.TCPServer(("127.0.0.1", port_of(sys.argv[1:])), Handler).serve_forever()
                """);
        script.toFile().setExecutable(true, false);
        return script;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> chromeOpts(org.openqa.selenium.chrome.ChromeOptions options) {
        return (java.util.Map<String, Object>) options.asMap().get("goog:chromeOptions");
    }

    private static java.util.List<String> argsOf(java.util.Map<String, Object> opts) {
        var args = opts.get("args");
        return args instanceof java.util.List<?> list
                ? list.stream().map(String::valueOf).toList()
                : java.util.List.of();
    }
}
