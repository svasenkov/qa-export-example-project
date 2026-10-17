package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import config.ConfigReader;
import config.TestConfig;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Layer("infra")
@Epic("Test infra")
@Feature("ConfigReader")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-backend")
@DisplayName("ConfigReader")
@Execution(ExecutionMode.SAME_THREAD)
class ConfigReaderTest extends AllureMeta {

    private static TestConfig configWith(Map<String, String> overrides) {
        return ConfigFactory.create(TestConfig.class, overrides);
    }

    @Test
    @DisplayName("resolveBaseUrl adds trailing slash to HTTP baseUrl")
    void resolveBaseUrlAddsTrailingSlash() {
        var config = configWith(Map.of("baseUrl", "http://localhost:3000"));
        assertEquals("http://localhost:3000/", ConfigReader.resolveBaseUrl(config));
    }

    @Test
    @DisplayName("resolveBaseUrl keeps trailing slash on baseUrl")
    void resolveBaseUrlKeepsTrailingSlash() {
        var config = configWith(Map.of("baseUrl", "http://localhost:3000/"));
        assertEquals("http://localhost:3000/", ConfigReader.resolveBaseUrl(config));
    }

    @Test
    @DisplayName("resolveBaseUrl fails fast when baseUrl is empty")
    void resolveBaseUrlFailsWhenEmpty() {
        var config = configWith(Map.of("baseUrl", ""));
        var error = assertThrows(IllegalStateException.class, () -> ConfigReader.resolveBaseUrl(config));
        assertTrue(error.getMessage().contains("Set baseUrl"));
    }

    @Test
    @DisplayName("resolveApiBaseUrl adds trailing slash to HTTP apiBaseUrl")
    void resolveApiBaseUrlAddsTrailingSlash() {
        var config = configWith(Map.of("apiBaseUrl", "http://api.example.com"));
        assertEquals("http://api.example.com/", ConfigReader.resolveApiBaseUrl(config));
    }

    @Test
    @DisplayName("resolveApiBaseUrl fails fast when apiBaseUrl is empty")
    void resolveApiBaseUrlFailsWhenEmpty() {
        var config = configWith(Map.of("apiBaseUrl", ""));
        var error = assertThrows(IllegalStateException.class, () -> ConfigReader.resolveApiBaseUrl(config));
        assertTrue(error.getMessage().contains("Set apiBaseUrl"));
    }

    @Test
    @DisplayName("loaded baseUrl has no trailing slash (Selenide Configuration.baseUrl)")
    void loadedBaseUrlHasNoTrailingSlash() {
        assertEquals("http://localhost:9821", ConfigReader.testConfig.baseUrl());
    }

    @Test
    @DisplayName("resolveBaseUrl uses loaded config")
    void resolveBaseUrlUsesLoadedConfig() {
        assertEquals("http://localhost:9821/", ConfigReader.resolveBaseUrl());
    }

    @Test
    @DisplayName("resolveApiBaseUrl uses loaded config")
    void resolveApiBaseUrlUsesLoadedConfig() {
        assertEquals("http://localhost:8831/", ConfigReader.resolveApiBaseUrl());
    }

    @Test
    @DisplayName("private constructor keeps utility class closed")
    void privateConstructorIsReachable() throws Exception {
        var constructor = ConfigReader.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
