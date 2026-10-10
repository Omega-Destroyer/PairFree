package io.github.omegadestroyer.soundpeter.eq

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.omegadestroyer.soundpeter.protocol.EqLayout
import io.github.omegadestroyer.soundpeter.protocol.EqPreset
import io.github.omegadestroyer.soundpeter.protocol.Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Something the Sound tab can put on the earbuds: user gains, one per band in whole dB on
 * top of the model's base curve, or a [preset], a complete curve sent as it is. Stock
 * (all gains 0) and the model's presets are built in; the user's own are saved on the phone.
 */
data class EqProfile(val id: String, val name: String, val gains: List<Int>, val preset: EqPreset? = null) {
    /** Built in: Stock or a preset. Cannot be changed or deleted. */
    val builtIn: Boolean get() = !id.startsWith(USER)

    companion object {
        const val STOCK = "builtin:stock"
        const val PRESET = "preset:"
        const val USER = "user:"
    }
}

/** What this phone last sent to the earbuds. The earbuds cannot report their EQ (docs/PROTOCOL.md section 4). */
data class EqSetting(val profileId: String?, val gains: List<Int>, val preampDb: Int = 0)

data class EqState(val model: Model, val layout: EqLayout, val profiles: List<EqProfile>, val setting: EqSetting) {
    /** The profile on the earbuds: a preset, or the profile the gains came from while they still match. */
    val selected: EqProfile? get() = profiles.firstOrNull { it.id == setting.profileId && (it.preset != null || it.gains == setting.gains) }

    /** The profile the setting started from, edited or not. */
    val basis: EqProfile? get() = profiles.firstOrNull { it.id == setting.profileId }

    /** The preset on the earbuds, if one is. Presets cannot be edited band by band. */
    val preset: EqPreset? get() = selected?.preset

    /**
     * What to draw: the difference from stock per band. For user gains these are the gains;
     * for a preset, its gain minus the base curve's at the same band.
     */
    val curve: List<Double>
        get() = preset?.takeIf { it.bands.size == layout.bands.size }
            ?.bands?.mapIndexed { i, b -> b.gainDb - layout.bands[i].gainDb }
            ?: setting.gains.map { it.toDouble() }

    /** The band frequencies of [curve]. A preset may move some bands. */
    val curveHz: List<Int> get() = (preset?.bands ?: layout.bands).map { it.hz }
}

/** What the Sound tab can do. [Equalizer] does it; previews pass a stand-in. */
interface EqActions {
    fun select(profile: EqProfile)
    fun startNew()
    fun setGain(band: Int, db: Int)
    fun setPreamp(db: Int)

    /** Saves the current gains under [name], replacing a saved profile of that name. */
    fun save(name: String)
    fun delete(profile: EqProfile)

    /** The current gains as a profile file. */
    fun exportJson(): String

    /** Adds the profile in [text] and applies it. False if it is not a profile for these bands. */
    fun importJson(text: String): Boolean
}

/**
 * Profiles and the current EQ for the connected earbuds. Everything is kept on the phone;
 * a change goes to the earbuds [SEND_AFTER_MS] after the last edit, every band at once,
 * as the official app does.
 */
