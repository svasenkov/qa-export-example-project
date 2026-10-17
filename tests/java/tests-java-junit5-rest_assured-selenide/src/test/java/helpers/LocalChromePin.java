package helpers;

import com.codeborne.selenide.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Pins <em>local</em> Chrome to the Chrome for Testing build in
 * {@code chrome-for-testing.properties}. Bypasses Selenium Manager so system
 * Chrome is never used silently.
 * <p>
 * Not a Chrome-only suite. {@code TestBase} calls {@link #apply} only when
 * {@code remoteUrl} is empty and {@code browser=chrome}. A remote hub uses the
 * image tag; other local browsers ({@code -Dbrowser=firefox}) skip this class.
 * Do not generalize until there is a matching pin + installer for that browser.
 */
public final class LocalChromePin {

    private static final String PIN_RESOURCE = "/chrome-for-testing.properties";
    private static final String PIN_OVERRIDE = "chromeForTestingVersion";
    private static final String INSTALLER = "scripts/install-chrome-for-testing.sh";
    private static final String CHROME_BINARY_ENV = "CHROME_BINARY_PATH";
    private static final String CHROMEDRIVER_ENV = "CHROMEDRIVER_PATH";

    private LocalChromePin() {
    }

    /**
     * Resolves the pinned binaries and points Selenide at them.
     *
     * @return Chrome for Testing browser binary (never system Chrome)
     */
    public static Path apply(String browserVersion) {
        if (browserVersion == null || browserVersion.isBlank()) {
            throw new IllegalStateException(
                    "browserVersion is required for local Chrome (canon: 148). "
                            + "Do not run e2e on system Chrome without explicit override.");
        }
        var version = pinnedVersion();
        requireSameMajor(browserVersion, version);

        var chrome = executableOverride(CHROME_BINARY_ENV);
        if (chrome == null) {
            chrome = chromeBinary(version);
        }
        if (!Files.isExecutable(chrome)) {
            throw notInstalled("Chrome " + version + " browser binary", chrome);
        }
        var driver = executableOverride(CHROMEDRIVER_ENV);
        if (driver == null) {
            driver = chromeDriver(version);
        }
        if (!Files.isExecutable(driver)) {
            var cached = seleniumCacheDriver(version);
            if (!Files.isExecutable(cached)) {
                throw notInstalled("chromedriver for Chrome " + version, driver);
            }
            driver = cached;
        }

        Configuration.browserBinary = chrome.toString();
        System.setProperty("webdriver.chrome.driver", driver.toString());
        Configuration.browserVersion = null;
        return chrome;
    }

    private static Path executableOverride(String environmentVariable) {
        var value = System.getenv(environmentVariable);
        return value == null || value.isBlank() ? null : Path.of(value.trim());
    }

    /** Exact Chrome for Testing build — SSOT for the installer, CI cache key and this resolver. */
    public static String pinnedVersion() {
        var override = System.getProperty(PIN_OVERRIDE, "").trim();
        if (!override.isEmpty()) {
            return override;
        }
        try (var stream = LocalChromePin.class.getResourceAsStream(PIN_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(PIN_RESOURCE + " is missing from test resources");
            }
            var properties = new Properties();
            properties.load(stream);
            var version = properties.getProperty("version", "").trim();
            if (version.isEmpty()) {
                throw new IllegalStateException("No version= entry in " + PIN_RESOURCE);
            }
            return version;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + PIN_RESOURCE, e);
        }
    }

    private static void requireSameMajor(String browserVersion, String pinnedVersion) {
        var requested = major(browserVersion);
        var pinned = major(pinnedVersion);
        if (!requested.equals(pinned)) {
            throw new IllegalStateException("""
                    browserVersion=%s asks for Chrome %s, but the pinned build is %s.
                    Align them: bump version= in %s, or set browserVersion to %s.
                    """.formatted(browserVersion, requested, pinnedVersion, PIN_RESOURCE, pinned).trim());
        }
    }

    private static String major(String version) {
        return version.split("\\.")[0];
    }

    private static Path chromeForTestingRoot() {
        var override = System.getenv("CHROME_FOR_TESTING_PATH");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".local/share/chrome-for-testing");
    }

    /** Name of the {@code <platform>-<version>} folder written by scripts/install-chrome-for-testing.sh. */
    private static String platformDir() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return arch.contains("aarch64") || arch.contains("arm") ? "mac_arm" : "mac";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        throw new IllegalStateException("Unsupported OS for LocalChromePin: " + os);
    }

    private static String seleniumCacheArch() {
        return switch (platformDir()) {
            case "mac_arm" -> "mac-arm64";
            case "mac" -> "mac-x64";
            case "linux" -> "linux64";
            default -> throw new IllegalStateException("Unsupported platform: " + platformDir());
        };
    }

    private static Path chromeBinary(String version) {
        var versionDir = chromeForTestingRoot()
                .resolve("chrome")
                .resolve(platformDir() + "-" + version);
        return switch (platformDir()) {
            case "mac_arm" -> versionDir.resolve(
                    "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
            case "mac" -> versionDir.resolve(
                    "chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
            case "linux" -> versionDir.resolve("chrome-linux64/chrome");
            default -> throw new IllegalStateException("Unsupported platform: " + platformDir());
        };
    }

    private static Path chromeDriver(String version) {
        var versionDir = chromeForTestingRoot()
                .resolve("chromedriver")
                .resolve(platformDir() + "-" + version);
        return switch (platformDir()) {
            case "mac_arm" -> versionDir.resolve("chromedriver-mac-arm64/chromedriver");
            case "mac" -> versionDir.resolve("chromedriver-mac-x64/chromedriver");
            case "linux" -> versionDir.resolve("chromedriver-linux64/chromedriver");
            default -> throw new IllegalStateException("Unsupported platform: " + platformDir());
        };
    }

    private static Path seleniumCacheDriver(String version) {
        return Path.of(System.getProperty("user.home"), ".cache/selenium/chromedriver")
                .resolve(seleniumCacheArch())
                .resolve(version)
                .resolve("chromedriver");
    }

    private static IllegalStateException notInstalled(String what, Path expected) {
        return new IllegalStateException("""
                %s not found at %s.
                Install the pinned build (not system Chrome), from the tests module root:
                  %s
                """.formatted(what, expected, INSTALLER).trim());
    }
}
