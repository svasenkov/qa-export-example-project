package helpers;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TokensCss {

    private static final Pattern ROOT_BLOCK =
            Pattern.compile(":root\\s*\\{([^}]+)\\}", Pattern.DOTALL);
    private static final Pattern TOKEN =
            Pattern.compile("(--[\\w-]+)\\s*:\\s*([^;]+);");

    private TokensCss() {
    }

    public static Path defaultTokensPath() {
        // cwd = tests module → app root. Tokens are frontend-only; cell id is not pinned.
        return resolveFromAppRoot(Path.of("..", "..", ".."));
    }

    public static Path resolveFromAppRoot(Path appRoot) {
        return firstExisting(tokensCssCandidates(appRoot));
    }

    private static Path[] tokensCssCandidates(Path appRoot) {
        var candidates = new ArrayList<Path>();
        candidates.add(hubTokens(appRoot));
        appendVendorTokens(appRoot.resolve("frontend"), candidates);
        return candidates.toArray(Path[]::new);
    }

    public static Path firstExisting(Path... candidates) {
        Path fallback = candidates[candidates.length - 1].normalize().toAbsolutePath();
        for (var candidate : candidates) {
            var abs = candidate.normalize().toAbsolutePath();
            if (Files.exists(abs)) {
                return abs;
            }
            fallback = abs;
        }
        return fallback;
    }

    public static Map<String, String> parseRootTokens(Path cssFile) throws Exception {
        var css = Files.readString(cssFile);
        var match = ROOT_BLOCK.matcher(css);
        if (!match.find()) {
            throw new IllegalArgumentException(":root block not found in " + cssFile);
        }
        var tokens = new LinkedHashMap<String, String>();
        Matcher tokenMatcher = TOKEN.matcher(match.group(1));
        while (tokenMatcher.find()) {
            tokens.put(tokenMatcher.group(1), tokenMatcher.group(2).trim());
        }
        return tokens;
    }

    private static Path hubTokens(Path appRoot) {
        return appRoot.resolve(Path.of(
                "frontend", "_shared", "frontend-javascript-app", "css", "tokens.css"));
    }

    private static void appendVendorTokens(Path frontendRoot, List<Path> out) {
        File[] langs = frontendRoot.toFile().listFiles(File::isDirectory);
        if (langs == null) {
            return;
        }
        Arrays.sort(langs, Comparator.comparing(File::getName));
        for (File lang : langs) {
            if (!isProductLanguageDir(lang)) {
                continue;
            }
            File[] cells = lang.listFiles(File::isDirectory);
            Arrays.sort(cells, Comparator.comparing(File::getName));
            for (File cell : cells) {
                if (cell.getName().startsWith(".")) {
                    continue;
                }
                out.add(vendorTokens(cell.toPath(), "ds"));
                out.add(vendorTokens(cell.toPath(), "frontend-javascript-app"));
            }
        }
    }

    private static boolean isProductLanguageDir(File dir) {
        String name = dir.getName();
        return !name.startsWith(".") && !name.startsWith("_")
                && !"scripts".equals(name) && !"node_modules".equals(name);
    }

    private static Path vendorTokens(Path cell, String vendor) {
        return cell.resolve(Path.of("vendor", vendor, "css", "tokens.css"));
    }
}
