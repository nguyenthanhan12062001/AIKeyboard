package com.ai.banphim.ime.engine;

/**
 * Result of processing a keystroke or backspace in the Vietnamese IME engine.
 */
public final class VietnameseEngineResult {
    private final String text;
    private final String rawText;
    private final boolean transformed;
    private final boolean revertSuggested;

    /** Creates an immutable engine step result. */
    public VietnameseEngineResult(String text, String rawText, boolean transformed, boolean revertSuggested) {
        this.text = text;
        this.rawText = rawText;
        this.transformed = transformed;
        this.revertSuggested = revertSuggested;
    }

    /** Returns the composed text after processing the keystroke. */
    public String getText() {
        return text;
    }

    /** Returns the raw unaccented keystroke sequence backing this token. */
    public String getRawText() {
        return rawText;
    }

    /** Returns true if the last keystroke modified existing characters via Telex/VNI rules. */
    public boolean isTransformed() {
        return transformed;
    }

    /** Returns true when a double-key undo or invalid English syllable revert occurred. */
    public boolean isRevertSuggested() {
        return revertSuggested;
    }
}
