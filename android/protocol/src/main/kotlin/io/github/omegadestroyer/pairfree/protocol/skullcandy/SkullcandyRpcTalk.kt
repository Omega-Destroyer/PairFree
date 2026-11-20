package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import kotlin.math.roundToInt

/** The Bragi BOS-RPC services and methods this session uses (docs/skullcandy/SERVICES.md; the ids are the schemas', decimal). */
public object BosRpc {
    public const val DEVICE: Int = 2
    public const val DEV_INFO: Int = 1
    public const val BATT_INFO: Int = 3
    public const val HEARING: Int = 4
    public const val MODE_READ: Int = 1
    public const val MODE_CONFIGURE: Int = 2
    public const val MODE_INFO: Int = 3
    public const val PEQ_READ_STATE: Int = 4
    public const val PEQ_READ_CONF: Int = 5
    public const val PEQ_INFO: Int = 6
    public const val PEQ_CONFIGURE: Int = 7
    public const val SIDETONE_READ: Int = 10
    public const val SIDETONE_CONFIGURE: Int = 11
    public const val SIDETONE_INFO: Int = 12
    public const val LANGUAGE: Int = 6
    public const val LANG_INFO: Int = 1
    public const val LANG_CONFIGURE: Int = 2
    public const val LOW_LATENCY: Int = 8
    public const val INSERTION: Int = 12
    public const val TRIGGER_GET: Int = 3
    public const val TRIGGER_SET: Int = 4
    public const val SPATIAL: Int = 17
    public const val SPATIAL_INFO: Int = 1
    public const val SPATIAL_SET: Int = 2
    public const val SOUNDS: Int = 22
    public const val CRUSHER: Int = 23
    public const val CRUSHER_CONFIG: Int = 1
    public const val CRUSHER_STATE_GET: Int = 2
    public const val CRUSHER_STATE_SET: Int = 3
    public const val BUTTONS: Int = 3
    public const val BUTTON_GET_ACTION: Int = 2
    public const val BUTTON_SET_ACTION: Int = 3
    public const val BUTTON_INFO_V2: Int = 7

    /** The services PairFree writes to: nothing else is ever built (the calibration, data streams, diagnostics, feature provisioning, time, device name and the secure session are not). */
    public val WRITABLE: Set<Pair<Int, Int>> = setOf(
        HEARING to MODE_CONFIGURE, HEARING to PEQ_CONFIGURE, HEARING to SIDETONE_CONFIGURE, LANGUAGE to LANG_CONFIGURE, LOW_LATENCY to 2, INSERTION to TRIGGER_SET,
        SPATIAL to SPATIAL_SET, SOUNDS to 2, CRUSHER to CRUSHER_STATE_SET, BUTTONS to BUTTON_SET_ACTION,
    )

    /** The reads: every request the session builds goes through [isAllowed]. */
    public val READABLE: Set<Pair<Int, Int>> = setOf(
        DEVICE to DEV_INFO, DEVICE to BATT_INFO, HEARING to MODE_READ, HEARING to MODE_INFO, HEARING to PEQ_READ_STATE, HEARING to PEQ_READ_CONF, HEARING to PEQ_INFO,
        HEARING to SIDETONE_READ, HEARING to SIDETONE_INFO, LANGUAGE to LANG_INFO, LOW_LATENCY to 1, INSERTION to TRIGGER_GET, SPATIAL to SPATIAL_INFO, SOUNDS to 1,
        CRUSHER to CRUSHER_CONFIG, CRUSHER to CRUSHER_STATE_GET, BUTTONS to BUTTON_GET_ACTION, BUTTONS to BUTTON_INFO_V2,
    )

    public fun isAllowed(rpc: BragiRpc): Boolean = rpc.type == SkullcandyRpc.REQUEST && ((rpc.service to rpc.method) in READABLE || (rpc.service to rpc.method) in WRITABLE)
}

/**
 * The conversation with the three products that speak Bragi's own protobuf RPC (the Method 360 and 540 ANC and the Crusher 1080 ANC; docs/skullcandy/PROTOCOL.md section 15): each request is
 * an RPC frame, each answer a response or a status with the same service, method and tag, and the subscriptions send more of them whenever the state changes. The session is the unsecured one:
 * the SDK itself carries on that way when the secure session fails, and a service that needs it answers "access denied" (status 41), which the device report shows. The model's rows are what the
 * SDK's own configuration for it lists, over RPC; the equaliser of the Method models and multipoint are Airoha RACE's ([SkullcandyRaceTalk]).
 */
public class SkullcandyRpcTalk(public val product: SkullcandyProduct) : SessionTalk<BragiRpc>() {
    override val pacing: Pacing = Pacing(replyMs = 2000, gapMs = 120, startGapMs = 120, pollMs = Pacing.POLL_MS, resends = 1)

    private var current = EarbudState()
    override val state: EarbudState get() = current

