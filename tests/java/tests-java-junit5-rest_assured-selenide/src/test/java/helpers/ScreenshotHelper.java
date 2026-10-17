package helpers;

import com.codeborne.selenide.SelenideElement;
import config.ConfigReader;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static com.codeborne.selenide.Condition.visible;
import static io.qameta.allure.Allure.step;

public final class ScreenshotHelper {

    private static final Path DIFF_DIR = Path.of("build", "screenshot-diff");
    private static final int DIFF_HIGHLIGHT_RGB = 0xFFFF00FF;
    private static final int SIZE_MISMATCH_RGB = 0xFFFF0000;

    private ScreenshotHelper() {
    }

    public static void captureAndCompare(
            SelenideElement element, String area, int viewport, String attachmentName) {
        element.shouldBe(visible);

        var screenshotFile = element.screenshot();
        byte[] actual;
        try {
            actual = Files.readAllBytes(screenshotFile.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        var label = area + "/" + viewport;
        var screenshotPath = screenshotFilePath(area, viewport);
        var screenshotPresent = screenshotExists(area, viewport);

        if (shouldUpdateScreenshots()) {
            step("Update screenshot: " + attachmentName, () ->
                    attachUpdateMode(attachmentName, actual, screenshotPresent, area, viewport));
            writeScreenshot(screenshotPath, actual);
            return;
        }

        if (!screenshotPresent) {
            step("Missing screenshot: " + attachmentName, () ->
                    attachPng(attachmentName + "-actual-unmatched", actual));
            throw new AssertionError(
                    "Screenshot missing for %s. Commit PNG to src/test/resources/%s "
                            + "or run with -DupdateScreenshots=true"
                            .formatted(label, screenshotResourcePath(area, viewport)));
        }

        try {
            var expected = readExpectedScreenshot(area, viewport);
            var comparison = compareImages(expected, actual, label);
            step("Compare screenshot: " + attachmentName, () -> {
                if (comparison.passed()) {
                    attachPng(attachmentName, actual);
                    return;
                }

                attachPng(attachmentName + "-expected", expected);
                attachPng(attachmentName + "-actual", actual);
                attachPng(attachmentName + "-diff", comparison.diffPng());
                saveFailArtifacts(label, actual, comparison.diffPng());
                throw new AssertionError(comparison.message());
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void attachUpdateMode(
            String attachmentName, byte[] actual, boolean screenshotPresent, String area, int viewport) {
        if (screenshotPresent) {
            try {
                attachPng(attachmentName + "-screenshot-old", readExpectedScreenshot(area, viewport));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            attachPng(attachmentName + "-screenshot-new", actual);
            return;
        }
        attachPng(attachmentName + "-screenshot-new", actual);
    }

    private static void attachPng(String name, byte[] png) {
        Allure.attachment(name, "image/png", new ByteArrayInputStream(png), AttachmentOptions.withFileExtension(".png"));
    }

    private static boolean shouldUpdateScreenshots() {
        return ConfigReader.testConfig.updateScreenshots();
    }

    private static String screenshotsDir() {
        var dir = ConfigReader.testConfig.screenshotsDir().trim();
        if (dir.isEmpty()) {
            throw new IllegalStateException("screenshotsDir must not be empty");
        }
        return dir.replace('\\', '/').replaceAll("/+$", "");
    }

    public static String screenshotMode() {
        return screenshotMode(System.getProperty("env", ""));
    }

    /** Folder next to {@code mock/}: {@code stage/} or {@code prod/}. Local compose ({@code ci}) uses {@code prod/}. */
    public static String screenshotMode(String env) {
        var key = env == null ? "" : env.trim();
        return switch (key) {
            case "mock" -> "mock";
            case "stage" -> "stage";
            case "prod", "ci", "" -> "prod";
            default -> throw new IllegalStateException(
                    "screenshot folder: unknown env '" + key
                            + "' (use mock, stage, prod, or ci)");
        };
    }

    static String screenshotOs() {
        var override = System.getenv("SCREENSHOT_OS");
        var raw = (override != null && !override.isBlank())
                ? override.trim()
                : osFamily();
        return mapScreenshotOs(raw);
    }

    static String screenshotBrowserFolder() {
        return screenshotBrowser() + "-" + screenshotBrowserMajor();
    }

    static String screenshotBrowser() {
        var override = System.getenv("SCREENSHOT_BROWSER");
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        return "chrome";
    }

    /**
     * Major segment of the screenshot folder ({@code chrome-148/}). Canon PNG
     * tree is Chrome for Testing; other browsers are sibling folders and need
     * {@code SCREENSHOT_BROWSER} — this major still comes from the Chrome pin.
     */
    static String screenshotBrowserMajor() {
        return LocalChromePin.pinnedVersion().split("\\.")[0];
    }

    private static String osFamily() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) {
            return "darwin";
        }
        if (name.contains("win")) {
            return "win32";
        }
        return "linux";
    }

    private static String mapScreenshotOs(String raw) {
        var key = raw.toLowerCase(Locale.ROOT);
        if (key.equals("darwin") || key.equals("macos") || key.startsWith("mac")) {
            return "macos";
        }
        if (key.equals("win32") || key.equals("windows") || key.startsWith("win")) {
            return "windows";
        }
        if (key.equals("linux") || key.contains("linux")) {
            return "linux";
        }
        return key.isEmpty() ? "linux" : key;
    }

    private static Path screenshotFilePath(String area, int viewport) {
        return Path.of(
                "src", "test", "resources",
                screenshotsDir(), screenshotMode(), screenshotOs(), screenshotBrowserFolder(),
                area, viewport + ".png");
    }

    private static String screenshotResourcePath(String area, int viewport) {
        return screenshotsDir() + "/" + screenshotMode() + "/" + screenshotOs()
                + "/" + screenshotBrowserFolder() + "/" + area + "/" + viewport + ".png";
    }

    private static boolean screenshotExists(String area, int viewport) {
        var resource = screenshotResourcePath(area, viewport);
        if (Thread.currentThread().getContextClassLoader().getResource(resource) != null) {
            return true;
        }
        return Files.exists(screenshotFilePath(area, viewport));
    }

    private static byte[] readExpectedScreenshot(String area, int viewport) throws IOException {
        var resource = screenshotResourcePath(area, viewport);
        var url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url != null) {
            try (InputStream in = url.openStream()) {
                return in.readAllBytes();
            }
        }
        var path = screenshotFilePath(area, viewport);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        throw new IOException("Screenshot not found: " + resource);
    }

    private static void writeScreenshot(Path screenshotPath, byte[] png) {
        try {
            Files.createDirectories(screenshotPath.getParent());
            Files.write(screenshotPath, png);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Internal value carrier — never compared or printed, so record array semantics are fine. */
    private record ImageComparison(boolean passed, byte[] diffPng, String message) {
    }

    private static ImageComparison compareImages(byte[] expectedBytes, byte[] actualBytes, String label)
            throws IOException {
        var expected = readImage(expectedBytes);
        var actual = readImage(actualBytes);
        var diffPng = createDiffPng(expected, actual);

        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            return new ImageComparison(
                    false,
                    diffPng,
                    "Screenshot size changed for %s: expected %dx%d, actual %dx%d"
                            .formatted(
                                    label,
                                    expected.getWidth(),
                                    expected.getHeight(),
                                    actual.getWidth(),
                                    actual.getHeight()));
        }

        var width = expected.getWidth();
        var height = expected.getHeight();
        var diffPixels = 0;
        var totalPixels = width * height;

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
                    diffPixels++;
                }
            }
        }

        var maxDiffRatio = ConfigReader.testConfig.screenshotDiffThreshold();
        var diffRatio = (double) diffPixels / totalPixels;
        if (diffRatio > maxDiffRatio) {
            return new ImageComparison(
                    false,
                    diffPng,
                    "Screenshot diff too high for %s: %.2f%% > %.2f%%"
                            .formatted(label, diffRatio * 100, maxDiffRatio * 100));
        }

        return new ImageComparison(true, diffPng, null);
    }

    private static byte[] createDiffPng(BufferedImage expected, BufferedImage actual) throws IOException {
        var expW = expected.getWidth();
        var expH = expected.getHeight();
        var actW = actual.getWidth();
        var actH = actual.getHeight();
        var width = Math.max(expW, actW);
        var height = Math.max(expH, actH);
        var diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var inExpected = x < expW && y < expH;
                var inActual = x < actW && y < actH;
                if (inExpected && inActual) {
                    var expectedRgb = expected.getRGB(x, y);
                    if (expectedRgb == actual.getRGB(x, y)) {
                        diff.setRGB(x, y, dimRgb(expectedRgb));
                    } else {
                        diff.setRGB(x, y, DIFF_HIGHLIGHT_RGB);
                    }
                } else {
                    diff.setRGB(x, y, SIZE_MISMATCH_RGB);
                }
            }
        }

        return toPngBytes(diff);
    }

    private static int dimRgb(int rgb) {
        var r = (rgb >> 16) & 0xFF;
        var g = (rgb >> 8) & 0xFF;
        var b = rgb & 0xFF;
        var dim = (r + g + b) / 9;
        return (dim << 16) | (dim << 8) | dim;
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void saveFailArtifacts(String label, byte[] actual, byte[] diff) {
        try {
            Files.createDirectories(DIFF_DIR);
            var prefix = label.replace('/', '_');
            Files.write(DIFF_DIR.resolve(prefix + "-actual.png"), actual);
            Files.write(DIFF_DIR.resolve(prefix + "-diff.png"), diff);
        } catch (IOException ignored) {
            // CI artifact is best-effort; Allure attachments are primary.
        }
    }

    private static BufferedImage readImage(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            var image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("Unsupported screenshot format");
            }
            return image;
        }
    }
}
