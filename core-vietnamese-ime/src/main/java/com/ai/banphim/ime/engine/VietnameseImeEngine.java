package com.ai.banphim.ime.engine;

/**
 * Pure Java Vietnamese Telex & VNI processor with syllable phonotactic validation
 * so English words are not erroneously accented.
 */
public final class VietnameseImeEngine {

    private VietnameseImeEngine() {}

    private static final class ParsedSyllable {
        String initial = "";
        String vowel = "";
        String fin = "";
        int tone = VietnameseTables.TONE_NONE;
        boolean validStructure = false;
    }

    /**
     * Processes a single typed character against the current Vietnamese word buffer.
     *
     * @param currentWord composed text currently under edit
     * @param rawHistory raw unaccented keystroke sequence for revert/undo handling
     * @param nextChar newly typed character
     * @param mode active Vietnamese input method (TELEX, VNI, or OFF)
     * @param style tone placement rule (MODERN or CLASSIC)
     * @return immutable transformation result containing composed text and raw history
     */
    public static VietnameseEngineResult processKey(
        String currentWord,
        String rawHistory,
        char nextChar,
        InputMethodMode mode,
        TonePlacementStyle style
    ) {
        String safeWord = currentWord == null ? "" : currentWord;
        String safeRaw = rawHistory == null ? safeWord : rawHistory;
        String nextRaw = safeRaw + nextChar;

        if (mode == null || mode == InputMethodMode.OFF || !isWordChar(nextChar)) {
            return new VietnameseEngineResult(safeWord + nextChar, nextRaw, false, false);
        }

        // If the word was already locked out as non-Vietnamese (contains raw English clusters),
        // avoid mangling unless it is still potentially valid.
        if (hasImpossibleVietnameseLetters(safeWord) && !hasVietnameseDiacritics(safeWord)) {
            return new VietnameseEngineResult(safeWord + nextChar, nextRaw, false, false);
        }

        char lowerNext = Character.toLowerCase(nextChar);

        if (mode == InputMethodMode.TELEX) {
            return processTelex(safeWord, nextRaw, nextChar, lowerNext, style);
        } else {
            return processVni(safeWord, nextRaw, nextChar, lowerNext, style);
        }
    }

    private static VietnameseEngineResult processTelex(
        String word,
        String nextRaw,
        char nextChar,
        char lowerNext,
        TonePlacementStyle style
    ) {
        // 1. Check tone keys: s, f, r, x, j, z
        int requestedTone = telexToneForChar(lowerNext);
        if (requestedTone >= 0 && !word.isEmpty()) {
            VietnameseEngineResult toneResult = tryApplyTone(word, nextRaw, nextChar, requestedTone, style);
            if (toneResult != null) {
                return toneResult;
            }
        }

        // 2. Check 'd' -> 'đ' (dd)
        if (lowerNext == 'd' && !word.isEmpty()) {
            VietnameseEngineResult dResult = tryApplyTelexD(word, nextRaw, nextChar);
            if (dResult != null) {
                return dResult;
            }
        }

        // 3. Check vowel modifiers: a->â, e->ê, o->ô, w->ă/ơ/ư
        if (lowerNext == 'a' || lowerNext == 'e' || lowerNext == 'o' || lowerNext == 'w') {
            VietnameseEngineResult modResult = tryApplyTelexVowelModifier(word, nextRaw, nextChar, lowerNext, style);
            if (modResult != null) {
                return modResult;
            }
        }

        // 4. Special standalone 'w' at start or after initial consonant -> 'ư' (e.g. w -> ư, thw -> thư)
        if (lowerNext == 'w') {
            String lowerWord = word.toLowerCase();
            if (word.isEmpty() || (VietnameseTables.VALID_INITIALS.contains(lowerWord) && !lowerWord.equals("qu"))) {
                boolean upper = Character.isUpperCase(nextChar);
                char uHorn = VietnameseTables.composeVowel('u', VietnameseTables.MOD_HORN, VietnameseTables.TONE_NONE, upper);
                String candidate = word + uHorn;
                if (isPotentiallyValidVietnamese(candidate)) {
                    return new VietnameseEngineResult(candidate, nextRaw, true, false);
                }
            }
        }

        // 5. Append normal character and re-evaluate syllable (reposition tone / harmonize ươ / revert if English)
        return appendAndValidate(word, nextRaw, nextChar, style);
    }

