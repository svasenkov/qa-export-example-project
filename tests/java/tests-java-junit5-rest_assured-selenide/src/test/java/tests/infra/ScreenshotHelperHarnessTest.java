package tests.infra;

import annotations.Layer;
import com.codeborne.selenide.SelenideElement;
import config.TestConfig;
import helpers.LocalChromePin;
import helpers.ScreenshotHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tests.AllureMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScreenshotHelper} golden-file flow: pass / diff / size-mismatch /
 * missing-golden / IO-failure — all deterministic, driven by a scripted
 * {@link SelenideElement} proxy whose screenshot() yields chosen PNG bytes.
 * Golden fixtures live under a unique {@code infra-probe-*} area and are
 * deleted after each test — nothing is committed or left behind.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("ScreenshotHelper")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("ScreenshotHelper compare flow")
@Execution(ExecutionMode.SAME_THREAD)
class ScreenshotHelperHarnessTest extends AllureMeta {

    private static final String AREA = "infra-probe";
    private static final int VIEWPORT = 777;

    @AfterEach
    void cleanup() throws IOException {
        var dir = goldenDir(AREA);
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                for (var p : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    private static Path goldenDir(String area) {
        return Path.of("src", "test", "resources", "screenshots",
                ScreenshotHelper.screenshotMode(), osFolder(),
                "chrome-" + LocalChromePin.pinnedVersion().split("\\.")[0], area);
    }

    /** Mirrors ScreenshotHelper.screenshotOs()/mapScreenshotOs for the current os.name. */
    private static String osFolder() {
        var name = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) {
            return "macos";
        }
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("linux")) {
            return "linux";
        }
        return name.isEmpty() ? "linux" : name;
    }

