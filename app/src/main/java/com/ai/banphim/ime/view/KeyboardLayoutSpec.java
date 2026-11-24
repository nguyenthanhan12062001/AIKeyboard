package com.ai.banphim.ime.view;

import com.ai.banphim.ime.engine.KeyboardLanguage.KeyboardLayoutType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines Gboard-inspired keyboard layouts across multiple languages (Vietnamese/English/Indonesian QWERTY,
 * Chinese Pinyin, Korean 2-Beolsik, Russian JCUKEN, French AZERTY, German QWERTZ,
 * Spanish QWERTY, Thai Kedmanee), plus Numeric and Symbols pages.
 */
public final class KeyboardLayoutSpec {

    /** Logical keyboard page currently rendered by {@link KeyboardCanvasView}. */
    public enum KeyboardPage {
        QWERTY,
        SYMBOLS_1,
        SYMBOLS_2,
        NUMERIC
    }

    /** Current capitalization mode for alphabetic keys. */
    public enum ShiftState {
        OFF,
        SINGLE,
        CAPS_LOCK
    }

    /** Semantic action emitted when a key is tapped or held. */
    public enum KeyAction {
        CHARACTER,
        SHIFT,
        BACKSPACE,
        MODE_SYMBOLS,
        MODE_ALPHA,
        MODE_SYMBOLS_MORE,
        EMOJI_OR_GLOBE,
        SPACE,
        ENTER
    }

    /** Immutable descriptor for a single keycap including primary, shift, hint, and long-press alternates. */
    public static final class KeySpec {
        public final String label;
        public final String shiftLabel;
        public final String hint;
        public final float weight;
        public final KeyAction action;
        public final List<String> alternates;

        /** Constructs a key specification with explicit labels, relative weight, action, and alternates. */
        public KeySpec(
            String label,
            String shiftLabel,
            String hint,
            float weight,
            KeyAction action,
            List<String> alternates
        ) {
            this.label = label;
            this.shiftLabel = shiftLabel;
            this.hint = hint;
            this.weight = weight;
            this.action = action;
            this.alternates = alternates == null ? Collections.emptyList() : alternates;
        }

        /** Creates a standard alphabetic key whose shift label is uppercased automatically. */
        public static KeySpec letter(String ch, String hint, String... alts) {
            List<String> altList = new ArrayList<>();
            if (hint != null && !hint.isEmpty()) {
                altList.add(hint);
            }
            if (alts != null) {
                altList.addAll(Arrays.asList(alts));
            }
            return new KeySpec(ch, ch.toUpperCase(), hint, 1.0f, KeyAction.CHARACTER, altList);
        }

        /** Creates a character key with an explicit shifted character (used by Korean/Thai layouts). */
        public static KeySpec mapped(String label, String shiftLabel, String hint, String... alts) {
            List<String> altList = new ArrayList<>();
            if (hint != null && !hint.isEmpty()) {
                altList.add(hint);
            }
            if (alts != null) {
                altList.addAll(Arrays.asList(alts));
            }
            return new KeySpec(label, shiftLabel, hint, 1.0f, KeyAction.CHARACTER, altList);
        }

        /** Creates a non-alphabetic symbol or punctuation key. */
        public static KeySpec symbol(String ch, float weight, String... alts) {
            List<String> altList = alts == null ? Collections.emptyList() : Arrays.asList(alts);
            return new KeySpec(ch, ch, "", weight, KeyAction.CHARACTER, altList);
        }

        /** Creates a functional key such as Shift, Backspace, Space, Enter, or page switch. */
        public static KeySpec special(String label, float weight, KeyAction action) {
            return new KeySpec(label, label, "", weight, action, Collections.emptyList());
        }
    }

    private KeyboardLayoutSpec() {}

    /** Builds key rows for the requested [page] using the default Vietnamese QWERTY layout. */
    public static List<List<KeySpec>> buildRows(KeyboardPage page, boolean showNumberRow) {
        return buildRows(page, showNumberRow, KeyboardLayoutType.QWERTY_VIET);
    }

