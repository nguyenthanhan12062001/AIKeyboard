package com.ai.banphim.ime.engine;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VietnameseImeEngineTest {

    private String typeTelex(String input) {
        return type(input, InputMethodMode.TELEX, TonePlacementStyle.MODERN);
    }

    private String typeVni(String input) {
        return type(input, InputMethodMode.VNI, TonePlacementStyle.MODERN);
    }

    private String type(String input, InputMethodMode mode, TonePlacementStyle style) {
        String current = "";
        String raw = "";
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            VietnameseEngineResult res = VietnameseImeEngine.processKey(current, raw, c, mode, style);
            current = res.getText();
            raw = res.getRawText();
        }
        return current;
    }

    @Test
    public void telexBasicWords() {
        assertEquals("tiếng", typeTelex("tieengs"));
        assertEquals("việt", typeTelex("vieetj"));
        assertEquals("được", typeTelex("dduowcj"));
        assertEquals("được", typeTelex("dduocwj"));
        assertEquals("người", typeTelex("nguowif"));
        assertEquals("thương", typeTelex("thuowng"));
        assertEquals("thương", typeTelex("thuongw"));
        assertEquals("trường", typeTelex("truongwf"));
        assertEquals("học", typeTelex("hocj"));
        assertEquals("mười", typeTelex("muoiwf"));
        assertEquals("quốc", typeTelex("quoocs"));
        assertEquals("giá", typeTelex("gias"));
        assertEquals("giữa", typeTelex("giuwax"));
        assertEquals("bàn", typeTelex("banf"));
        assertEquals("phím", typeTelex("phims"));
        assertEquals("tắ", typeTelex("taws"));
        assertEquals("tắt", typeTelex("tawts"));
        assertEquals("tắt", typeTelex("tawst"));
        assertEquals("ư", typeTelex("w"));
        assertEquals("thư", typeTelex("thw"));
        assertEquals("chưa", typeTelex("chuaw"));
        assertEquals("thưa", typeTelex("thuaw"));
        assertEquals("cưu", typeTelex("cuuw"));
    }

    @Test
    public void telexTonePlacementModernVsClassic() {
        assertEquals("hòa", type("hoaf", InputMethodMode.TELEX, TonePlacementStyle.MODERN));
        assertEquals("hoà", type("hoaf", InputMethodMode.TELEX, TonePlacementStyle.CLASSIC));
        assertEquals("thúy", type("thuys", InputMethodMode.TELEX, TonePlacementStyle.MODERN));
        assertEquals("thuý", type("thuys", InputMethodMode.TELEX, TonePlacementStyle.CLASSIC));
        // Tone shifts when final consonant added later
        assertEquals("hoán", typeTelex("hoasn"));
    }

    @Test
    public void telexUppercaseAndUndo() {
        assertEquals("VIỆT", typeTelex("VIEETJ"));
        assertEquals("ĐÀ", typeTelex("DDAF"));
        assertEquals("Việt", typeTelex("Vieetj"));
        assertEquals("tas", typeTelex("tass"));
        assertEquals("dd", typeTelex("ddd"));
        assertEquals("aa", typeTelex("aaa"));
        assertEquals("ta", typeTelex("tasz"));
    }

    @Test
    public void telexEnglishNonInterference() {
        assertEquals("windows", typeTelex("windows"));
        assertEquals("password", typeTelex("password"));
        assertEquals("screen", typeTelex("screen"));
        assertEquals("google", typeTelex("google"));
        assertEquals("android", typeTelex("android"));
        assertEquals("text", typeTelex("text"));
        assertEquals("world", typeTelex("world"));
        assertEquals("github", typeTelex("github"));
    }

    @Test
    public void vniBasicWords() {
        assertEquals("tiếng", typeVni("tie6ng1"));
        assertEquals("việt", typeVni("vie6t5"));
        assertEquals("được", typeVni("d9u7o7c5"));
        assertEquals("được", typeVni("d9uoc75"));
        assertEquals("người", typeVni("ngu7o7i2"));
        assertEquals("hà", typeVni("ha2"));
        assertEquals("tắt", typeVni("ta8t1"));
        assertEquals("bàn", typeVni("ba2n"));
        assertEquals("phím", typeVni("phi1m"));
        assertEquals("ta", typeVni("ta10"));
    }
}
