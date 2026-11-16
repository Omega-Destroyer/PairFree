package io.github.omegadestroyer.pairfree.look

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.github.omegadestroyer.pairfree.widget.PairFreeWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One colour of a look: a fixed value, or one of the colours Android makes from the wallpaper (Android 12 and later),
 * read when it is used so it follows the phone.
 */
class Tone private constructor(@param:ColorInt private val argb: Int, @param:ColorRes private val system: Int) {
    @ColorInt
    fun resolve(context: Context): Int = if (system != 0) context.getColor(system) else argb

    companion object {
        fun fixed(@ColorInt argb: Int) = Tone(argb, 0)
        fun system(@ColorRes resource: Int) = Tone(0, resource)
    }
}

/** A look's colours by role (docs/DESIGN.md, Looks). */
data class Palette(
    val background: Tone,
    val panel: Tone,
    val raised: Tone,
    val hairline: Tone,
    val text: Tone,
    val secondaryText: Tone,
    val label: Tone,
    val accent: Tone,
    val onAccent: Tone,
    val destructive: Tone,
)

/**
 * A look as its file in data/looks describes it (docs/LOOKS.md): colours, fonts by name, shapes in dp, and how the device
 * drawings are drawn. Nothing here needs Compose, so the widget and the notification read the same look as the screens.
 */
data class LookSpec(
    val id: String,
    /** The look's name, `look_<id>` in the language file. */
    @param:StringRes val name: Int,
    val dark: Palette?,
    val light: Palette?,
    val textFont: String,
    val numberFont: String,
    val labelCaps: Boolean,
    val hairline: Float,
    val corner: Float,
    val smallCorner: Float,
    val panelBorder: Boolean,
    val artStyle: String,
    val artStroke: Float,
) {
    /** True when the look is drawn dark while the phone is in dark mode ([night]) or not. A look with both palettes follows the phone. */
    fun isDark(night: Boolean): Boolean = if (dark != null && light != null) night else dark != null

    fun palette(night: Boolean): Palette = (if (isDark(night)) dark else light)!!
}

/**
 * The app's look. The looks are the ones built in, one file each in data/looks, which the build lists in [ALL], so a new
 * look is offered without a line of code. The choice is kept in the app's settings; the screens, the home screen widget
 * and the notification all follow it.
 */
object Looks {
    val ALL: List<LookSpec> = BUILT_IN_LOOKS

    const val DEFAULT = "studio"
    private const val PREFS = "pairfree"
    private const val KEY = "look"

    private var state: MutableStateFlow<LookSpec>? = null

    private fun stored(context: Context): LookSpec {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return ALL.firstOrNull { it.id == id } ?: ALL.firstOrNull { it.id == DEFAULT } ?: ALL.first()
    }

    /** The chosen look, and every later choice. */
    fun flow(context: Context): StateFlow<LookSpec> = state ?: MutableStateFlow(stored(context.applicationContext)).also { state = it }

    fun chosen(context: Context): LookSpec = flow(context).value

    /** Sets the look to one of [ALL]. The screens are drawn again in it at once, and the widgets too. */
    fun choose(context: Context, look: LookSpec) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, look.id) }
        flow(app)
        state?.value = look
        PairFreeWidget.refreshAll(app)
    }

    /** Whether the phone is in dark mode. */
    fun night(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

}
