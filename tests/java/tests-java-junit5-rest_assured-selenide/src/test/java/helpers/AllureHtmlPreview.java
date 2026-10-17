package helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allure 3.13 {@code html-attachment-preview} contract: DOMPurify strips
 * {@code <style>}/{@code <script>}, iframe has no {@code allow-scripts}, then
 * dark theme injects {@code data-allure-preview-theme} into {@code <head>}.
 */
public final class AllureHtmlPreview {

    /** Copied from Allure 3.13 awesome {@code html-attachment-preview}. */
    public static final String DARK_THEME_STYLE =
            "<style data-allure-preview-theme>"
                    + ":root,html,body{background:#1c1c1e !important;color:#e5e5e7 !important;}"
                    + "body *{border-color:rgba(255,255,255,0.12) !important;}"
                    + "</style>";

    private static final Pattern STYLE_BLOCK = Pattern.compile("(?is)<style\\b[^>]*>.*?</style>");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
    private static final Pattern HEAD = Pattern.compile("(?i)<head(\\s[^>]*)?>");
    private static final Pattern BODY = Pattern.compile("(?i)<body(\\s[^>]*)?>");

    private AllureHtmlPreview() {
    }

    public static String stripUnsafe(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String withoutStyle = STYLE_BLOCK.matcher(html).replaceAll("");
        return SCRIPT_BLOCK.matcher(withoutStyle).replaceAll("");
    }

    /**
     * @param dark {@code true} = Allure {@code data-theme=dark} (injects preview CSS);
     *             {@code false} = light report (no inject).
     */
    public static String asIframe(String html, boolean dark) {
        String clean = stripUnsafe(html);
        if (!dark) {
            return clean;
        }
        Matcher head = HEAD.matcher(clean);
        if (head.find()) {
            return head.replaceFirst(Matcher.quoteReplacement(head.group() + DARK_THEME_STYLE));
        }
        Matcher body = BODY.matcher(clean);
        if (body.find()) {
            return body.replaceFirst(Matcher.quoteReplacement(body.group() + DARK_THEME_STYLE));
        }
        return DARK_THEME_STYLE + clean;
    }

    public static void writeGallery(Path dir, String requestHtml, String responseHtml) throws IOException {
        Files.createDirectories(dir);
        write(dir.resolve("request-light.html"), asIframe(requestHtml, false));
        write(dir.resolve("request-dark.html"), asIframe(requestHtml, true));
        write(dir.resolve("response-light.html"), asIframe(responseHtml, false));
        write(dir.resolve("response-dark.html"), asIframe(responseHtml, true));
        write(dir.resolve("index.html"), galleryIndex());
    }

    private static void write(Path path, String html) throws IOException {
        Files.writeString(path, html, StandardCharsets.UTF_8);
    }

    private static String galleryIndex() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>HTTP attachment primitive</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; font: 13px/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                    h1 { margin: 0; padding: 10px 14px; font-size: 14px; border-bottom: 1px solid rgba(127,127,127,.25); }
                    h2 { margin: 0; padding: 8px 14px 4px; font-size: 11px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; opacity: .7; }
                    .grid { display: grid; grid-template-columns: 1fr 1fr; min-height: 100vh; }
                    .light { background: #fff; color: #111; }
                    .dark { background: #1c1c1e; color: #e5e5e7; }
                    iframe { display: block; width: 100%; min-height: 280px; border: 0; background: transparent; }
                    .note { padding: 8px 14px 12px; font-size: 12px; opacity: .75; }
                  </style>
                </head>
                <body>
                  <div class="grid">
                    <section class="light">
                      <h1>Allure light — no preview-theme inject</h1>
                      <p class="note">DOMPurify strip of style/script, same as Allure 3 iframe. Canvas #fff.</p>
                      <h2>Request</h2>
                      <iframe src="request-light.html" title="request light"></iframe>
                      <h2>Response</h2>
                      <iframe src="response-light.html" title="response light"></iframe>
                    </section>
                    <section class="dark">
                      <h1>Allure dark — preview-theme inject</h1>
                      <p class="note">html,body { background:#1c1c1e; color:#e5e5e7 !important }</p>
                      <h2>Request</h2>
                      <iframe src="request-dark.html" title="request dark"></iframe>
                      <h2>Response</h2>
                      <iframe src="response-dark.html" title="response dark"></iframe>
                    </section>
                  </div>
                </body>
                </html>
                """;
    }
}
