package io.github.omegadestroyer.pairfree

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import io.github.omegadestroyer.pairfree.widget.PairFreeWidget
import java.util.Locale

/**
 * The app's language (docs/TRANSLATING.md). The languages are the ones built in: the English file and each
 * `res/values-<code>/strings.xml`, which the build lists in [ALL], so a new language file is offered without a line of
 * code. With none chosen the app follows the phone.
 *
 * Android 13 and later keep the choice themselves ([LocaleManager]): it also shows under the phone's own per-app
 * language setting, and every part of the app follows it. On Android 12 PairFree keeps it and puts it on each context it
 * draws text from ([wrap]).
 */
object Languages {
    /** The built-in languages as language tags (`en`, `ko`, `pt-BR`), English first. */
    val ALL: List<String> = BuildConfig.LANGUAGES.toList()

    private const val PREFS = "pairfree"
    private const val KEY = "app_language"
    private val system: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The language the user chose, as one of [ALL]; null while the app follows the phone. */
    fun chosen(context: Context): String? {
        val tag = if (system) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        } ?: return null
        return ALL.firstOrNull { it.equals(tag, ignoreCase = true) }
            ?: ALL.firstOrNull { it.substringBefore('-').equals(tag.substringBefore('-'), ignoreCase = true) }
    }

    /** Sets the language to [tag], one of [ALL], or back to the phone's with null. The screens are drawn again in it. */
    fun choose(activity: Activity, tag: String?) {
        if (system) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { if (tag == null) remove(KEY) else putString(KEY, tag) }
            activity.recreate()
        }
        PairFreeWidget.refreshAll(activity.applicationContext)
    }

    /**
     * [base] in the chosen language, for Android 12, where the system does not do it: every activity, service and the
     * application take their base context through this, and so does the widget when it draws.
     */
    fun wrap(base: Context): Context {
        if (system) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    /** A language by its own name ("Deutsch", "한국어"), so the list needs no translation. */
    fun name(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** The activity [context] is, or is wrapped around. */
    fun activityOf(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
