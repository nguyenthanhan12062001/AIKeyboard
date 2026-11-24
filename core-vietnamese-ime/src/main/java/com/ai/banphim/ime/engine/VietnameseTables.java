package com.ai.banphim.ime.engine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Unicode NFC character tables and phonotactic validation rules for Vietnamese syllables.
 */
public final class VietnameseTables {

    public static final int TONE_NONE = 0;
    public static final int TONE_ACUTE = 1;  // sắc (s / 1)
    public static final int TONE_GRAVE = 2;  // huyền (f / 2)
    public static final int TONE_HOOK = 3;   // hỏi (r / 3)
    public static final int TONE_TILDE = 4;  // ngã (x / 4)
    public static final int TONE_DOT = 5;    // nặng (j / 5)

    public static final int MOD_NONE = 0;
    public static final int MOD_CIRCUMFLEX = 1; // â, ê, ô
    public static final int MOD_BREVE = 2;      // ă
    public static final int MOD_HORN = 3;       // ơ, ư

    /** Metadata decomposing a precomposed Vietnamese vowel into base, modifier, tone, and case. */
    public static final class VowelInfo {
        public final char plainBase; // 'a', 'e', 'i', 'o', 'u', 'y'
        public final int modifier;   // MOD_*
        public final int tone;       // TONE_*
        public final boolean upperCase;

        /** Constructs a vowel decomposition descriptor. */
        public VowelInfo(char plainBase, int modifier, int tone, boolean upperCase) {
            this.plainBase = plainBase;
            this.modifier = modifier;
            this.tone = tone;
            this.upperCase = upperCase;
        }
    }

