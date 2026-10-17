package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import helpers.HarCapture;
import java.util.List;
import java.util.logging.Level;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Layer("infra")
@Epic("Test infra")
@Feature("HAR capture")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
class HarCaptureTest extends AllureMeta {

    @Test
    void toHarBuildsEntriesFromPerformanceLogs() {
        long now = System.currentTimeMillis();
        String requestMsg = """
                {"message":{"method":"Network.requestWillBeSent","params":{"requestId":"r1","timestamp":1.0,"wallTime":1700000000.0,"request":{"url":"https://example.com/","method":"GET","headers":{"Accept":"*/*"}}}}}
                """.trim();
        String responseMsg = """
                {"message":{"method":"Network.responseReceived","params":{"requestId":"r1","response":{"status":200,"statusText":"OK","mimeType":"text/html","headers":{"content-type":"text/html"},"protocol":"http/1.1","encodedDataLength":42}}}}
                """.trim();
        String finishedMsg = """
                {"message":{"method":"Network.loadingFinished","params":{"requestId":"r1","timestamp":1.05,"encodedDataLength":1280}}}
                """.trim();

        LogEntries entries = new LogEntries(List.of(
                new LogEntry(Level.INFO, now, requestMsg),
                new LogEntry(Level.INFO, now + 1, responseMsg),
                new LogEntry(Level.INFO, now + 2, finishedMsg)
        ));

        String har = HarCapture.toHar(entries);
        assertTrue(har.contains("1.2"), () -> "HAR missing version: " + har);
        assertTrue(har.contains("example.com"), () -> "HAR missing url: " + har);
        assertTrue(har.contains("200"), () -> "HAR missing status: " + har);
        assertTrue(har.contains("1280"), () -> "HAR missing loadingFinished size: " + har);
        assertTrue(HarCapture.supportsBrowser("chrome"));
        assertTrue(!HarCapture.supportsBrowser("firefox"));
    }
}