class Equalizer(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val send: (List<ByteArray>) -> Unit,
) : EqActions {
    private val _state = MutableStateFlow<EqState?>(null)
    val state: StateFlow<EqState?> = _state.asStateFlow()

    private var address: String? = null
    private var pending: Job? = null

    /** Loads what this phone last sent to [address], and the profiles saved for [model]. Null [model] or no layout: no EQ. */
    fun open(address: String, model: Model?) {
        val layout = model?.eq
        if (model == null || layout == null) {
            this.address = null
            _state.value = null
            return
        }
        if (this.address == address && _state.value?.model == model) return
        pending?.cancel()
        this.address = address
        val zeros = List(layout.bands.size) { 0 }
        _state.value = EqState(
            model,
            layout,
            builtIns(layout) + loadProfiles(model.code, layout),
            loadSetting(address, layout) ?: EqSetting(EqProfile.STOCK, zeros),
        )
    }

    override fun select(profile: EqProfile) {
        val s = _state.value ?: return
        // A preset keeps the last user gains in the setting, so New and edits start from them.
        apply(if (profile.preset != null) s.setting.copy(profileId = profile.id) else s.setting.copy(profileId = profile.id, gains = profile.gains))
    }

    override fun startNew() {
        val s = _state.value ?: return
        apply(s.setting.copy(profileId = null, gains = List(s.layout.bands.size) { 0 }))
    }

    override fun setGain(band: Int, db: Int) {
        val s = _state.value ?: return
        if (band !in s.layout.bands.indices || s.preset != null) return
        val gains = s.setting.gains.toMutableList()
        gains[band] = db.coerceIn(-s.layout.userRangeDb, s.layout.userRangeDb)
        apply(s.setting.copy(gains = gains))
    }

    override fun setPreamp(db: Int) {
        val s = _state.value ?: return
        apply(s.setting.copy(preampDb = db.coerceIn(PREAMP_MIN_DB, 0)))
    }

    override fun save(name: String) {
        val s = _state.value ?: return
        if (s.preset != null) return
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        val profile = EqProfile(EqProfile.USER + clean, clean, s.setting.gains)
        val profiles = s.profiles.filter { it.id != profile.id } + profile
        _state.value = s.copy(profiles = profiles, setting = s.setting.copy(profileId = profile.id))
        storeProfiles(s.model.code, profiles)
        storeSetting()
    }

    override fun delete(profile: EqProfile) {
        val s = _state.value ?: return
        if (profile.builtIn) return
        val profiles = s.profiles.filter { it.id != profile.id }
        val setting = if (s.setting.profileId == profile.id) s.setting.copy(profileId = null) else s.setting
        _state.value = s.copy(profiles = profiles, setting = setting)
        storeProfiles(s.model.code, profiles)
        storeSetting()
    }

    override fun exportJson(): String {
        val s = _state.value ?: return ""
        val name = s.basis?.takeIf { !it.builtIn }?.name ?: "SoundPeter EQ"
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("model", s.model.code)
            .put("name", name)
            .put("bands_hz", JSONArray(s.layout.bands.map { it.hz }))
            .put("gains_db", JSONArray(s.setting.gains))
            .put("preamp_db", s.setting.preampDb)
            .toString(2)
    }

    override fun importJson(text: String): Boolean {
        val s = _state.value ?: return false
        return try {
            val json = JSONObject(text)
            if (json.optString("format") != FORMAT || json.optInt("version") != 1) return false
            val hz = json.getJSONArray("bands_hz").let { a -> List(a.length()) { a.getInt(it) } }
            if (hz != s.layout.bands.map { it.hz }) return false
            val range = -s.layout.userRangeDb..s.layout.userRangeDb
            val gains = json.getJSONArray("gains_db").let { a -> List(a.length()) { a.getInt(it).coerceIn(range) } }
            val name = json.optString("name").trim().take(MAX_NAME).ifEmpty { "Imported" }
            val profile = EqProfile(EqProfile.USER + name, name, gains)
            val profiles = s.profiles.filter { it.id != profile.id } + profile
            _state.value = s.copy(profiles = profiles)
            storeProfiles(s.model.code, profiles)
            apply(EqSetting(profile.id, gains, json.optInt("preamp_db", 0).coerceIn(PREAMP_MIN_DB, 0)))
            true
        } catch (_: JSONException) {
            false
        }
    }

    private fun apply(setting: EqSetting) {
        val s = _state.value ?: return
        _state.value = s.copy(setting = setting)
        storeSetting()
        pending?.cancel()
        val preset = s.profiles.firstOrNull { it.id == setting.profileId }?.preset
        pending = scope.launch {
            delay(SEND_AFTER_MS)
            send(if (preset != null) s.layout.frames(preset, setting.preampDb) else s.layout.frames(setting.gains, setting.preampDb))
        }
    }

    // Storage: one JSON value per model for saved profiles, one per pair of earbuds for the setting.

    private fun loadProfiles(code: String, layout: EqLayout): List<EqProfile> = try {
        val array = JSONArray(prefs.getString(KEY_PROFILES + code, "[]"))
        List(array.length()) { array.getJSONObject(it) }.mapNotNull { o ->
            val gains = o.getJSONArray("gains").let { a -> List(a.length()) { a.getInt(it) } }
            if (gains.size != layout.bands.size) null else EqProfile(EqProfile.USER + o.getString("name"), o.getString("name"), gains)
        }
    } catch (_: JSONException) {
        emptyList()
    }

    private fun storeProfiles(code: String, profiles: List<EqProfile>) {
        val array = JSONArray()
        for (p in profiles.filter { !it.builtIn }) array.put(JSONObject().put("name", p.name).put("gains", JSONArray(p.gains)))
        prefs.edit { putString(KEY_PROFILES + code, array.toString()) }
    }

    private fun loadSetting(address: String, layout: EqLayout): EqSetting? = try {
        prefs.getString(KEY_SETTING + address, null)?.let { text ->
            val o = JSONObject(text)
            val gains = o.getJSONArray("gains").let { a -> List(a.length()) { a.getInt(it) } }
            if (gains.size != layout.bands.size) null
            else EqSetting(o.optString("profile").ifEmpty { null }, gains, o.optInt("preamp", 0))
        }
    } catch (_: JSONException) {
        null
    }

    private fun storeSetting() {
        val a = address ?: return
        val setting = _state.value?.setting ?: return
        val o = JSONObject()
            .put("profile", setting.profileId ?: "")
            .put("gains", JSONArray(setting.gains))
            .put("preamp", setting.preampDb)
        prefs.edit { putString(KEY_SETTING + a, o.toString()) }
    }

    companion object {
        /** Wait this long after the last change before sending; a drag sends once it settles. */
        const val SEND_AFTER_MS = 300L

        /** The preamp only lowers the master gain, never raises it. */
        const val PREAMP_MIN_DB = -6

        const val FORMAT = "soundpeter-eq"
        private const val MAX_NAME = 24
        private const val KEY_PROFILES = "eq_profiles_"
        private const val KEY_SETTING = "eq_setting_"

        /** Stock, the base curve alone (the earbuds' own tuning), then the model's presets. */
        fun builtIns(layout: EqLayout): List<EqProfile> {
            val zeros = List(layout.bands.size) { 0 }
            return listOf(EqProfile(EqProfile.STOCK, "Stock", zeros)) +
                layout.presets.map { EqProfile(EqProfile.PRESET + it.name, it.name, zeros, it) }
        }
    }
}
