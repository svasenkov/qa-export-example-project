package tests.infra;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Standalone element for harness unit tests: state (visibility, attributes, text)
 * is scripted in the test, click() may run a scripted side effect, and
 * screenshot() returns scripted PNG bytes — the same contract a real element has.
 */
public class FakeWebElement implements WebElement, TakesScreenshot {

    private boolean displayed = true;
    private String text = "";
    private final Map<String, String> attributes = new ConcurrentHashMap<>();
    private byte[] screenshotPng;
    private Consumer<FakeWebElement> onClick;
    private RuntimeException onScreenshot;

    FakeWebElement displayed(boolean value) {
        displayed = value;
        return this;
    }

    FakeWebElement text(String value) {
        text = value;
        return this;
    }

    FakeWebElement attribute(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    FakeWebElement screenshot(byte[] png) {
        screenshotPng = png;
        return this;
    }

    FakeWebElement onClick(Consumer<FakeWebElement> action) {
        onClick = action;
        return this;
    }

    FakeWebElement failOnScreenshot(RuntimeException failure) {
        onScreenshot = failure;
        return this;
    }

    @Override
    public void click() {
        if (onClick != null) {
            onClick.accept(this);
        }
    }

    @Override
    public boolean isDisplayed() {
        return displayed;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public String getDomAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public String getDomProperty(String name) {
        return attributes.get(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
        if (onScreenshot != null) {
            throw onScreenshot;
        }
        if (target == OutputType.BYTES) {
            return (X) screenshotPng;
        }
        if (target == OutputType.BASE64) {
            return (X) Base64.getEncoder().encodeToString(screenshotPng);
        }
        return target.convertFromPngBytes(screenshotPng);
    }

    @Override
    public void submit() {
    }

    @Override
    public void sendKeys(CharSequence... keysToSend) {
    }

    @Override
    public void clear() {
    }

    @Override
    public String getTagName() {
        return "div";
    }

    @Override
    public boolean isSelected() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public List<WebElement> findElements(By by) {
        return List.of();
    }

    @Override
    public WebElement findElement(By by) {
        return this;
    }

    @Override
    public Point getLocation() {
        return new Point(0, 0);
    }

    @Override
    public Dimension getSize() {
        return new Dimension(10, 10);
    }

    @Override
    public Rectangle getRect() {
        return new Rectangle(0, 0, 10, 10);
    }

    @Override
    public String getCssValue(String propertyName) {
        return "";
    }

    @Override
    public String getAccessibleName() {
        return "";
    }

    @Override
    public String getAriaRole() {
        return "";
    }
}