    private static VietnameseEngineResult processVni(
        String word,
        String nextRaw,
        char nextChar,
        char lowerNext,
        TonePlacementStyle style
    ) {
        if (word.isEmpty()) {
            return new VietnameseEngineResult(String.valueOf(nextChar), nextRaw, false, false);
        }

        // 1. Tone digits 0..5
        int requestedTone = vniToneForChar(lowerNext);
        if (requestedTone >= 0) {
            VietnameseEngineResult toneResult = tryApplyTone(word, nextRaw, nextChar, requestedTone, style);
            if (toneResult != null) {
                return toneResult;
            }
        }

        // 2. '9' -> 'đ'
        if (lowerNext == '9') {
            VietnameseEngineResult dResult = tryApplyVniD(word, nextRaw, nextChar);
            if (dResult != null) {
                return dResult;
            }
        }

        // 3. '6' (^), '7' (horn), '8' (breve)
        if (lowerNext == '6' || lowerNext == '7' || lowerNext == '8') {
            VietnameseEngineResult modResult = tryApplyVniVowelModifier(word, nextRaw, nextChar, lowerNext, style);
            if (modResult != null) {
                return modResult;
            }
        }

        return appendAndValidate(word, nextRaw, nextChar, style);
    }

    private static VietnameseEngineResult appendAndValidate(
        String word,
        String nextRaw,
        char nextChar,
        TonePlacementStyle style
    ) {
        String candidate = word + nextChar;
        // Harmonize "uơ" or "ưo" -> "ươ" when a final consonant or glide is appended (e.g., thuow + n -> thương)
        candidate = harmonizeUoCluster(candidate, style);

        // Reposition tone if tone was applied before final consonant (e.g., hóa + n -> hoán)
        ParsedSyllable parsed = parseSyllable(candidate);
        String rawBefore = nextRaw.substring(0, Math.max(0, nextRaw.length() - 1));
        if (!word.equals(rawBefore) && !parsed.validStructure) {
            return new VietnameseEngineResult(nextRaw, nextRaw, false, true);
        }
        if (parsed.validStructure && parsed.tone != VietnameseTables.TONE_NONE) {
            if (!VietnameseTables.isToneAllowedWithFinal(parsed.tone, parsed.fin)) {
                // Invalid tone for -c/-ch/-p/-t (e.g., "tẽt" for "text") -> revert!
                return new VietnameseEngineResult(nextRaw, nextRaw, false, true);
            }
            candidate = applyToneToSyllable(candidate, parsed.tone, style);
            parsed = parseSyllable(candidate);
        }

        if (hasVietnameseDiacritics(candidate) && !isPotentiallyValidVietnamese(candidate)) {
            return new VietnameseEngineResult(nextRaw, nextRaw, false, true);
        }

        boolean transformed = !candidate.equals(nextRaw);
        return new VietnameseEngineResult(candidate, nextRaw, transformed, false);
    }

    private static VietnameseEngineResult tryApplyTone(
        String word,
        String nextRaw,
        char nextChar,
        int requestedTone,
        TonePlacementStyle style
    ) {
        ParsedSyllable parsed = parseSyllable(word);
        if (!parsed.validStructure || parsed.vowel.isEmpty()) {
            return null;
        }

        // 'z' / '0' clears tone
        if (requestedTone == VietnameseTables.TONE_NONE) {
            if (parsed.tone == VietnameseTables.TONE_NONE) {
                return null; // Nothing to remove -> treat literally
            }
            String stripped = applyToneToSyllable(word, VietnameseTables.TONE_NONE, style);
            return new VietnameseEngineResult(stripped, nextRaw, !stripped.equals(nextRaw), false);
        }

        // Check stop final compatibility (c, ch, p, t only accept sắc/nặng)
        if (!VietnameseTables.isToneAllowedWithFinal(requestedTone, parsed.fin)) {
            return null;
        }

        // Repeat same tone key -> undo tone and append literal key (e.g., "tás" + "s" -> "tass")
        if (parsed.tone == requestedTone) {
            String stripped = applyToneToSyllable(word, VietnameseTables.TONE_NONE, style);
            String undone = stripped + nextChar;
            if (hasVietnameseDiacritics(undone) && !isPotentiallyValidVietnamese(undone)) {
                return new VietnameseEngineResult(nextRaw, nextRaw, false, true);
            }
            return new VietnameseEngineResult(undone, nextRaw, !undone.equals(nextRaw), true);
        }

        String accented = applyToneToSyllable(word, requestedTone, style);
        if (!accented.equals(word)) {
            return new VietnameseEngineResult(accented, nextRaw, true, false);
        }
        return null;
    }

