package tests.e2e;

import tests.TestBase;
import annotations.Layer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Layer("e2e")
@Epic("Home")
@Feature("Home load")
@Severity(SeverityLevel.NORMAL)
@DisplayName("Home")
class HomeTests extends TestBase {

    @Test
    @Tag("e2e")
    @Tag("smoke")
    @DisplayName("Page load fetches health and items from API")
    void pageLoadFetchesItems() {
        homePage.openPage()
                .shouldShowHealthText("service: " + config.apiHealthService())
                .shouldShowItemText("Alpha");
    }
}