    // Order of base rows:
    // 0:a, 1:ă, 2:â, 3:e, 4:ê, 5:i, 6:o, 7:ô, 8:ơ, 9:u, 10:ư, 11:y
    private static final char[][] LOWER_VOWELS = {
        {'a', 'á', 'à', 'ả', 'ã', 'ạ'},
        {'ă', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ'},
        {'â', 'ấ', 'ầ', 'ẩ', 'ẫ', 'ậ'},
        {'e', 'é', 'è', 'ẻ', 'ẽ', 'ẹ'},
        {'ê', 'ế', 'ề', 'ể', 'ễ', 'ệ'},
        {'i', 'í', 'ì', 'ỉ', 'ĩ', 'ị'},
        {'o', 'ó', 'ò', 'ỏ', 'õ', 'ọ'},
        {'ô', 'ố', 'ồ', 'ổ', 'ỗ', 'ộ'},
        {'ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ'},
        {'u', 'ú', 'ù', 'ủ', 'ũ', 'ụ'},
        {'ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự'},
        {'y', 'ý', 'ỳ', 'ỷ', 'ỹ', 'ỵ'}
    };

    private static final char[][] UPPER_VOWELS = {
        {'A', 'Á', 'À', 'Ả', 'Ã', 'Ạ'},
        {'Ă', 'Ắ', 'Ằ', 'Ẳ', 'Ẵ', 'Ặ'},
        {'Â', 'Ấ', 'Ầ', 'Ẩ', 'Ẫ', 'Ậ'},
        {'E', 'É', 'È', 'Ẻ', 'Ẽ', 'Ẹ'},
        {'Ê', 'Ế', 'Ề', 'Ể', 'Ễ', 'Ệ'},
        {'I', 'Í', 'Ì', 'Ỉ', 'Ĩ', 'Ị'},
        {'O', 'Ó', 'Ò', 'Ỏ', 'Õ', 'Ọ'},
        {'Ô', 'Ố', 'Ồ', 'Ổ', 'Ỗ', 'Ộ'},
        {'Ơ', 'Ớ', 'Ờ', 'Ở', 'Ỡ', 'Ợ'},
        {'U', 'Ú', 'Ù', 'Ủ', 'Ũ', 'Ụ'},
        {'Ư', 'Ứ', 'Ừ', 'Ử', 'Ữ', 'Ự'},
        {'Y', 'Ý', 'Ỳ', 'Ỷ', 'Ỹ', 'Ỵ'}
    };

    private static final char[] ROW_PLAIN_BASE = {
        'a', 'a', 'a', 'e', 'e', 'i', 'o', 'o', 'o', 'u', 'u', 'y'
    };

    private static final int[] ROW_MODIFIER = {
        MOD_NONE, MOD_BREVE, MOD_CIRCUMFLEX,
        MOD_NONE, MOD_CIRCUMFLEX,
        MOD_NONE,
        MOD_NONE, MOD_CIRCUMFLEX, MOD_HORN,
        MOD_NONE, MOD_HORN,
        MOD_NONE
    };

    private static final Map<Character, VowelInfo> CHAR_TO_VOWEL = new HashMap<>();

    public static final Set<String> VALID_INITIALS = new HashSet<>(Arrays.asList(
        "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh",
        "l", "m", "n", "ng", "ngh", "nh", "p", "ph", "qu", "r", "s",
        "t", "th", "tr", "v", "x"
    ));

    public static final Set<String> VALID_FINALS = new HashSet<>(Arrays.asList(
        "", "c", "ch", "m", "n", "ng", "nh", "p", "t"
    ));

    public static final Set<String> STOP_FINALS = new HashSet<>(Arrays.asList(
        "c", "ch", "p", "t"
    ));

    // All valid Vietnamese vowel nuclei (without tones, lowercase)
    public static final Set<String> VALID_VOWEL_CLUSTERS = new HashSet<>(Arrays.asList(
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        "ai", "ao", "au", "ay", "âu", "ây",
        "eo", "êu",
        "ia", "ie", "iê", "iu",
        "oa", "oă", "oe", "oi", "oo",
        "ôi", "ơi",
        "ua", "uă", "uâ", "uê", "ui", "uo", "uô", "uoi", "uou", "uơ", "uy",
        "ưa", "ưi", "ưo", "ươ", "ưu",
        "ya", "ye", "yê",
        "iêu", "oai", "oao", "oay", "oeo",
        "uây", "uôi", "ươi", "ươu", "uya", "uyê", "uyu", "yêu"
    ));

    static {
        for (int row = 0; row < LOWER_VOWELS.length; row++) {
            for (int tone = 0; tone < 6; tone++) {
                CHAR_TO_VOWEL.put(
                    LOWER_VOWELS[row][tone],
                    new VowelInfo(ROW_PLAIN_BASE[row], ROW_MODIFIER[row], tone, false)
                );
                CHAR_TO_VOWEL.put(
                    UPPER_VOWELS[row][tone],
                    new VowelInfo(ROW_PLAIN_BASE[row], ROW_MODIFIER[row], tone, true)
                );
            }
        }
    }

    private VietnameseTables() {}

    /** Looks up decomposition info for a Vietnamese vowel character, or null if not a vowel. */
    public static VowelInfo getVowelInfo(char c) {
        return CHAR_TO_VOWEL.get(c);
    }

    /** Returns true if [c] is a recognized plain or accented Vietnamese vowel. */
    public static boolean isVietnameseVowel(char c) {
        return CHAR_TO_VOWEL.containsKey(c);
    }

    /** Checks whether [tone] is permitted with a stop final consonant (-c, -ch, -p, -t). */
    public static boolean isToneAllowedWithFinal(int tone, String finalConsonant) {
        if (STOP_FINALS.contains(finalConsonant)) {
            return tone == TONE_NONE || tone == TONE_ACUTE || tone == TONE_DOT;
        }
        return true;
    }

    /** Composes a precomposed Unicode NFC vowel from its base letter, modifier, tone, and case. */
    public static char composeVowel(char plainBase, int modifier, int tone, boolean upperCase) {
        int row = findRow(Character.toLowerCase(plainBase), modifier);
        if (row < 0) {
            row = findRow(Character.toLowerCase(plainBase), MOD_NONE);
        }
        if (row < 0) {
            return upperCase ? Character.toUpperCase(plainBase) : Character.toLowerCase(plainBase);
        }
        int safeTone = (tone >= 0 && tone <= 5) ? tone : TONE_NONE;
        return upperCase ? UPPER_VOWELS[row][safeTone] : LOWER_VOWELS[row][safeTone];
    }

    /** Removes both Vietnamese tone mark and vowel/d modifier, returning plain ASCII letter. */
    public static char stripToneAndModifier(char c) {
        if (c == 'đ') return 'd';
        if (c == 'Đ') return 'D';
        VowelInfo info = CHAR_TO_VOWEL.get(c);
        if (info == null) return c;
        return info.upperCase ? Character.toUpperCase(info.plainBase) : info.plainBase;
    }

    /** Removes only the tone diacritic while preserving circumflex, breve, or horn modifiers. */
    public static char stripToneOnly(char c) {
        VowelInfo info = CHAR_TO_VOWEL.get(c);
        if (info == null) return c;
        return composeVowel(info.plainBase, info.modifier, TONE_NONE, info.upperCase);
    }

    private static int findRow(char lowerBase, int modifier) {
        switch (lowerBase) {
            case 'a':
                if (modifier == MOD_BREVE) return 1;
                if (modifier == MOD_CIRCUMFLEX) return 2;
                return 0;
            case 'e':
                if (modifier == MOD_CIRCUMFLEX) return 4;
                return 3;
            case 'i':
                return 5;
            case 'o':
                if (modifier == MOD_CIRCUMFLEX) return 7;
                if (modifier == MOD_HORN) return 8;
                return 6;
            case 'u':
                if (modifier == MOD_HORN) return 10;
                return 9;
            case 'y':
                return 11;
            default:
                return -1;
        }
    }
}
