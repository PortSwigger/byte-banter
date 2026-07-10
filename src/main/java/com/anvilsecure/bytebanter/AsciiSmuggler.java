package com.anvilsecure.bytebanter;

/**
 * ASCII smuggling helper: hides ASCII text inside the invisible Unicode "Tags"
 * block (U+E0000 - U+E007F). Each ASCII code point {@code c} (0x00 - 0x7F) maps
 * to the Tags code point {@code 0xE0000 + c}. Those code points render as
 * nothing in virtually every UI yet survive copy/paste and are decoded by many
 * LLMs, which is exactly what makes them useful for prompt-injection payloads.
 *
 * <p>Reference:
 * <a href="https://marcogerber.ch/ascii-smuggling-a-threat-hidden-in-plain-sight/">
 * ASCII Smuggling — a threat hidden in plain sight</a>.</p>
 */
public final class AsciiSmuggler {

    /** Base of the Unicode Tags block; ASCII char {@code c} encodes to {@code TAG_BASE + c}. */
    private static final int TAG_BASE = 0xE0000;
    /** Inclusive upper bound of the Tags block (U+E007F). */
    private static final int TAG_MAX = 0xE007F;

    private AsciiSmuggler() {
    }

    /**
     * Encodes every ASCII character (code point &lt;= 0x7F) of {@code text} into
     * its invisible Tags-block equivalent. Non-ASCII characters are passed
     * through unchanged so the caller can mix visible non-ASCII carriers if
     * desired.
     */
    public static String encode(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length() * 2);
        text.codePoints().forEach(cp -> {
            if (cp <= 0x7F) {
                sb.appendCodePoint(TAG_BASE + cp);
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    /**
     * Reverses {@link #encode(String)}: every code point in the Tags block is
     * mapped back to its ASCII character; all other code points are left
     * untouched. Useful for a decode/inspection tool and for round-trip tests.
     */
    public static String decode(String smuggled) {
        if (smuggled == null || smuggled.isEmpty()) {
            return smuggled;
        }
        StringBuilder sb = new StringBuilder(smuggled.length());
        smuggled.codePoints().forEach(cp -> {
            if (cp >= TAG_BASE && cp <= TAG_MAX) {
                sb.appendCodePoint(cp - TAG_BASE);
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}
