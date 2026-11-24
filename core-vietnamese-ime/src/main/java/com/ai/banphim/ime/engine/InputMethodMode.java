package com.ai.banphim.ime.engine;

/**
 * Vietnamese offline input method mode determining how diacritics and tones are composed.
 */
public enum InputMethodMode {
    /** Letter-based Telex typing (e.g., aw, aa, dd, s/f/r/x/j). */
    TELEX,
    /** Number-based VNI typing (e.g., 1-5 tones, 6-8 modifiers, 9 for đ). */
    VNI,
    /** Disables Vietnamese composition for plain QWERTY input. */
    OFF
}
