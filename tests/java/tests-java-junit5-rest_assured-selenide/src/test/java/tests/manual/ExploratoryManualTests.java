package tests.manual;

import tests.AllureMeta;
import annotations.Layer;
import annotations.Manual;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.qameta.allure.Allure.step;

/**
 * Residual @Manual charters (see docs/coverage-profile.md for this module).
 * Auth happy path, catalogue order, reload and garbage token are e2e — only browser residual stays here.
 * Checklist steps for humans; {@link annotations.Manual} marks them for TestOps.
 */
@Layer("manual")
@Epic("Exploratory")
@Feature("Manual checklist")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Exploratory manual")
class ExploratoryManualTests extends AllureMeta {

    @Test
    @Manual
    @Tag("manual")
    @DisplayName("Home residual: 390px viewport and offline error")
    void homeResidualCharter() {
        step("Open / and let health + items load");
        step("Narrow the viewport to 390px — cards stack, nothing overflows");
        step("Kill the network (offline devtools) and reload — items panel shows a readable error, not a blank page");
    }

    @Test
    @Manual
    @Tag("manual")
    @DisplayName("Security residual: XSS, second tab, JWT expiry")
    void securityResidualCharter() {
        step("Register with an XSS / HTML payload in the username — Welcome panel and header show escaped text, no alert");
        step("Sign in in a second tab, logout in the first — observe what the second tab shows on next action");
        step("Wait for token expiry (or shrink JWT_EXPIRATION_MS on a local stand) — expired session degrades to logged-out, not an error page");
    }
}