    private val f = product.features
    private val hasAnc = "adjustableNoiseCancellation" in f || "boseQuietControlANC" in f || "activeNoiseCancellation" in f
    private val hasTransparency = "adjustableStayAwareMode" in f || "stayAwareMode" in f
    private val hasSidetone = "adjustableSidetoneMode" in f
    private val hasLatency = "lowLatencyMode" in f || "gamingMode" in f
    private val hasWear = "wearDetect" in f
    private val hasCrusher = "crusherTechnologyDial" in f || "crusherTechnologyProfiles" in f
    private val hasSpatial = "boseTrueSpatialMotion" in f || "boseTrueSpatialStill" in f
    private val hasRpcEq = "boseEqualizer" in f

    private val hasButtons = "customButtonActions" in f
    private val outbox = ArrayList<BragiRpc>()

    // the button map: what the earbuds list (their configurations, keys, gestures and actions) and what each wanted key and gesture holds now
    private val configs = ArrayList<String>()
    private val controls = ArrayList<String>()
    private val gestureNames = ArrayList<String>()
    private val actionNames = LinkedHashSet<String>()
    private val asked = HashSet<Triple<String, String, String>>()
    private val buttonValues = LinkedHashMap<Triple<String, String, String>, String?>()

    private var tag = 0
    private val parser = BragiParser()
    private var readBacks: List<BragiRpc> = emptyList()

    /** What the earbuds said about each request that did not succeed: the (service, method) and the first byte of the status. */
    public val failures: MutableMap<Pair<Int, Int>, Int> = LinkedHashMap()

    /** The last reply body to each request, by name, for the device report (never the device info's identifiers). */
    public val replies: MutableMap<String, ByteArray> = LinkedHashMap()

    /** True when the device info said the earbuds require the secure session for some services (docs/skullcandy/PROTOCOL.md section 6). */
    public var authenticatedAccess: Boolean? = null
        private set

    /** True when some request was refused with "service access denied" (status 41). */
    public val denied: Boolean get() = failures.values.any { it == STATUS_DENIED }

    private var active: Int? = null
    private var ancPreset: Int? = null
    private var ancGain: Int? = null
    private var atPreset: Int? = null
    private var atGain: Int? = null
    private var ancPresetMax: Int? = null
    private var ancGainMax: Int? = null
    private var atPresetMax: Int? = null
    private var atGainMax: Int? = null
    private var sidetoneOn: Boolean? = null
    private var sidetoneVolume: Int? = null
    private var sidetoneMax: Int? = null
    private var latency: Int? = null
    private var wearPause: Boolean? = null
    private var wearTransparency: Boolean? = null
    private var crusherActive: Boolean? = null
    private var crusherIntensity: Int? = null
    private var crusherMode: Int? = null
    private var crusherMax: Int? = null
    private var crusherReadOnly: Boolean = false
    private var crusherModes: Int? = null
    private var spatialAvailable: Boolean? = null
    private var spatialMode: Int? = null
    private var reducedPrompts: Boolean? = null
    private var batteryPrompt: Boolean? = null
    private var locale: String? = null
    private var locales: List<String> = emptyList()

    // the parametric equaliser the earbuds hold (Crusher 1080 ANC): its limits, what is active, and the filters
    private class PeqFilter(val id: Int, val editable: Boolean, val name: String, val bands: List<Triple<Int, Float, Float>>)

    private var peqMinGain: Float? = null
    private var peqMaxGain: Float? = null
    private var peqEnabled: Boolean? = null
    private var peqCurrent: Int? = null
    private val peqFilters = LinkedHashMap<Int, PeqFilter>()

    /** The preset the earbuds hold active, by name, or null for the editable curve. */
    public val eqPresetName: String? get() = peqCurrent?.let { id -> peqFilters[id]?.takeIf { !it.editable }?.name }

    /** The editable curve's bands in Hz, empty until the earbuds have named it. */
    public val eqBandsHz: List<Int> get() = peqFilters.values.firstOrNull { it.editable }?.bands?.map { it.first }.orEmpty()

    /** The preset filters the earbuds hold, by name. */
    public val eqPresetNames: List<String> get() = peqFilters.values.filter { !it.editable && it.name.isNotBlank() }.map { it.name }

    /**
     * The gain range of the curve, in dB each way. The earbuds' range is 0 to +20 (the SDK's `GainRangeAttribute` for the Crusher 1080 ANC): PairFree shows it around its middle, minus 10 to plus 10, and
     * sends the value plus 10 (an inference; the app's Bose equaliser screen was not read).
     */
    public val eqRangeDb: Int get() = (((peqMaxGain ?: 20f) - (peqMinGain ?: 0f)) / 2).roundToInt().coerceAtLeast(1)

    private val eqCentre: Float get() = ((peqMaxGain ?: 20f) + (peqMinGain ?: 0f)) / 2

    /** The gains of the editable curve in whole dB around the middle, once the earbuds have reported it. */
    public val eqGains: List<Int>? get() = peqFilters.values.firstOrNull { it.editable && eqPresetName == null }?.bands?.map { (it.second - eqCentre).roundToInt() }

