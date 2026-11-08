package io.github.omegadestroyer.pairfree.widget

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetGrid
import io.github.omegadestroyer.pairfree.protocol.widget.WidgetSlot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One widget's buttons for each pair of earbuds ([devices], by address), and the pair it showed last. */
data class WidgetConfig(val last: String?, val devices: Map<String, List<WidgetSlot>>)

/** The widgets' settings on the phone, one JSON value per widget (docs/DESIGN.md, Widget). */
object WidgetStore {
    private const val KEY = "widget_"

    fun load(prefs: SharedPreferences, id: Int): WidgetConfig? = try {
        prefs.getString(KEY + id, null)?.let { text ->
            val o = JSONObject(text)
            val devices = o.optJSONObject("devices")
            WidgetConfig(
                o.optString("last").ifEmpty { null },
                devices?.keys()?.asSequence()?.associateWith { address -> slotsOf(devices.getJSONObject(address).optJSONArray("slots")) }.orEmpty(),
            )
        }
    } catch (_: JSONException) {
        null
    }

    fun save(prefs: SharedPreferences, id: Int, config: WidgetConfig) {
        val devices = JSONObject()
        for ((address, slots) in config.devices) devices.put(address, JSONObject().put("slots", JSONArray(padded(slots).map(::slotJson))))
        prefs.edit { putString(KEY + id, JSONObject().put("last", config.last ?: "").put("devices", devices).toString()) }
    }

    fun delete(prefs: SharedPreferences, id: Int) = prefs.edit { remove(KEY + id) }

    /** Always [WidgetGrid.SLOTS] slots: missing ones are empty. */
    fun padded(slots: List<WidgetSlot>): List<WidgetSlot> = List(WidgetGrid.SLOTS) { slots.getOrNull(it) ?: WidgetSlot.Empty }

    private fun slotsOf(a: JSONArray?): List<WidgetSlot> = padded(List(a?.length() ?: 0) { slotFrom(a!!.optJSONObject(it)) })

    private fun slotJson(slot: WidgetSlot): JSONObject = when (slot) {
        WidgetSlot.Empty -> JSONObject().put("type", "empty")
        is WidgetSlot.Noise -> choiceJson(slot.choice).put("type", "noise")
        is WidgetSlot.NoiseCycle -> JSONObject().put("type", "noise_cycle").put("choices", JSONArray(slot.choices.map(::choiceJson)))
        is WidgetSlot.Eq -> JSONObject().put("type", "eq").put("profile", slot.profileId)
        is WidgetSlot.EqCycle -> JSONObject().put("type", "eq_cycle").put("profiles", JSONArray(slot.profileIds))
    }

    private fun choiceJson(c: NoiseChoice): JSONObject {
        val o = JSONObject().put("mode", c.mode)
        when (val sub = c.sub) {
            null -> Unit
            is Sub.Level -> o.put("sub", JSONObject().put("level", sub.command.name).put("code", sub.code))
            is Sub.Row -> o.put("sub", JSONObject().put("row", sub.key).put("option", sub.option))
            is Sub.Step -> o.put("sub", JSONObject().put("step", sub.key).put("value", sub.value))
        }
        return o
    }

    private fun choiceFrom(o: JSONObject?): NoiseChoice? {
        o ?: return null
        val mode = o.optString("mode")
        if (mode !in Command.ANC_MODE.labels.values) return null
        val sub = o.optJSONObject("sub")?.let { s ->
            when {
                s.has("level") -> {
                    val command = Command.entries.firstOrNull { it.name == s.optString("level") } ?: return null
                    Sub.Level(command, s.optInt("code"))
                }
                s.has("row") -> Sub.Row(s.optString("row"), s.optString("option"))
                s.has("step") -> Sub.Step(s.optString("step"), s.optInt("value"))
                else -> return null
            }
        }
        return NoiseChoice(mode, sub)
    }

    /** An unknown type, or a toggle with fewer than two entries, reads as empty. */
    private fun slotFrom(o: JSONObject?): WidgetSlot = try {
        when (o?.optString("type")) {
            "noise" -> choiceFrom(o)?.let { WidgetSlot.Noise(it) } ?: WidgetSlot.Empty
            "noise_cycle" -> {
                val a = o.optJSONArray("choices")
                val choices = List(a?.length() ?: 0) { choiceFrom(a!!.optJSONObject(it)) }.filterNotNull()
                if (choices.size >= 2) WidgetSlot.NoiseCycle(choices) else WidgetSlot.Empty
            }
            "eq" -> o.optString("profile").takeIf { it.isNotEmpty() }?.let { WidgetSlot.Eq(it) } ?: WidgetSlot.Empty
            "eq_cycle" -> {
                val a = o.optJSONArray("profiles")
                val ids = List(a?.length() ?: 0) { a!!.optString(it) }.filter { it.isNotEmpty() }
                if (ids.size >= 2) WidgetSlot.EqCycle(ids) else WidgetSlot.Empty
            }
            else -> WidgetSlot.Empty
        }
    } catch (_: JSONException) {
        WidgetSlot.Empty
    }
}

/**
 * What PairFree learnt from earbuds while connected, for the choices their tables do not give (docs/DESIGN.md, Widget):
 * SOUNDPEATS models the server's list had none for, Sony and Apple's noise control, Sony's, QCY's and Pixel's EQ.
 */
data class WidgetCache(val noise: Boolean, val ancLevels: List<Int>, val transparencyLevels: List<Int>, val eq: List<Pair<String, String>>?) {
    companion object {
        private const val KEY = "widget_cache_"

        fun load(prefs: SharedPreferences, address: String): WidgetCache? = try {
            prefs.getString(KEY + address, null)?.let { text ->
                val o = JSONObject(text)
                fun ints(name: String) = o.optJSONArray(name)?.let { a -> List(a.length()) { a.getInt(it) } }.orEmpty()
                WidgetCache(
                    o.optBoolean("noise"),
                    ints("anc_levels"),
                    ints("transparency_levels"),
                    o.optJSONArray("eq")?.let { a -> List(a.length()) { a.getJSONObject(it).let { p -> p.getString("id") to p.getString("name") } } },
                )
            }
        } catch (_: JSONException) {
            null
        }

        /** Merges what the connected earbuds [device] show now: noise control never goes back to none, level lists only grow, the EQ list is replaced. */
        fun record(prefs: SharedPreferences, device: Paired, state: EarbudState, eq: EqState?) {
            val old = load(prefs, device.address)
            val noise = (old?.noise ?: false) || state.supports(Command.ANC_MODE)
            val anc = ((old?.ancLevels.orEmpty()) + state.levels(Command.ANC_LEVEL).map { it.code }).distinct().sorted()
            val trans = ((old?.transparencyLevels.orEmpty()) + state.levels(Command.TRANSPARENCY_LEVEL).map { it.code }).distinct().sorted()
            val profiles = eq?.profiles?.map { it.id to it.name } ?: old?.eq
            val new = WidgetCache(noise, anc, trans, profiles)
            if (new == old) return
            val o = JSONObject().put("noise", noise).put("anc_levels", JSONArray(anc)).put("transparency_levels", JSONArray(trans))
            profiles?.let { list -> o.put("eq", JSONArray(list.map { (id, name) -> JSONObject().put("id", id).put("name", name) })) }
            prefs.edit { putString(KEY + device.address, o.toString()) }
        }
    }
}
