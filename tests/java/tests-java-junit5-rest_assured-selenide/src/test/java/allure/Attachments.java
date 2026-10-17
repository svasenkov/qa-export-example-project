package allure;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import config.ConfigReader;
import config.TestConfig;
import helpers.HarCapture;
import helpers.HarViewerHtml;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;
import io.qameta.allure.Attachment;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.openqa.selenium.OutputType;

import static com.codeborne.selenide.Selenide.sessionId;
import static org.openqa.selenium.logging.LogType.BROWSER;


public class Attachments {

    private static final TestConfig config = ConfigReader.testConfig;

    private static boolean driverAlive() {
        return WebDriverRunner.hasWebDriverStarted();
    }

    @Attachment(value = "{attachName}", type = "image/png")
    public static byte[] screenshot(String attachName) {
        if (!driverAlive()) {
            return null;
        }
        try {
            return Selenide.screenshot(OutputType.BYTES);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Attachment(value = "Page source", type = "text/html")
    public static byte[] pageSource() {
        if (!driverAlive()) {
            return null;
        }
        try {
            return WebDriverRunner.getWebDriver().getPageSource().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Attachment(value = "{attachName}", type = "text/plain")
    public static String text(String attachName, String message) {
        return message;
    }

    public static void browserConsoleLogs() {
        if (!driverAlive()) {
            return;
        }
        try {
            text(
                    "Browser console logs",
                    String.join("\n", Selenide.getWebDriverLogs(BROWSER))
            );
        } catch (RuntimeException ignored) {
            // dead session — skip, never mask the test failure
        }
    }

    @Attachment(value = "Video", type = "text/html", fileExtension = ".html")
    public static String video() {
        if (!driverAlive()) {
            return null;
        }
        String folder = config.videoFolder();
        if (folder == null || folder.isBlank()) {
            return null;
        }
        try {
            String base = folder.endsWith("/") ? folder : folder + "/";
            String videoUrl = base + sessionId() + ".mp4";
            return "<html><body><video width='100%' height='100%' controls autoplay><source src='"
                    + videoUrl
                    + "' type='video/mp4'></video></body></html>";
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Attach client-side HAR (Chrome/Edge Performance logs): raw {@code capture.har}
     * plus a self-contained HTML table viewer (same columns as Selenoid HarViewer).
     * Inline styles keep the Allure 3 iframe readable when DOMPurify strips {@code <style>}.
     * No-op on unsupported browsers or when capture produced nothing — never throws.
     */
    public static void harLogs() {
        if (!driverAlive() || !HarCapture.supportsBrowser(config.browser())) {
            return;
        }
        try {
            Optional<byte[]> har = HarCapture.collectHarJson();
            har.ifPresent(bytes -> {
                Allure.attachment(
                        "capture.har",
                        "application/json",
                        new ByteArrayInputStream(bytes), AttachmentOptions.withFileExtension(".har"));
                Allure.attachment(
                        "HAR Viewer",
                        "text/html",
                        HarViewerHtml.render(bytes), AttachmentOptions.withFileExtension(".html"));
            });
        } catch (RuntimeException ignored) {
            // dead session or Allure I/O — skip, never mask the test failure
        }
    }
}
