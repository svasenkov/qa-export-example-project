package tests.ui;

import tests.TestBase;
import annotations.Layer;
import com.codeborne.selenide.WebDriverRunner;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browser-level a11y audit per SHELL-010: Deque axe-core injected into the live
 * browser on the stand — the full ruleset including color contrast, which the
 * component-level jsdom lint cannot measure.
 */
@Layer("ui")
@Epic("Accessibility")
@Feature("axe audit")
@Severity(SeverityLevel.CRITICAL)
@DisplayName("Accessibility axe audit")
class AccessibilityAxeTests extends TestBase {

    @Test
    @Tag("ui")
    @DisplayName("Home page has no axe violations")
    void homePageHasNoAxeViolations() {
        homePage.openPage();
        assertNoViolations(new AxeBuilder().analyze(WebDriverRunner.getWebDriver()).getViolations());
    }

    @Test
    @Tag("ui")
    @DisplayName("Login page has no axe violations")
    void loginPageHasNoAxeViolations() {
        loginPage.openPage();
        assertNoViolations(new AxeBuilder().analyze(WebDriverRunner.getWebDriver()).getViolations());
    }

    @Test
    @Tag("ui")
    @DisplayName("Register page has no axe violations")
    void registerPageHasNoAxeViolations() {
        registerPage.openPage();
        assertNoViolations(new AxeBuilder().analyze(WebDriverRunner.getWebDriver()).getViolations());
    }

    private static void assertNoViolations(List<Rule> violations) {
        assertTrue(violations.isEmpty(), () -> describe(violations));
    }

    private static String describe(List<Rule> violations) {
        return violations.stream()
                .map(AccessibilityAxeTests::describe)
                .collect(Collectors.joining("\n"));
    }

    private static String describe(Rule violation) {
        var nodes = violation.getNodes().stream()
                .map(node -> node.getHtml() == null ? "?" : node.getHtml())
                .map(html -> html.length() > 120 ? html.substring(0, 120) + "…" : html)
                .collect(Collectors.joining("; "));
        return "%s [%s]: %s — nodes: %s".formatted(
                violation.getId(), violation.getImpact(), violation.getHelp(), nodes);
    }
}
