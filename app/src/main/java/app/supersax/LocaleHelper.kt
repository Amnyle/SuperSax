package app.supersax

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the language setting ("system", "en" or "fr") to a context. */
object LocaleHelper {
    fun wrap(base: Context, language: String): Context {
        if (language == "system") return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** The effective language code for content files. */
    fun effective(context: Context): String = context.resources.configuration.locales[0].language
}
