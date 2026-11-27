package io.github.omegadestroyer.pairfree.widget

import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.annotation.IdRes
import io.github.omegadestroyer.pairfree.look.Looks
import io.github.omegadestroyer.pairfree.look.Palette
import io.github.omegadestroyer.pairfree.look.Tone

/**
 * The app's look on the home screen widget (docs/DESIGN.md, Widget). The widget's layout holds white shapes and no
 * colours; this puts the chosen look's colours on them. Each colour goes out as a pair, for the phone's light and dark
 * mode, and the home screen picks one, so a look with both palettes follows the phone without the widget being drawn
 * again. A look with one palette gives the same colour twice.
 *
 * Colours a look takes from the wallpaper are read here, when the widget is drawn: a new wallpaper shows on the widget
 * the next time it is drawn (PairFreeApplication draws it again when the phone's configuration changes).
 */
internal class WidgetLook(private val context: Context) {
    private val spec = Looks.chosen(context)
    private val day: Palette = spec.palette(night = false)
    private val night: Palette = spec.palette(night = true)

    /** Fills the white shape behind [view] with a role's colour. */
    fun fill(views: RemoteViews, @IdRes view: Int, role: (Palette) -> Tone) {
        views.setColorStateList(
            view,
            "setBackgroundTintList",
            ColorStateList.valueOf(role(day).resolve(context)),
            ColorStateList.valueOf(role(night).resolve(context)),
        )
    }

    /** Sets the text of [view] in a role's colour. */
    fun text(views: RemoteViews, @IdRes view: Int, role: (Palette) -> Tone) {
        views.setColorInt(view, "setTextColor", role(day).resolve(context), role(night).resolve(context))
    }
}