    private static VietnameseEngineResult tryApplyTelexD(String word, String nextRaw, char nextChar) {
        if (word.isEmpty()) return null;
        char first = word.charAt(0);
        if (first == 'd' || first == 'D') {
            // Only transform leading 'd' if word starts with d and has valid Vietnamese structure
            String rest = word.substring(1);
            if (rest.isEmpty() || isRestPlausibleAfterD(rest)) {
                char barred = (first == 'D') ? 'Đ' : 'đ';
                return new VietnameseEngineResult(barred + rest, nextRaw, true, false);
            }
        } else if (first == 'đ' || first == 'Đ') {
            // Repeat 'd' -> undo 'đ' back to 'd' + literal 'd' (e.g. "đ" + "d" -> "dd")
            char plainD = (first == 'Đ') ? 'D' : 'd';
            String undone = plainD + word.substring(1) + nextChar;
            return new VietnameseEngineResult(undone, nextRaw, false, true);
        }
        return null;
    }

    private static VietnameseEngineResult tryApplyVniD(String word, String nextRaw, char nextChar) {
        if (word.isEmpty()) return null;
        char first = word.charAt(0);
        if (first == 'd' || first == 'D') {
            char barred = (first == 'D') ? 'Đ' : 'đ';
            return new VietnameseEngineResult(barred + word.substring(1), nextRaw, true, false);
        } else if (first == 'đ' || first == 'Đ') {
            char plainD = (first == 'Đ') ? 'D' : 'd';
            return new VietnameseEngineResult(plainD + word.substring(1) + nextChar, nextRaw, false, true);
        }
        return null;
    }

