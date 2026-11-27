package io.github.omegadestroyer.pairfree.eq

import android.content.SharedPreferences
import androidx.core.content.edit

/** The equalizer's texts in the app's settings file, under the keys every release has used. */
class PrefsTextStore(private val prefs: SharedPreferences) : TextStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) = prefs.edit { putString(key, value) }
}
