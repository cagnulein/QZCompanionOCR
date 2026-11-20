package org.cagnulein.qzcompanionpeloton;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FloatingWindowStyleTest {
    private static final String URL = "http://192.168.1.5:6666/floating/floating.htm";

    @Test public void verticalIsUnchangedByDefault() {
        assertEquals(URL, FloatingWindowStyle.url(URL, false));
    }

    @Test public void horizontalUsesQzExistingHtml() {
        assertEquals("http://192.168.1.5:6666/floating/hfloating.htm",
                FloatingWindowStyle.url(URL, true));
        assertEquals("http://192.168.1.5:6666/floating/hfloating.htm?x=1#top",
                FloatingWindowStyle.url(URL + "?x=1#top", true));
    }

    @Test public void irrelevantUrlsAreUnchanged() {
        String other = URL + ".backup";
        assertEquals(other, FloatingWindowStyle.url(other, true));
        String alreadyHorizontal = "http://192.168.1.5:6666/floating/hfloating.htm";
        assertEquals(alreadyHorizontal, FloatingWindowStyle.url(alreadyHorizontal, true));
        assertNull(FloatingWindowStyle.url(null, true));
    }

    @Test public void geometryPreferencesDoNotConflict() {
        String key = "floatWindowLayoutUpdateParamY";
        assertEquals(key, FloatingWindowStyle.key(key, false));
        assertEquals(key + "Horizontal", FloatingWindowStyle.key(key, true));
    }
}