    private static VietnameseEngineResult tryApplyTelexVowelModifier(
        String word,
        String nextRaw,
        char nextChar,
        char lowerKey,
        TonePlacementStyle style
    ) {
        ParsedSyllable parsed = parseSyllable(word);

        if (lowerKey == 'w' && (!parsed.validStructure || parsed.vowel.isEmpty())) {
            int n = word.length();
            int vStart = 0;
            String lower = word.toLowerCase();
            if (lower.startsWith("qu") && n > 2) {
                vStart = 2;
            } else if (lower.startsWith("gi") && n > 2) {
                vStart = 2;
            } else {
                int idx = 0;
                while (idx < n && !VietnameseTables.isVietnameseVowel(word.charAt(idx))) {
                    idx++;
                }
                vStart = idx;
            }
            if (vStart < n) {
                String vSeg = word.substring(vStart);
                String lV = stripAllTones(vSeg).toLowerCase();
                if (lV.equals("uu") && vStart + 1 < n) {
                    char c0 = word.charAt(vStart);
                    char c1 = word.charAt(vStart + 1);
                    VietnameseTables.VowelInfo v0 = VietnameseTables.getVowelInfo(c0);
                    VietnameseTables.VowelInfo v1 = VietnameseTables.getVowelInfo(c1);
                    if (v0 != null && v1 != null) {
                        StringBuilder sb = new StringBuilder(word);
                        sb.setCharAt(vStart, VietnameseTables.composeVowel('u', VietnameseTables.MOD_HORN, v0.tone, v0.upperCase));
                        sb.setCharAt(vStart + 1, VietnameseTables.composeVowel(v1.plainBase, v1.modifier, v1.tone, v1.upperCase));
                        String res = applyToneToSyllable(sb.toString(), VietnameseTables.TONE_NONE, style);
                        if (isPotentiallyValidVietnamese(res)) {
                            return new VietnameseEngineResult(res, nextRaw, true, false);
                        }
                    }
                }
            }
            return null;
        }

        if (!parsed.validStructure || parsed.vowel.isEmpty()) {
            return null;
        }

        int vStart = parsed.initial.length();
        int vEnd = word.length() - parsed.fin.length();
        String vowelSegment = word.substring(vStart, vEnd);

        // Handle 'w' in Telex
        if (lowerKey == 'w') {
            // Check special "uo" -> "ươ" or "uơ"
            String lowerV = stripAllTones(vowelSegment).toLowerCase();
            if (lowerV.equals("uo") || lowerV.equals("ưo") || lowerV.equals("uơ")) {
                StringBuilder sb = new StringBuilder(word);
                boolean hasFinalOrGlide = !parsed.fin.isEmpty();
                int uMod = hasFinalOrGlide ? VietnameseTables.MOD_HORN : VietnameseTables.MOD_NONE;
                if (lowerV.equals("ưo")) uMod = VietnameseTables.MOD_HORN;
                char c0 = word.charAt(vStart);
                char c1 = word.charAt(vStart + 1);
                VietnameseTables.VowelInfo v0 = VietnameseTables.getVowelInfo(c0);
                VietnameseTables.VowelInfo v1 = VietnameseTables.getVowelInfo(c1);
                if (v0 != null && v1 != null) {
                    if (lowerV.equals("uo") && parsed.fin.isEmpty()) {
                        // Start as "ươ" if preceded by consonant like "duow", or "uơ" for "thuow";
                        // default to "ươ" if it's already "uơ" toggle, otherwise "ươ" when final exists or "uơ"
                        uMod = VietnameseTables.MOD_HORN;
                    }
                    sb.setCharAt(vStart, VietnameseTables.composeVowel('u', uMod, v0.tone, v0.upperCase));
                    sb.setCharAt(vStart + 1, VietnameseTables.composeVowel('o', VietnameseTables.MOD_HORN, v1.tone, v1.upperCase));
                    String res = applyToneToSyllable(sb.toString(), parsed.tone, style);
                    if (isPotentiallyValidVietnamese(res)) {
                        return new VietnameseEngineResult(res, nextRaw, true, false);
                    }
                }
            }
            if ((lowerV.equals("ua") && !parsed.initial.equalsIgnoreCase("qu")) || lowerV.equals("uu")) {
                VietnameseTables.VowelInfo v0 = VietnameseTables.getVowelInfo(word.charAt(vStart));
                VietnameseTables.VowelInfo v1 = VietnameseTables.getVowelInfo(word.charAt(vStart + 1));
                if (v0 != null && v1 != null) {
                    if (v0.modifier == VietnameseTables.MOD_HORN) {
                        // Undo horn on u and append literal 'w'
                        StringBuilder sb = new StringBuilder(word);
                        sb.setCharAt(vStart, VietnameseTables.composeVowel('u', VietnameseTables.MOD_NONE, v0.tone, v0.upperCase));
                        String undone = sb.toString() + nextChar;
                        return new VietnameseEngineResult(undone, nextRaw, !undone.equals(nextRaw), true);
                    } else {
                        StringBuilder sb = new StringBuilder(word);
                        sb.setCharAt(vStart, VietnameseTables.composeVowel('u', VietnameseTables.MOD_HORN, v0.tone, v0.upperCase));
                        sb.setCharAt(vStart + 1, VietnameseTables.composeVowel(v1.plainBase, v1.modifier, v1.tone, v1.upperCase));
                        String res = applyToneToSyllable(sb.toString(), parsed.tone, style);
                        if (isPotentiallyValidVietnamese(res)) {
                            return new VietnameseEngineResult(res, nextRaw, true, false);
                        }
                    }
                }
            }

            if (lowerV.equals("uoi") || lowerV.equals("uou")) {
                StringBuilder sb = new StringBuilder(word);
                VietnameseTables.VowelInfo v0 = VietnameseTables.getVowelInfo(word.charAt(vStart));
                VietnameseTables.VowelInfo v1 = VietnameseTables.getVowelInfo(word.charAt(vStart + 1));
                if (v0 != null && v1 != null) {
                    sb.setCharAt(vStart, VietnameseTables.composeVowel('u', VietnameseTables.MOD_HORN, v0.tone, v0.upperCase));
                    sb.setCharAt(vStart + 1, VietnameseTables.composeVowel('o', VietnameseTables.MOD_HORN, v1.tone, v1.upperCase));
                    String res = applyToneToSyllable(sb.toString(), parsed.tone, style);
                    if (isPotentiallyValidVietnamese(res)) {
                        return new VietnameseEngineResult(res, nextRaw, true, false);
                    }
                }
            }

            // Try transforming from right to left among a, o, u (or undoing existing breve/horn)
            for (int i = vEnd - 1; i >= vStart; i--) {
                char c = word.charAt(i);
                VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(c);
                if (info == null) continue;
                int targetMod = -1;
                if (info.plainBase == 'a') targetMod = VietnameseTables.MOD_BREVE;
                else if (info.plainBase == 'o' || info.plainBase == 'u') targetMod = VietnameseTables.MOD_HORN;

                if (targetMod != -1) {
                    if (info.modifier == targetMod) {
                        // Undo existing breve/horn and append literal 'w'
                        StringBuilder sb = new StringBuilder(word);
                        sb.setCharAt(i, VietnameseTables.composeVowel(info.plainBase, VietnameseTables.MOD_NONE, info.tone, info.upperCase));
                        String undone = sb.toString() + nextChar;
                        return new VietnameseEngineResult(undone, nextRaw, !undone.equals(nextRaw), true);
                    } else if (info.modifier == VietnameseTables.MOD_NONE) {
                        StringBuilder sb = new StringBuilder(word);
                        sb.setCharAt(i, VietnameseTables.composeVowel(info.plainBase, targetMod, info.tone, info.upperCase));
                        String candidate = harmonizeUoCluster(sb.toString(), style);
                        candidate = applyToneToSyllable(candidate, parsed.tone, style);
                        if (isPotentiallyValidVietnamese(candidate)) {
                            return new VietnameseEngineResult(candidate, nextRaw, true, false);
                        }
                    }
                }
            }
            return null;
        }

        // Handle aa -> â, ee -> ê, oo -> ô
        for (int i = vEnd - 1; i >= vStart; i--) {
            char c = word.charAt(i);
            VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(c);
            if (info != null && info.plainBase == lowerKey) {
                if (info.modifier == VietnameseTables.MOD_CIRCUMFLEX) {
                    // Undo circumflex and append literal key
                    StringBuilder sb = new StringBuilder(word);
                    sb.setCharAt(i, VietnameseTables.composeVowel(info.plainBase, VietnameseTables.MOD_NONE, info.tone, info.upperCase));
                    String undone = sb.toString() + nextChar;
                    return new VietnameseEngineResult(undone, nextRaw, !undone.equals(nextRaw), true);
                } else if (info.modifier == VietnameseTables.MOD_NONE) {
                    StringBuilder sb = new StringBuilder(word);
                    sb.setCharAt(i, VietnameseTables.composeVowel(info.plainBase, VietnameseTables.MOD_CIRCUMFLEX, info.tone, info.upperCase));
                    String candidate = applyToneToSyllable(sb.toString(), parsed.tone, style);
                    if (isPotentiallyValidVietnamese(candidate)) {
                        return new VietnameseEngineResult(candidate, nextRaw, true, false);
                    }
                }
            }
        }

        return null;
    }

