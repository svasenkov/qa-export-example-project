package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import helpers.AllureHtmlPreview;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AllureHtmlPreview} — DOMPurify strip + dark-theme inject contract.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Allure HTML preview")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-backend")
@DisplayName("AllureHtmlPreview")
class AllureHtmlPreviewTest extends AllureMeta {

    @Test
    @DisplayName("stripUnsafe returns empty for null and empty input")
    void stripUnsafeEmpty() {
        assertEquals("", AllureHtmlPreview.stripUnsafe(null));
        assertEquals("", AllureHtmlPreview.stripUnsafe(""));
    }

    @Test
    @DisplayName("stripUnsafe removes style and script blocks, keeps markup")
    void stripUnsafeRemovesBlocks() {
        var clean = AllureHtmlPreview.stripUnsafe(
                "<style>p{color:red}</style><p>x</p><SCRIPT>alert(1)</SCRIPT><div>y</div>");
        assertFalse(clean.contains("<style"), "style stripped");
        assertFalse(clean.contains("<SCRIPT"), "script stripped case-insensitively");
        assertTrue(clean.contains("<p>x</p>"), "markup kept");
        assertTrue(clean.contains("<div>y</div>"), "markup kept");
    }

    @Test
    @DisplayName("asIframe light mode returns stripped html unchanged")
    void asIframeLight() {
        assertEquals("<p>x</p>", AllureHtmlPreview.asIframe("<style></style><p>x</p>", false));
    }

    @Test
    @DisplayName("asIframe dark mode injects theme style into head, body, or prefix")
    void asIframeDark() {
        var withHead = AllureHtmlPreview.asIframe("<html><HEAD class='x'></HEAD><body>b</body></html>", true);
        assertTrue(withHead.contains("<HEAD class='x'><style data-allure-preview-theme>"),
                "style injected right after head open tag");

        var withBody = AllureHtmlPreview.asIframe("<html><body>b</body></html>", true);
        assertTrue(withBody.contains("<body><style data-allure-preview-theme>"),
                "no head → injected after body");

        var bare = AllureHtmlPreview.asIframe("<p>x</p>", true);
        assertTrue(bare.startsWith("<style data-allure-preview-theme>"),
                "no head/body → prefixed");
        assertTrue(bare.endsWith("<p>x</p>"));
    }
}
