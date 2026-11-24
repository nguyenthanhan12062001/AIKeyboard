package com.ai.banphim.ime.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure Java offline Unicode Hangul 2-Beolsik (두벌식) syllable composition and decomposition engine.
 * Supports initial consonants (Choseong), medial vowels + compound vowels (Jungseong),
 * final consonants + double/compound finals (Jongseong), Dokkaebi-bul final splitting,
 * and step-by-step Backspace decomposition.
 */
public final class KoreanHangulEngine {

    private static final char HANGUL_BASE = 0xAC00;
    private static final char HANGUL_END = 0xD7A3;

    private static final String[] CHO = {
        "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ",
        "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    private static final String[] JUNG = {
        "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ",
        "ㅙ", "ㅚ", "ㅛ", "ㅜ", "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"
    };

    private static final String[] JONG = {
        "", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ",
        "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ",
        "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    private static final Map<String, Integer> CHO_INDEX = new HashMap<>();
    private static final Map<String, Integer> JUNG_INDEX = new HashMap<>();
    private static final Map<String, Integer> JONG_INDEX = new HashMap<>();
    private static final Map<String, String> COMPOUND_JUNG = new HashMap<>();
    private static final Map<String, String[]> SPLIT_JUNG = new HashMap<>();
    private static final Map<String, String> COMPOUND_JONG = new HashMap<>();
    private static final Map<String, String[]> SPLIT_JONG = new HashMap<>();

    static {
        for (int i = 0; i < CHO.length; i++) CHO_INDEX.put(CHO[i], i);
        for (int i = 0; i < JUNG.length; i++) JUNG_INDEX.put(JUNG[i], i);
        for (int i = 1; i < JONG.length; i++) JONG_INDEX.put(JONG[i], i);

        registerCompoundJung("ㅗ", "ㅏ", "ㅘ");
        registerCompoundJung("ㅗ", "ㅐ", "ㅙ");
        registerCompoundJung("ㅗ", "ㅣ", "ㅚ");
        registerCompoundJung("ㅜ", "ㅓ", "ㅝ");
        registerCompoundJung("ㅜ", "ㅔ", "ㅞ");
        registerCompoundJung("ㅜ", "ㅣ", "ㅟ");
        registerCompoundJung("ㅡ", "ㅣ", "ㅢ");

        registerCompoundJong("ㄱ", "ㅅ", "ㄳ");
        registerCompoundJong("ㄴ", "ㅈ", "ㄵ");
        registerCompoundJong("ㄴ", "ㅎ", "ㄶ");
        registerCompoundJong("ㄹ", "ㄱ", "ㄺ");
        registerCompoundJong("ㄹ", "ㅁ", "ㄻ");
        registerCompoundJong("ㄹ", "ㅂ", "ㄼ");
        registerCompoundJong("ㄹ", "ㅅ", "ㄽ");
        registerCompoundJong("ㄹ", "ㅌ", "ㄾ");
        registerCompoundJong("ㄹ", "ㅍ", "ㄿ");
        registerCompoundJong("ㄹ", "ㅎ", "ㅀ");
        registerCompoundJong("ㅂ", "ㅅ", "ㅄ");
    }

    private static void registerCompoundJung(String a, String b, String res) {
        COMPOUND_JUNG.put(a + b, res);
        SPLIT_JUNG.put(res, new String[] { a, b });
    }

    private static void registerCompoundJong(String a, String b, String res) {
        COMPOUND_JONG.put(a + b, res);
        SPLIT_JONG.put(res, new String[] { a, b });
    }

    /** Output of a single Jamo keystroke: text ready to commit plus active composing block. */
    public static final class StepResult {
        public final String committed;
        public final String composing;

        /** Creates an immutable Hangul composition step result. */
        public StepResult(String committed, String composing) {
            this.committed = committed;
            this.composing = composing;
        }
    }

    private KoreanHangulEngine() {}

    /** Returns true if [s] is a single Hangul consonant or vowel compatibility Jamo. */
    public static boolean isHangulJamo(String s) {
        if (s == null || s.length() != 1) return false;
        return CHO_INDEX.containsKey(s) || JUNG_INDEX.containsKey(s);
    }

    /** Processes [jamo] against [currentComposing], applying compound vowels/finals and Dokkaebi-bul splits. */
    public static StepResult inputJamo(String currentComposing, String jamo) {
        if (jamo == null || jamo.isEmpty()) {
            return new StepResult("", currentComposing == null ? "" : currentComposing);
        }
        if (currentComposing == null || currentComposing.isEmpty()) {
            return new StepResult("", jamo);
        }

        char last = currentComposing.charAt(currentComposing.length() - 1);
        boolean isConsonant = CHO_INDEX.containsKey(jamo);
        boolean isVowel = JUNG_INDEX.containsKey(jamo);

        // Case 1: Composing is a standalone consonant jamo (Cho)
        if (currentComposing.length() == 1 && CHO_INDEX.containsKey(currentComposing)) {
            if (isVowel) {
                int cho = CHO_INDEX.get(currentComposing);
                int jung = JUNG_INDEX.get(jamo);
                return new StepResult("", String.valueOf(composeSyllable(cho, jung, 0)));
            }
            // Two consonants: commit first, keep new consonant composing
            return new StepResult(currentComposing, jamo);
        }

        // Case 2: Composing is a standalone vowel jamo
        if (currentComposing.length() == 1 && JUNG_INDEX.containsKey(currentComposing)) {
            if (isVowel) {
                String comp = COMPOUND_JUNG.get(currentComposing + jamo);
                if (comp != null) {
                    return new StepResult("", comp);
                }
            }
            return new StepResult(currentComposing, jamo);
        }

        // Case 3: Composing is a composed Hangul syllable block
        if (isHangulSyllable(last)) {
            int offset = last - HANGUL_BASE;
            int cho = offset / (21 * 28);
            int jung = (offset % (21 * 28)) / 28;
            int jong = offset % 28;

            if (jong == 0) {
                if (isVowel) {
                    String curJung = JUNG[jung];
                    String combinedJung = COMPOUND_JUNG.get(curJung + jamo);
                    if (combinedJung != null) {
                        return new StepResult("", String.valueOf(composeSyllable(cho, JUNG_INDEX.get(combinedJung), 0)));
                    }
                    return new StepResult(currentComposing, jamo);
                } else if (isConsonant) {
                    Integer jongIdx = JONG_INDEX.get(jamo);
                    if (jongIdx != null) {
                        return new StepResult("", String.valueOf(composeSyllable(cho, jung, jongIdx)));
                    }
                    // Consonant cannot be Jongseong (e.g. ㄸ, ㅃ, ㅉ): commit syllable, start new Cho
                    return new StepResult(currentComposing, jamo);
                }
            } else {
                // Already has a final consonant
                String curJong = JONG[jong];
                if (isConsonant) {
                    String combinedJong = COMPOUND_JONG.get(curJong + jamo);
                    if (combinedJong != null) {
                        int newJongIdx = JONG_INDEX.get(combinedJong);
                        return new StepResult("", String.valueOf(composeSyllable(cho, jung, newJongIdx)));
                    }
                    return new StepResult(currentComposing, jamo);
                } else if (isVowel) {
                    // Dokkaebi-bul: move final consonant (or second part of compound final) to next syllable's initial
                    String[] split = SPLIT_JONG.get(curJong);
                    int nextJungIdx = JUNG_INDEX.get(jamo);
                    if (split != null) {
                        int stayJongIdx = JONG_INDEX.get(split[0]);
                        int moveChoIdx = CHO_INDEX.get(split[1]);
                        String committed = String.valueOf(composeSyllable(cho, jung, stayJongIdx));
                        String nextComp = String.valueOf(composeSyllable(moveChoIdx, nextJungIdx, 0));
                        return new StepResult(committed, nextComp);
                    } else {
                        Integer moveChoIdx = CHO_INDEX.get(curJong);
                        if (moveChoIdx != null) {
                            String committed = String.valueOf(composeSyllable(cho, jung, 0));
                            String nextComp = String.valueOf(composeSyllable(moveChoIdx, nextJungIdx, 0));
                            return new StepResult(committed, nextComp);
                        }
                    }
                }
            }
        }

        return new StepResult(currentComposing, jamo);
    }

    /** Decomposes the composed syllable in [currentComposing] by one Jamo step on Backspace. */
    public static String backspace(String currentComposing) {
        if (currentComposing == null || currentComposing.isEmpty()) return "";
        char last = currentComposing.charAt(currentComposing.length() - 1);
        if (!isHangulSyllable(last)) {
            String splitJ = SPLIT_JUNG.containsKey(currentComposing) ? SPLIT_JUNG.get(currentComposing)[0] : null;
            if (splitJ != null) return splitJ;
            return currentComposing.substring(0, currentComposing.length() - 1);
        }

        int offset = last - HANGUL_BASE;
        int cho = offset / (21 * 28);
        int jung = (offset % (21 * 28)) / 28;
        int jong = offset % 28;

        if (jong != 0) {
            String curJong = JONG[jong];
            String[] split = SPLIT_JONG.get(curJong);
            if (split != null) {
                return String.valueOf(composeSyllable(cho, jung, JONG_INDEX.get(split[0])));
            }
            return String.valueOf(composeSyllable(cho, jung, 0));
        }

        String curJung = JUNG[jung];
        String[] splitJung = SPLIT_JUNG.get(curJung);
        if (splitJung != null) {
            return String.valueOf(composeSyllable(cho, JUNG_INDEX.get(splitJung[0]), 0));
        }
        return CHO[cho];
    }

    private static boolean isHangulSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }

    private static char composeSyllable(int cho, int jung, int jong) {
        return (char) (HANGUL_BASE + (cho * 21 * 28) + (jung * 28) + jong);
    }
}
