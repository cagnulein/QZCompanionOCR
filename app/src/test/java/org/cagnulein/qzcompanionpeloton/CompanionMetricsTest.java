package org.cagnulein.qzcompanionpeloton;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionMetricsTest {
    @Test public void defaultsToFiveMetrics() {
        assertEquals("watt,cadence,heart,resistance,pelotonresistance", CompanionMetrics.normalize(null));
        assertEquals(18, CompanionMetrics.IDS.length);
        assertEquals(CompanionMetrics.IDS.length, CompanionMetrics.LABELS.length);
        int count = 0;
        for (boolean value : CompanionMetrics.checked(null)) if (value) count++;
        assertEquals(5, count);
    }

    @Test public void unknownAndRepeatedMetricsNeverReachTheWebView() {
        assertEquals("speed,watt,heart",
                CompanionMetrics.normalize("speed,wrong,watt,speed,heart,,"));
    }

    @Test public void remoteSelectionsRoundTripThroughSharedPreferencesFormat() {
        boolean[] checks = new boolean[CompanionMetrics.IDS.length];
        checks[0] = true;
        checks[10] = true;
        String chosen = CompanionMetrics.fromChecked(checks);
        assertEquals("speed,calories", chosen);
        assertArrayEquals(checks, CompanionMetrics.checked(chosen));
    }

    @Test public void cannotPersistAnEmptyOrMalformedChoice() {
        assertEquals(CompanionMetrics.DEFAULT_SELECTION, CompanionMetrics.normalize(""));
        assertEquals(CompanionMetrics.DEFAULT_SELECTION,
                CompanionMetrics.fromChecked(new boolean[CompanionMetrics.IDS.length]));
        try {
            CompanionMetrics.fromChecked(new boolean[1]);
            fail("Wrong size must not be accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
