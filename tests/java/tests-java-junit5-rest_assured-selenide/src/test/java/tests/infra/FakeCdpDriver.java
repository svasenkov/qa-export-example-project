package tests.infra;

import org.openqa.selenium.chromium.HasCdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RemoteWebDriver stub that IS a {@link HasCdp} instance — covers the direct
 * CDP path in {@code ViewportHelper} without going through the Augmenter.
 */
class FakeCdpDriver extends FakeWebDriver implements HasCdp {

    private final List<String> cdpCommands = new ArrayList<>();

    FakeCdpDriver() {
        super("chrome");
    }

    List<String> cdpCommands() {
        return cdpCommands;
    }

    @Override
    public Map<String, Object> executeCdpCommand(String commandName, Map<String, Object> parameters) {
        cdpCommands.add(commandName);
        return Map.of();
    }
}
