package com.ai.banphim.ime.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java offline Japanese Romaji-to-Hiragana/Katakana + common Kanji candidate converter.
 */
public final class JapaneseRomajiEngine {

    private static final Map<String, String> ROMAJI_MAP = new HashMap<>();
    private static final Map<String, List<String>> KANJI_WORDS = new HashMap<>();

    static {
        initRomaji();
        initKanji();
    }

    private JapaneseRomajiEngine() {}

    /** Converts Latin [romaji] input into Hiragana including sokuon (っ) and syllabic ん rules. */
    public static String toHiragana(String romaji) {
        if (romaji == null || romaji.isEmpty()) return "";
        String lower = romaji.toLowerCase();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = lower.length();

        while (i < n) {
            char c = lower.charAt(i);
            if (c == '-') {
                sb.append('ー');
                i++;
                continue;
            }
            if (c == '\'') {
                i++;
                continue;
            }

            // Double consonant -> small tsu (except 'n')
            if (i + 1 < n && c == lower.charAt(i + 1) && isConsonant(c) && c != 'n') {
                sb.append('っ');
                i++;
                continue;
            }

            // Match longest romaji sequence (up to 4 chars, e.g. "ltsu", "xtsu")
            boolean matched = false;
            for (int len = Math.min(4, n - i); len >= 1; len--) {
                String sub = lower.substring(i, i + len);
                String kana = ROMAJI_MAP.get(sub);
                if (kana != null) {
                    sb.append(kana);
                    i += len;
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            // Standalone 'n' followed by consonant (not 'y' or vowel), 'nn', or "n'"
            if (c == 'n') {
                if (i + 1 < n) {
                    char next = lower.charAt(i + 1);
                    if (next == '\'') {
                        sb.append('ん');
                        i += 2;
                        continue;
                    }
                    if (next == 'n') {
                        sb.append('ん');
                        // If followed by vowel or 'y' (e.g. "konnichiwa"), keep second 'n' for "ni"
                        if (i + 2 < n && (isVowel(lower.charAt(i + 2)) || lower.charAt(i + 2) == 'y')) {
                            i += 1;
                        } else {
                            i += 2;
                        }
                        continue;
                    }
                    if (!isVowel(next) && next != 'y') {
                        sb.append('ん');
                        i++;
                        continue;
                    }
                }
            }

            // Leave unresolved trailing consonant(s) as raw ASCII until user finishes typing
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** Shifts Hiragana code points in [hiragana] to their corresponding Katakana forms. */
    public static String toKatakana(String hiragana) {
        if (hiragana == null || hiragana.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(hiragana.length());
        for (int i = 0; i < hiragana.length(); i++) {
            char c = hiragana.charAt(i);
            if (c >= '\u3041' && c <= '\u3096') {
                sb.append((char) (c + 0x60));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Returns ordered Kanji, Hiragana, Katakana, and raw Romaji candidates for [rawRomaji]. */
    public static List<String> getCandidates(String rawRomaji) {
        if (rawRomaji == null || rawRomaji.isEmpty()) return Collections.emptyList();
        String hira = toHiragana(rawRomaji);
        String kata = toKatakana(hira);
        Set<String> out = new LinkedHashSet<>();

        List<String> kanji = KANJI_WORDS.get(hira);
        if (kanji != null) {
            out.addAll(kanji);
        }
        if (!hira.isEmpty()) out.add(hira);
        if (!kata.isEmpty() && !kata.equals(hira)) out.add(kata);
        out.add(rawRomaji);
        return new ArrayList<>(out);
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'i' || c == 'u' || c == 'e' || c == 'o';
    }

    private static boolean isConsonant(char c) {
        return c >= 'a' && c <= 'z' && !isVowel(c);
    }

    private static void initRomaji() {
        ROMAJI_MAP.put("a", "あ"); ROMAJI_MAP.put("i", "い"); ROMAJI_MAP.put("u", "う"); ROMAJI_MAP.put("e", "え"); ROMAJI_MAP.put("o", "お");
        ROMAJI_MAP.put("ka", "か"); ROMAJI_MAP.put("ki", "き"); ROMAJI_MAP.put("ku", "く"); ROMAJI_MAP.put("ke", "け"); ROMAJI_MAP.put("ko", "こ");
        ROMAJI_MAP.put("sa", "さ"); ROMAJI_MAP.put("shi", "し"); ROMAJI_MAP.put("si", "し"); ROMAJI_MAP.put("su", "す"); ROMAJI_MAP.put("se", "せ"); ROMAJI_MAP.put("so", "そ");
        ROMAJI_MAP.put("ta", "た"); ROMAJI_MAP.put("chi", "ち"); ROMAJI_MAP.put("ti", "ち"); ROMAJI_MAP.put("tsu", "つ"); ROMAJI_MAP.put("tu", "つ"); ROMAJI_MAP.put("te", "て"); ROMAJI_MAP.put("to", "と");
        ROMAJI_MAP.put("na", "な"); ROMAJI_MAP.put("ni", "に"); ROMAJI_MAP.put("nu", "ぬ"); ROMAJI_MAP.put("ne", "ね"); ROMAJI_MAP.put("no", "の");
        ROMAJI_MAP.put("ha", "は"); ROMAJI_MAP.put("hi", "ひ"); ROMAJI_MAP.put("fu", "ふ"); ROMAJI_MAP.put("hu", "ふ"); ROMAJI_MAP.put("he", "へ"); ROMAJI_MAP.put("ho", "ほ");
        ROMAJI_MAP.put("ma", "ま"); ROMAJI_MAP.put("mi", "み"); ROMAJI_MAP.put("mu", "む"); ROMAJI_MAP.put("me", "め"); ROMAJI_MAP.put("mo", "も");
        ROMAJI_MAP.put("ya", "や"); ROMAJI_MAP.put("yu", "ゆ"); ROMAJI_MAP.put("yo", "よ");
        ROMAJI_MAP.put("ra", "ら"); ROMAJI_MAP.put("ri", "り"); ROMAJI_MAP.put("ru", "る"); ROMAJI_MAP.put("re", "れ"); ROMAJI_MAP.put("ro", "ろ");
        ROMAJI_MAP.put("wa", "わ"); ROMAJI_MAP.put("wo", "を");

        ROMAJI_MAP.put("ga", "が"); ROMAJI_MAP.put("gi", "ぎ"); ROMAJI_MAP.put("gu", "ぐ"); ROMAJI_MAP.put("ge", "げ"); ROMAJI_MAP.put("go", "ご");
        ROMAJI_MAP.put("za", "ざ"); ROMAJI_MAP.put("ji", "じ"); ROMAJI_MAP.put("zi", "じ"); ROMAJI_MAP.put("zu", "ず"); ROMAJI_MAP.put("ze", "ぜ"); ROMAJI_MAP.put("zo", "ぞ");
        ROMAJI_MAP.put("da", "だ"); ROMAJI_MAP.put("di", "ぢ"); ROMAJI_MAP.put("du", "づ"); ROMAJI_MAP.put("de", "で"); ROMAJI_MAP.put("do", "ど");
        ROMAJI_MAP.put("ba", "ば"); ROMAJI_MAP.put("bi", "び"); ROMAJI_MAP.put("bu", "ぶ"); ROMAJI_MAP.put("be", "べ"); ROMAJI_MAP.put("bo", "ぼ");
        ROMAJI_MAP.put("pa", "ぱ"); ROMAJI_MAP.put("pi", "ぴ"); ROMAJI_MAP.put("pu", "ぷ"); ROMAJI_MAP.put("pe", "ぺ"); ROMAJI_MAP.put("po", "ぽ");

        // Yoon combinations
        ROMAJI_MAP.put("kya", "きゃ"); ROMAJI_MAP.put("kyu", "きゅ"); ROMAJI_MAP.put("kyo", "きょ");
        ROMAJI_MAP.put("sha", "しゃ"); ROMAJI_MAP.put("shu", "しゅ"); ROMAJI_MAP.put("sho", "しょ");
        ROMAJI_MAP.put("sya", "しゃ"); ROMAJI_MAP.put("syu", "しゅ"); ROMAJI_MAP.put("syo", "しょ");
        ROMAJI_MAP.put("cha", "ちゃ"); ROMAJI_MAP.put("chu", "ちゅ"); ROMAJI_MAP.put("cho", "ちょ");
        ROMAJI_MAP.put("tya", "ちゃ"); ROMAJI_MAP.put("tyu", "ちゅ"); ROMAJI_MAP.put("tyo", "ちょ");
        ROMAJI_MAP.put("nya", "にゃ"); ROMAJI_MAP.put("nyu", "にゅ"); ROMAJI_MAP.put("nyo", "にょ");
        ROMAJI_MAP.put("hya", "ひゃ"); ROMAJI_MAP.put("hyu", "ひゅ"); ROMAJI_MAP.put("hyo", "ひょ");
        ROMAJI_MAP.put("mya", "みゃ"); ROMAJI_MAP.put("myu", "みゅ"); ROMAJI_MAP.put("myo", "みょ");
        ROMAJI_MAP.put("rya", "りゃ"); ROMAJI_MAP.put("ryu", "りゅ"); ROMAJI_MAP.put("ryo", "りょ");
        ROMAJI_MAP.put("gya", "ぎゃ"); ROMAJI_MAP.put("gyu", "ぎゅ"); ROMAJI_MAP.put("gyo", "ぎょ");
        ROMAJI_MAP.put("ja", "じゃ"); ROMAJI_MAP.put("ju", "じゅ"); ROMAJI_MAP.put("jo", "じょ");
        ROMAJI_MAP.put("jya", "じゃ"); ROMAJI_MAP.put("jyu", "じゅ"); ROMAJI_MAP.put("jyo", "じょ");
        ROMAJI_MAP.put("bya", "びゃ"); ROMAJI_MAP.put("byu", "びゅ"); ROMAJI_MAP.put("byo", "びょ");
        ROMAJI_MAP.put("pya", "ぴゃ"); ROMAJI_MAP.put("pyu", "ぴゅ"); ROMAJI_MAP.put("pyo", "ぴょ");
    }

    private static void initKanji() {
        KANJI_WORDS.put("わたし", Collections.singletonList("私"));
        KANJI_WORDS.put("あなた", Collections.singletonList("貴方"));
        KANJI_WORDS.put("にほん", Collections.singletonList("日本"));
        KANJI_WORDS.put("にほんご", Collections.singletonList("日本語"));
        KANJI_WORDS.put("えいご", Collections.singletonList("英語"));
        KANJI_WORDS.put("ちゅうごく", Collections.singletonList("中国"));
        KANJI_WORDS.put("べとなむ", Collections.singletonList("ベトナム"));
        KANJI_WORDS.put("ありがとう", Collections.singletonList("有難う"));
        KANJI_WORDS.put("おはよう", Collections.singletonList("お早う"));
        KANJI_WORDS.put("こんにちは", Collections.singletonList("今日は"));
        KANJI_WORDS.put("こんにちわ", Arrays.asList("こんにちは", "今日は"));
        KANJI_WORDS.put("こんばんは", Collections.singletonList("今晩は"));
        KANJI_WORDS.put("こんばんわ", Arrays.asList("こんばんは", "今晩は"));
        KANJI_WORDS.put("さようなら", Collections.singletonList("左様なら"));
        KANJI_WORDS.put("せんせい", Collections.singletonList("先生"));
        KANJI_WORDS.put("がくせい", Collections.singletonList("学生"));
        KANJI_WORDS.put("ともだち", Collections.singletonList("友達"));
        KANJI_WORDS.put("かぞく", Collections.singletonList("家族"));
        KANJI_WORDS.put("でんわ", Collections.singletonList("電話"));
        KANJI_WORDS.put("きょう", Collections.singletonList("今日"));
        KANJI_WORDS.put("あした", Collections.singletonList("明日"));
        KANJI_WORDS.put("きのう", Collections.singletonList("昨日"));
        KANJI_WORDS.put("いま", Collections.singletonList("今"));
        KANJI_WORDS.put("じかん", Collections.singletonList("時間"));
        KANJI_WORDS.put("すき", Collections.singletonList("好き"));
        KANJI_WORDS.put("だいすき", Collections.singletonList("大好き"));
        KANJI_WORDS.put("たべる", Collections.singletonList("食べる"));
        KANJI_WORDS.put("のむ", Collections.singletonList("飲む"));
        KANJI_WORDS.put("いく", Collections.singletonList("行く"));
        KANJI_WORDS.put("くる", Collections.singletonList("来る"));
        KANJI_WORDS.put("みる", Collections.singletonList("見る"));
        KANJI_WORDS.put("きく", Collections.singletonList("聞く"));
        KANJI_WORDS.put("はなす", Collections.singletonList("話す"));
        KANJI_WORDS.put("よむ", Collections.singletonList("読む"));
        KANJI_WORDS.put("かく", Collections.singletonList("書く"));
        KANJI_WORDS.put("しごと", Collections.singletonList("仕事"));
        KANJI_WORDS.put("べんきょう", Collections.singletonList("勉強"));
        KANJI_WORDS.put("ほんやく", Collections.singletonList("翻訳"));
        KANJI_WORDS.put("あい", Collections.singletonList("愛"));
        KANJI_WORDS.put("ひと", Collections.singletonList("人"));
        KANJI_WORDS.put("みず", Collections.singletonList("水"));
        KANJI_WORDS.put("やま", Collections.singletonList("山"));
        KANJI_WORDS.put("かわ", Collections.singletonList("川"));
        KANJI_WORDS.put("そら", Collections.singletonList("空"));
        KANJI_WORDS.put("はな", Collections.singletonList("花"));
    }
}
