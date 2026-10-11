package net.redngreen.easypos.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language switch (shop-settings spec): instant, persistent across
 * restarts, no reboot of the process. AppCompatDelegate handles API 33+'s
 * per-app locales and the pre-33 backport alike.
 */
object LanguageController {

    /** Currently applied language tag — "en", "ms", or system default "". */
    val current: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "" }

    fun apply(languageTag: String) {
        val locales =
            if (languageTag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(languageTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
