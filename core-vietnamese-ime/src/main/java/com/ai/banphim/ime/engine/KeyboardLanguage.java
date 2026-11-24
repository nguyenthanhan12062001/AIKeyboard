package com.ai.banphim.ime.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Supported keyboard input languages and their corresponding layouts & offline IME behaviors.
 */
public enum KeyboardLanguage {
    VIETNAMESE(
        "vi",
        "Tiếng Việt",
        "VI",
        "vi_VN",
        KeyboardLayoutType.QWERTY_VIET
    ),
    ENGLISH(
        "en",
        "English (US)",
        "EN",
        "en_US",
        KeyboardLayoutType.QWERTY
    ),
    CHINESE_PINYIN(
        "zh",
        "中文 (拼音)",
        "拼",
        "zh_CN",
        KeyboardLayoutType.QWERTY_PINYIN
    ),
    JAPANESE_ROMAJI(
        "ja",
        "日本語 (ローマ字)",
        "あ",
        "ja_JP",
        KeyboardLayoutType.QWERTY
    ),
    KOREAN_HANGUL(
        "ko",
        "한국어 (두벌식)",
        "한",
        "ko_KR",
        KeyboardLayoutType.KOREAN_2SET
    ),
    RUSSIAN(
        "ru",
        "Русский",
        "RU",
        "ru_RU",
        KeyboardLayoutType.RUSSIAN_JCUKEN
    ),
    FRENCH(
        "fr",
        "Français",
        "FR",
        "fr_FR",
        KeyboardLayoutType.FRENCH_AZERTY
    ),
    GERMAN(
        "de",
        "Deutsch",
        "DE",
        "de_DE",
        KeyboardLayoutType.GERMAN_QWERTZ
    ),
    SPANISH(
        "es",
        "Español",
        "ES",
        "es_ES",
        KeyboardLayoutType.SPANISH_QWERTY
    ),
    THAI(
        "th",
        "ไทย",
        "ไทย",
        "th_TH",
        KeyboardLayoutType.THAI_KEDMANEE
    ),
    INDONESIAN(
        "id",
        "Bahasa Indonesia",
        "ID",
        "in_ID",
        KeyboardLayoutType.QWERTY
    );

    /**
     * Physical key arrangement used by {@link com.ai.banphim.ime.view.KeyboardLayoutSpec}.
     */
    public enum KeyboardLayoutType {
        QWERTY,
        QWERTY_VIET,
        QWERTY_PINYIN,
        KOREAN_2SET,
        RUSSIAN_JCUKEN,
        FRENCH_AZERTY,
        GERMAN_QWERTZ,
        SPANISH_QWERTY,
        THAI_KEDMANEE
    }

    public final String code;
    public final String nativeName;
    public final String shortBadge;
    public final String subtypeLocale;
    public final KeyboardLayoutType layoutType;

    KeyboardLanguage(
        String code,
        String nativeName,
        String shortBadge,
        String subtypeLocale,
        KeyboardLayoutType layoutType
    ) {
        this.code = code;
        this.nativeName = nativeName;
        this.shortBadge = shortBadge;
        this.subtypeLocale = subtypeLocale;
        this.layoutType = layoutType;
    }

    /**
     * Returns an unmodifiable list of all supported keyboard input languages.
     */
    public static List<KeyboardLanguage> all() {
        return Collections.unmodifiableList(Arrays.asList(values()));
    }

    /**
     * Resolves a {@link KeyboardLanguage} from its ISO language code or enum name,
     * falling back to {@link #ENGLISH} if unrecognized.
     */
    public static KeyboardLanguage fromCode(String code) {
        if (code == null) return ENGLISH;
        String normalized = code.trim().toLowerCase();
        if ("in".equals(normalized)) return INDONESIAN;
        for (KeyboardLanguage lang : values()) {
            if (lang.code.equals(normalized) || lang.name().equalsIgnoreCase(normalized)) {
                return lang;
            }
        }
        return ENGLISH;
    }

    /**
     * Resolves a {@link KeyboardLanguage} from an Android IME subtype locale string (e.g. {@code "vi_VN"}).
     */
    public static KeyboardLanguage fromSubtypeLocale(String subtypeLocale) {
        if (subtypeLocale == null || subtypeLocale.trim().isEmpty()) return ENGLISH;
        String normalized = subtypeLocale.trim().replace('-', '_').toLowerCase();
        for (KeyboardLanguage lang : values()) {
            if (lang.subtypeLocale.toLowerCase().equals(normalized)) {
                return lang;
            }
        }
        String[] parts = normalized.split("_");
        String langPart = parts.length > 0 ? parts[0] : "";
        String countryPart = parts.length > 1 ? parts[1].toUpperCase() : "";
        return fromLocaleParts(langPart, countryPart);
    }

    /**
     * Maps language and country codes from the system locale to the closest built-in keyboard language.
     */
    public static KeyboardLanguage fromLocaleParts(String language, String country) {
        String lang = language == null ? "" : language.trim().toLowerCase();
        String ctry = country == null ? "" : country.trim().toUpperCase();

        if ("vi".equals(lang) || "VN".equals(ctry)) {
            return VIETNAMESE;
        }
        if ("id".equals(lang) || "in".equals(lang) || "ID".equals(ctry)) {
            return INDONESIAN;
        }
        if ("zh".equals(lang) || "CN".equals(ctry) || "TW".equals(ctry) || "HK".equals(ctry) || "MO".equals(ctry)) {
            return CHINESE_PINYIN;
        }
        if ("ja".equals(lang) || "JP".equals(ctry)) {
            return JAPANESE_ROMAJI;
        }
        if ("ko".equals(lang) || "KR".equals(ctry)) {
            return KOREAN_HANGUL;
        }
        if ("ru".equals(lang) || "RU".equals(ctry)) {
            return RUSSIAN;
        }
        if ("fr".equals(lang) || "FR".equals(ctry)) {
            return FRENCH;
        }
        if ("de".equals(lang) || "DE".equals(ctry) || "AT".equals(ctry)) {
            return GERMAN;
        }
        if ("es".equals(lang) || "ES".equals(ctry) || "MX".equals(ctry) || "AR".equals(ctry) || "CO".equals(ctry)) {
            return SPANISH;
        }
        if ("th".equals(lang) || "TH".equals(ctry)) {
            return THAI;
        }
        return ENGLISH;
    }
}
