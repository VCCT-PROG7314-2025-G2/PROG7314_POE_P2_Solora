package dev.solora.profile

import android.content.Context
import java.util.Locale

/**
 * Handles app language/locale changes
 * Supports English, Afrikaans, and isiXhosa
 */
object LocaleHelper {
    // Apply language setting to context
    fun setLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        val ctx = context.createConfigurationContext(config)
        return ctx
    }
}
