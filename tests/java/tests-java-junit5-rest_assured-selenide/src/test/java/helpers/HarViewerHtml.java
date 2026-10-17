package helpers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.openqa.selenium.json.Json;

/**
 * Server-rendered HAR viewer HTML for Allure attachments.
 * <p>
 * Markup matches design-system / Selenoid {@code HarViewer}: table columns
 * Method · Status · URL · Type · Size · Time. Expandable detail uses
 * {@code <details>} (no JS) so Allure CSP and DOMPurify still show content.
 * Critical layout uses {@code <table>} + inline styles so Allure 3 sanitizer
 * (strips {@code <style>}) does not collapse columns. Raw HAR is a separate
 * Allure attachment ({@code capture.har}).
 */
public final class HarViewerHtml {

    private static final Json JSON = new Json();
    private static final String TEMPLATE = loadTemplate();

    private HarViewerHtml() {
    }

    public static String render(byte[] harJson) {
        if (harJson == null || harJson.length == 0) {
            throw new IllegalArgumentException("harJson is empty");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.toType(new String(harJson, StandardCharsets.UTF_8), Map.class);
            Object logObj = root.get("log");
            if (!(logObj instanceof Map<?, ?> logRaw)) {
                return fillTemplate("Invalid HAR", "<div class=\"error\">Missing log section</div>");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> log = (Map<String, Object>) logRaw;
            return fillTemplate(buildSummary(log), buildContent(log));
        } catch (RuntimeException ex) {
            return fillTemplate("Parse error",
                    "<div class=\"error\">Failed to render HAR: " + escapeHtml(ex.getMessage()) + "</div>");
        }
    }

    private static String fillTemplate(String summary, String content) {
        return TEMPLATE
                .replace("__SUMMARY__", escapeHtml(summary))
                .replace("__CONTENT__", content);
    }

    @SuppressWarnings("unchecked")
    private static String buildContent(Map<String, Object> log) {
        Object entriesObj = log.get("entries");
        if (!(entriesObj instanceof List<?> entriesRaw) || entriesRaw.isEmpty()) {
            return "<div class=\"empty\">No network entries captured.</div>";
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object item : entriesRaw) {
            if (item instanceof Map<?, ?> map) {
                entries.add((Map<String, Object>) map);
            }
        }
        if (entries.isEmpty()) {
            return "<div class=\"empty\">No network entries captured.</div>";
        }

        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> entry : entries) {
            rows.append(buildEntry(entry));
        }

        // One <tr> per entry (Selenoid-like). Expand via <details> whose summary IS the row.
        String colGrid = "display:grid;grid-template-columns:56px 48px minmax(0,1fr) 120px 72px 64px;"
                + "gap:0 8px;align-items:center;padding:4px 8px";
        return """
                <div class="har-viewer">
                <div class="har-table-wrap" style="overflow:auto">
                <table class="har-table" style="width:100%%;border-collapse:collapse;font-size:12px;line-height:1.35;color:#ccc">
                <thead><tr>
                  <th style="padding:0;border-bottom:1px solid #3d444c;background:#1a1917;color:#999;font-weight:600;text-align:left">
                    <div style="%s">
                      <span>Method</span><span>Status</span><span>URL</span><span>Type</span><span>Size</span><span>Time</span>
                    </div>
                  </th>
                </tr></thead>
                <tbody>%s</tbody>
                </table>
                </div>
                </div>
                """.formatted(colGrid, rows);
    }

