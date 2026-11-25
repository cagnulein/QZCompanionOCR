package org.cagnulein.qzcompanionpeloton;

/** Pure layout-selection helpers: QZ serves the actual HTML over the network. */
final class FloatingWindowStyle {
    static final String PREFERENCE = "horizontalfloating";
    static final int DEFAULT_BAR_HEIGHT_DP = 84;
    private static final String VERTICAL_PATH = "/floating/floating.htm";
    private static final String HORIZONTAL_PATH = "/floating/hfloating.htm";

    private FloatingWindowStyle() {}

    static String key(String originalKey, boolean horizontal) {
        return horizontal ? originalKey + "Horizontal" : originalKey;
    }

    static String url(String originalUrl, boolean horizontal) {
        if (originalUrl == null || !horizontal) return originalUrl;
        int start = originalUrl.indexOf(VERTICAL_PATH);
        if (start < 0) return originalUrl;
        int end = start + VERTICAL_PATH.length();
        if (end < originalUrl.length()) {
            char next = originalUrl.charAt(end);
            if (next != '?' && next != '#') return originalUrl;
        }
        return originalUrl.substring(0, start) + HORIZONTAL_PATH + originalUrl.substring(end);
    }
}