    private static VietnameseEngineResult tryApplyVniVowelModifier(
        String word,
        String nextRaw,
        char nextChar,
        char digit,
        TonePlacementStyle style
    ) {
        ParsedSyllable parsed = parseSyllable(word);
        if (!parsed.validStructure || parsed.vowel.isEmpty()) {
            return null;
        }
        int vStart = parsed.initial.length();
        int vEnd = word.length() - parsed.fin.length();
        String lowerV = stripAllTones(word.substring(vStart, vEnd)).toLowerCase();

        // VNI '7' on "uo", "ưo", "uoi", "uou" -> "ươ..."
        if (digit == '7' && (lowerV.equals("uo") || lowerV.equals("ưo") || lowerV.equals("uoi") || lowerV.equals("uou"))) {
            StringBuilder sb = new StringBuilder(word);
            VietnameseTables.VowelInfo v0 = VietnameseTables.getVowelInfo(word.charAt(vStart));
            VietnameseTables.VowelInfo v1 = VietnameseTables.getVowelInfo(word.charAt(vStart + 1));
            if (v0 != null && v1 != null) {
                sb.setCharAt(vStart, VietnameseTables.composeVowel('u', VietnameseTables.MOD_HORN, v0.tone, v0.upperCase));
                sb.setCharAt(vStart + 1, VietnameseTables.composeVowel('o', VietnameseTables.MOD_HORN, v1.tone, v1.upperCase));
                String candidate = applyToneToSyllable(sb.toString(), parsed.tone, style);
                if (isPotentiallyValidVietnamese(candidate)) {
                    return new VietnameseEngineResult(candidate, nextRaw, true, false);
                }
            }
        }

        int targetMod;
        if (digit == '6') targetMod = VietnameseTables.MOD_CIRCUMFLEX;
        else if (digit == '7') targetMod = VietnameseTables.MOD_HORN;
        else targetMod = VietnameseTables.MOD_BREVE;

        for (int i = vEnd - 1; i >= vStart; i--) {
            char c = word.charAt(i);
            VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(c);
            if (info == null) continue;

            boolean matches =
                (targetMod == VietnameseTables.MOD_CIRCUMFLEX && (info.plainBase == 'a' || info.plainBase == 'e' || info.plainBase == 'o')) ||
                (targetMod == VietnameseTables.MOD_HORN && (info.plainBase == 'o' || info.plainBase == 'u')) ||
                (targetMod == VietnameseTables.MOD_BREVE && info.plainBase == 'a');

            if (!matches) continue;

            if (info.modifier == targetMod) {
                StringBuilder sb = new StringBuilder(word);
                sb.setCharAt(i, VietnameseTables.composeVowel(info.plainBase, VietnameseTables.MOD_NONE, info.tone, info.upperCase));
                String undone = sb.toString() + nextChar;
                return new VietnameseEngineResult(undone, nextRaw, !undone.equals(nextRaw), true);
            } else if (info.modifier == VietnameseTables.MOD_NONE) {
                StringBuilder sb = new StringBuilder(word);
                sb.setCharAt(i, VietnameseTables.composeVowel(info.plainBase, targetMod, info.tone, info.upperCase));
                String candidate = harmonizeUoCluster(sb.toString(), style);
                candidate = applyToneToSyllable(candidate, parsed.tone, style);
                if (isPotentiallyValidVietnamese(candidate)) {
                    return new VietnameseEngineResult(candidate, nextRaw, true, false);
                }
            }
        }

        return null;
    }