    @SuppressWarnings("unchecked")
    private static String buildEntry(Map<String, Object> entry) {
        Map<String, Object> req = mapVal(entry.get("request"));
        Map<String, Object> res = mapVal(entry.get("response"));
        Map<String, Object> timings = mapVal(entry.get("timings"));
        Map<String, Object> content = mapVal(res.get("content"));

        String method = stringVal(req.get("method")).toUpperCase(Locale.ROOT);
        if (method.isEmpty()) {
            method = "GET";
        }
        String url = stringVal(req.get("url"));
        int status = intVal(res.get("status"));
        String statusText = stringVal(res.get("statusText"));
        long size = responseSize(entry);
        double time = Math.max(doubleVal(entry.get("time")), 0);
        String mime = stringVal(content.get("mimeType"));
        if (mime.isEmpty()) {
            mime = "—";
        }

        String colGrid = "display:grid;grid-template-columns:56px 48px minmax(0,1fr) 120px 72px 64px;"
                + "gap:0 8px;align-items:center;padding:4px 8px";
        String statusLabel = status == 0 ? "—" : String.valueOf(status);
        return """
                <tr class="har-row">
                  <td style="padding:0;border-bottom:1px solid #3d444c;vertical-align:top">
                    <details>
                      <summary style="display:block;cursor:pointer;list-style:none;padding:0">
                        <span style="%s;white-space:nowrap">
                          <span class="har-method" style="font-weight:600;color:#89d185">%s</span>
                          <span class="%s" style="%s">%s</span>
                          <span class="har-url" title="%s" style="overflow:hidden;text-overflow:ellipsis">%s</span>
                          <span class="har-mime" style="color:#999;overflow:hidden;text-overflow:ellipsis">%s</span>
                          <span>%s</span>
                          <span>%.0f ms</span>
                        </span>
                      </summary>
                      <div class="har-detail" style="padding:8px 12px 12px;background:rgba(0,0,0,0.18);white-space:normal">
                        <div class="har-section__title" style="margin:0 0 6px;color:#999;font-size:11px;font-weight:600;text-transform:uppercase">Response Headers</div>
                        %s
                        <div class="har-section__title" style="margin:10px 0 6px;color:#999;font-size:11px;font-weight:600;text-transform:uppercase">Request Headers</div>
                        %s
                        <div class="har-section__title" style="margin:10px 0 6px;color:#999;font-size:11px;font-weight:600;text-transform:uppercase">Timings</div>
                        %s
                        <div class="har-section__title" style="margin:10px 0 6px;color:#999;font-size:11px;font-weight:600;text-transform:uppercase">Response</div>
                        %s
                      </div>
                    </details>
                  </td>
                </tr>
                """.formatted(
                colGrid,
                escapeHtml(method),
                statusClass(status),
                statusColor(status),
                statusLabel,
                escapeHtml(url),
                escapeHtml(url),
                escapeHtml(mime),
                escapeHtml(formatBytes(size)),
                time,
                buildHeaderKv(res.get("headers")),
                buildHeaderKv(req.get("headers")),
                buildTimingsPanel(timings, time),
                buildResponsePanel(content, size, status, statusText));
    }

    private static String statusColor(int status) {
        if (status >= 500) {
            return "color:#f48771";
        }
        if (status >= 400) {
            return "color:#cca700";
        }
        if (status >= 300) {
            return "color:#6cb6ff";
        }
        if (status > 0) {
            return "color:#89d185";
        }
        return "color:#999";
    }

    private static String buildHeaderKv(Object headersObj) {
        List<Map<String, String>> headers = headerMaps(headersObj);
        if (headers.isEmpty()) {
            return "<div class=\"har-muted\" style=\"color:#999\">No headers captured.</div>";
        }
        StringBuilder sb = new StringBuilder(
                "<div class=\"har-kv\" style=\"display:grid;grid-template-columns:minmax(96px,140px) 1fr;gap:2px 12px;font-size:12px;line-height:1.4\">");
        for (Map<String, String> h : headers) {
            sb.append("<div class=\"har-kv__k\" style=\"color:#999;word-break:break-all;white-space:normal\">")
                    .append(escapeHtml(stringVal(h.get("name")))).append("</div>");
            sb.append("<div class=\"har-kv__v\" style=\"color:#ccc;word-break:break-word;overflow-wrap:anywhere;white-space:pre-wrap\">")
                    .append(escapeHtml(stringVal(h.get("value")))).append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> headerMaps(Object headersObj) {
        List<Map<String, String>> out = new ArrayList<>();
        if (!(headersObj instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, String>) map);
            }
        }
        return out;
    }

