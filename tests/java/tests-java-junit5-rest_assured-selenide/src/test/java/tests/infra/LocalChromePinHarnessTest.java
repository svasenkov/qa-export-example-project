package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.Configuration;
import helpers.LocalChromePin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tests.AllureMeta;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LocalChromePin} path resolution: platform mapping (via os.name/os.arch
 * stubs), fixture installs under the real pin root, the Selenium cache fallback,
 * and pin-resource failure modes via a shadow class loader.
 * Isolated + same-thread: mutates global system properties and Configuration.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Local browser pin")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("LocalChromePin paths")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LocalChromePinHarnessTest extends AllureMeta {

    private static final String PIN_PROP = "chromeForTestingVersion";
    /** Fake pin suffix — never a real install. */
    private static final String FAKE_VERSION = "148.0.7778.999";

    private String originalOsName;
    private String originalOsArch;
    private String originalPinOverride;

    @BeforeEach
    void snapshotProps() {
        originalOsName = System.getProperty("os.name");
        originalOsArch = System.getProperty("os.arch");
        originalPinOverride = System.getProperty(PIN_PROP);
    }

    @AfterEach
    void restoreProps() {
        restore("os.name", originalOsName);
        restore("os.arch", originalOsArch);
        restore(PIN_PROP, originalPinOverride);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static Path pinRoot() {
        return Path.of(System.getProperty("user.home"), ".local/share/chrome-for-testing");
    }

    private static void makeExecutable(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "#!/bin/sh\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private static void fakeInstall(String platformDir, String version, boolean chrome, boolean driver)
            throws IOException {
        var root = pinRoot();
        if (chrome) {
            makeExecutable(root.resolve("chrome/" + platformDir + "-" + version + "/"
                    + switch (platformDir) {
                        case "mac_arm" -> "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing";
                        case "mac" -> "chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing";
                        default -> "chrome-linux64/chrome";
                    }));
        }
        if (driver) {
            makeExecutable(root.resolve("chromedriver/" + platformDir + "-" + version + "/"
                    + switch (platformDir) {
                        case "mac_arm" -> "chromedriver-mac-arm64/chromedriver";
                        case "mac" -> "chromedriver-mac-x64/chromedriver";
                        default -> "chromedriver-linux64/chromedriver";
                    }));
        }
    }

    // --- version + platform matrix ---------------------------------------------

    @Test
    @DisplayName("chromeVersion override via chromeForTestingVersion system property")
    void pinOverrideProperty() {
        System.setProperty(PIN_PROP, " 148.1.2.3 ");
        assertEquals("148.1.2.3", LocalChromePin.pinnedVersion());
    }

    @Test
    @DisplayName("apply resolves a fake install and the selenium cache driver")
    void applyResolvesFixturesAndCacheFallback() throws IOException {
        System.setProperty(PIN_PROP, FAKE_VERSION);
        var driverCache = Path.of(System.getProperty("user.home"),
                ".cache/selenium/chromedriver/mac-arm64", FAKE_VERSION, "chromedriver");
        var browserBinary = Configuration.browserBinary;
        var browserVersion = Configuration.browserVersion;
        try {
            fakeInstall("mac_arm", FAKE_VERSION, true, false);
            makeExecutable(driverCache);

            var binary = LocalChromePin.apply("148");
            assertTrue(binary.toString().contains("chrome-mac-arm64"), binary.toString());
            assertEquals(driverCache.toString(), System.getProperty("webdriver.chrome.driver"));
            assertEquals(binary.toString(), Configuration.browserBinary);
            org.junit.jupiter.api.Assertions.assertNull(Configuration.browserVersion);
        } finally {
            Configuration.browserBinary = browserBinary;
            Configuration.browserVersion = browserVersion;
            Files.deleteIfExists(driverCache);
            deleteFakeVersionDirs(FAKE_VERSION);
        }
    }

    @Test
    @DisplayName("apply reports a missing browser binary and a missing driver")
    void applyReportsMissingArtifacts() throws IOException {
        System.setProperty(PIN_PROP, "148.0.7778.998");
        try {
            var noChrome = assertThrows(IllegalStateException.class,
                    () -> LocalChromePin.apply("148"));
            assertTrue(noChrome.getMessage().contains("browser binary"), noChrome.getMessage());
            assertTrue(noChrome.getMessage().contains("install-chrome-for-testing.sh"),
                    noChrome.getMessage());

            fakeInstall("mac_arm", "148.0.7778.998", true, false);
            var noDriver = assertThrows(IllegalStateException.class,
                    () -> LocalChromePin.apply("148"));
            assertTrue(noDriver.getMessage().contains("chromedriver"), noDriver.getMessage());
        } finally {
            deleteFakeVersionDirs("148.0.7778.998");
        }
    }

    @Test
    @DisplayName("platform mapping covers mac x64, linux and rejects unknown OS")
    void platformMatrix() throws IOException {
        var version = "148.0.7778.997";
        try {
            // mac x64 — fake install so the driver lookup reaches the selenium cache path
            System.setProperty("os.arch", "x86_64");
            fakeInstall("mac", version, true, false);
            System.setProperty(PIN_PROP, version);
            var macX64 = assertThrows(IllegalStateException.class, () -> LocalChromePin.apply("148"));
            assertTrue(macX64.getMessage().contains("mac-x64"), macX64.getMessage());

            // linux — chrome present, driver missing → cache path linux64
            System.setProperty("os.name", "Linux");
            fakeInstall("linux", version, true, false);
            var linux = assertThrows(IllegalStateException.class, () -> LocalChromePin.apply("148"));
            assertTrue(linux.getMessage().contains("linux64"), linux.getMessage());

            // unknown OS
            System.setProperty("os.name", "Windows 11");
            var unsupported = assertThrows(IllegalStateException.class,
                    () -> LocalChromePin.apply("148"));
            assertTrue(unsupported.getMessage().contains("Unsupported OS"), unsupported.getMessage());

            // generic arm (not aarch64) still maps to mac_arm on macOS
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("os.arch", "armv7");
            var arm = assertThrows(IllegalStateException.class, () -> LocalChromePin.apply("148"));
            assertTrue(arm.getMessage().contains("mac_arm"), arm.getMessage());
        } finally {
            deleteFakeVersionDirs(version);
        }
    }

    private static void deleteFakeVersionDirs(String version) throws IOException {
        var root = pinRoot();
        for (var platform : new String[]{"mac_arm", "mac", "linux"}) {
            deleteRecursively(root.resolve("chrome/" + platform + "-" + version));
            deleteRecursively(root.resolve("chromedriver/" + platform + "-" + version));
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (var path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // --- environment-variable seams (scripted getenv) ----------------------------

    private static java.util.function.Function<String, String> env(
            java.util.Map<String, String> values) {
        return values::get;
    }

    @Test
    @DisplayName("apply rejects null and blank browserVersion")
    void applyRejectsBlankVersion() {
        assertThrows(IllegalStateException.class, () -> LocalChromePin.apply(null));
        assertThrows(IllegalStateException.class, () -> LocalChromePin.apply("   "));
    }

    @Test
    @DisplayName("apply honors CHROME_BINARY_PATH/CHROMEDRIVER_PATH overrides")
    void applyEnvOverrides() throws IOException {
        var chromeExe = Files.createTempFile("fake-chrome", ".bin");
        var driverExe = Files.createTempFile("fake-chromedriver", ".bin");
        makeExecutable(chromeExe);
        makeExecutable(driverExe);
        var browserBinary = Configuration.browserBinary;
        var browserVersion = Configuration.browserVersion;
        var driverProp = System.getProperty("webdriver.chrome.driver");
        try {
            var binary = LocalChromePin.apply("148", env(java.util.Map.of(
                    "CHROME_BINARY_PATH", "  " + chromeExe + "  ",
                    "CHROMEDRIVER_PATH", driverExe.toString())));
            assertEquals(chromeExe, binary);
            assertEquals(driverExe.toString(), System.getProperty("webdriver.chrome.driver"));

            // blank override → falls through to the install lookup (fake pin → misses)
            System.setProperty(PIN_PROP, FAKE_VERSION);
            var blank = assertThrows(IllegalStateException.class, () ->
                    LocalChromePin.apply("148", env(java.util.Map.of(
                            "CHROME_BINARY_PATH", " "))));
            assertTrue(blank.getMessage().contains("browser binary"), blank.getMessage());

            // non-executable override → reported, not silently used
            var nonExe = Files.createTempFile("nonexe", ".bin");
            var rejected = assertThrows(IllegalStateException.class, () ->
                    LocalChromePin.apply("148", env(java.util.Map.of(
                            "CHROME_BINARY_PATH", nonExe.toString()))));
            assertTrue(rejected.getMessage().contains("browser binary"), rejected.getMessage());
        } finally {
            Configuration.browserBinary = browserBinary;
            Configuration.browserVersion = browserVersion;
            if (driverProp == null) {
                System.clearProperty("webdriver.chrome.driver");
            } else {
                System.setProperty("webdriver.chrome.driver", driverProp);
            }
            Files.deleteIfExists(chromeExe);
            Files.deleteIfExists(driverExe);
        }
    }

    @Test
    @DisplayName("apply honors CHROME_FOR_TESTING_PATH root override")
    void applyRootOverride() throws IOException {
        var root = Files.createTempDirectory("cft-root");
        try {
            var error = assertThrows(IllegalStateException.class, () ->
                    LocalChromePin.apply("148", env(java.util.Map.of(
                            "CHROME_FOR_TESTING_PATH", "  " + root + " "))));
            assertTrue(error.getMessage().contains(root.toString()), error.getMessage());
        } finally {
            deleteRecursively(root);
        }

        // blank override → default ~/.local/share/chrome-for-testing root (fake pin → miss)
        System.setProperty(PIN_PROP, FAKE_VERSION);
        try {
            assertThrows(IllegalStateException.class, () ->
                    LocalChromePin.apply("148", env(java.util.Map.of(
                            "CHROME_FOR_TESTING_PATH", " "))));
        } finally {
            System.clearProperty(PIN_PROP);
        }
    }

    // --- pin resource failure modes (shadow class loader) ------------------------

    private static URLClassLoader shadowLoader(URL pinResource) throws IOException {
        URL classesRoot;
        try {
            classesRoot = new java.io.File(LocalChromePin.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toURI().toURL();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return new URLClassLoader(new URL[]{classesRoot}, null) {
            @Override
            public URL getResource(String name) {
                if ("chrome-for-testing.properties".equals(name)) {
                    return pinResource;
                }
                return super.getResource(name);
            }
        };
    }

    private static String pinnedVersionVia(Class<?> shadowed) throws Exception {
        try {
            return (String) shadowed.getMethod("pinnedVersion").invoke(null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    @DisplayName("pinnedVersion fails closed when the resource is missing")
    void pinResourceMissing() throws Exception {
        var shadowed = Class.forName("helpers.LocalChromePin", true, shadowLoader(null));
        var error = assertThrows(IllegalStateException.class, () -> pinnedVersionVia(shadowed));
        assertTrue(error.getMessage().contains("missing from test resources"), error.getMessage());
    }

    @Test
    @DisplayName("pinnedVersion fails closed on a resource without version=")
    void pinResourceWithoutVersion() throws Exception {
        var file = Files.createTempFile("pin", ".properties");
        Files.writeString(file, "unrelated=yes\n");
        try {
            var shadowed = Class.forName("helpers.LocalChromePin", true, shadowLoader(file.toUri().toURL()));
            var error = assertThrows(IllegalStateException.class, () -> pinnedVersionVia(shadowed));
            assertTrue(error.getMessage().contains("No version="), error.getMessage());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("pinnedVersion wraps a read failure")
    void pinResourceUnreadable() throws Exception {
        var handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return new URLConnection(u) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public java.io.InputStream getInputStream() {
                        // open succeeds; the read inside Properties.load fails —
                        // ClassLoader.getResourceAsStream swallows open-time IOErrors.
                        return new java.io.InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("simulated read failure");
                            }
                        };
                    }
                };
            }
        };
        var shadowed = Class.forName("helpers.LocalChromePin", true,
                shadowLoader(new URL("fakepin", null, -1, "virtual", handler)));
        var error = assertThrows(IllegalStateException.class, () -> pinnedVersionVia(shadowed));
        assertTrue(error.getMessage().contains("Cannot read"), error.getMessage());
        assertTrue(error.getCause() instanceof IOException, "cause preserved");
    }

    @Test
    @DisplayName("pinnedVersion reads the shipped resource")
    void pinResourceValid() throws Exception {
        var file = Files.createTempFile("pin", ".properties");
        var properties = new Properties();
        try (var input = LocalChromePinHarnessTest.class
                .getResourceAsStream("/chrome-for-testing.properties")) {
            properties.load(input);
        }
        Files.writeString(file, "version=" + properties.getProperty("version") + "\n");
        try {
            var shadowed = Class.forName("helpers.LocalChromePin", true, shadowLoader(file.toUri().toURL()));
            assertEquals(properties.getProperty("version"), pinnedVersionVia(shadowed));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