    private static String harmonizeUoCluster(String word, TonePlacementStyle style) {
        ParsedSyllable parsed = parseSyllable(word);
        if (!parsed.validStructure || parsed.vowel.length() < 2) return word;
        int vStart = parsed.initial.length();
        String plainToneStripped = stripAllTones(parsed.vowel).toLowerCase();

        // Convert "ưo" -> "ươ" or "uơ" + final/glide -> "ươ"
        boolean shouldHarmonize =
            (plainToneStripped.startsWith("ưo") && (!parsed.fin.isEmpty() || plainToneStripped.equals("ưoi") || plainToneStripped.equals("ưou"))) ||
            (plainToneStripped.startsWith("uơ") && !parsed.fin.isEmpty());
        if (shouldHarmonize) {
            StringBuilder sb = new StringBuilder(word);
            VietnameseTables.VowelInfo v0 = VietnameseTables.getVowelInfo(word.charAt(vStart));
            VietnameseTables.VowelInfo v1 = VietnameseTables.getVowelInfo(word.charAt(vStart + 1));
            if (v0 != null && v1 != null) {
                sb.setCharAt(vStart, VietnameseTables.composeVowel('u', VietnameseTables.MOD_HORN, v0.tone, v0.upperCase));
                sb.setCharAt(vStart + 1, VietnameseTables.composeVowel('o', VietnameseTables.MOD_HORN, v1.tone, v1.upperCase));
                return applyToneToSyllable(sb.toString(), parsed.tone, style);
            }
        }
        return word;
    }

    private static String applyToneToSyllable(String word, int tone, TonePlacementStyle style) {
        ParsedSyllable parsed = parseSyllable(word);
        if (!parsed.validStructure || parsed.vowel.isEmpty()) {
            return word;
        }
        int vStart = parsed.initial.length();
        int vLen = parsed.vowel.length();

        int targetRelIndex = chooseToneTargetIndex(parsed.vowel, parsed.fin, style);
        StringBuilder sb = new StringBuilder(word);

        for (int i = 0; i < vLen; i++) {
            int absIdx = vStart + i;
            VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(word.charAt(absIdx));
            if (info == null) continue;
            int newTone = (i == targetRelIndex) ? tone : VietnameseTables.TONE_NONE;
            sb.setCharAt(absIdx, VietnameseTables.composeVowel(info.plainBase, info.modifier, newTone, info.upperCase));
        }
        return sb.toString();
    }