    /** True when the model's equaliser is the earbuds' own parametric one and its editable filter has been read. */
    public val hasEq: Boolean get() = hasRpcEq && peqFilters.values.any { it.editable && it.bands.isNotEmpty() }

    override val eq: Any? get() = if (hasRpcEq && peqFilters.isNotEmpty()) Triple(peqCurrent, peqFilters.keys.toList(), peqEnabled) else null

    private fun request(service: Int, method: Int, body: ByteArray = byteArrayOf()): BragiRpc {
        val t = tag
        tag = (tag + 1) and 0xFF
        return BragiRpc(SkullcandyRpc.REQUEST, service, method, t, body)
    }

    private fun subscribe(service: Int, method: Int): BragiRpc = request(service, method, BragiProto.bool(1, true))

    override val groups: List<SettingGroup> get() = buildList {
        val mode = active
        val noise = ArrayList<Setting>()
        if (mode == 2) {
            ancGain?.let { g -> ancGainMax?.takeIf { it > 0 }?.let { noise += Setting.Level(ANC_LEVEL, g.coerceIn(0, it), 0, it) } }
            ancPreset?.let { p -> ancPresetMax?.takeIf { it > 0 }?.let { noise += Setting.Level(ANC_PROFILE, p.coerceIn(0, it), 0, it) } }
        }
        if (mode == 3) {
            atGain?.let { g -> atGainMax?.takeIf { it > 0 }?.let { noise += Setting.Level(TRANSPARENCY_LEVEL, g.coerceIn(0, it), 0, it) } }
            atPreset?.let { p -> atPresetMax?.takeIf { it > 0 }?.let { noise += Setting.Level(TRANSPARENCY_PROFILE, p.coerceIn(0, it), 0, it) } }
        }
        if (noise.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, noise))
        latency?.let { add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle(GAMING_MODE, it == LATENCY_PERMANENT || it == LATENCY_TEMPORARY)))) }
        val side = ArrayList<Setting>()
        sidetoneOn?.let { side += Setting.Toggle(SIDETONE, it) }
        sidetoneVolume?.let { v -> sidetoneMax?.takeIf { it > 0 }?.let { side += Setting.Level(SIDETONE_LEVEL, v.coerceIn(0, it), 0, it) } }
        if (side.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, side))
        val wear = ArrayList<Setting>()
        wearPause?.let { wear += Setting.Toggle(WEAR_DETECT, it) }
        wearTransparency?.let { wear += Setting.Toggle(WEAR_TRANSPARENCY, it) }
        if (wear.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, wear))
        val crusher = ArrayList<Setting>()
        crusherActive?.let { crusher += Setting.Toggle(CRUSHER_ACTIVE, it) }
        crusherIntensity?.let { i -> crusherMax?.takeIf { it > 0 }?.let { crusher += Setting.Level(CRUSHER_INTENSITY, i.coerceIn(0, it), 0, it, enabled = !crusherReadOnly) } }
        crusherMode?.let { m -> crusherModes?.takeIf { it > 0 }?.let { crusher += Setting.Choice(CRUSHER_MODE, CRUSHER_MODE_NAMES.getOrNull(m), CRUSHER_MODE_NAMES.take(it)) } }
        if (crusher.isNotEmpty()) add(SettingGroup("sound", SettingTab.SOUND, crusher))
        if (spatialAvailable == true) spatialMode?.let { add(SettingGroup("sound", SettingTab.SOUND, listOf(Setting.Choice(SPATIAL_MODE, SPATIAL_NAMES.getOrNull(it - 1), SPATIAL_NAMES)))) }
        if (hasButtons) {
            val rows = ArrayList<Setting>()
            for ((k, v) in buttonValues) {
                val (config, _, gesture) = k
                val short = RpcButtons.GESTURES.firstOrNull { it.second == gesture }?.first ?: continue
                val key = if (config.equals("left", true) || config.equals("right", true)) "sk_rbutton_${config.lowercase()}_$short" else "sk_button_$short"
                rows += Setting.Choice(key, RpcButtons.optionOf(v), offeredActions())
            }
            if (rows.isNotEmpty()) add(SettingGroup("sk_buttons", SettingTab.CONTROLS, rows))
        }
        val prompts = ArrayList<Setting>()
        reducedPrompts?.let { prompts += Setting.Toggle(REDUCED_PROMPTS, it) }
        batteryPrompt?.let { prompts += Setting.Toggle(BATTERY_PROMPT, it) }
        if (locales.size > 1) prompts += Setting.Choice(LANGUAGE, locale, locales)
        if (prompts.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, prompts))
    }

    override fun start(): List<BragiRpc> = buildList {
        add(request(BosRpc.DEVICE, BosRpc.DEV_INFO, BragiProto.unsigned(SkullcandyCommand.GENDER_FIELD, 0uL)))
        add(request(BosRpc.DEVICE, BosRpc.BATT_INFO))
        if (hasAnc || hasTransparency) {
            add(request(BosRpc.HEARING, BosRpc.MODE_INFO))
            add(subscribe(BosRpc.HEARING, BosRpc.MODE_READ))
        }
        if (hasSidetone) {
            add(request(BosRpc.HEARING, BosRpc.SIDETONE_INFO))
            add(subscribe(BosRpc.HEARING, BosRpc.SIDETONE_READ))
        }
        if (hasLatency) add(request(BosRpc.LOW_LATENCY, 1))
        if (hasWear) add(request(BosRpc.INSERTION, BosRpc.TRIGGER_GET))
        if (hasCrusher) {
            add(request(BosRpc.CRUSHER, BosRpc.CRUSHER_CONFIG))
            add(subscribe(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_GET))
        }
        if (hasSpatial) add(subscribe(BosRpc.SPATIAL, BosRpc.SPATIAL_INFO))
        if (hasButtons) add(request(BosRpc.BUTTONS, BosRpc.BUTTON_INFO_V2))
        add(request(BosRpc.SOUNDS, 1))
        add(request(BosRpc.LANGUAGE, BosRpc.LANG_INFO))
        if (hasRpcEq) {
            add(request(BosRpc.HEARING, BosRpc.PEQ_INFO))
            add(subscribe(BosRpc.HEARING, BosRpc.PEQ_READ_STATE))
            add(request(BosRpc.HEARING, BosRpc.PEQ_READ_CONF))
        }
    }

    override fun poll(): List<BragiRpc> = listOf(request(BosRpc.DEVICE, BosRpc.BATT_INFO))

    override fun allowed(message: BragiRpc): Boolean = BosRpc.isAllowed(message)

    override fun bytes(message: BragiRpc): ByteArray = message.frame().bytes()

    override fun describe(message: BragiRpc): String = "rpc ${message.service}.${message.method} tag ${message.tag}"

    override fun answers(sent: BragiRpc, got: Any): Boolean = (got as? BragiRpc)?.matches(sent) == true

    override fun takeReadBacks(): List<BragiRpc> = readBacks.also { readBacks = emptyList() }

    override fun takeOutbox(): List<BragiRpc> = outbox.toList().also { outbox.clear() }

    /** The actions the model's manifest lists and the earbuds' own list of actions holds, by their option names. */
    private fun offeredActions(): List<String> = RpcButtons.mappable(product).filter { o -> RpcButtons.ACTIONS[o]?.let { it in actionNames } == true }

    private fun askButtons() {
        val key = RpcButtons.keyOf(product)
        if (controls.none { it.equals(key, true) }) return
        for (config in configs) for ((_, gesture) in RpcButtons.gesturesOf(product)) {
            if (gestureNames.none { it.equals(gesture, true) }) continue
            val k = Triple(config, key, gesture)
            if (!asked.add(k)) continue
            outbox += request(BosRpc.BUTTONS, BosRpc.BUTTON_GET_ACTION, BragiProto.text(1, config) + BragiProto.unsigned(2, 1uL) + BragiProto.text(3, "$key.$gesture"))
        }
    }

    private fun buttonInfo(m: List<BragiField>): Boolean {
        for (info in m.fields(1)) {
            val i = info.message() ?: continue
            i.field(1)?.message()?.field(1)?.text()?.let { if (it !in configs) configs += it }
            i.field(2)?.message()?.field(1)?.text()?.let { if (it !in controls) controls += it }
            i.field(3)?.message()?.field(1)?.text()?.let { if (it !in gestureNames) gestureNames += it }
            i.field(5)?.message()?.field(1)?.text()?.let { actionNames += it }
        }
        askButtons()
        return true
    }

    private fun buttonAction(m: List<BragiField>): Boolean {
        val config = m.field(1)?.text() ?: return false
        val ekg = m.field(3)?.text() ?: return false
        val dot = ekg.indexOf('.')
        if (dot < 0) return false
        val first = m.fields(4).firstNotNullOfOrNull { it.message()?.field(1)?.text() }
        buttonValues[Triple(config, ekg.substring(0, dot), ekg.substring(dot + 1))] = first
        return true
    }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val answers = mutableListOf<Any>()
        for (frame in parser.feed(bytes)) {
            val rpc = frame.rpc() ?: continue
            answers += rpc
            guarded { changed = apply(rpc) || changed }
        }
        return Fed(changed, answers)
    }

    private fun name(service: Int, method: Int): String = "$service.$method"

    private fun apply(r: BragiRpc): Boolean {
        if (r.type == SkullcandyRpc.STATUS) {
            val code = r.body.firstOrNull()?.u() ?: return false
            if (code != SkullcandyRpc.SUCCESS && code != STATUS_REPLACED) failures[r.service to r.method] = code
            return false
        }
        if (r.type != SkullcandyRpc.RESPONSE) return false
        failures.remove(r.service to r.method)
        // the device info carries serial and identifier fields: keep only the flag and a name
        if (!(r.service == BosRpc.DEVICE && r.method == BosRpc.DEV_INFO)) replies[name(r.service, r.method)] = r.body
        val m = BragiProto.parse(r.body) ?: return false
        return when (r.service to r.method) {
            BosRpc.DEVICE to BosRpc.DEV_INFO -> {
                authenticatedAccess = m.field(SkullcandyCommand.AUTH_REQUIRED_FIELD)?.bool()
                replies["2.1"] = listOfNotNull(m.field(4)?.data?.let { BragiProto.bytes(4, it) }, m.field(5)?.data?.let { BragiProto.bytes(5, it) }).fold(byteArrayOf()) { a, b -> a + b }
                false
            }
            BosRpc.DEVICE to BosRpc.BATT_INFO -> battery(m)
            BosRpc.HEARING to BosRpc.MODE_INFO -> {
                m.field(1)?.message()?.let { ancPresetMax = it.field(1)?.int(); ancGainMax = it.field(2)?.int() }
                m.field(2)?.message()?.let { atPresetMax = it.field(1)?.int(); atGainMax = it.field(2)?.int() }
                true
            }
            BosRpc.HEARING to BosRpc.MODE_READ -> mode(m)
            BosRpc.HEARING to BosRpc.SIDETONE_INFO -> { sidetoneMax = m.field(1)?.int(); true }
            BosRpc.HEARING to BosRpc.SIDETONE_READ -> { sidetoneOn = m.field(1)?.bool() ?: false; sidetoneVolume = m.field(2)?.int() ?: 0; true }
            BosRpc.LOW_LATENCY to 1 -> { latency = m.field(1)?.int() ?: 0; true }
            BosRpc.INSERTION to BosRpc.TRIGGER_GET, BosRpc.INSERTION to BosRpc.TRIGGER_SET -> {
                wearPause = m.field(1)?.bool() ?: false
                wearTransparency = m.field(2)?.bool() ?: false
                true
            }
            BosRpc.CRUSHER to BosRpc.CRUSHER_CONFIG -> {
                crusherMax = m.field(1)?.int()
                crusherReadOnly = m.field(2)?.bool() ?: false
                crusherModes = m.field(3)?.int()
                true
            }
            BosRpc.CRUSHER to BosRpc.CRUSHER_STATE_GET, BosRpc.CRUSHER to BosRpc.CRUSHER_STATE_SET -> {
                crusherActive = m.field(1)?.bool() ?: false
                crusherIntensity = m.field(2)?.int() ?: 0
                crusherMode = m.field(3)?.int()
                true
            }
            BosRpc.SPATIAL to BosRpc.SPATIAL_INFO -> { spatialAvailable = m.field(1)?.bool() ?: false; spatialMode = m.field(2)?.int(); true }
            BosRpc.SOUNDS to 1, BosRpc.SOUNDS to 2 -> { reducedPrompts = m.field(1)?.bool() ?: false; batteryPrompt = m.field(2)?.bool(); true }
            BosRpc.LANGUAGE to BosRpc.LANG_INFO -> {
                locale = m.field(1)?.message()?.field(1)?.text()
                locales = m.fields(2).mapNotNull { it.message()?.field(1)?.text() }
                true
            }
            BosRpc.BUTTONS to BosRpc.BUTTON_INFO_V2 -> buttonInfo(m)
            BosRpc.BUTTONS to BosRpc.BUTTON_GET_ACTION -> buttonAction(m)
            BosRpc.HEARING to BosRpc.PEQ_INFO -> { peqMinGain = m.field(4)?.float(); peqMaxGain = m.field(5)?.float(); true }
            BosRpc.HEARING to BosRpc.PEQ_READ_STATE -> { peqEnabled = m.field(1)?.bool() ?: false; peqCurrent = m.field(2)?.int(); true }
            BosRpc.HEARING to BosRpc.PEQ_READ_CONF -> {
                val id = m.field(1)?.int() ?: 0
                val bands = m.fields(4).mapNotNull { b ->
                    val bm = b.message() ?: return@mapNotNull null
                    Triple(bm.field(1)?.int() ?: 0, bm.field(2)?.float() ?: 0f, bm.field(3)?.float() ?: 0f)
                }
                peqFilters[id] = PeqFilter(id, m.field(2)?.bool() ?: false, m.field(3)?.text().orEmpty(), bands)
                true
            }
            else -> false
        }
    }

    private fun battery(m: List<BragiField>): Boolean {
        val next = current.values.toMutableMap()
        for (b in m.fields(1)) {
            val e = b.message() ?: continue
            val level = e.field(2)?.int() ?: continue
            if (level !in 0..100) continue
            when (e.field(1)?.int()) {
                1 -> next[Command.BATTERY_RIGHT] = Value.Number(level)
                2 -> next[Command.BATTERY_LEFT] = Value.Number(level)
                else -> next[Command.BATTERY_LEFT] = Value.Number(level)
            }
        }
        return replace(next)
    }

    private fun mode(m: List<BragiField>): Boolean {
        active = m.field(1)?.int()
        m.field(2)?.message()?.let { ancPreset = it.field(1)?.int() ?: 0; ancGain = it.field(2)?.int() ?: 0 }
        m.field(3)?.message()?.let { atPreset = it.field(1)?.int() ?: 0; atGain = it.field(2)?.int() ?: 0 }
        val label = when (active) { 1 -> "off"; 2 -> "anc"; 3 -> "transparency"; else -> null }
        val next = current.values.toMutableMap()
        if (label != null) next[Command.ANC_MODE] = Value.Number(Command.ANC_MODE.labels.entries.first { it.value == label }.key)
        replace(next)
        return true
    }

    private fun replace(next: Map<Command, Value>): Boolean {
        if (next == current.values) return false
        current = current.copy(values = next)
        return true
    }

    private fun configure(mode: Int, preset: Int? = null, gain: Int? = null): BragiRpc {
        var body = BragiProto.unsigned(1, mode.toULong()) + BragiProto.bool(2, true)
        if (preset != null) body += BragiProto.unsigned(3, preset.toULong())
        if (gain != null) body += BragiProto.unsigned(4, gain.toULong())
        return request(BosRpc.HEARING, BosRpc.MODE_CONFIGURE, body)
    }

    private fun readMode(): BragiRpc = request(BosRpc.HEARING, BosRpc.MODE_READ, BragiProto.bool(1, false))

    override fun set(command: Command, value: Int): List<BragiRpc>? {
        if (command != Command.ANC_MODE || !(hasAnc || hasTransparency)) return null
        val mode = when (Command.ANC_MODE.labels[value]) {
            "off" -> 1
            "anc" -> if (hasAnc) 2 else return null
            "transparency" -> if (hasTransparency) 3 else return null
            else -> return null
        }
        active = mode
        readBacks = listOf(readMode())
        return listOf(configure(mode))
    }

    override fun change(change: SettingChange): List<BragiRpc>? {
        when (change) {
            is SettingChange.Toggle -> {
                val on = change.on
                return when (change.key) {
                    GAMING_MODE -> if (!hasLatency) null else {
                        latency = if (on) LATENCY_PERMANENT else LATENCY_NORMAL
                        readBacks = listOf(request(BosRpc.LOW_LATENCY, 1))
                        listOf(request(BosRpc.LOW_LATENCY, 2, BragiProto.unsigned(1, latency!!.toULong())))
                    }
                    SIDETONE -> if (!hasSidetone) null else {
                        sidetoneOn = on
                        readBacks = listOf(request(BosRpc.HEARING, BosRpc.SIDETONE_READ, BragiProto.bool(1, false)))
                        listOf(request(BosRpc.HEARING, BosRpc.SIDETONE_CONFIGURE, BragiProto.bool(1, on)))
                    }
                    WEAR_DETECT, WEAR_TRANSPARENCY -> if (!hasWear) null else {
                        if (change.key == WEAR_DETECT) wearPause = on else wearTransparency = on
                        readBacks = listOf(request(BosRpc.INSERTION, BosRpc.TRIGGER_GET))
                        val body = if (change.key == WEAR_DETECT) BragiProto.bool(1, on) else BragiProto.bool(2, on)
                        listOf(request(BosRpc.INSERTION, BosRpc.TRIGGER_SET, body))
                    }
                    CRUSHER_ACTIVE -> if (!hasCrusher) null else {
                        crusherActive = on
                        readBacks = listOf(request(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_GET, BragiProto.bool(1, false)))
                        listOf(request(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_SET, BragiProto.bool(1, on)))
                    }
                    REDUCED_PROMPTS, BATTERY_PROMPT -> {
                        if (change.key == REDUCED_PROMPTS) reducedPrompts = on else batteryPrompt = on
                        readBacks = listOf(request(BosRpc.SOUNDS, 1))
                        listOf(request(BosRpc.SOUNDS, 2, BragiProto.bool(if (change.key == REDUCED_PROMPTS) 1 else 2, on)))
                    }
                    else -> null
                }
            }
            is SettingChange.Level -> {
                val v = change.value
                return when (change.key) {
                    ANC_LEVEL -> if (active != 2) null else { ancGain = v; readBacks = listOf(readMode()); listOf(configure(2, gain = v)) }
                    TRANSPARENCY_LEVEL -> if (active != 3) null else { atGain = v; readBacks = listOf(readMode()); listOf(configure(3, gain = v)) }
                    ANC_PROFILE -> if (active != 2) null else { ancPreset = v; readBacks = listOf(readMode()); listOf(configure(2, preset = v)) }
                    TRANSPARENCY_PROFILE -> if (active != 3) null else { atPreset = v; readBacks = listOf(readMode()); listOf(configure(3, preset = v)) }
                    SIDETONE_LEVEL -> if (!hasSidetone) null else {
                        sidetoneVolume = v
                        readBacks = listOf(request(BosRpc.HEARING, BosRpc.SIDETONE_READ, BragiProto.bool(1, false)))
                        listOf(request(BosRpc.HEARING, BosRpc.SIDETONE_CONFIGURE, BragiProto.unsigned(2, v.toULong())))
                    }
                    CRUSHER_INTENSITY -> if (!hasCrusher || crusherReadOnly) null else {
                        crusherIntensity = v
                        readBacks = listOf(request(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_GET, BragiProto.bool(1, false)))
                        listOf(request(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_SET, BragiProto.unsigned(2, v.toULong())))
                    }
                    else -> null
                }
            }
            is SettingChange.Choice -> {
                val option = change.option
                if (hasButtons && (change.key.startsWith("sk_rbutton_") || change.key.startsWith("sk_button_"))) return buttonChange(change.key, option)
                return when (change.key) {
                    CRUSHER_MODE -> {
                        val mode = CRUSHER_MODE_NAMES.indexOf(option).takeIf { it >= 0 && hasCrusher } ?: return null
                        crusherMode = mode
                        readBacks = listOf(request(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_GET, BragiProto.bool(1, false)))
                        listOf(request(BosRpc.CRUSHER, BosRpc.CRUSHER_STATE_SET, BragiProto.unsigned(3, mode.toULong())))
                    }
                    SPATIAL_MODE -> {
                        val mode = SPATIAL_NAMES.indexOf(option).takeIf { it >= 0 && hasSpatial }?.plus(1) ?: return null
                        spatialMode = mode
                        listOf(request(BosRpc.SPATIAL, BosRpc.SPATIAL_SET, BragiProto.unsigned(1, mode.toULong())))
                    }
                    LANGUAGE -> {
                        if (option !in locales) return null
                        locale = option
                        readBacks = listOf(request(BosRpc.LANGUAGE, BosRpc.LANG_INFO))
                        listOf(request(BosRpc.LANGUAGE, BosRpc.LANG_CONFIGURE, BragiProto.bytes(1, BragiProto.text(1, option))))
                    }
                    else -> null
                }
            }
            else -> return null
        }
    }

    private fun buttonChange(key: String, option: String): List<BragiRpc>? {
        val action = RpcButtons.ACTIONS[option] ?: return null
        if (option !in offeredActions()) return null
        val parts = key.removePrefix("sk_rbutton_").removePrefix("sk_button_").split('_')
        val (side, short) = if (key.startsWith("sk_rbutton_")) parts.getOrNull(0) to parts.getOrNull(1) else null to parts.getOrNull(0)
        val gesture = RpcButtons.GESTURES.firstOrNull { it.first == short }?.second ?: return null
        val k = buttonValues.keys.firstOrNull { it.third == gesture && (side == null || it.first.equals(side, true)) } ?: return null
        buttonValues[k] = action
        readBacks = listOf(request(BosRpc.BUTTONS, BosRpc.BUTTON_GET_ACTION, BragiProto.text(1, k.first) + BragiProto.unsigned(2, 1uL) + BragiProto.text(3, "${k.second}.${k.third}")))
        return listOf(request(BosRpc.BUTTONS, BosRpc.BUTTON_SET_ACTION, BragiProto.text(1, k.first) + BragiProto.text(2, "${k.second}.${k.third}") + BragiProto.bytes(3, BragiProto.text(1, action))))
    }

    /** The write that makes the preset [name] the active filter, as the RPC bytes of one request. */
    public fun eqPreset(name: String): List<ByteArray>? {
        val filter = peqFilters.values.firstOrNull { !it.editable && it.name == name } ?: return null
        peqCurrent = filter.id
        return listOf(request(BosRpc.HEARING, BosRpc.PEQ_CONFIGURE, BragiProto.unsigned(1, filter.id.toULong()) + BragiProto.bool(2, true) + BragiProto.bool(3, true)).bytes())
    }

    /** The write of the editable curve with [gains] in whole dB around the middle of the range, keeping each band's frequency and Q, and making it the active filter. */
    public fun eqCurve(gains: List<Int>): List<ByteArray>? {
        val filter = peqFilters.values.firstOrNull { it.editable } ?: return null
        if (gains.size != filter.bands.size) return null
        val lo = peqMinGain ?: 0f
        val hi = peqMaxGain ?: 20f
        var body = BragiProto.unsigned(1, filter.id.toULong()) + BragiProto.bool(2, true) + BragiProto.bool(3, true)
        val updated = filter.bands.mapIndexed { i, (hz, _, q) ->
            val g = (gains[i] + eqCentre).coerceIn(lo, hi)
            body += BragiProto.bytes(4, BragiProto.unsigned(1, hz.toULong()) + BragiProto.float(2, g) + BragiProto.float(3, q))
            Triple(hz, g, q)
        }
        peqFilters[filter.id] = PeqFilter(filter.id, true, filter.name, updated)
        peqCurrent = filter.id
        return listOf(request(BosRpc.HEARING, BosRpc.PEQ_CONFIGURE, body).bytes())
    }

    /** The RPC request of one of the equaliser's payloads. */
    public fun messageOf(bytes: ByteArray): BragiRpc? = BragiRpc.of(bytes)

    public companion object {
        public const val ANC_LEVEL: String = "sk_anc_level"
        public const val TRANSPARENCY_LEVEL: String = "sk_transparency_level"
        public const val ANC_PROFILE: String = "sk_anc_profile"
        public const val TRANSPARENCY_PROFILE: String = "sk_transparency_profile"
        public const val GAMING_MODE: String = "sk_gaming_mode"
        public const val SIDETONE: String = "sk_sidetone"
        public const val SIDETONE_LEVEL: String = "sk_sidetone_level"
        public const val WEAR_DETECT: String = "sk_wear_detect"
        public const val WEAR_TRANSPARENCY: String = "sk_wear_transparency"
        public const val CRUSHER_ACTIVE: String = "sk_crusher_active"
        public const val CRUSHER_INTENSITY: String = "sk_crusher_intensity"
        public const val CRUSHER_MODE: String = "sk_crusher_mode"
        public const val SPATIAL_MODE: String = "sk_spatial"
        public const val REDUCED_PROMPTS: String = "sk_reduced_prompts"
        public const val BATTERY_PROMPT: String = "sk_battery_prompt"
        public const val LANGUAGE: String = "sk_language"

        /** The crusher modes in the schema's order (0 high, 1 medium, 2 low). */
        public val CRUSHER_MODE_NAMES: List<String> = listOf("high", "medium", "low")

        /** The spatial audio modes in the schema's order from 1 (off, fixed to the head, fixed to the room). */
        public val SPATIAL_NAMES: List<String> = listOf("off", "fixed_to_head", "fixed_to_room")

        private const val LATENCY_NORMAL = 1
        private const val LATENCY_PERMANENT = 3
        private const val LATENCY_TEMPORARY = 4
        private const val STATUS_DENIED = 41
        private const val STATUS_REPLACED = 100
    }
}