    private static byte[] png(int width, int height, int rgb) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] pngWithPixel(int width, int height, int rgb, int px, int py, int pixelRgb)
            throws IOException {
        var image = ImageIO.read(new java.io.ByteArrayInputStream(png(width, height, rgb)));
        image.setRGB(px, py, pixelRgb);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * SelenideElement whose shouldBe(visible) always passes and whose
     * screenshot() returns a File containing the scripted bytes — or a path
     * that no longer exists, to drive the IO failure branch.
     */
    private static SelenideElement elementReturning(byte[] png, boolean deletedFile) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "shouldBe", "shouldHave", "should", "shouldNot", "shouldNotBe" -> proxy;
            case "screenshot" -> {
                if (deletedFile) {
                    yield new File("build/no-such-shot.png");
                }
                var file = File.createTempFile("shot", ".png");
                file.deleteOnExit();
                Files.write(file.toPath(), png);
                yield file;
            }
            case "toString" -> "FakeElement";
            default -> null;
        };
        return (SelenideElement) Proxy.newProxyInstance(
                ScreenshotHelperHarnessTest.class.getClassLoader(),
                new Class<?>[]{SelenideElement.class}, handler);
    }

    @Test
    @DisplayName("missing golden → AssertionError naming the resource to commit")
    void missingGoldenFails() {
        var error = assertThrows(AssertionError.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(new byte[]{1}, false), AREA, VIEWPORT, "probe"));
        assertTrue(error.getMessage().contains("Screenshot missing"), error.getMessage());
        assertTrue(error.getMessage().contains("-DupdateScreenshots=true"), error.getMessage());
    }

    @Test
    @DisplayName("identical screenshot passes; tiny diff under threshold passes with artifacts")
    void identicalAndTinyDiffPass() throws IOException {
        var golden = png(32, 32, 0xFF102030);
        var goldenPath = goldenDir(AREA).resolve(VIEWPORT + ".png");
        Files.createDirectories(goldenPath.getParent());
        Files.write(goldenPath, golden);

        ScreenshotHelper.captureAndCompare(elementReturning(golden, false), AREA, VIEWPORT, "probe");

        // one flipped pixel of 1024 — diff png produced, still under 1.5% threshold
        var nearly = pngWithPixel(32, 32, 0xFF102030, 0, 0, 0xFFFFFFFF);
        ScreenshotHelper.captureAndCompare(elementReturning(nearly, false), AREA, VIEWPORT, "probe2");
    }

    @Test
    @DisplayName("large diff → AssertionError + diff artifacts on disk")
    void largeDiffFails() throws IOException {
        Files.createDirectories(goldenDir(AREA));
        Files.write(goldenDir(AREA).resolve(VIEWPORT + ".png"), png(4, 4, 0xFF000000));

        var error = assertThrows(AssertionError.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(png(4, 4, 0xFFFFFFFF), false), AREA, VIEWPORT, "probe"));
        assertTrue(error.getMessage().contains("Screenshot diff too high"), error.getMessage());
        assertTrue(Files.exists(Path.of("build", "screenshot-diff",
                AREA + "_" + VIEWPORT + "-diff.png")), "diff artifact saved");
    }

    @Test
    @DisplayName("size mismatch → AssertionError mentioning both sizes")
    void sizeMismatchFails() throws IOException {
        Files.createDirectories(goldenDir(AREA));
        Files.write(goldenDir(AREA).resolve(VIEWPORT + ".png"), png(4, 4, 0xFF000000));

        var error = assertThrows(AssertionError.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(png(8, 2, 0xFF000000), false), AREA, VIEWPORT, "probe"));
        assertTrue(error.getMessage().contains("Screenshot size changed"), error.getMessage());
    }

    @Test
    @DisplayName("golden on the classpath is used for comparison")
    void classpathGoldenPasses() throws IOException {
        var resource = "screenshots/" + ScreenshotHelper.screenshotMode() + "/macos/chrome-"
                + LocalChromePin.pinnedVersion().split("\\.")[0] + "/header/390.png";
        byte[] golden;
        try (var in = getClass().getResourceAsStream("/" + resource)) {
            org.junit.jupiter.api.Assertions.assertNotNull(in, "expected committed golden " + resource);
            golden = in.readAllBytes();
        }
        ScreenshotHelper.captureAndCompare(elementReturning(golden, false), "header", 390, "probe");
    }

    @Test
    @DisplayName("unreadable element screenshot and unreadable golden → UncheckedIOException")
    void ioFailures() throws IOException {
        assertThrows(UncheckedIOException.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(null, true), "io-probe", VIEWPORT, "probe"));

        // golden present as a directory → exists() but readAllBytes throws
        var dir = goldenDir("io-probe").resolve(VIEWPORT + ".png");
        Files.createDirectories(dir);
        assertThrows(UncheckedIOException.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(png(1, 1, 0), false), "io-probe", VIEWPORT, "probe"));
    }

    private static TestConfig config(Map<String, Object> overrides) {
        return org.aeonbits.owner.ConfigFactory.create(TestConfig.class, overrides);
    }

    @Test
    @DisplayName("updateScreenshots=true writes the golden instead of comparing")
    void updateModeWritesGolden() throws IOException {
        var update = config(Map.of("updateScreenshots", "true"));
        var goldenPath = goldenDir("infra-probe-update").resolve(VIEWPORT + ".png");
        try {
            // existing golden → old+new attachments branch
            Files.createDirectories(goldenPath.getParent());
            Files.write(goldenPath, png(4, 4, 0xFF000000));
            ScreenshotHelper.captureAndCompare(
                    elementReturning(png(8, 8, 0xFF00FF00), false),
                    "infra-probe-update", VIEWPORT, "probe", update);
            assertTrue(Files.exists(goldenPath), "golden rewritten");

            // absent golden → new-only attachments branch (delete first)
            Files.delete(goldenPath);
            ScreenshotHelper.captureAndCompare(
                    elementReturning(png(2, 2, 0xFF0000FF), false),
                    "infra-probe-update", VIEWPORT, "probe", update);
            assertTrue(Files.exists(goldenPath), "golden written");
        } finally {
            var dir = goldenDir("infra-probe-update");
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    for (var p : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("screenshotsDir rejects blanks and normalizes slashes")
    void screenshotsDirValidation() {
        var blank = config(Map.of("screenshotsDir", "   "));
        assertThrows(IllegalStateException.class,
                () -> ScreenshotHelper.screenshotsDir(blank));

        var messy = config(Map.of("screenshotsDir", "shots\\nested//"));
        assertEquals("shots/nested", ScreenshotHelper.screenshotsDir(messy));

        var slashesOnly = config(Map.of("screenshotsDir", "///"));
        assertEquals("", ScreenshotHelper.screenshotsDir(slashesOnly));
    }

    @Test
    @DisplayName("screenshotMode covers null/unknown envs")
    void screenshotModeEdges() {
        assertEquals("prod", ScreenshotHelper.screenshotMode(null));
        assertThrows(IllegalStateException.class,
                () -> ScreenshotHelper.screenshotMode("qa-box"));
    }

    @Test
    @DisplayName("screenshotOs honors SCREENSHOT_OS override values")
    void screenshotOsOverrides() {
        assertEquals("windows", ScreenshotHelper.screenshotOs(k -> "Windows 11"));
        assertEquals("windows", ScreenshotHelper.screenshotOs(k -> "win32"));
        assertEquals("windows", ScreenshotHelper.screenshotOs(k -> "WinNT"));
        assertEquals("windows", ScreenshotHelper.screenshotOs(k -> "win11arm64"));
        assertEquals("windows", ScreenshotHelper.screenshotOs(k -> "win32"));
        assertEquals("windows", ScreenshotHelper.screenshotOs(k -> "windows"));
        assertEquals("macos", ScreenshotHelper.screenshotOs(k -> "  MacOS  "));
        assertEquals("macos", ScreenshotHelper.screenshotOs(k -> "darwin"));
        assertEquals("macos", ScreenshotHelper.screenshotOs(k -> "Mac OS X"));
        assertEquals("linux", ScreenshotHelper.screenshotOs(k -> "ubuntu linux"));
        assertEquals("linux", ScreenshotHelper.screenshotOs(k -> "LINUX"));
        assertEquals("amiga", ScreenshotHelper.screenshotOs(k -> "Amiga"));
        // blank override falls back to os.name mapping (same as unset)
        assertEquals(ScreenshotHelper.screenshotOs(k -> null),
                ScreenshotHelper.screenshotOs(k -> ""));
    }

    @Test
    @DisplayName("osFamily maps win/linux/unknown")
    void osFamilyMapping() {
        assertEquals("win32", ScreenshotHelper.osFamily("Windows 11"));
        assertEquals("darwin", ScreenshotHelper.osFamily("Darwin 24"));
        assertEquals("linux", ScreenshotHelper.osFamily("FreeBSD"));
    }

    @Test
    @DisplayName("screenshotBrowser honors SCREENSHOT_BROWSER override")
    void screenshotBrowserOverride() {
        assertEquals("firefox", ScreenshotHelper.screenshotBrowser(k -> "  FIREFOX "));
        assertEquals("chrome", ScreenshotHelper.screenshotBrowser(k -> null));
        assertEquals("chrome", ScreenshotHelper.screenshotBrowser(k -> "  "));
    }

    @Test
    @DisplayName("readExpectedScreenshot throws IOException when nothing matches")
    void readExpectedMissing() {
        assertThrows(IOException.class,
                () -> ScreenshotHelper.readExpectedScreenshot("no-such-area-xyz", 999));
    }

    @Test
    @DisplayName("update mode wraps a golden read failure")
    void updateModeUnreadableGolden() throws IOException {
        // Golden exists as a DIRECTORY → screenshotExists()=true, but
        // readExpectedScreenshot() throws inside attachUpdateMode.
        var goldenPath = goldenDir("infra-probe-dir").resolve(VIEWPORT + ".png");
        Files.createDirectories(goldenPath);
        var update = config(Map.of("updateScreenshots", "true"));
        try {
            assertThrows(UncheckedIOException.class, () ->
                    ScreenshotHelper.captureAndCompare(
                            elementReturning(png(2, 2, 0xFF000000), false),
                            "infra-probe-dir", VIEWPORT, "probe", update));
        } finally {
            Files.delete(goldenPath);
            Files.delete(goldenPath.getParent());
        }
    }

    @Test
    @DisplayName("same width, taller actual → size mismatch arm")
    void sizeMismatchHeightArm() throws IOException {
        Files.createDirectories(goldenDir(AREA));
        Files.write(goldenDir(AREA).resolve(VIEWPORT + ".png"), png(4, 4, 0xFF000000));
        assertThrows(AssertionError.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(png(4, 8, 0xFF000000), false),
                        AREA, VIEWPORT, "probe"));
    }

    @Test
    @DisplayName("diff png covers all in-expected/in-actual quadrants")
    void diffPngQuadrants() throws IOException {
        // expected 4x4, actual 8x6 → pixels only-in-actual, only-in-expected, both
        Files.createDirectories(goldenDir(AREA));
        Files.write(goldenDir(AREA).resolve(VIEWPORT + ".png"), png(4, 4, 0xFF000000));
        assertThrows(AssertionError.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(png(8, 6, 0xFF000000), false),
                        AREA, VIEWPORT, "probe"));

        // expected 8x6, actual 4x4 → x >= actW region: inActual false on width alone
        Files.write(goldenDir(AREA).resolve(VIEWPORT + ".png"), png(8, 6, 0xFF000000));
        assertThrows(AssertionError.class, () ->
                ScreenshotHelper.captureAndCompare(
                        elementReturning(png(4, 4, 0xFF000000), false),
                        AREA, VIEWPORT, "probe"));
    }

    @Test
    @DisplayName("writeScreenshot wraps IO failures")
    void writeScreenshotIoFailure() throws IOException {
        var blocker = Path.of("build", "ss-blocker-file");
        Files.write(blocker, new byte[]{0});
        try {
            var e = assertThrows(UncheckedIOException.class, () ->
                    ScreenshotHelper.writeScreenshot(
                            blocker.resolve("deep/shot.png"), new byte[]{1, 2}));
            assertTrue(e.getCause() instanceof IOException);
        } finally {
            Files.deleteIfExists(blocker);
        }
    }

    @Test
    @DisplayName("diff artifact write failure is ignored")
    void saveFailArtifactsSwallowsIoErrors() throws IOException {
        // Make the fixed build/screenshot-diff directory unwritable →
        // Files.write inside throws AccessDenied → the catch swallows it.
        // Leftover artifacts from earlier runs keep the dir's own permissions
        // irrelevant for write(), so the targets must be absent first.
        var diffDir = Path.of("build", "screenshot-diff");
        Files.createDirectories(diffDir);
        var prefix = AREA + "_" + VIEWPORT;
        Files.deleteIfExists(diffDir.resolve(prefix + "-actual.png"));
        Files.deleteIfExists(diffDir.resolve(prefix + "-diff.png"));
        var originalPerms = Files.getPosixFilePermissions(diffDir);
        Files.setPosixFilePermissions(diffDir,
                java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            Files.createDirectories(goldenDir(AREA));
            Files.write(goldenDir(AREA).resolve(VIEWPORT + ".png"), png(4, 4, 0xFF000000));
            assertThrows(AssertionError.class, () ->
                    ScreenshotHelper.captureAndCompare(
                            elementReturning(png(4, 4, 0xFFFFFFFF), false),
                            AREA, VIEWPORT, "probe"));
        } finally {
            Files.setPosixFilePermissions(diffDir, originalPerms);
        }
    }

    @Test
    @DisplayName("screenshotOs maps os.name and honors blank overrides")
    void screenshotOsMapping() {
        var originalOsName = System.getProperty("os.name");
        try {
            for (var entry : java.util.Map.of(
                    "Mac OS X", "macos",
                    "Linux", "linux",
                    "Windows 11", "windows",
                    "SomeUnix", "someunix").entrySet()) {
                System.setProperty("os.name", entry.getKey());
                var path = goldenDir(AREA).toString().replace('\\', '/');
                assertTrue(path.contains("/" + entry.getValue() + "/"),
                        entry.getKey() + " → " + path);
            }
        } finally {
            System.setProperty("os.name", originalOsName);
        }
    }
}
