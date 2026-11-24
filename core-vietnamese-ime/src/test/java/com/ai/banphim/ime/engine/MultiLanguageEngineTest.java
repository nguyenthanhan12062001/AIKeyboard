package com.ai.banphim.ime.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class MultiLanguageEngineTest {

    @Test
    public void localeDefaultsResolveVietnameseAndChineseAndOthers() {
        LocaleDefaultResolver.InitialDefaults vn = LocaleDefaultResolver.resolve(Locale.forLanguageTag("vi-VN"));
        assertEquals(KeyboardLanguage.VIETNAMESE, vn.activeLanguage);
        assertEquals(InputMethodMode.TELEX, vn.inputMethodMode);
        assertTrue(vn.enabledLanguages.contains(KeyboardLanguage.VIETNAMESE));
        assertTrue(vn.enabledLanguages.contains(KeyboardLanguage.ENGLISH));

        LocaleDefaultResolver.InitialDefaults cn = LocaleDefaultResolver.resolve(Locale.SIMPLIFIED_CHINESE);
        assertEquals(KeyboardLanguage.CHINESE_PINYIN, cn.activeLanguage);
        assertTrue(cn.enabledLanguages.contains(KeyboardLanguage.CHINESE_PINYIN));

        assertEquals(KeyboardLanguage.JAPANESE_ROMAJI, LocaleDefaultResolver.resolve(Locale.JAPAN).activeLanguage);
        assertEquals(KeyboardLanguage.KOREAN_HANGUL, LocaleDefaultResolver.resolve(Locale.KOREA).activeLanguage);
        assertEquals(KeyboardLanguage.FRENCH, LocaleDefaultResolver.resolve(Locale.FRANCE).activeLanguage);
        assertEquals(KeyboardLanguage.GERMAN, LocaleDefaultResolver.resolve(Locale.GERMANY).activeLanguage);
        assertEquals(KeyboardLanguage.RUSSIAN, LocaleDefaultResolver.resolve("ru", "RU").activeLanguage);
        assertEquals(KeyboardLanguage.SPANISH, LocaleDefaultResolver.resolve("es", "ES").activeLanguage);
        assertEquals(KeyboardLanguage.THAI, LocaleDefaultResolver.resolve("th", "TH").activeLanguage);
        assertEquals(KeyboardLanguage.INDONESIAN, LocaleDefaultResolver.resolve(Locale.forLanguageTag("id-ID")).activeLanguage);
        assertEquals(KeyboardLanguage.INDONESIAN, LocaleDefaultResolver.resolve("in", "ID").activeLanguage);
        assertEquals(KeyboardLanguage.ENGLISH, LocaleDefaultResolver.resolve(Locale.US).activeLanguage);
    }

    @Test
    public void pinyinEngineSegmentsAndResolvesPhrasesAndSingleChars() {
        PinyinEngine.LookupResult hello = PinyinEngine.lookup("nihao");
        assertEquals("ni'hao", hello.segmentedDisplay);
        assertFalse(hello.candidates.isEmpty());
        assertEquals("你好", hello.candidates.get(0).text);
        assertEquals(5, hello.candidates.get(0).consumedRawLength);

        PinyinEngine.LookupResult china = PinyinEngine.lookup("zhongguo");
        assertEquals("中国", china.candidates.get(0).text);

        PinyinEngine.LookupResult thanks = PinyinEngine.lookup("xiexie");
        assertEquals("谢谢", thanks.candidates.get(0).text);

        // Selecting single char "你" should only consume "ni" (2 chars)
        PinyinEngine.Candidate niOnly = null;
        for (PinyinEngine.Candidate c : hello.candidates) {
            if ("你".equals(c.text)) {
                niOnly = c;
                break;
            }
        }
        assertTrue(niOnly != null && niOnly.consumedRawLength == 2);
    }

    @Test
    public void koreanHangulEngineComposesDokkaebibulAndDecomposesOnBackspace() {
        String comp = "";
        StringBuilder out = new StringBuilder();
        for (String jamo : new String[] { "ㅎ", "ㅏ", "ㄴ", "ㄱ", "ㅡ", "ㄹ" }) {
            KoreanHangulEngine.StepResult step = KoreanHangulEngine.inputJamo(comp, jamo);
            out.append(step.committed);
            comp = step.composing;
        }
        out.append(comp);
        assertEquals("한글", out.toString());

        // Compound vowel + compound final + backspace decomposition: 값 -> 갑 -> 가 -> ㄱ -> ""
        String c2 = "";
        for (String jamo : new String[] { "ㄱ", "ㅏ", "ㅂ", "ㅅ" }) {
            c2 = KoreanHangulEngine.inputJamo(c2, jamo).composing;
        }
        assertEquals("값", c2);
        c2 = KoreanHangulEngine.backspace(c2);
        assertEquals("갑", c2);
        c2 = KoreanHangulEngine.backspace(c2);
        assertEquals("가", c2);
        c2 = KoreanHangulEngine.backspace(c2);
        assertEquals("ㄱ", c2);
        c2 = KoreanHangulEngine.backspace(c2);
        assertEquals("", c2);
    }

    @Test
    public void japaneseRomajiEngineConvertsHiraganaKatakanaAndKanji() {
        assertEquals("こんにちは", JapaneseRomajiEngine.toHiragana("konnichiha"));
        assertEquals("こんにちわ", JapaneseRomajiEngine.toHiragana("konnichiwa"));
        assertTrue(JapaneseRomajiEngine.getCandidates("konnichiwa").contains("こんにちは"));
        assertEquals("きって", JapaneseRomajiEngine.toHiragana("kitte"));

        List<String> cands = JapaneseRomajiEngine.getCandidates("arigatou");
        assertTrue(cands.contains("有難う"));
        assertTrue(cands.contains("ありがとう"));
        assertTrue(cands.contains("アリガトウ"));
    }
}
