package com.zyntasolutions.zyntapos.core.i18n

/**
 * Supported application locales.
 *
 * Each locale has a BCP-47 tag and a native display name for the language-picker UI.
 */
enum class SupportedLocale(
    val tag: String,
    val nativeName: String,
    val englishName: String,
) {
    EN("en", "English", "English"),
    SI("si", "\u0DC3\u0DD2\u0D82\u0DC4\u0DBD", "Sinhala"),
    TA("ta", "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD", "Tamil"),
    ;

    companion object {
        /** Resolve a BCP-47 tag to a [SupportedLocale], defaulting to [EN]. */
        fun fromTag(tag: String): SupportedLocale =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: EN
    }
}
