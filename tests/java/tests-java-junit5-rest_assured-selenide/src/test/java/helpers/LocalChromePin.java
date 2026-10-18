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
        return apply(browserVersion, System::getenv);
    }

    /** Test seam: harness coverage drives a scripted environment lookup. */
    public static Path apply(String browserVersion, java.util.function.Function<String, String> getenv) {
        if (browserVersion == null || browserVersion.isBlank()) {
            throw new IllegalStateException(
                    "browserVersion is required for local Chrome (canon: 148). "
                            + "Do not run e2e on system Chrome without explicit override.");
        }
        var version = pinnedVersion();
        requireSameMajor(browserVersion, version);

        var chrome = executableOverride(CHROME_BINARY_ENV, getenv);
        if (chrome == null) {
            chrome = chromeBinary(version, getenv);
        }
        if (!Files.isExecutable(chrome)) {
            throw notInstalled("Chrome " + version + " browser binary", chrome);
        }
        var driver = executableOverride(CHROMEDRIVER_ENV, getenv);
        if (driver == null) {
            driver = chromeDriver(version, getenv);
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

    private static Path executableOverride(
            String environmentVariable, java.util.function.Function<String, String> getenv) {
        var value = getenv.apply(environmentVariable);
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

    private static Path chromeForTestingRoot(java.util.function.Function<String, String> getenv) {
        var override = getenv.apply("CHROME_FOR_TESTING_PATH");
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return Path.of(System.getProperty("user.home"), ".local/share/chrome-for-testing");
    }

    /**
     * {@code <platform>-<version>} folder names written by
     * scripts/install-chrome-for-testing.sh plus the matching Selenium cache arch.
     */
    private enum PinPlatform {
        MAC_ARM(
                "mac_arm",
                "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing",
                "chromedriver-mac-arm64/chromedriver",
                "mac-arm64"),
        MAC_X64(
                "mac",
                "chrome-mac-x64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing",
                "chromedriver-mac-x64/chromedriver",
                "mac-x64"),
        LINUX(
                "linux",
                "chrome-linux64/chrome",
                "chromedriver-linux64/chromedriver",
                "linux64");

        final String dirName;
        final String chromeRelPath;
        final String driverRelPath;
        final String seleniumArch;

        PinPlatform(String dirName, String chromeRelPath, String driverRelPath, String seleniumArch) {
            this.dirName = dirName;
            this.chromeRelPath = chromeRelPath;
            this.driverRelPath = driverRelPath;
            this.seleniumArch = seleniumArch;
        }
    }

    private static PinPlatform platform() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return arch.contains("aarch64") || arch.contains("arm") ? PinPlatform.MAC_ARM : PinPlatform.MAC_X64;
        }
        if (os.contains("linux")) {
            return PinPlatform.LINUX;
        }
        throw new IllegalStateException("Unsupported OS for LocalChromePin: " + os);
    }

    private static Path chromeBinary(
            String version, java.util.function.Function<String, String> getenv) {
        var platform = platform();
        return chromeForTestingRoot(getenv)
                .resolve("chrome")
                .resolve(platform.dirName + "-" + version)
                .resolve(platform.chromeRelPath);
    }

    private static Path chromeDriver(
            String version, java.util.function.Function<String, String> getenv) {
        var platform = platform();
        return chromeForTestingRoot(getenv)
                .resolve("chromedriver")
                .resolve(platform.dirName + "-" + version)
                .resolve(platform.driverRelPath);
    }

    private static Path seleniumCacheDriver(String version) {
        return Path.of(System.getProperty("user.home"), ".cache/selenium/chromedriver")
                .resolve(platform().seleniumArch)
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
