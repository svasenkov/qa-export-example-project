package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import helpers.TokensCss;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Layer("infra")
@Epic("Test infra")
@Feature("Tokens CSS")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-frontend")
@DisplayName("TokensCss")
class TokensCssTest extends AllureMeta {

    @ParameterizedTest
    @MethodSource("canonicalSizeTokens")
    @DisplayName("tokens.css keeps canonical component size tokens")
    void tokensMatchComponentSizesCanon(String token, String expected) throws Exception {
        var tokens = TokensCss.parseRootTokens(TokensCss.defaultTokensPath());
        assertTrue(tokens.containsKey(token), "Missing token: " + token);
        assertEquals(expected, tokens.get(token));
    }

    static Stream<Arguments> canonicalSizeTokens() {
        return Stream.of(
                Arguments.of("--control-height-md", "36px"),
                Arguments.of("--icon-size-md", "18px"),
                Arguments.of("--input-min-width", "200px"),
                Arguments.of("--header-height", "40px")
        );
    }

    @Test
    @DisplayName("defaultTokensPath resolves an existing tokens.css")
    void defaultTokensPathResolvesExistingFile() {
        assertTrue(Files.exists(TokensCss.defaultTokensPath()));
    }

    @Test
    @DisplayName("firstExisting returns the first path that exists")
    void firstExistingReturnsFirstHit(@TempDir Path temp) throws Exception {
        var missing = temp.resolve("missing.css");
        var hit = temp.resolve("hit.css");
        var later = temp.resolve("later.css");
        Files.writeString(hit, ":root { --x: 1px; }");
        Files.writeString(later, ":root { --y: 2px; }");

        assertEquals(hit, TokensCss.firstExisting(missing, hit, later));
    }

    @Test
    @DisplayName("firstExisting returns the last path when none exist")
    void firstExistingReturnsLastWhenNoneExist(@TempDir Path temp) {
        var missing = temp.resolve("missing.css");
        var fallback = temp.resolve("fallback.css");

        assertEquals(fallback, TokensCss.firstExisting(missing, fallback));
    }

    @Test
    @DisplayName("resolveFromAppRoot prefers the frontend hub over any vendor copy")
    void resolveFromAppRootPrefersHub(@TempDir Path temp) throws Exception {
        var hub = writeTokens(temp.resolve(Path.of(
                "frontend", "_shared", "frontend-javascript-app", "css", "tokens.css")));
        writeTokens(temp.resolve(Path.of(
                "frontend", "javascript", "frontend-javascript-vue",
                "vendor", "ds", "css", "tokens.css")));

        assertEquals(hub.toAbsolutePath().normalize(), TokensCss.resolveFromAppRoot(temp));
    }

    @Test
    @DisplayName("resolveFromAppRoot finds vendor/ds on javascript-vue when hub is missing")
    void resolveFromAppRootFindsVueVendorWhenHubMissing(@TempDir Path temp) throws Exception {
        var vue = writeTokens(temp.resolve(Path.of(
                "frontend", "javascript", "frontend-javascript-vue",
                "vendor", "ds", "css", "tokens.css")));

        assertEquals(vue.toAbsolutePath().normalize(), TokensCss.resolveFromAppRoot(temp));
    }

    @Test
    @DisplayName("resolveFromAppRoot ignores scripts/.github/node_modules and uses a product cell")
    void resolveFromAppRootSkipsNonProductFrontendDirs(@TempDir Path temp) throws Exception {
        writeTokens(temp.resolve(Path.of(
                "frontend", "scripts", "not-a-cell", "vendor", "ds", "css", "tokens.css")));
        writeTokens(temp.resolve(Path.of(
                "frontend", ".github", "workflows", "vendor", "ds", "css", "tokens.css")));
        writeTokens(temp.resolve(Path.of(
                "frontend", "node_modules", "pkg", "vendor", "ds", "css", "tokens.css")));
        writeTokens(temp.resolve(Path.of(
                "frontend", "javascript", ".github", "vendor", "ds", "css", "tokens.css")));
        var vue = writeTokens(temp.resolve(Path.of(
                "frontend", "javascript", "frontend-javascript-vue",
                "vendor", "ds", "css", "tokens.css")));

        assertEquals(vue.toAbsolutePath().normalize(), TokensCss.resolveFromAppRoot(temp));
    }

    @Test
    @DisplayName("resolveFromAppRoot falls back to vendor/frontend-javascript-app when vendor/ds is missing")
    void resolveFromAppRootFallsBackToVendoredApp(@TempDir Path temp) throws Exception {
        var baked = writeTokens(temp.resolve(Path.of(
                "frontend", "javascript", "frontend-javascript-vue",
                "vendor", "frontend-javascript-app", "css", "tokens.css")));

        assertEquals(baked.toAbsolutePath().normalize(), TokensCss.resolveFromAppRoot(temp));
    }

    @Test
    @DisplayName("resolveFromAppRoot falls back to hub path when frontend tree is missing")
    void resolveFromAppRootFallsBackToHubWhenFrontendMissing(@TempDir Path temp) {
        var hub = temp.resolve(Path.of(
                "frontend", "_shared", "frontend-javascript-app", "css", "tokens.css"));

        assertEquals(hub.toAbsolutePath().normalize(), TokensCss.resolveFromAppRoot(temp));
    }

    @Test
    @DisplayName("parseRootTokens rejects css without :root block")
    void parseRootTokensRejectsMissingRootBlock(@TempDir Path temp) throws Exception {
        var css = temp.resolve("tokens-invalid.css");
        Files.writeString(css, "body { color: red; }");

        assertThrows(IllegalArgumentException.class, () -> TokensCss.parseRootTokens(css));
    }

    private static Path writeTokens(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, ":root { --x: 1px; }");
        return file;
    }
}