    /** Builds key rows for the given [page], number-row toggle, and language [layoutType]. */
    public static List<List<KeySpec>> buildRows(
        KeyboardPage page,
        boolean showNumberRow,
        KeyboardLayoutType layoutType
    ) {
        switch (page) {
            case SYMBOLS_1:
                return buildSymbols1();
            case SYMBOLS_2:
                return buildSymbols2();
            case NUMERIC:
                return buildNumeric();
            case QWERTY:
            default:
                return buildLanguageLayout(showNumberRow, layoutType);
        }
    }

    private static List<List<KeySpec>> buildLanguageLayout(
        boolean showNumberRow,
        KeyboardLayoutType layoutType
    ) {
        switch (layoutType) {
            case KOREAN_2SET:
                return buildKorean2Set(showNumberRow);
            case RUSSIAN_JCUKEN:
                return buildRussianJcuken(showNumberRow);
            case FRENCH_AZERTY:
                return buildFrenchAzerty(showNumberRow);
            case GERMAN_QWERTZ:
                return buildGermanQwertz(showNumberRow);
            case SPANISH_QWERTY:
                return buildSpanishQwerty(showNumberRow);
            case THAI_KEDMANEE:
                return buildThaiKedmanee(showNumberRow);
            case QWERTY_PINYIN:
                return buildQwerty(showNumberRow, true, false);
            case QWERTY:
                return buildQwerty(showNumberRow, false, false);
            case QWERTY_VIET:
            default:
                return buildQwerty(showNumberRow, false, true);
        }
    }

    private static List<KeySpec> buildNumberRow() {
        return Arrays.asList(
            KeySpec.symbol("1", 1f), KeySpec.symbol("2", 1f), KeySpec.symbol("3", 1f),
            KeySpec.symbol("4", 1f), KeySpec.symbol("5", 1f), KeySpec.symbol("6", 1f),
            KeySpec.symbol("7", 1f), KeySpec.symbol("8", 1f), KeySpec.symbol("9", 1f),
            KeySpec.symbol("0", 1f)
        );
    }

    private static List<KeySpec> buildBottomRow(boolean pinyinPunctuation) {
        if (pinyinPunctuation) {
            return Arrays.asList(
                KeySpec.special("?123", 1.45f, KeyAction.MODE_SYMBOLS),
                KeySpec.symbol("，", 0.95f, ",", "、", ";"),
                KeySpec.special("EMOJI", 1.0f, KeyAction.EMOJI_OR_GLOBE),
                KeySpec.special("SPACE", 4.15f, KeyAction.SPACE),
                KeySpec.symbol("。", 0.95f, ".", "？", "！", "：", "；", "“", "”", "…"),
                KeySpec.special("ENTER", 1.5f, KeyAction.ENTER)
            );
        }
        return Arrays.asList(
            KeySpec.special("?123", 1.45f, KeyAction.MODE_SYMBOLS),
            KeySpec.symbol(",", 0.95f, ";", "'"),
            KeySpec.special("EMOJI", 1.0f, KeyAction.EMOJI_OR_GLOBE),
            KeySpec.special("SPACE", 4.15f, KeyAction.SPACE),
            KeySpec.symbol(".", 0.95f, ",", "?", "!", "@", "#", ":", ";", "\"", "'", "-"),
            KeySpec.special("ENTER", 1.5f, KeyAction.ENTER)
        );
    }

