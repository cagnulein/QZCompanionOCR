package org.cagnulein.qzcompanionpeloton;

/** Text sizing for Companion's horizontal QZ overlay; independent from QZ settings. */
final class CompanionTextScale {
    static final String AUTO_PREFERENCE = "companionHorizontalTextAutoScale";
    static final String PERCENT_PREFERENCE = "companionHorizontalTextPercent";
    static final int DEFAULT_PERCENT = 100;
    static final int MIN_PERCENT = 50;
    static final int MAX_PERCENT = 150;
    static final int STEP_PERCENT = 5;

    private CompanionTextScale() {}

    static int clampPercent(int percent) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
    }

    static int progressForPercent(int percent) {
        return Math.round((clampPercent(percent) - MIN_PERCENT) / (float) STEP_PERCENT);
    }

    static int percentForProgress(int progress) {
        return clampPercent(MIN_PERCENT + Math.max(0, Math.min(20, progress)) * STEP_PERCENT);
    }

    static float calculate(int widthPx, int heightPx, int displayWidthPx,
            int defaultHeightPx, boolean autoScale, int textPercent) {
        float geometryScale = autoScale
                ? Math.min(widthPx / (float) Math.max(1, displayWidthPx),
                           heightPx / (float) Math.max(1, defaultHeightPx))
                : 1.0f;
        float result = geometryScale * clampPercent(textPercent) / 100.0f;
        // Prevent pathological dimensions from collapsing text completely.
        return Math.max(0.30f, Math.min(2.0f, result));
    }
}
