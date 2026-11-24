# Changelog - Bộ Gõ Thông Minh & AI (Bàn Phím AI)

All notable changes to this project will be documented in this file.

## [v3.2] - 2026-11-24
### Added
- **Keyboard Theme Customization System**:
  - **My Themes**: User-created custom themes with thumbnail preview, management, and deletion.
  - **Default Themes**: System Dynamic, System auto-matching, Gboard Light, Dark, AMOLED Black, and Ocean Blue presets.
  - **Color Swatches**: Collection of 9 background colors (White, Light Gray, Dark Gray, Black, Red, Green, Blue, Teal, Cyan).
  - **Personal Photo Background**: Select photo from device gallery using zero-permission Android system picker (`ActivityResultContracts.GetContent()`), with live preview and Opacity slider adjustment (0.1f to 1.0f).
  - **Preview & Apply Flow**: Real-time keyboard preview dialog with Cancel and Apply controls before committing theme changes.

## [v3.1] - 2026-11-24
### Fixed & Improved
- **Vietnamese Telex Input Composition**: Fixed phonotactic vowel clustering and modifier placement bugs for complex Telex sequences (e.g. `chuaw` -> `chưa`, `thuaw` -> `thưa`, `cuuw` -> `cưu`).
- Added robust fallback parsing and undo/toggle support for `ua` and `uu` vowel modifiers when typing `w` in Telex mode.
- Expanded `VietnameseImeEngineTest` suite with comprehensive unit test assertions for new Telex cases.

## [v3.0] - 2026-11-24
### Added
- **Onboarding & Feature Discovery System**:
  - Implemented 3-slide interactive introductory walkthrough on first app launch.
  - Interactive spotlight coach-marks guiding users through core companion app features.
  - DataStore persistence for onboarding completion state (`KeyboardSettings`).
- **Hybrid Translation Engine**:
  - Integrated Google ML Kit On-Device Translation alongside Online AI translation modes (`AUTO_HYBRID`, `OFFLINE_MLKIT`, `ONLINE_AI`).
  - Added Pros/Cons selection UI in Settings (`AiSettingsTab.kt` & localized strings across 12 languages) to help users choose between online and offline translation.
- **Security & Lexicon**:
  - Encrypted personal lexicon storage (`PersonalLexiconCrypto`, Room DB) with zero-learning security zones.
- **UI/UX Polish**:
  - Refreshed Material Design 3 theme with Indigo/Electric Blue styling, full support for Light, Dark, and AMOLED modes.