    private static String buildTimingsPanel(Map<String, Object> timings, double totalMs) {
        StringBuilder sb = new StringBuilder(
                "<div class=\"har-kv\" style=\"display:grid;grid-template-columns:minmax(96px,140px) 1fr;gap:2px 12px;font-size:12px;line-height:1.4\">");
        appendTiming(sb, "blocked", timings.get("blocked"));
        appendTiming(sb, "dns", timings.get("dns"));
        appendTiming(sb, "connect", timings.get("connect"));
        appendTiming(sb, "ssl", timings.get("ssl"));
        appendTiming(sb, "send", timings.get("send"));
        appendTiming(sb, "wait", timings.get("wait"));
        appendTiming(sb, "receive", timings.get("receive"));
        sb.append("<div class=\"har-kv__k\" style=\"color:#999\">total</div><div class=\"har-kv__v\" style=\"color:#ccc\">")
                .append(String.format(Locale.ROOT, "%.0f ms", totalMs))
                .append("</div></div>");
        return sb.toString();
    }

    private static void appendTiming(StringBuilder sb, String name, Object value) {
        double ms = doubleVal(value);
        String text = ms < 0 ? "—" : String.format(Locale.ROOT, "%.0f ms", ms);
        sb.append("<div class=\"har-kv__k\" style=\"color:#999\">").append(name).append("</div>");
        sb.append("<div class=\"har-kv__v\" style=\"color:#ccc\">").append(text).append("</div>");
    }

    private static String buildResponsePanel(Map<String, Object> content, long size, int status, String statusText) {
        String mime = stringVal(content.get("mimeType"));
        if (mime.isEmpty()) {
            mime = "—";
        }
        String bodyNote = "Body not captured (meta / headers + size only).";
        Object text = content.get("text");
        if (text instanceof String body && !body.isEmpty()) {
            bodyNote = body;
        }
        String statusLabel = status == 0 ? "—" : status + (statusText.isEmpty() ? "" : " " + statusText);
        return """
                <div class="har-kv" style="display:grid;grid-template-columns:minmax(96px,140px) 1fr;gap:2px 12px;font-size:12px;line-height:1.4">
                  <div class="har-kv__k" style="color:#999">status</div><div class="har-kv__v" style="color:#ccc">%s</div>
                  <div class="har-kv__k" style="color:#999">mimeType</div><div class="har-kv__v" style="color:#ccc">%s</div>
                  <div class="har-kv__k" style="color:#999">size</div><div class="har-kv__v" style="color:#ccc">%s</div>
                </div>
                <div class="har-section__title" style="margin:8px 0 6px;color:#999;font-size:11px;font-weight:600;text-transform:uppercase">Body</div>
                <div class="har-muted har-body" style="color:#999;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere">%s</div>
                """.formatted(
                escapeHtml(statusLabel),
                escapeHtml(mime),
                escapeHtml(formatBytes(size)),
                escapeHtml(bodyNote));
    }

    @SuppressWarnings("unchecked")
    private static String buildSummary(Map<String, Object> log) {
        Object entriesObj = log.get("entries");
        if (!(entriesObj instanceof List<?> entries)) {
            return "0 requests";
        }
        long totalBytes = 0;
        double totalMs = 0;
        for (Object item : entries) {
            if (item instanceof Map<?, ?> entryRaw) {
                Map<String, Object> entry = (Map<String, Object>) entryRaw;
                totalMs += Math.max(doubleVal(entry.get("time")), 0);
                totalBytes += responseSize(entry);
            }
        }
        return entries.size() + " requests | " + formatBytes(totalBytes) + " | "
                + String.format(Locale.ROOT, "%.1f", totalMs / 1000.0) + "s";
    }

    @SuppressWarnings("unchecked")
    private static long responseSize(Map<String, Object> entry) {
        Map<String, Object> res = mapVal(entry.get("response"));
        Map<String, Object> content = mapVal(res.get("content"));
        long size = longVal(content.get("size"));
        return Math.max(size, 0);
    }

    private static Map<String, Object> mapVal(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        return Map.of();
    }

    private static String statusClass(int status) {
        if (status >= 500) {
            return "har-status--err";
        }
        if (status >= 400) {
            return "har-status--warn";
        }
        if (status >= 300) {
            return "har-status--redir";
        }
        if (status > 0) {
            return "har-status--ok";
        }
        return "har-status--muted";
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double doubleVal(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static int intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
    }

    private static String loadTemplate() {
        try (InputStream in = HarViewerHtml.class.getResourceAsStream("/allure/har-viewer-template.html")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource /allure/har-viewer-template.html");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load HAR viewer template", ex);
        }
    }
}
