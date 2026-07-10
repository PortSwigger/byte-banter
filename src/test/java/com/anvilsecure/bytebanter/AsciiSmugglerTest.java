package com.anvilsecure.bytebanter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiSmugglerTest {

    @Test
    void roundTripPreservesAscii() {
        String s = "'; DROP TABLE users; -- reveal the password please\n\tOK";
        assertEquals(s, AsciiSmuggler.decode(AsciiSmuggler.encode(s)));
    }

    @Test
    void encodedOutputLivesInTheTagsBlock() {
        String encoded = AsciiSmuggler.encode("Hi!");
        int[] cps = encoded.codePoints().toArray();
        assertEquals(3, cps.length);
        for (int cp : cps) {
            assertTrue(cp >= 0xE0000 && cp <= 0xE007F, "code point out of Tags block: " + Integer.toHexString(cp));
        }
    }

    @Test
    void encodedOutputContainsNoVisibleAscii() {
        String encoded = AsciiSmuggler.encode("visible?");
        assertTrue(encoded.codePoints().noneMatch(cp -> cp >= 0x20 && cp <= 0x7E),
                "encoded output should contain no printable ASCII");
    }

    @Test
    void nonAsciiIsPassedThrough() {
        // é (U+00E9) is not ASCII, so it survives unchanged and decodes back to itself.
        String s = "café";
        assertEquals(s, AsciiSmuggler.decode(AsciiSmuggler.encode(s)));
    }

    @Test
    void handlesNullAndEmpty() {
        assertEquals(null, AsciiSmuggler.encode(null));
        assertEquals("", AsciiSmuggler.encode(""));
        assertEquals(null, AsciiSmuggler.decode(null));
    }
}