    private static int chooseToneTargetIndex(String vowelCluster, String finalConsonant, TonePlacementStyle style) {
        int len = vowelCluster.length();
        if (len <= 1) return 0;

        String clean = stripAllTones(vowelCluster).toLowerCase();

        // Special case: "ươ" -> always put tone on 'ơ' (index 1)
        if (clean.startsWith("ươ")) {
            return 1;
        }

        // Priority 1: any vowel with circumflex/breve/horn (â, ă, ê, ô, ơ, ư)
        int modifiedIdx = -1;
        for (int i = 0; i < len; i++) {
            VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(vowelCluster.charAt(i));
            if (info != null && info.modifier != VietnameseTables.MOD_NONE) {
                modifiedIdx = i;
            }
        }
        if (modifiedIdx >= 0) {
            return modifiedIdx;
        }

        if (len == 2) {
            // If there is a final consonant, tone goes on the 2nd vowel (e.g., toán, tiền, hoàng)
            if (!finalConsonant.isEmpty()) {
                return 1;
            }
            // Open diphthongs oa, oe, uy: MODERN -> index 0 (hòa, xòe, thúy), CLASSIC -> index 1 (hoà, xoè, thuý)
            if (clean.equals("oa") || clean.equals("oe") || clean.equals("uy")) {
                return (style == TonePlacementStyle.CLASSIC) ? 1 : 0;
            }
            // Other open 2-vowel clusters (ai, ao, au, ay, eo, ia, iu, oi, ua, ui, ưa, ưu...): index 0
            return 0;
        }

        // 3-vowel clusters without diacritics (oai, oao, oay, oeo, uya, uyu): index 1
        return 1;
    }

    private static ParsedSyllable parseSyllable(String word) {
        ParsedSyllable res = new ParsedSyllable();
        if (word == null || word.isEmpty()) return res;

        int n = word.length();
        int idx = 0;

        // 1. Check initial consonant(s)
        // Special handling for 'qu' and 'gi' followed by a vowel
        String lower = word.toLowerCase();
        if (lower.startsWith("qu") && n > 2 && VietnameseTables.isVietnameseVowel(word.charAt(2))) {
            res.initial = word.substring(0, 2);
            idx = 2;
        } else if (lower.startsWith("gi") && n > 2 && VietnameseTables.isVietnameseVowel(word.charAt(2))) {
            res.initial = word.substring(0, 2);
            idx = 2;
        } else {
            while (idx < n && !VietnameseTables.isVietnameseVowel(word.charAt(idx))) {
                idx++;
            }
            res.initial = word.substring(0, idx);
        }

        // 2. Vowel cluster
        int vStart = idx;
        while (idx < n && VietnameseTables.isVietnameseVowel(word.charAt(idx))) {
            VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(word.charAt(idx));
            if (info != null && info.tone != VietnameseTables.TONE_NONE) {
                res.tone = info.tone;
            }
            idx++;
        }
        res.vowel = word.substring(vStart, idx);

        // 3. Final consonant(s)
        res.fin = word.substring(idx);

        // Validate structure against Vietnamese syllable bounds
        String initLower = res.initial.toLowerCase();
        String finLower = res.fin.toLowerCase();
        if (!VietnameseTables.VALID_INITIALS.contains(initLower)) {
            return res;
        }
        if (!VietnameseTables.VALID_FINALS.contains(finLower)) {
            return res;
        }
        if (res.vowel.isEmpty()) {
            res.validStructure = true;
            return res;
        }

        String cleanV = stripAllTones(res.vowel).toLowerCase();
        if (!VietnameseTables.VALID_VOWEL_CLUSTERS.contains(cleanV)) {
            return res;
        }

        res.validStructure = true;
        return res;
    }

