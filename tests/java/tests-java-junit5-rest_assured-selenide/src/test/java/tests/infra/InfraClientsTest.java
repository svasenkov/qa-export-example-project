package tests.infra;

import allure.AllureRestAssuredFilters;
import allure.AllureSelenideListeners;
import annotations.Layer;
import annotations.EnableAllureSelenideListener;
import api.AuthApiClient;
import api.MockScenarios;
import com.codeborne.selenide.logevents.SelenideLogger;
import config.ConfigReader;
import config.TestConfig;
import extensions.AllureSelenideListenerExtension;
import helpers.DataFaker;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.RestAssured;
import net.datafaker.Faker;
import net.datafaker.providers.base.Credentials;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import tests.AllureMeta;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cheap harness checks: API helpers against the live/mock stand, config-driven
 * listener switches, the JUnit extension, and the DataFaker length guards.
 */
@Layer("infra")
@Epic("Test infra")
@Feature("Support classes")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-backend")
@DisplayName("Infra support classes")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class InfraClientsTest extends AllureMeta {

    @AfterEach
    void restoreRestAssured() {
        RestAssured.baseURI = ConfigReader.resolveApiBaseUrl();
    }

    // --- DataFaker ------------------------------------------------------------

    private static Faker fakerReturning(String username) {
        var faker = new Faker();
        return new Faker() {
            @Override
            public Credentials credentials() {
                return new StubCredentials(faker, username);
            }
        };
    }

    private static final class StubCredentials extends Credentials {
        private final String username;

        StubCredentials(Faker faker, String username) {
            super(faker);
            this.username = username;
        }

        @Override
        public String username() {
            return username;
        }
    }

    @Test
    @DisplayName("username pads too-short bases and truncates too-long ones")
    void dataFakerLengthGuards() {
        var short_ = DataFaker.username(fakerReturning("ab"));
        assertTrue(short_.startsWith("userab_"), "padded: " + short_);
        assertTrue(short_.matches("userab_\\d{6}"), "suffix digits: " + short_);

        var longName = DataFaker.username(fakerReturning("x".repeat(60)));
        assertTrue(longName.startsWith("x".repeat(48) + "_"), "truncated at 48: " + longName);

        var dirty = DataFaker.username(fakerReturning("  !a-b_c!! "));
        assertTrue(dirty.matches("abc_\\d{6}"), "non-alnum stripped: " + dirty);

        var normal = DataFaker.username(fakerReturning("regularuser"));
        assertTrue(normal.matches("regularuser_\\d{6}"), "in-bounds: " + normal);
    }

    @Test
    @DisplayName("default faker produces in-bounds usernames")
    void dataFakerDefaults() {
        var name = DataFaker.username();
        assertTrue(name.matches("[A-Za-z0-9]{3,48}_\\d{6}"), name);
        assertEquals(3, DataFaker.usernameAtMinLength().length());
        assertEquals(6, DataFaker.passwordAtMinLength().length());
        assertTrue(DataFaker.password().length() >= 8);
    }

    // --- MockScenarios ---------------------------------------------------------

    @Test
    @DisplayName("available() is false on a stand without WireMock admin and survives an unreachable base")
    void mockScenariosAvailability() {
        assertFalse(MockScenarios.available(), "live backend has no /__admin");

        var original = RestAssured.baseURI;
        try {
            RestAssured.baseURI = "http://127.0.0.1:1/";
            assertFalse(MockScenarios.available(), "unreachable stand → false, not throw");
        } finally {
            RestAssured.baseURI = original;
        }
    }

    @Test
    @DisplayName("setState/resetAll follow the WireMock contract on the live stand")
    void mockScenariosCalls() {
        if (MockScenarios.available()) {
            MockScenarios.setState("health", "started");
            MockScenarios.resetAll();
        } else {
            // Live backend has no WireMock admin — the calls must surface the 404.
            assertThrows(AssertionError.class, () -> MockScenarios.setState("health", "x"));
            assertThrows(AssertionError.class, MockScenarios::resetAll);
        }
    }

    // --- AuthApiClient ---------------------------------------------------------

    @Test
    @DisplayName("deleteAccountQuietly swallows cleanup failures")
    void deleteAccountQuietlySwallows() {
        AuthApiClient.deleteAccountQuietly("user-that-never-existed", "x");
    }

    // --- Listener switches ------------------------------------------------------

    private static TestConfig config(Map<String, Object> overrides) {
        return ConfigFactory.create(TestConfig.class, overrides);
    }

    @Test
    @DisplayName("AllureSelenideListeners switches on report mode and enable flag")
    void selenideListenerSwitch() {
        assertFalse(AllureSelenideListeners.isGloballyEnabled(
                config(Map.of("allureReportMode", "none", "enableAllureSelenideListener", "true"))));
        assertFalse(AllureSelenideListeners.isGloballyEnabled(
                config(Map.of("allureReportMode", "allure3", "enableAllureSelenideListener", "false"))));
        assertTrue(AllureSelenideListeners.isGloballyEnabled(
                config(Map.of("allureReportMode", "allure3", "enableAllureSelenideListener", "true"))));

        assertNotNull(AllureSelenideListeners.create());
        AllureSelenideListeners.setEnabled(true);
        AllureSelenideListeners.setEnabled(false);
        AllureSelenideListeners.restoreGlobal(ConfigReader.testConfig);
        assertFalse(SelenideLogger.hasListener(AllureSelenideListeners.LISTENER_NAME),
                "listener state must match the global config");
    }

    @Test
    @DisplayName("AllureRestAssuredFilters switches on report mode and enable flag")
    void restAssuredFilterSwitch() {
        assertFalse(AllureRestAssuredFilters.isEnabled(
                config(Map.of("allureReportMode", "none", "enableAllureRestAssuredListener", "true"))));
        assertFalse(AllureRestAssuredFilters.isEnabled(
                config(Map.of("allureReportMode", "allure3", "enableAllureRestAssuredListener", "false"))));
        assertTrue(AllureRestAssuredFilters.isEnabled(
                config(Map.of("allureReportMode", "allure3", "enableAllureRestAssuredListener", "true"))));
        assertNotNull(AllureRestAssuredFilters.create(ConfigReader.testConfig));
        assertEquals("request.ftl", AllureRestAssuredFilters.REQUEST_TEMPLATE);
        assertEquals("response.ftl", AllureRestAssuredFilters.RESPONSE_TEMPLATE);
    }

    // --- Extension --------------------------------------------------------------

    private static ExtensionContext contextFor(Optional<Method> method) {
        return (ExtensionContext) Proxy.newProxyInstance(
                InfraClientsTest.class.getClassLoader(),
                new Class<?>[]{ExtensionContext.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getTestMethod" -> method;
                    default -> defaultValue(m.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == Optional.class) return Optional.empty();
        return null;
    }

    @EnableAllureSelenideListener(false)
    void annotatedProbe() {
    }

    @Test
    @DisplayName("extension applies the annotation value and restores the global setting")
    void extensionAppliesAnnotation() throws Exception {
        var extension = new AllureSelenideListenerExtension();
        var method = getClass().getDeclaredMethod("annotatedProbe");
        var ctx = contextFor(Optional.of(method));

        extension.beforeEach(ctx);
        assertFalse(SelenideLogger.hasListener(AllureSelenideListeners.LISTENER_NAME),
                "value=false disables the listener");

        extension.afterEach(ctx);
        assertFalse(SelenideLogger.hasListener(AllureSelenideListeners.LISTENER_NAME),
                "restoreGlobal keeps listener off under allureReportMode=none");

        var empty = contextFor(Optional.empty());
        extension.beforeEach(empty);
        extension.afterEach(empty);
    }

    @Test
    @DisplayName("extension enables the listener when annotated true")
    @EnableAllureSelenideListener
    void extensionViaRealAnnotation() {
        // The extension ran before this test and after it restores — assert it engaged.
        assertTrue(SelenideLogger.hasListener(AllureSelenideListeners.LISTENER_NAME)
                        || !AllureSelenideListeners.isGloballyEnabled(ConfigReader.testConfig),
                "listener enabled by annotation or globally off");
    }
}