    private static List<List<KeySpec>> buildQwerty(
        boolean showNumberRow,
        boolean pinyinMode,
        boolean vietAlternates
    ) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) rows.add(buildNumberRow());

        rows.add(Arrays.asList(
            KeySpec.letter("q", showNumberRow ? "" : "1"),
            vietAlternates ? KeySpec.letter("w", showNumberRow ? "" : "2", "ư") : KeySpec.letter("w", showNumberRow ? "" : "2"),
            vietAlternates ? KeySpec.letter("e", showNumberRow ? "" : "3", "ê", "€") : KeySpec.letter("e", showNumberRow ? "" : "3", "€"),
            KeySpec.letter("r", showNumberRow ? "" : "4"),
            KeySpec.letter("t", showNumberRow ? "" : "5"),
            KeySpec.letter("y", showNumberRow ? "" : "6"),
            vietAlternates ? KeySpec.letter("u", showNumberRow ? "" : "7", "ư") : KeySpec.letter("u", showNumberRow ? "" : "7"),
            KeySpec.letter("i", showNumberRow ? "" : "8"),
            vietAlternates ? KeySpec.letter("o", showNumberRow ? "" : "9", "ô", "ơ") : KeySpec.letter("o", showNumberRow ? "" : "9"),
            KeySpec.letter("p", showNumberRow ? "" : "0")
        ));

        rows.add(Arrays.asList(
            vietAlternates ? KeySpec.letter("a", "", "ă", "â", "@") : KeySpec.letter("a", "", "@"),
            KeySpec.letter("s", "", "§", "$"),
            vietAlternates ? KeySpec.letter("d", "", "đ") : KeySpec.letter("d", ""),
            KeySpec.letter("f", ""),
            KeySpec.letter("g", ""),
            KeySpec.letter("h", ""),
            KeySpec.letter("j", ""),
            KeySpec.letter("k", ""),
            KeySpec.letter("l", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.5f, KeyAction.SHIFT),
            KeySpec.letter("z", ""),
            KeySpec.letter("x", ""),
            KeySpec.letter("c", "", "©"),
            KeySpec.letter("v", ""),
            KeySpec.letter("b", ""),
            KeySpec.letter("n", ""),
            KeySpec.letter("m", ""),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));

        rows.add(buildBottomRow(pinyinMode));
        return rows;
    }

    private static List<List<KeySpec>> buildKorean2Set(boolean showNumberRow) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) rows.add(buildNumberRow());

        rows.add(Arrays.asList(
            KeySpec.mapped("ㅂ", "ㅃ", showNumberRow ? "ㅃ" : "1", "ㅃ"),
            KeySpec.mapped("ㅈ", "ㅉ", showNumberRow ? "ㅉ" : "2", "ㅉ"),
            KeySpec.mapped("ㄷ", "ㄸ", showNumberRow ? "ㄸ" : "3", "ㄸ"),
            KeySpec.mapped("ㄱ", "ㄲ", showNumberRow ? "ㄲ" : "4", "ㄲ"),
            KeySpec.mapped("ㅅ", "ㅆ", showNumberRow ? "ㅆ" : "5", "ㅆ"),
            KeySpec.mapped("ㅛ", "ㅛ", showNumberRow ? "" : "6"),
            KeySpec.mapped("ㅕ", "ㅕ", showNumberRow ? "" : "7"),
            KeySpec.mapped("ㅑ", "ㅑ", showNumberRow ? "" : "8"),
            KeySpec.mapped("ㅐ", "ㅒ", showNumberRow ? "ㅒ" : "9", "ㅒ"),
            KeySpec.mapped("ㅔ", "ㅖ", showNumberRow ? "ㅖ" : "0", "ㅖ")
        ));

        rows.add(Arrays.asList(
            KeySpec.mapped("ㅁ", "ㅁ", ""),
            KeySpec.mapped("ㄴ", "ㄴ", ""),
            KeySpec.mapped("ㅇ", "ㅇ", ""),
            KeySpec.mapped("ㄹ", "ㄹ", ""),
            KeySpec.mapped("ㅎ", "ㅎ", ""),
            KeySpec.mapped("ㅗ", "ㅗ", ""),
            KeySpec.mapped("ㅓ", "ㅓ", ""),
            KeySpec.mapped("ㅏ", "ㅏ", ""),
            KeySpec.mapped("ㅣ", "ㅣ", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.5f, KeyAction.SHIFT),
            KeySpec.mapped("ㅋ", "ㅋ", ""),
            KeySpec.mapped("ㅌ", "ㅌ", ""),
            KeySpec.mapped("ㅊ", "ㅊ", ""),
            KeySpec.mapped("ㅍ", "ㅍ", ""),
            KeySpec.mapped("ㅠ", "ㅠ", ""),
            KeySpec.mapped("ㅜ", "ㅜ", ""),
            KeySpec.mapped("ㅡ", "ㅡ", ""),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));

        rows.add(buildBottomRow(false));
        return rows;
    }

    private static List<List<KeySpec>> buildRussianJcuken(boolean showNumberRow) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) rows.add(buildNumberRow());

        rows.add(Arrays.asList(
            KeySpec.letter("й", showNumberRow ? "" : "1"),
            KeySpec.letter("ц", showNumberRow ? "" : "2"),
            KeySpec.letter("у", showNumberRow ? "" : "3"),
            KeySpec.letter("к", showNumberRow ? "" : "4"),
            KeySpec.letter("е", showNumberRow ? "" : "5", "ё"),
            KeySpec.letter("н", showNumberRow ? "" : "6"),
            KeySpec.letter("г", showNumberRow ? "" : "7"),
            KeySpec.letter("ш", showNumberRow ? "" : "8"),
            KeySpec.letter("щ", showNumberRow ? "" : "9"),
            KeySpec.letter("з", showNumberRow ? "" : "0"),
            KeySpec.letter("х", ""),
            KeySpec.letter("ъ", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.letter("ф", ""),
            KeySpec.letter("ы", ""),
            KeySpec.letter("в", ""),
            KeySpec.letter("а", ""),
            KeySpec.letter("п", ""),
            KeySpec.letter("р", ""),
            KeySpec.letter("о", ""),
            KeySpec.letter("л", ""),
            KeySpec.letter("д", ""),
            KeySpec.letter("ж", ""),
            KeySpec.letter("э", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.4f, KeyAction.SHIFT),
            KeySpec.letter("я", ""),
            KeySpec.letter("ч", ""),
            KeySpec.letter("с", ""),
            KeySpec.letter("м", ""),
            KeySpec.letter("и", ""),
            KeySpec.letter("т", ""),
            KeySpec.letter("ь", "ъ"),
            KeySpec.letter("б", ""),
            KeySpec.letter("ю", ""),
            KeySpec.special("DEL", 1.4f, KeyAction.BACKSPACE)
        ));

        rows.add(buildBottomRow(false));
        return rows;
    }

    private static List<List<KeySpec>> buildFrenchAzerty(boolean showNumberRow) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) rows.add(buildNumberRow());

        rows.add(Arrays.asList(
            KeySpec.letter("a", showNumberRow ? "" : "1", "à", "â", "æ"),
            KeySpec.letter("z", showNumberRow ? "" : "2"),
            KeySpec.letter("e", showNumberRow ? "" : "3", "é", "è", "ê", "ë", "€"),
            KeySpec.letter("r", showNumberRow ? "" : "4"),
            KeySpec.letter("t", showNumberRow ? "" : "5"),
            KeySpec.letter("y", showNumberRow ? "" : "6", "ÿ"),
            KeySpec.letter("u", showNumberRow ? "" : "7", "ù", "û", "ü"),
            KeySpec.letter("i", showNumberRow ? "" : "8", "î", "ï"),
            KeySpec.letter("o", showNumberRow ? "" : "9", "ô", "œ"),
            KeySpec.letter("p", showNumberRow ? "" : "0")
        ));

        rows.add(Arrays.asList(
            KeySpec.letter("q", ""),
            KeySpec.letter("s", ""),
            KeySpec.letter("d", ""),
            KeySpec.letter("f", ""),
            KeySpec.letter("g", ""),
            KeySpec.letter("h", ""),
            KeySpec.letter("j", ""),
            KeySpec.letter("k", ""),
            KeySpec.letter("l", ""),
            KeySpec.letter("m", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.5f, KeyAction.SHIFT),
            KeySpec.letter("w", ""),
            KeySpec.letter("x", ""),
            KeySpec.letter("c", "", "ç"),
            KeySpec.letter("v", ""),
            KeySpec.letter("b", ""),
            KeySpec.letter("n", ""),
            KeySpec.symbol("'", 1.0f),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));

        rows.add(buildBottomRow(false));
        return rows;
    }

    private static List<List<KeySpec>> buildGermanQwertz(boolean showNumberRow) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) rows.add(buildNumberRow());

        rows.add(Arrays.asList(
            KeySpec.letter("q", showNumberRow ? "" : "1"),
            KeySpec.letter("w", showNumberRow ? "" : "2"),
            KeySpec.letter("e", showNumberRow ? "" : "3", "€"),
            KeySpec.letter("r", showNumberRow ? "" : "4"),
            KeySpec.letter("t", showNumberRow ? "" : "5"),
            KeySpec.letter("z", showNumberRow ? "" : "6"),
            KeySpec.letter("u", showNumberRow ? "" : "7", "ü"),
            KeySpec.letter("i", showNumberRow ? "" : "8"),
            KeySpec.letter("o", showNumberRow ? "" : "9", "ö"),
            KeySpec.letter("p", showNumberRow ? "" : "0")
        ));

        rows.add(Arrays.asList(
            KeySpec.letter("a", "", "ä"),
            KeySpec.letter("s", "", "ß"),
            KeySpec.letter("d", ""),
            KeySpec.letter("f", ""),
            KeySpec.letter("g", ""),
            KeySpec.letter("h", ""),
            KeySpec.letter("j", ""),
            KeySpec.letter("k", ""),
            KeySpec.letter("l", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.5f, KeyAction.SHIFT),
            KeySpec.letter("y", ""),
            KeySpec.letter("x", ""),
            KeySpec.letter("c", ""),
            KeySpec.letter("v", ""),
            KeySpec.letter("b", ""),
            KeySpec.letter("n", ""),
            KeySpec.letter("m", ""),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));

        rows.add(buildBottomRow(false));
        return rows;
    }

    private static List<List<KeySpec>> buildSpanishQwerty(boolean showNumberRow) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) rows.add(buildNumberRow());

        rows.add(Arrays.asList(
            KeySpec.letter("q", showNumberRow ? "" : "1"),
            KeySpec.letter("w", showNumberRow ? "" : "2"),
            KeySpec.letter("e", showNumberRow ? "" : "3", "é", "€"),
            KeySpec.letter("r", showNumberRow ? "" : "4"),
            KeySpec.letter("t", showNumberRow ? "" : "5"),
            KeySpec.letter("y", showNumberRow ? "" : "6"),
            KeySpec.letter("u", showNumberRow ? "" : "7", "ú", "ü"),
            KeySpec.letter("i", showNumberRow ? "" : "8", "í"),
            KeySpec.letter("o", showNumberRow ? "" : "9", "ó"),
            KeySpec.letter("p", showNumberRow ? "" : "0")
        ));

        rows.add(Arrays.asList(
            KeySpec.letter("a", "", "á"),
            KeySpec.letter("s", ""),
            KeySpec.letter("d", ""),
            KeySpec.letter("f", ""),
            KeySpec.letter("g", ""),
            KeySpec.letter("h", ""),
            KeySpec.letter("j", ""),
            KeySpec.letter("k", ""),
            KeySpec.letter("l", ""),
            KeySpec.letter("ñ", "")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.5f, KeyAction.SHIFT),
            KeySpec.letter("z", ""),
            KeySpec.letter("x", ""),
            KeySpec.letter("c", ""),
            KeySpec.letter("v", ""),
            KeySpec.letter("b", ""),
            KeySpec.letter("n", ""),
            KeySpec.letter("m", ""),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));

        rows.add(Arrays.asList(
            KeySpec.special("?123", 1.45f, KeyAction.MODE_SYMBOLS),
            KeySpec.symbol(",", 0.95f, ";", "'"),
            KeySpec.special("EMOJI", 1.0f, KeyAction.EMOJI_OR_GLOBE),
            KeySpec.special("SPACE", 4.15f, KeyAction.SPACE),
            KeySpec.symbol(".", 0.95f, "¿", "¡", "?", "!", ",", ":"),
            KeySpec.special("ENTER", 1.5f, KeyAction.ENTER)
        ));
        return rows;
    }

    private static List<List<KeySpec>> buildThaiKedmanee(boolean showNumberRow) {
        List<List<KeySpec>> rows = new ArrayList<>();
        if (showNumberRow) {
            rows.add(Arrays.asList(
                KeySpec.symbol("๑", 1f, "1"), KeySpec.symbol("๒", 1f, "2"), KeySpec.symbol("๓", 1f, "3"),
                KeySpec.symbol("๔", 1f, "4"), KeySpec.symbol("๕", 1f, "5"), KeySpec.symbol("๖", 1f, "6"),
                KeySpec.symbol("๗", 1f, "7"), KeySpec.symbol("๘", 1f, "8"), KeySpec.symbol("๙", 1f, "9"),
                KeySpec.symbol("๐", 1f, "0")
            ));
        }

        rows.add(Arrays.asList(
            KeySpec.mapped("ๆ", "๐", "๐"),
            KeySpec.mapped("ไ", "\"", "\""),
            KeySpec.mapped("ำ", "ฎ", "ฎ"),
            KeySpec.mapped("พ", "ฑ", "ฑ"),
            KeySpec.mapped("ะ", "ธ", "ธ"),
            KeySpec.mapped("ั", "ํ", "ํ"),
            KeySpec.mapped("ี", "๊", "๊"),
            KeySpec.mapped("ร", "ณ", "ณ"),
            KeySpec.mapped("น", "ฯ", "ฯ"),
            KeySpec.mapped("ย", "ญ", "ญ"),
            KeySpec.mapped("บ", "ฐ", "ฐ"),
            KeySpec.mapped("ล", ",", ",")
        ));

        rows.add(Arrays.asList(
            KeySpec.mapped("ฟ", "ฤ", "ฤ"),
            KeySpec.mapped("ห", "ฆ", "ฆ"),
            KeySpec.mapped("ก", "ฏ", "ฏ"),
            KeySpec.mapped("ด", "โ", "โ"),
            KeySpec.mapped("เ", "ฌ", "ฌ"),
            KeySpec.mapped("้", "็", "็"),
            KeySpec.mapped("่", "๋", "๋"),
            KeySpec.mapped("า", "ษ", "ษ"),
            KeySpec.mapped("ส", "ศ", "ศ"),
            KeySpec.mapped("ว", "ซ", "ซ"),
            KeySpec.mapped("ง", ".", ".")
        ));

        rows.add(Arrays.asList(
            KeySpec.special("SHIFT", 1.35f, KeyAction.SHIFT),
            KeySpec.mapped("ผ", "(", "("),
            KeySpec.mapped("ป", ")", ")"),
            KeySpec.mapped("แ", "ฉ", "ฉ"),
            KeySpec.mapped("อ", "ฮ", "ฮ"),
            KeySpec.mapped("ิ", "ฺ", "ฺ"),
            KeySpec.mapped("ื", "์", "์"),
            KeySpec.mapped("ท", "?", "?"),
            KeySpec.mapped("ม", "ฒ", "ฒ"),
            KeySpec.mapped("ใ", "ฬ", "ฬ"),
            KeySpec.mapped("ฝ", "ฦ", "ฦ"),
            KeySpec.special("DEL", 1.35f, KeyAction.BACKSPACE)
        ));

        rows.add(buildBottomRow(false));
        return rows;
    }

    private static List<List<KeySpec>> buildSymbols1() {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(buildNumberRow());
        rows.add(Arrays.asList(
            KeySpec.symbol("@", 1f), KeySpec.symbol("#", 1f), KeySpec.symbol("$", 1f, "₫", "€", "£", "¥"),
            KeySpec.symbol("_", 1f), KeySpec.symbol("&", 1f), KeySpec.symbol("-", 1f, "—"),
            KeySpec.symbol("+", 1f, "±"), KeySpec.symbol("(", 1f, "[", "{"),
            KeySpec.symbol(")", 1f, "]", "}"), KeySpec.symbol("/", 1f, "\\")
        ));
        rows.add(Arrays.asList(
            KeySpec.special("=\\<", 1.5f, KeyAction.MODE_SYMBOLS_MORE),
            KeySpec.symbol("*", 1f), KeySpec.symbol("\"", 1f), KeySpec.symbol("'", 1f),
            KeySpec.symbol(":", 1f), KeySpec.symbol(";", 1f), KeySpec.symbol("!", 1f),
            KeySpec.symbol("?", 1f),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));
        rows.add(Arrays.asList(
            KeySpec.special("ABC", 1.45f, KeyAction.MODE_ALPHA),
            KeySpec.symbol(",", 0.95f),
            KeySpec.special("EMOJI", 1.0f, KeyAction.EMOJI_OR_GLOBE),
            KeySpec.special("SPACE", 4.15f, KeyAction.SPACE),
            KeySpec.symbol(".", 0.95f, "…"),
            KeySpec.special("ENTER", 1.5f, KeyAction.ENTER)
        ));
        return rows;
    }

    private static List<List<KeySpec>> buildSymbols2() {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
            KeySpec.symbol("~", 1f), KeySpec.symbol("`", 1f), KeySpec.symbol("|", 1f),
            KeySpec.symbol("•", 1f), KeySpec.symbol("√", 1f), KeySpec.symbol("π", 1f),
            KeySpec.symbol("÷", 1f), KeySpec.symbol("×", 1f), KeySpec.symbol("§", 1f),
            KeySpec.symbol("Δ", 1f)
        ));
        rows.add(Arrays.asList(
            KeySpec.symbol("₫", 1f), KeySpec.symbol("£", 1f), KeySpec.symbol("€", 1f),
            KeySpec.symbol("¥", 1f), KeySpec.symbol("^", 1f), KeySpec.symbol("°", 1f),
            KeySpec.symbol("=", 1f), KeySpec.symbol("{", 1f), KeySpec.symbol("}", 1f),
            KeySpec.symbol("\\", 1f)
        ));
        rows.add(Arrays.asList(
            KeySpec.special("?123", 1.5f, KeyAction.MODE_SYMBOLS),
            KeySpec.symbol("%", 1f), KeySpec.symbol("©", 1f), KeySpec.symbol("®", 1f),
            KeySpec.symbol("™", 1f), KeySpec.symbol("✓", 1f), KeySpec.symbol("[", 1f),
            KeySpec.symbol("]", 1f),
            KeySpec.special("DEL", 1.5f, KeyAction.BACKSPACE)
        ));
        rows.add(Arrays.asList(
            KeySpec.special("ABC", 1.45f, KeyAction.MODE_ALPHA),
            KeySpec.symbol("<", 0.95f),
            KeySpec.special("EMOJI", 1.0f, KeyAction.EMOJI_OR_GLOBE),
            KeySpec.special("SPACE", 4.15f, KeyAction.SPACE),
            KeySpec.symbol(">", 0.95f),
            KeySpec.special("ENTER", 1.5f, KeyAction.ENTER)
        ));
        return rows;
    }

    private static List<List<KeySpec>> buildNumeric() {
        List<List<KeySpec>> rows = new ArrayList<>();
        rows.add(Arrays.asList(
            KeySpec.symbol("1", 1f), KeySpec.symbol("2", 1f), KeySpec.symbol("3", 1f),
            KeySpec.symbol("-", 0.85f)
        ));
        rows.add(Arrays.asList(
            KeySpec.symbol("4", 1f), KeySpec.symbol("5", 1f), KeySpec.symbol("6", 1f),
            KeySpec.symbol("+", 0.85f)
        ));
        rows.add(Arrays.asList(
            KeySpec.symbol("7", 1f), KeySpec.symbol("8", 1f), KeySpec.symbol("9", 1f),
            KeySpec.special("DEL", 0.85f, KeyAction.BACKSPACE)
        ));
        rows.add(Arrays.asList(
            KeySpec.special("ABC", 0.95f, KeyAction.MODE_ALPHA),
            KeySpec.symbol("*", 0.65f),
            KeySpec.symbol("0", 1.0f),
            KeySpec.symbol(".", 0.65f, "#"),
            KeySpec.special("ENTER", 0.95f, KeyAction.ENTER)
        ));
        return rows;
    }
}
