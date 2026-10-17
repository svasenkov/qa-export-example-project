package tests.infra;

import tests.AllureMeta;
import annotations.Layer;
import helpers.ScreenshotHelper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Layer("infra")
@Epic("Test infra")
@Feature("ScreenshotHelper")
@Severity(SeverityLevel.NORMAL)
@Tag("infra")
@Tag("infra-backend")
@DisplayName("ScreenshotHelper")
@Execution(ExecutionMode.SAME_THREAD)
class ScreenshotHelperTest extends AllureMeta {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "mock, mock",
            "stage, stage",
            "prod, prod",
            "ci, prod",
            "'', prod",
    })
    @DisplayName("screenshotMode maps env to a stand folder")
    void screenshotModeMapsEnvToStandFolder(String env, String folder) {
        assertEquals(folder, ScreenshotHelper.screenshotMode(env));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "local", "multistack_ci"})
    @DisplayName("screenshotMode rejects unknown env")
    void screenshotModeRejectsUnknownEnv(String env) {
        var error = assertThrows(IllegalStateException.class, () -> ScreenshotHelper.screenshotMode(env));
        assertTrue(error.getMessage().contains("unknown env"), error.getMessage());
    }
}