/** The Sound tab's view of the Crusher 1080 ANC's own parametric equaliser ([SkullcandyRpcTalk]). */
public class SkullcandyRpcBandEq(private val talk: SkullcandyRpcTalk) : BandEq {
    override val eqBandsHz: List<Int> = talk.eqBandsHz
    override val eqRangeDb: Int = talk.eqRangeDb
    override val eqPresetNames: List<String> = talk.eqPresetNames

    override fun eqPresetFrames(name: String): List<ByteArray>? = talk.eqPreset(name)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = talk.eqCurve(gains)

    override val eqReport: BandEqReport?
        get() = talk.eqPresetName?.let { BandEqReport(it, null) } ?: talk.eqGains?.let { BandEqReport(null, it) }
}


/**
 * The button actions of the RPC models (docs/skullcandy/PROTOCOL.md section 15): the app's bridge converts each action to an SDK command (`n9.Z`) and calls `setCommands(side, key, gesture, [command])`, which the RPC transport sends as
 * `set_action(config, "<key>.<gesture>", [action])`. The key of each model's button, the gestures and the actions are the app's own; the strings of the actions are the SDK's, checked against the list the earbuds report.
 */
public object RpcButtons {
    /** The gestures the app maps for these models, as the SDK names them (`n9.c0`): click, click2, click3 and hold (`HOLD_UP`). */
    public val GESTURES: List<Pair<String, String>> = listOf("single" to "click", "double" to "click2", "triple" to "click3", "hold" to "hold_up")

