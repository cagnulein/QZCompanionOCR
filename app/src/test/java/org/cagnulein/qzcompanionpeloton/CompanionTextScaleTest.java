package org.cagnulein.qzcompanionpeloton;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CompanionTextScaleTest {
    private static final int SCREEN = 1920;
    private static final int DEFAULT_HEIGHT = 168; // 84dp on a 2x display

    @Test public void defaultWindowKeepsDefaultTextSize() {
        assertEquals(1.0f, CompanionTextScale.calculate(
                SCREEN, DEFAULT_HEIGHT, SCREEN, DEFAULT_HEIGHT, true, 100), 0.0001f);
    }

    @Test public void reducingHeightBy25PercentReducesTextAndBarBy25Percent() {
        assertEquals(0.75f, CompanionTextScale.calculate(
                SCREEN, 126, SCREEN, DEFAULT_HEIGHT, true, 100), 0.0001f);
    }

    @Test public void reducingWidthBy25PercentAlsoReducesText() {
        assertEquals(0.75f, CompanionTextScale.calculate(
                1440, DEFAULT_HEIGHT, SCREEN, DEFAULT_HEIGHT, true, 100), 0.0001f);
    }

    @Test public void smallerOfWidthAndHeightDeterminesAutomaticScale() {
        assertEquals(0.50f, CompanionTextScale.calculate(
                960, 126, SCREEN, DEFAULT_HEIGHT, true, 100), 0.0001f);
    }

    @Test public void sliderAdjustsAutomaticScale() {
        assertEquals(0.60f, CompanionTextScale.calculate(
                SCREEN, 126, SCREEN, DEFAULT_HEIGHT, true, 80), 0.0001f);
        assertEquals(1.125f, CompanionTextScale.calculate(
                SCREEN, 126, SCREEN, DEFAULT_HEIGHT, true, 150), 0.0001f);
    }

    @Test public void disablingAutoScalingMakesTextSizeIndependentOfWindow() {
        assertEquals(1.20f, CompanionTextScale.calculate(
                960, 84, SCREEN, DEFAULT_HEIGHT, false, 120), 0.0001f);
    }

    @Test public void sliderProgressRoundTripsAndIsClamped() {
        assertEquals(10, CompanionTextScale.progressForPercent(100));
        assertEquals(100, CompanionTextScale.percentForProgress(10));
        assertEquals(50, CompanionTextScale.percentForProgress(-4));
        assertEquals(150, CompanionTextScale.percentForProgress(99));
        assertEquals(50, CompanionTextScale.clampPercent(-400));
        assertEquals(150, CompanionTextScale.clampPercent(10000));
    }

    @Test public void degenerateWindowDimensionsCannotCollapseTextCompletely() {
        assertEquals(0.30f, CompanionTextScale.calculate(
                1, 1, SCREEN, DEFAULT_HEIGHT, true, 100), 0.0001f);
    }
}
