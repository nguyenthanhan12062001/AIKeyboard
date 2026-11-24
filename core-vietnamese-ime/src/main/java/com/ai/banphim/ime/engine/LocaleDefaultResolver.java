package com.ai.banphim.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Resolves the first-launch keyboard language, enabled language list, Vietnamese input mode,
 * and default translation target based on the device's system locale.
 */
public final class LocaleDefaultResolver {

    /**
     * Immutable bundle of initial keyboard configuration defaults derived from a system locale.
     */
    public static final class InitialDefaults {
        public final KeyboardLanguage activeLanguage;
        public final List<KeyboardLanguage> enabledLanguages;
        public final InputMethodMode inputMethodMode;
        public final String defaultTranslateTargetCode;

        public InitialDefaults(
            KeyboardLanguage activeLanguage,
            List<KeyboardLanguage> enabledLanguages,
            InputMethodMode inputMethodMode,
            String defaultTranslateTargetCode
        ) {
            this.activeLanguage = activeLanguage;
            this.enabledLanguages = Collections.unmodifiableList(enabledLanguages);
            this.inputMethodMode = inputMethodMode;
            this.defaultTranslateTargetCode = defaultTranslateTargetCode;
        }
    }

    private LocaleDefaultResolver() {}

    /**
     * Resolves initial keyboard defaults from the given Android/Java {@link Locale}.
     */
    public static InitialDefaults resolve(Locale systemLocale) {
        if (systemLocale == null) {
            return resolve(Locale.ENGLISH);
        }
        return resolve(systemLocale.getLanguage(), systemLocale.getCountry());
    }

    /**
     * Resolves initial keyboard defaults from ISO language and country codes.
     */
    public static InitialDefaults resolve(String languageCode, String countryCode) {
        KeyboardLanguage primary = KeyboardLanguage.fromLocaleParts(languageCode, countryCode);
        List<KeyboardLanguage> enabled = new ArrayList<>();
        enabled.add(primary);
        if (primary != KeyboardLanguage.ENGLISH) {
            enabled.add(KeyboardLanguage.ENGLISH);
        } else {
            // Keep Vietnamese available as secondary when system is English
            enabled.add(KeyboardLanguage.VIETNAMESE);
        }

        InputMethodMode mode = InputMethodMode.TELEX;
        String translateTarget = (primary == KeyboardLanguage.ENGLISH) ? "vi" : "en";
        return new InitialDefaults(primary, enabled, mode, translateTarget);
    }
}
