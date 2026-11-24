package org.cagnulein.qzcompanionpeloton;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Companion-only metric choices; IDs match QZ's hfloating.htm availableMetrics. */
final class CompanionMetrics {
    static final String PREFERENCE = "companionHorizontalMetricIds";
    static final String DEFAULT_SELECTION = "watt,cadence,heart,resistance,pelotonresistance";

    static final String[] IDS = {
        "speed", "inclination", "pace", "elevation", "cadence", "heart",
        "watt", "powerzone", "resistance", "pelotonresistance",
        "calories", "jouls", "distance", "elapsed", "rowremainingtime",
        "pelotonoffset", "gears", "nextrow"
    };

    static final String[] LABELS = {
        "Speed", "Incline", "Pace", "Elevation", "Cadence", "Heart rate",
        "Power", "Power zone", "Resistance", "Peloton resistance",
        "Calories", "Total output", "Distance", "Elapsed time",
        "Remaining time", "Peloton offset", "Gears", "Next interval"
    };

    private CompanionMetrics() {}

    static String normalize(String csv) {
        if (csv == null) return DEFAULT_SELECTION;
        Set<String> filtered = new LinkedHashSet<>();
        for (String item : csv.split(",")) {
            String id = item.trim();
            if (Arrays.asList(IDS).contains(id)) filtered.add(id);
        }
        if (filtered.isEmpty()) return DEFAULT_SELECTION;
        StringBuilder result = new StringBuilder();
        for (String id : filtered) {
            if (result.length() > 0) result.append(',');
            result.append(id);
        }
        return result.toString();
    }

    static boolean[] checked(String csv) {
        Set<String> selected = new LinkedHashSet<>(Arrays.asList(normalize(csv).split(",")));
        boolean[] checked = new boolean[IDS.length];
        for (int i = 0; i < IDS.length; ++i) checked[i] = selected.contains(IDS[i]);
        return checked;
    }

    static String fromChecked(boolean[] values) {
        if (values == null || values.length != IDS.length)
            throw new IllegalArgumentException("Invalid horizontal metric choices");
        StringBuilder selected = new StringBuilder();
        for (int i = 0; i < IDS.length; ++i) {
            if (values[i]) {
                if (selected.length() != 0) selected.append(',');
                selected.append(IDS[i]);
            }
        }
        return normalize(selected.toString());
    }
}