    /** Checks whether a token conforms to Vietnamese syllable phonotactics. */
    public static boolean isPotentiallyValidVietnamese(String word) {
        ParsedSyllable parsed = parseSyllable(word);
        if (!parsed.validStructure) return false;
        if (parsed.vowel.isEmpty()) return true;

        String cleanV = stripAllTones(parsed.vowel).toLowerCase();
        String fin = parsed.fin.toLowerCase();

        // 'ă' and 'â' (alone) must usually be closed by a final consonant in standard syllables
        // Note: allow during mid-typing before final consonant is typed unless tone is invalid
        if (!VietnameseTables.isToneAllowedWithFinal(parsed.tone, fin)) {
            return false;
        }

        // Clusters that cannot take any final consonant (e.g., ai, ao, au, ay, eo, ia, ua, ưa, ưi, ưu...)
        if (!fin.isEmpty()) {
            if (cleanV.equals("ai") || cleanV.equals("ao") || cleanV.equals("au") || cleanV.equals("ay") ||
                cleanV.equals("âu") || cleanV.equals("ây") || cleanV.equals("eo") || cleanV.equals("êu") ||
                cleanV.equals("ia") || cleanV.equals("iu") || cleanV.equals("oi") || cleanV.equals("ôi") ||
                cleanV.equals("ơi") || cleanV.equals("ua") || cleanV.equals("ui") || cleanV.equals("ưa") ||
                cleanV.equals("ưi") || cleanV.equals("ưu") || cleanV.equals("iêu") || cleanV.equals("yêu") ||
                cleanV.equals("uôi") || cleanV.equals("ươi") || cleanV.equals("ươu") || cleanV.equals("oai") ||
                cleanV.equals("oao") || cleanV.equals("oay") || cleanV.equals("oeo") || cleanV.equals("uây") ||
                cleanV.equals("uya") || cleanV.equals("uyu")) {
                return false;
            }
        }

        // 'c' / 'ng' cannot follow front vowels i, e, ê in native Vietnamese spelling
        if ((fin.equals("c") || fin.equals("ng")) && (cleanV.equals("i") || cleanV.equals("e") || cleanV.equals("ê"))) {
            return false;
        }

        // 'ch' / 'nh' only follow a, i, ê, uê, uy, oa
        if ((fin.equals("ch") || fin.equals("nh")) &&
            !(cleanV.equals("a") || cleanV.equals("i") || cleanV.equals("ê") ||
              cleanV.equals("uê") || cleanV.equals("uy") || cleanV.equals("oa") || cleanV.equals("y"))) {
            return false;
        }

        return true;
    }

    private static boolean isRestPlausibleAfterD(String rest) {
        if (rest.isEmpty()) return true;
        char c0 = rest.charAt(0);
        if (!VietnameseTables.isVietnameseVowel(c0)) return false;
        return isPotentiallyValidVietnamese("d" + rest);
    }

    private static String stripAllTones(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            sb.append(VietnameseTables.stripToneOnly(s.charAt(i)));
        }
        return sb.toString();
    }

    /** Returns true if [word] already contains any Vietnamese tone or vowel/d modifier. */
    public static boolean hasVietnameseDiacritics(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == 'đ' || c == 'Đ') return true;
            VietnameseTables.VowelInfo info = VietnameseTables.getVowelInfo(c);
            if (info != null && (info.tone != VietnameseTables.TONE_NONE || info.modifier != VietnameseTables.MOD_NONE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasImpossibleVietnameseLetters(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = Character.toLowerCase(word.charAt(i));
            if (c == 'f' || c == 'j' || c == 'w' || c == 'z') {
                return true;
            }
        }
        return false;
    }

    /** Returns true if [c] is treated as part of an active Vietnamese token (letter or VNI digit). */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static int telexToneForChar(char lowerChar) {
        switch (lowerChar) {
            case 's': return VietnameseTables.TONE_ACUTE;
            case 'f': return VietnameseTables.TONE_GRAVE;
            case 'r': return VietnameseTables.TONE_HOOK;
            case 'x': return VietnameseTables.TONE_TILDE;
            case 'j': return VietnameseTables.TONE_DOT;
            case 'z': return VietnameseTables.TONE_NONE;
            default: return -1;
        }
    }

    private static int vniToneForChar(char lowerChar) {
        switch (lowerChar) {
            case '1': return VietnameseTables.TONE_ACUTE;
            case '2': return VietnameseTables.TONE_GRAVE;
            case '3': return VietnameseTables.TONE_HOOK;
            case '4': return VietnameseTables.TONE_TILDE;
            case '5': return VietnameseTables.TONE_DOT;
            case '0': return VietnameseTables.TONE_NONE;
            default: return -1;
        }
    }
}