    /** The actions by option name and the SDK's string for each command. *Inference:* the strings follow the SDK's naming (the command's name in lower case, its first underscore a dot, and the three it names explicitly); each is offered only when the earbuds list it. */
    public val ACTIONS: Map<String, String> = linkedMapOf(
        "play_pause" to "media.play_pause", "stay_aware" to "ancat.toggle", "track_forward" to "media.next", "track_back" to "media.prev", "volume_up" to "volume.up",
        "volume_down" to "volume.down", "spotify" to "spotify.onetouch", "take_photo" to "remote.hid_volume_down", "voice_assistant" to "assistant.toggle",
        "none" to "device.no_action", "spatial" to "spatial.toggle",
    )

    /** The option of an action string the earbuds report, or null for one PairFree does not name. */
    public fun optionOf(action: String?): String? = ACTIONS.entries.firstOrNull { it.value == action }?.key

    /** The model's mappable actions from the manifest (`mappableButtonActions`), by option name. */
    public fun mappable(product: SkullcandyProduct): List<String> =
        if (product.sdk == "CHP300") listOf("play_pause", "voice_assistant", "spatial", "none")
        else listOf("play_pause", "stay_aware", "track_forward", "track_back", "volume_up", "volume_down", "spotify", "take_photo", "voice_assistant", "none")

    /** The key of the button the app maps: the power button of the earbuds, the joystick of the Crusher 1080 ANC (the SDK's `POWER` and `CENTR`). */
    public fun keyOf(product: SkullcandyProduct): String = if (product.sdk == "CHP300") "centr" else "power"

    /** The gestures the manifest's defaults list for the model: all four on the earbuds, double and triple press and the hold on the Crusher's joystick. */
    public fun gesturesOf(product: SkullcandyProduct): List<Pair<String, String>> = if (product.sdk == "CHP300") GESTURES.filter { it.first != "single" } else GESTURES
}
