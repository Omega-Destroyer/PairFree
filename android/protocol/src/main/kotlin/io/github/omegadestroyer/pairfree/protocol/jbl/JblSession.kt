package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel.Mode

/**
 * One connection to JBL headphones or earbuds over Harman's commands (docs/jbl/PROTOCOL.md), shown on
 * PairFree's screens. It reads the product id, the firmware, the battery and the settings JBL's app
 * shows for the model ([JblModel]), each the way the model's control class in JBL's app reads it. The
 * battery, firmware and noise control (where the model has both noise cancelling and Ambient Aware) go
 * to [state], everything else to [groups].
 *
 * A change shows at once; the earbuds acknowledge it, and [takeReadBacks] gives the reads that confirm
 * it, which the connection sends a little later, so the screen ends up showing what the earbuds hold.
 */
public class JblSession(initial: JblModel) {
    public var model: JblModel = initial
        private set

    /** What the shared screens show. */
    public var state: EarbudState = EarbudState()
        private set

    /** The product id the device reported. */
    public var productId: Int? = null
        private set

    public var firmware: String? = null
        private set

    /** The Airoha chip's name an Airoha model reports, for the device report. */
    public var chipName: String? = null
        private set

    /** The model name an Airoha model reports (on JBL's, its product id), for the device report. */
    public var modelName: String? = null
        private set

    /**
     * The last answer to each of PairFree's reads, for the device report, by command (and status type
     * or feature id): the device info with its name and address blanked, and no feature but the ones
     * PairFree asks for, since the earbuds can send their name and address unasked.
     */
    public val replies: Map<String, ByteArray> get() = _replies
    private val _replies = linkedMapOf<String, ByteArray>()

    private var noiseKnown = false
    private var mode: Mode? = null       // null while off
    private var ancOn: Boolean? = null   // the old commands report noise cancelling and ambient sound apart
    private var ambient: Int? = null
    private var autoOffOn: Boolean? = null
    private var autoOffMinutes: Int? = null
    private var autoPlayPause: Boolean? = null
    private val outbox = mutableListOf<JblFrame>()
    private val readBacks = mutableListOf<JblFrame>()

    // Protocol 4: each mode's status and level, and whether ambient sound control is on.
    private val modeOn = mutableMapOf<Mode, Boolean>()
    private val modeLevel = mutableMapOf<Mode, Int>()
    private var ascOn: Boolean? = null

    // The settings beyond noise control, auto power-off and auto play/pause.
    private var extras = JblExtras(initial)

    /** The EQ the earbuds hold, once they report it (docs/jbl/PROTOCOL.md section 11); a write shows here at once. */
    public var eq: JblEqCurve? = null
        private set

    // The design EQ the earbuds report, which the calibration is worked out from.
    private var designEq: JblEqCurve? = null

    // The older BES classes' EQ transfer: the parts still to come and those in, and the raw frames of the last write.
    private var partsDue = 0
    private var parts = ByteArray(0)
    private var rawOut: Set<JblRaw> = emptySet()
    private var eqOut: List<JblFrame> = emptyList()

    // Airoha's PEQ: the write under way, if any.
    private var airohaEq: JblAirohaEq? = null

    // Airoha's gesture key map (docs/jbl/PROTOCOL.md section 10.1): the read or write under way, the read still to make,
    // and what the last read found: each gesture's action, the chip's variant and the key id a write carries.
    private var gestureFlow: JblAirohaGesture? = null
    private var gestureReadDue = false
    private val airohaGestures = mutableMapOf<Int, Int>()
    private var gestureVariant: String? = null
    private var gestureKeyId: Int? = null

    // Realtek's segment EQ: the reply parts in so far.
    private var segments = ByteArray(0)

    /** The MTU the LE link agreed on; Realtek's segment EQ is cut to fit it, as JBL's app does. */
    public var mtu: Int = DEFAULT_MTU

    // Airoha models: the chip family they report, and whether the earbud PairFree talks to (the agent) is the right one.
    private var chip: RaceChip? = null
    private var agentRight = false

    /** The reads on opening the channel. */
    public fun start(): List<JblFrame> = buildList {
        if (model.family == JblModel.Family.AIROHA) {
            addAll(raceStart())
            return@buildList
        }
        if (model.family == JblModel.Family.P4) {
            // JBL's app tells the earbuds the host is a phone (host type 1) before it reads them.
            add(JblP4Packet.set(JblP4.FEATURE_HOST_TYPE to byteArrayOf(1)))
            add(JblP4Packet.get(p4Features()))
            addAll(eqReads())
            return@buildList
        }
        when (model.family) {
            JblModel.Family.DIRECT -> {
                add(JblPacket.of(JblCommand.DEVICE_INFO))
                // The Tune 720BT ignores reads of single status types but answers this one (FajkPes).
                add(JblPacket.of(JblCommand.STATUS, JblStatus.ALL))
            }
            JblModel.Family.FEATURE -> add(JblPacket.featureGet(featureIds()))
            JblModel.Family.P4, JblModel.Family.AIROHA -> Unit
        }
        add(batteryRead())
        addAll(noiseReads())
        addAll(extras.reads())
        addAll(eqReads())
    }

    /**
     * The EQ reads JBL's app sends on connecting: the design EQ where the class reads it, then the EQ in use. On protocol
     * 4 the query for the EQ in use alone: the design EQ JBL's app reads there is raw data it never uses.
     */
    private fun eqReads(): List<JblFrame> {
        val spec = model.eq?.takeIf { it.written } ?: return emptyList()
        if (spec.path == JblEqSpec.Path.P4) return listOf(JblEq.p4Query(JblEqWire.CURRENT))
        if (spec.path == JblEqSpec.Path.AIROHA) return emptyList()
        if (spec.path == JblEqSpec.Path.SEGMENT) {
            return listOfNotNull(JblRealtekEq.read(JblEqWire.DESIGN).takeIf { spec.readsDesign }, JblRealtekEq.read(JblEqWire.CURRENT))
        }
        // The older BES classes: the design EQ alone, for the calibration (their reply names no preset).
        if (!spec.reports) return listOfNotNull(JblEq.read41(JblEq41.DESIGN).takeIf { spec.readsDesign })
        return listOfNotNull(JblEq.read(JblEqWire.DESIGN).takeIf { spec.readsDesign }, JblEq.read(JblEqWire.CURRENT))
    }

    /**
     * The write that puts [preset] on the earbuds, or with none a curve of the user's own from [gains] (JBL's ten bands,
     * in dB). The calibration is worked out from the design EQ as JBL's app does on its BES models ([JblCalibration]);
     * -1 when the earbuds have not reported their design EQ, as JBL's app sends then (always on protocol 4, where none is
     * reported in a form it keeps). Realtek's segment EQ is worked out on top of the design EQ, so without it nothing is
     * sent, as in JBL's app. Null on a model whose EQ PairFree does not write, or when there is nothing to send.
     */
    public fun eqWrite(preset: JblEqPreset?, gains: List<Float>): List<JblFrame>? = eqFrames(preset, gains)?.also { eqOut = it }

    /** The frames of the last [eqWrite], once: the connection sends them as they are, since the 41 transfer's are not all frames. */
    public fun takeEqWrite(): List<JblFrame> = eqOut.also { eqOut = emptyList() }

    private fun eqFrames(preset: JblEqPreset?, gains: List<Float>): List<JblFrame>? {
        val spec = model.eq?.takeIf { it.written } ?: return null
        val bands = preset?.bands ?: JblEq.custom(if (halvesCustom()) gains.map { it * HALF } else gains)
        val calibration = when {
            !spec.calibrate -> 0f
            else -> designEq?.let { JblCalibration.of(it, bands) } ?: NO_DESIGN_EQ
        }
        val curve = JblEqCurve(preset?.category ?: JblEqWire.CUSTOM, calibration, JblEq.SAMPLE_RATE, preset?.leftGain ?: 0f, preset?.rightGain ?: 0f, bands)
        if (spec.path == JblEqSpec.Path.AIROHA) {
            // A curve of the user's own goes as Harman's custom EQ (103), as JBL's app writes it to Airoha's SDK.
            val write = JblAirohaEq.of(
                chip ?: return null, model.form != Form.HEADPHONES, null, preset?.category ?: JblAirohaEq.CUSTOM, bands, curve.leftGain,
            ) ?: return null
            airohaEq = write
            eq = curve
            return write.start()
        }
        if (spec.path == JblEqSpec.Path.SEGMENT) {
            val frames = JblRealtekEq.write(curve, designEq ?: return null, mtu) ?: return null
            eq = curve
            readBacks += JblRealtekEq.read(JblEqWire.CURRENT)
            return frames
        }
        eq = curve
        when (spec.path) {
            // Every curve goes as the user's preset index, a preset's under category 0, as BesEQControl maps it.
            JblEqSpec.Path.CMD41 -> return JblEq.write41(curve, graphic = preset == null).also { rawOut = it.toSet() }
            // The Club One picks a preset by number (40); a curve of the user's own selects the user's slot, then goes as 41.
            JblEqSpec.Path.CLUB -> {
                if (preset != null) return listOf(JblEq.preset40(preset.category))
                return listOf(JblEq.preset40(JblEq41.USER_INDEX)) + JblEq.write41(curve, graphic = true).also { rawOut = it.toSet() }
            }
            else -> Unit
        }
        if (spec.path == JblEqSpec.Path.P4) {
            readBacks += JblEq.p4Query(JblEqWire.CURRENT)
            if (spec.airohaLib) return JblEq.p4WriteAiroha(curve, spec.airohaRates)?.frames(mtu)
            return listOf(JblEq.p4Write(curve))
        }
        readBacks += JblEq.read(JblEqWire.CURRENT)
        return listOf(JblEq.write(curve, spec.path == JblEqSpec.Path.A2_Q10))
    }

    /** True when JBL's app would halve a curve of the user's own: the model has the workaround and the firmware is not newer. */
    private fun halvesCustom(): Boolean {
        val until = model.eq?.halveCustomUntil ?: return false
        return !newer(firmware, until)
    }

    /** True when [version] is newer than [than], each three numbers joined by dots; false when either is unknown, as JBL's app compares them. */
    private fun newer(version: String?, than: String): Boolean {
        val a = version?.split('.')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size >= 3 } ?: return false
        val b = than.split('.').map { it.toInt() }
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    /**
     * The older BES classes' EQ header: how many parts follow. JBL's app reads the count as decimal digits of its hex form,
     * and acknowledges it once more than there are parts, and once more again (HandleParse, BleDevice.sendEqData).
     */
    private fun eqParts(p: ByteArray) {
        if (p.isEmpty()) return
        partsDue = p.u8(0).toString(16).toIntOrNull() ?: 0
        parts = ByteArray(0)
        repeat(partsDue + 2) { outbox += JblEq.ack41() }
    }

    /** A part of an EQ the older BES classes send: its index, then data. */
    private fun eqPart(data: ByteArray) {
        if (partsDue <= 0 || data.isEmpty()) return
        partsDue--
        parts += data.copyOfRange(1, data.size)
    }

    /** The earbuds' closing frame: the parts make the EQ; the design EQ (category 0) is kept for the calibration. */
    private fun eqPartsDone() {
        val (category, curve) = JblEq.parse41(parts) ?: return
        parts = ByteArray(0)
        _replies[key(JblEq41.REPLY, category)] = curve.toString().encodeToByteArray()
        if (category == JblEq41.DESIGN) designEq = curve.copy(category = JblEqWire.DESIGN)
    }

    /**
     * A part of a Realtek segment EQ reply (SegmentEQCmd.assembleEqPackage): the first starts the EQ afresh, each but the
     * last is acknowledged, and the last completes it.
     */
    private fun segmentPart(p: ByteArray) {
        val (index, count, data) = JblRealtekEq.part(p) ?: return
        if (index == 0) segments = ByteArray(0)
        segments += data
        if (index != count - 1) {
            outbox += JblRealtekEq.ack()
            return
        }
        val curve = JblRealtekEq.parse(segments) ?: return
        _replies[key(JblCommand.SEGMENT_EQ, curve.category)] = segments.copyOf()
        segments = ByteArray(0)
        eqIs(curve)
    }

    /** An EQ reply: the design EQ is kept for the calibration, PersoniFi's left alone, anything else is the EQ in use. */
    private fun eqReply(p: ByteArray) {
        val curve = JblEq.parse(p) ?: return
        _replies[key(JblCommand.EQ, curve.category)] = p.copyOf()
        eqIs(curve)
    }

    private fun eqIs(curve: JblEqCurve) {
        when (curve.category) {
            JblEqWire.DESIGN -> designEq = curve
            JblEqWire.PERSONIFI -> Unit
            else -> eq = curve
        }
    }

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<JblFrame> = buildList {
        if (model.family == JblModel.Family.AIROHA) {
            addAll(raceBatteries())
            return@buildList
        }
        if (model.family == JblModel.Family.P4) {
            add(JblP4Packet.get(p4Batteries()))
            return@buildList
        }
        add(batteryRead())
        if (model.family == JblModel.Family.FEATURE) add(JblPacket.featureGet(batteryFeatures()))
    }

    /** What the session wants sent next: the reads of a model learnt from its product id. */
    public fun takeOutbox(): List<JblFrame> = outbox.toList().also { outbox.clear() }

    /** The reads that confirm the last changes, to send once the earbuds have had time to apply them. */
    public fun takeReadBacks(): List<JblFrame> = readBacks.distinct().also { readBacks.clear() }

    /**
     * False for what PairFree never sends: factory reset, power off, firmware update, analytics, and on Airoha models
     * pairing, relays to the other earbud, NV key writes (but those of PairFree's own Airoha PEQ write) and every key event
     * but Smart Audio & Video's.
     */
    public fun allowed(frame: JblFrame): Boolean = when (frame) {
        is JblPacket -> frame.command !in JblCommand.NEVER_SEND
        is JblP4Packet -> frame.command !in JblP4.NEVER_SEND
        is JblP4Part -> frame.command !in JblP4.NEVER_SEND
        is RacePacket -> raceAllowed(frame) || airohaEq?.sent?.contains(frame) == true || gestureFlow?.sent?.contains(frame) == true
        // Raw bytes only as the parts of PairFree's own last EQ write.
        is JblRaw -> frame in rawOut
        else -> false
    }

    /** Applies one frame from the device. Returns true when [state] or [groups] changed. */
    public fun receive(frame: JblFrame): Boolean {
        val before = Triple(state, groups, eq)
        when (frame) {
            is JblP4Packet -> p4(frame)
            is JblPacket -> aa(frame)
            is RacePacket -> race(frame)
            is JblRaw -> eqPart(frame.data)
        }
        refresh()
        return Triple(state, groups, eq) != before
    }

    private fun aa(packet: JblPacket) {
        val p = packet.payload
        when (packet.command) {
            JblCommand.DEVICE_INFO_REPLY -> deviceInfo(p)
            JblCommand.STATUS_REPLY -> status(p)
            JblCommand.BATTERY -> battery(p)
            JblCommand.NOISE_MODES -> {
                noiseModes(p)
                extras.aa(packet.command, p)
            }
            JblCommand.FEATURE -> for ((id, v) in packet.features()) feature(id, v)
            JblCommand.EQ -> eqReply(p)
            JblCommand.SEGMENT_EQ -> segmentPart(p)
            JblEq41.REPLY -> eqParts(p)
            JblEq41.DEVICE_FIN -> if (p.isNotEmpty() && p.u8(0) == JblEq41.REPLY) eqPartsDone()
            else -> if (extras.reportsAa(packet.command)) {
                _replies[key(packet.command)] = p.copyOf()
                extras.aa(packet.command, p)
            }
        }
    }

    /**
     * A protocol 4 answer or notification: the product id (two bytes, low first), the firmware (its bytes
     * joined by dots), the batteries and the noise modes, each a status bit (7) and a level (bits 0 to 6).
     */
    private fun p4(packet: JblP4Packet) {
        if (packet.identifier != JblP4.IDENTIFIER) return
        var noise = false
        for ((id, v) in packet.features()) {
            if (id !in REPORTED_P4 || v.isEmpty()) continue
            _replies["p4." + id.toString(16).padStart(4, '0')] = v.copyOf()
            when (id) {
                JblP4.FEATURE_PRODUCT_ID -> if (v.size >= 2) productIdIs(JblP4Packet.u16(v, 0))
                JblP4.FEATURE_FIRMWARE -> firmware = v.joinToString(".") { (it.toInt() and 0xFF).toString() }
                JblP4.FEATURE_BATTERY_LEFT -> p4Battery(Command.BATTERY_LEFT, v)
                JblP4.FEATURE_BATTERY_RIGHT -> if (model.form != Form.HEADPHONES) p4Battery(Command.BATTERY_RIGHT, v)
                JblP4.FEATURE_BATTERY_CASE -> if (model.form != Form.HEADPHONES) p4Battery(Command.BATTERY_CASE, v)
                JblP4.FEATURE_AMBIENT_SOUND_CONTROL -> {
                    ascOn = v.u8(0) and STATUS_BIT != 0
                    noise = true
                }
                // Auto power-off (size 2): status at bit 15, the time in seconds in bits 0 to 14; 32767 is unknown.
                JblP4.FEATURE_AUTO_OFF -> if (v.size >= 2) {
                    val word = v.u8(0) or (v.u8(1) shl 8)
                    autoOffOn = word and P4_STATUS16 != 0
                    (word and P4_LEVEL16).takeIf { it != P4_LEVEL16 }?.let { autoOffMinutes = it / SECONDS_PER_MINUTE }
                }
                JblP4.FEATURE_AUTO_PLAY_PAUSE -> autoPlayPause = v.u8(0) and STATUS_BIT != 0
                JblP4.FEATURE_EQ_INFO -> JblEq.p4Parse(v)?.takeIf { it.category != JblEqWire.DESIGN && it.category != JblEqWire.PERSONIFI }?.let { curve ->
                    eq = if (curve.category == JblEqWire.CUSTOM && halvesCustom()) curve.copy(bands = curve.bands.map { it.copy(gainDb = it.gainDb * 2) }) else curve
                }
                in JblExtras.P4_IDS -> extras.p4(id, v)
                else -> P4_MODES[id]?.let { m ->
                    modeOn[m] = v.u8(0) and STATUS_BIT != 0
                    modeLevel[m] = v.u8(0) and LEVEL_BITS
                    noise = true
                }
            }
        }
        if (noise && (model.noise == JblModel.Noise.ASC || model.noise == JblModel.Noise.MODE4) && modeOn.isNotEmpty()) {
            // Off when ambient sound control is off (asc models) or every mode's status is 0.
            mode = if (ascOn == false) null else MODE_ORDER.firstOrNull { modeOn[it] == true }
            noiseKnown = true
        }
    }

    /** A protocol 4 battery: bit 7 charging, bits 0 to 6 the level. */
    private fun p4Battery(command: Command, v: ByteArray) {
        state = state.copy(values = state.values + (command to Value.Number(level(v.u8(0) and LEVEL_BITS))))
    }

    private fun deviceInfo(p: ByteArray) {
        // Name (16 bytes), product id (low first), colour, battery, address (6 bytes), firmware (3 bytes).
        _replies[key(JblCommand.DEVICE_INFO_REPLY)] = p.copyOf().also { r ->
            for (i in 0 until minOf(INFO_NAME_END, r.size)) r[i] = 0
            for (i in INFO_MAC until minOf(INFO_MAC + MAC_BYTES, r.size)) r[i] = 0
        }
        if (p.size >= INFO_PID + 2) productIdIs((p.u8(INFO_PID + 1) shl 8) or p.u8(INFO_PID))
        if (p.size >= INFO_FIRMWARE + 3) firmware = "${p.u8(INFO_FIRMWARE)}.${p.u8(INFO_FIRMWARE + 1)}.${p.u8(INFO_FIRMWARE + 2)}"
    }

    /** A status reply: the type, then its values (JBL's HandleParse.parseDevStatus and parseDeviceAllStatus). */
    private fun status(p: ByteArray) {
        if (p.size < 2) return
        val type = p.u8(0)
        _replies[key(JblCommand.STATUS_REPLY, type)] = p.copyOf()
        when (type) {
            JblStatus.ALL -> {
                if (model.noise == JblModel.Noise.OLD || model.noise == JblModel.Noise.CLUB) {
                    ancOn = boolean(p.u8(1))
                    if (p.size > 2) ambient = p.u8(2)
                    oldNoise()
                }
                // The one-byte auto power-off, unless FF, and on newer firmware the three-byte one after it.
                if (p.size > ALL_AUTO_OFF && p.u8(ALL_AUTO_OFF) != 0xFF) autoOffByte(p.u8(ALL_AUTO_OFF))
                if (p.size > ALL_AUTO_PLAY) autoPlayPause = p.u8(ALL_AUTO_PLAY) != 0
                if (p.size >= ALL_AUTO_OFF_2 + 3) {
                    val minutes = p.u8(ALL_AUTO_OFF_2 + 1) or (p.u8(ALL_AUTO_OFF_2 + 2) shl 8)
                    val on = boolean(p.u8(ALL_AUTO_OFF_2))
                    if (minutes != 0xFFFF && on != null) {
                        autoOffOn = on
                        autoOffMinutes = minutes
                    }
                }
            }
            JblStatus.ANC -> {
                ancOn = boolean(p.u8(1))
                oldNoise()
            }
            JblStatus.AMBIENT -> {
                ambient = p.u8(1)
                oldNoise()
            }
            JblStatus.AUTO_OFF -> if (p.size == 2) {
                autoOffByte(p.u8(1))
            } else if (p.size >= 4) {
                autoOffOn = p.u8(1) != 0
                autoOffMinutes = p.u8(2) or (p.u8(3) shl 8)
            }
            JblStatus.AUTO_PLAY_PAUSE -> autoPlayPause = p.u8(1) != 0
        }
    }

    /** One byte of auto power-off: bit 7 on, bits 0 to 6 the minutes. */
    private fun autoOffByte(b: Int) {
        autoOffOn = b and AUTO_OFF_ON != 0
        autoOffMinutes = b and AUTO_OFF_MINUTES
    }

    /** Noise control from the old commands: noise cancelling on wins, else the ambient mode. */
    private fun oldNoise() {
        val anc = ancOn ?: return
        mode = when {
            anc -> Mode.ANC
            ambient == JblAmbient.TALK_THRU -> Mode.TALK_THRU
            ambient == JblAmbient.AMBIENT_AWARE -> Mode.AMBIENT_AWARE
            else -> null
        }
        noiseKnown = true
    }

    /**
     * The battery: a type byte (01), then whether the secondary and the primary unit are charging, their
     * levels, whether the case is charging and its level. JBL's app shows the secondary as the left
     * earbud, the primary as the right one and, on headphones, the primary alone. Over 100 is unknown.
     */
    private fun battery(p: ByteArray) {
        if (p.size < BATTERY_CASE + 1 || p.u8(0) != 1) return
        _replies[key(JblCommand.BATTERY)] = p.copyOf()
        val secondary = level(p.u8(BATTERY_SECONDARY))
        val primary = level(p.u8(BATTERY_PRIMARY))
        val values = if (model.form == Form.HEADPHONES) {
            mapOf(Command.BATTERY_LEFT to Value.Number(primary ?: secondary))
        } else {
            mapOf(
                Command.BATTERY_LEFT to Value.Number(secondary),
                Command.BATTERY_RIGHT to Value.Number(primary),
                Command.BATTERY_CASE to Value.Number(level(p.u8(BATTERY_CASE))),
            )
        }
        state = state.copy(values = state.values + values)
    }

    /** Command 91's answer: the sub-command, then pairs of mode and value; the mode with a value is on. */
    private fun noiseModes(p: ByteArray) {
        val sub = if (p.isEmpty()) return else p.u8(0)
        // 12 answers a read; JblTray saw the earbuds send 10, the write's own form, too.
        if (sub != JblNoise.REPLY && sub != JblNoise.SET) return
        _replies[key(JblCommand.NOISE_MODES)] = p.copyOf()
        var on: Mode? = null
        var at = 1
        while (at + 1 < p.size) {
            if (p.u8(at + 1) != 0) on = modeOf(p.u8(at)) ?: on
            at += 2
        }
        mode = on
        noiseKnown = true
    }

    private fun feature(id: Int, v: ByteArray) {
        if (id !in REPORTED_FEATURES) return
        _replies[key(JblCommand.FEATURE, id)] = v.copyOf()
        when (id) {
            JblFeature.PRODUCT_ID -> if (v.size >= 2) productIdIs((v.u8(1) shl 8) or v.u8(0))
            JblFeature.FIRMWARE -> if (v.size >= 3) firmware = "${v.u8(0)}.${v.u8(1)}.${v.u8(2)}"
            JblFeature.AUTO_OFF -> if (v.size >= 3) {
                autoOffOn = v.u8(0) == 1
                autoOffMinutes = v.u8(1) or (v.u8(2) shl 8)
            }
            JblFeature.AUTO_PLAY_PAUSE -> if (v.isNotEmpty()) autoPlayPause = v.u8(0) == 1
            JblFeature.BATTERY_HEADBAND -> unitBattery(Command.BATTERY_LEFT, v)
            JblFeature.BATTERY_LEFT -> unitBattery(Command.BATTERY_LEFT, v)
            JblFeature.BATTERY_RIGHT -> unitBattery(Command.BATTERY_RIGHT, v)
            JblFeature.BATTERY_CASE -> unitBattery(Command.BATTERY_CASE, v)
            else -> extras.feature(id, v)
        }
    }

    /** A battery feature: whether it is charging, then its level. */
    private fun unitBattery(command: Command, v: ByteArray) {
        if (v.size < 2) return
        state = state.copy(values = state.values + (command to Value.Number(level(v.u8(1)))))
    }

    /** The product id names the model where two share a name; a model of another kind is read afresh. */
    private fun productIdIs(pid: Int) {
        productId = pid
        val known = JblModels.of(pid) ?: return
        if (known == model) return
        val reread = known.family != model.family || known.noise != model.noise
        val more = known.extras != model.extras || known.smartAudioVideo != model.smartAudioVideo || known.effects != model.effects
        model = known
        if (more) extras = JblExtras(known)
        if (reread) {
            outbox += start()
        } else if (more) {
            outbox += extras.reads()
            outbox += extras.raceReads()
            if (known.family == JblModel.Family.AIROHA) {
                if (known.autoOff != null) outbox += raceAutoOffRead()
                if (known.autoPlayPause) outbox += raceAutoPlayRead()
            }
            extras.p4Features().takeIf { it.isNotEmpty() }?.let { outbox += JblP4Packet.get(it) }
        }
    }

    /** Puts the firmware and, where the model has both noise cancelling and Ambient Aware, the noise mode where the shared screens read them. */
    private fun refresh() {
        val values = buildMap {
            firmware?.let { put(Command.FIRMWARE_VERSION, Value.Text("${model.name}_$it")) }
            if (sharedNoise && noiseKnown) put(Command.ANC_MODE, Value.Number(ancNumberOf(mode)))
        }
        state = state.copy(values = state.values + values)
    }

    /** The shared noise control (off, noise cancelling, ambient sound) fits models with noise cancelling and Ambient Aware. */
    private val sharedNoise: Boolean get() = Mode.ANC in model.modes && Mode.AMBIENT_AWARE in model.modes

    /** Every setting the model has beyond the shared screens. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group("noise_adaptive", SettingTab.HOME, extras.adaptiveRows()),
            group("sound", SettingTab.SOUND, extras.soundRows()),
            group("calls", SettingTab.DEVICE, extras.callRows()),
            group("other", SettingTab.DEVICE, otherRows()),
            group("find", SettingTab.DEVICE, extras.findRows()),
            group("touch_left", SettingTab.CONTROLS, if (model.airohaGestures != null) airohaTouchRows("left") else extras.touchRows("left")),
            group("touch_right", SettingTab.CONTROLS, if (model.airohaGestures != null) airohaTouchRows("right") else extras.touchRows("right")),
        )

    private fun noiseRows(): List<Setting> {
        if (!noiseKnown) return emptyList()
        // TalkThru beside the shared control; on other models a switch for each mode it has.
        val separate = if (sharedNoise) model.modes.filter { it == Mode.TALK_THRU } else model.modes
        return separate.map { Setting.Toggle(KEYS.getValue(it), mode == it) }
    }

    private fun otherRows(): List<Setting> = listOfNotNull(
        model.autoOff?.let { autoOffRow() },
        autoPlayPause?.takeIf { model.autoPlayPause }?.let { Setting.Toggle("pause_taken_off", it) },
    ) + extras.otherRows()

    /** Auto power-off: never or after the minutes JBL's app offers, or on and off alone where it offers no times. */
    private fun autoOffRow(): Setting? {
        val on = autoOffOn ?: return null
        if (model.autoOffMinutes.isEmpty()) return Setting.Toggle("auto_off", on)
        val minutes = autoOffMinutes
        val options = listOf(NEVER) + (model.autoOffMinutes + listOfNotNull(minutes?.takeIf { on })).distinct().sorted().map { it.toString() }
        return Setting.Choice("auto_off", if (on) minutes?.toString() else NEVER, options)
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /**
     * The packets for a change from the screens, or null when the model does not have the setting. The
     * change shows at once; [takeReadBacks] then has the reads that confirm it.
     */
    public fun change(change: SettingChange): List<JblFrame>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        if (change is SettingChange.Choice && setting is Setting.Choice && change.option !in setting.options) return null
        if (change is SettingChange.Choice && model.airohaGestures != null) {
            AIROHA_TOUCH_KEYS.entries.firstOrNull { it.value == change.key }?.let { return airohaTouch(it.key, change.option) }
        }
        extras.change(change)?.let { out ->
            extras.readBack(change.key)?.let { readBacks += it }
            return out
        }
        return when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle) return null
                when (change.key) {
                    "anc", "ambient_aware", "talk_thru" -> {
                        val m = KEYS.entries.first { it.value == change.key }.key
                        noise(if (change.on) m else null)
                    }
                    "auto_off" -> autoOff(change.on, autoOffMinutes?.takeIf { it > 0 } ?: CLUB_ONE_MINUTES)
                    "pause_taken_off" -> autoPlay(change.on)
                    else -> null
                }
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options || change.key != "auto_off") return null
                if (change.option == NEVER) {
                    autoOff(false, autoOffMinutes?.takeIf { it > 0 } ?: model.autoOffMinutes.first())
                } else {
                    autoOff(true, change.option.toIntOrNull() ?: return null)
                }
            }
            is SettingChange.Level, is SettingChange.Press -> null
        }
    }

    /** The noise mode from the shared screens, on models with noise cancelling and Ambient Aware. */
    public fun set(command: Command, value: Int): List<JblFrame>? {
        if (command != Command.ANC_MODE || !sharedNoise || !noiseKnown) return null
        val m = when (value) {
            ancNumber("off") -> null
            ancNumber("anc") -> Mode.ANC
            ancNumber("transparency") -> Mode.AMBIENT_AWARE
            else -> return null
        }
        return noise(m)
    }

    /** Sets noise control to [m], or off, the way the model's control class in JBL's app does. */
    private fun noise(m: Mode?): List<JblFrame>? {
        if (m != null && m !in model.modes) return null
        val out: List<JblFrame> = when (model.noise ?: return null) {
            JblModel.Noise.ASC -> {
                // Ambient sound control on or off, with the mode and its level, as JBL's protocol 4 classes
                // write it; off keeps the current mode. A level not yet read is left as it is (127).
                val target = m ?: mode ?: Mode.ANC
                readBacks += JblP4Packet.get(p4NoiseFeatures())
                ascOn = m != null
                for (it in MODE_ORDER) modeOn[it] = it == target
                listOf(
                    JblP4Packet.set(
                        JblP4.FEATURE_AMBIENT_SOUND_CONTROL to JblP4Packet.statusLevel(m != null, JblP4.IGNORE_LEVEL),
                        P4_FEATURES.getValue(target) to JblP4Packet.statusLevel(true, modeLevel[target] ?: JblP4.IGNORE_LEVEL),
                    ),
                )
            }
            JblModel.Noise.MODE4 -> {
                // The base General4Control.setAncSetting: the single mode feature with status 1, and off is
                // noise cancelling with status 0. No ambient sound control feature.
                readBacks += JblP4Packet.get(p4NoiseFeatures())
                for (it in MODE_ORDER) modeOn[it] = it == m
                // The base write always uses level 127 (General4Control.setAncSetting), not the reported level.
                val feature = P4_FEATURES.getValue(m ?: Mode.ANC)
                listOf(JblP4Packet.set(feature to JblP4Packet.statusLevel(m != null, JblP4.IGNORE_LEVEL)))
            }
            JblModel.Noise.MODES -> {
                readBacks += JblPacket.of(JblCommand.NOISE_MODES, JblNoise.GET)
                if (m == null) {
                    listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.OFF))
                } else {
                    // Every mode with its value, the chosen one 1, as JBL's app writes them.
                    val pairs = MODE_ORDER.flatMap { listOf(MODE_NUMBERS.getValue(it), if (it == m) 1 else 0) }
                    listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.SET, *pairs.toIntArray()))
                }
            }
            JblModel.Noise.RACE -> {
                readBacks += RacePacket.mmiGet(JblRace.MMI_ANC)
                listOf(raceNoise(m) ?: return null)
            }
            JblModel.Noise.OLD, JblModel.Noise.CLUB -> {
                readBacks += JblPacket.of(JblCommand.STATUS, JblStatus.ANC)
                readBacks += JblPacket.of(JblCommand.STATUS, JblStatus.AMBIENT)
                when (m) {
                    null -> listOfNotNull(
                        JblPacket.of(JblCommand.SET_ANC, 0),
                        // The Club One's control class turns noise cancelling off alone.
                        JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.OFF).takeIf { model.noise == JblModel.Noise.OLD },
                    )
                    Mode.ANC -> listOf(JblPacket.of(JblCommand.SET_ANC, 1))
                    Mode.TALK_THRU -> listOf(JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.TALK_THRU))
                    Mode.AMBIENT_AWARE -> listOf(JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.AMBIENT_AWARE))
                }
            }
        }
        mode = m
        if (model.noise != JblModel.Noise.MODES) {
            ancOn = m == Mode.ANC
            ambient = when (m) {
                Mode.TALK_THRU -> JblAmbient.TALK_THRU
                Mode.AMBIENT_AWARE -> JblAmbient.AMBIENT_AWARE
                else -> JblAmbient.OFF
            }
        }
        refresh()
        return out
    }

    private fun autoOff(on: Boolean, minutes: Int): List<JblFrame>? {
        val kind = model.autoOff ?: return null
        val out: JblFrame = when (kind) {
            JblModel.AutoOff.V1 -> JblPacket.of(JblCommand.SET_AUTO_OFF, (if (on) AUTO_OFF_ON else 0) or (minutes and AUTO_OFF_MINUTES))
            JblModel.AutoOff.V2 -> JblPacket.of(JblCommand.SET_AUTO_OFF, if (on) 1 else 0, minutes and 0xFF, (minutes shr 8) and 0xFF)
            JblModel.AutoOff.FEATURE -> JblPacket.featureSet(JblFeature.AUTO_OFF, if (on) 1 else 0, minutes and 0xFF, (minutes shr 8) and 0xFF)
            // Protocol 4 (StatusLevelSize2): status at bit 15, the time in seconds in bits 0 to 14 (JBL sends minutes x 60).
            JblModel.AutoOff.P4 -> {
                val word = (if (on) P4_STATUS16 else 0) or ((minutes * SECONDS_PER_MINUTE) and P4_LEVEL16)
                JblP4Packet.set(JblP4.FEATURE_AUTO_OFF to byteArrayOf(word.toByte(), (word shr 8).toByte()))
            }
            JblModel.AutoOff.RACE -> raceAutoOff(on, minutes) ?: return null
        }
        readBacks += settingsRead(JblFeature.AUTO_OFF, JblP4.FEATURE_AUTO_OFF)
        autoOffOn = on
        autoOffMinutes = minutes
        return listOf(out)
    }

    private fun autoPlay(on: Boolean): List<JblFrame>? {
        if (!model.autoPlayPause) return null
        val out: JblFrame = when (model.family) {
            JblModel.Family.DIRECT -> JblPacket.of(JblCommand.SET_AUTO_PLAY_PAUSE, if (on) 1 else 0)
            JblModel.Family.FEATURE -> JblPacket.featureSet(JblFeature.AUTO_PLAY_PAUSE, if (on) 1 else 0)
            // Protocol 4 (StatusLevelSize1): status at bit 7, the level left as it is.
            JblModel.Family.P4 -> JblP4Packet.set(JblP4.FEATURE_AUTO_PLAY_PAUSE to JblP4Packet.statusLevel(on, JblP4.IGNORE_LEVEL))
            JblModel.Family.AIROHA -> raceAutoPlay(on) ?: return null
        }
        readBacks += settingsRead(JblFeature.AUTO_PLAY_PAUSE, JblP4.FEATURE_AUTO_PLAY_PAUSE)
        autoPlayPause = on
        return listOf(out)
    }

    /**
     * The read that reports a setting: the whole status on direct models, the command-06 feature on feature models, the
     * protocol 4 feature on p4 ones, and on Airoha models the config setting or NV key their chip keeps it in.
     */
    private fun settingsRead(feature: Int, p4Feature: Int): JblFrame = when (model.family) {
        JblModel.Family.DIRECT -> JblPacket.of(JblCommand.STATUS, JblStatus.ALL)
        JblModel.Family.FEATURE -> JblPacket.featureGet(listOf(feature))
        JblModel.Family.P4 -> JblP4Packet.get(listOf(p4Feature))
        JblModel.Family.AIROHA -> if (feature == JblFeature.AUTO_OFF) raceAutoOffRead() else raceAutoPlayRead()
    }

    /** Protocol 4: the product id, firmware, batteries, its noise features and the settings PairFree offers. */
    private fun p4Features(): List<Int> = buildList {
        add(JblP4.FEATURE_PRODUCT_ID)
        add(JblP4.FEATURE_FIRMWARE)
        addAll(p4Batteries())
        if (model.noise == JblModel.Noise.ASC || model.noise == JblModel.Noise.MODE4) addAll(p4NoiseFeatures())
        if (model.autoOff != null) add(JblP4.FEATURE_AUTO_OFF)
        if (model.autoPlayPause) add(JblP4.FEATURE_AUTO_PLAY_PAUSE)
        addAll(extras.p4Features())
    }

    private fun p4Batteries(): List<Int> =
        if (model.form == Form.HEADPHONES) listOf(JblP4.FEATURE_BATTERY_LEFT) else listOf(JblP4.FEATURE_BATTERY_LEFT, JblP4.FEATURE_BATTERY_RIGHT, JblP4.FEATURE_BATTERY_CASE)

    private fun p4NoiseFeatures(): List<Int> = listOf(JblP4.FEATURE_ANC, JblP4.FEATURE_AMBIENT_AWARE, JblP4.FEATURE_TALK_THRU, JblP4.FEATURE_AMBIENT_SOUND_CONTROL)

    private fun noiseReads(): List<JblPacket> = when (model.noise) {
        JblModel.Noise.ASC, JblModel.Noise.MODE4 -> emptyList()
        JblModel.Noise.MODES -> listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.GET))
        JblModel.Noise.OLD -> listOf(JblPacket.of(JblCommand.STATUS, JblStatus.ANC), JblPacket.of(JblCommand.STATUS, JblStatus.AMBIENT))
        JblModel.Noise.CLUB -> listOf(JblPacket.of(JblCommand.STATUS, JblStatus.ANC))
        JblModel.Noise.RACE, null -> emptyList()
    }

    private fun batteryRead(): JblPacket = JblPacket.of(JblCommand.BATTERY, 0)

    /** The features read on connecting, as the model's control class in JBL's app asks for them. */
    private fun featureIds(): List<Int> = buildList {
        add(JblFeature.PRODUCT_ID)
        add(JblFeature.FIRMWARE)
        if (model.autoOff != null) add(JblFeature.AUTO_OFF)
        if (model.autoPlayPause) add(JblFeature.AUTO_PLAY_PAUSE)
        addAll(batteryFeatures())
    }

    private fun batteryFeatures(): List<Int> =
        if (model.form == Form.HEADPHONES) listOf(JblFeature.BATTERY_HEADBAND) else listOf(JblFeature.BATTERY_LEFT, JblFeature.BATTERY_RIGHT, JblFeature.BATTERY_CASE)

    // Airoha models: Airoha's RACE commands, as JBL's app sends them through Airoha's SDK (docs/jbl/PROTOCOL.md section 10).

    /**
     * The reads on opening an Airoha model's channel, in the order JBL's app sends them: the chip family (an NV key
     * Airoha's SDK reads before anything else), the push of changes made on the earbuds and which earbud PairFree
     * talks to (neither where JBL's app leaves them out, [JblModel.racePush]), the firmware, the batteries, noise
     * control and the other settings. The reads that depend on the chip follow its answer ([raceChip]).
     */
    private fun raceStart(): List<JblFrame> = buildList {
        add(RacePacket.nvRead(JblRace.NV_CHIP_NAME, RACE_NV_LENGTH))
        if (model.racePush) {
            add(RacePacket.of(JblRace.PUSH_STATUS, 1))
            add(RacePacket.of(JblRace.AGENT_CHANNEL))
        }
        add(RacePacket.of(JblRace.FIRMWARE, RACE_AGENT))
        addAll(raceBatteries())
        if (model.modes.isNotEmpty()) add(RacePacket.mmiGet(JblRace.MMI_ANC))
        addAll(extras.raceReads())
    }

    /** The agent's battery, and on earbuds the other one's (not on the AB1562, which relays that read) and the case's. */
    private fun raceBatteries(): List<JblFrame> = buildList {
        add(RacePacket.of(JblRace.BATTERY, RACE_AGENT))
        if (model.form == Form.HEADPHONES) return@buildList
        if (chip != RaceChip.AB1562) add(RacePacket.of(JblRace.BATTERY, RACE_PARTNER))
        add(RacePacket.of(JblRace.CASE_BATTERY))
    }

    /**
     * No firmware update, pairing, relay, NV key write or key event other than Smart Audio & Video's; the NV key writes
     * and relays of an Airoha PEQ write PairFree built itself are let through by [allowed].
     */
    private fun raceAllowed(p: RacePacket): Boolean = when {
        p.id in JblRace.NEVER_SEND -> false
        p.id == JblRace.KEY_EVENT -> p.payload.size >= 2 && ((p.payload[0].toInt() and 0xFF) or ((p.payload[1].toInt() and 0xFF) shl 8)) in JblRace.KEY_EVENTS
        p.id in RACE_FIRMWARE_UPDATE -> p.id == JblRace.FIRMWARE || p.id == JblRace.AE_INFO
        else -> true
    }

    /** One RACE response or indication. The earbuds also send noise control and the batteries unasked, as indications. */
    private fun race(p: RacePacket) {
        if (p.type == JblRace.TYPE_COMMAND || p.type == JblRace.TYPE_COMMAND_NO_RESPONSE) return
        if (airohaEq?.receive(p) { outbox += it } == true) {
            if (airohaEq?.done == true || airohaEq?.failed == true) startGestureRead()
            return
        }
        gestureFlow?.let { flow ->
            if (!flow.receive(p) { outbox += it }) return@let
            // The read's own question of which earbud PairFree talks to answers the session's too.
            if (p.id == JblRace.AGENT_CHANNEL) agentRight = flow.agentRight
            if (flow.done) {
                airohaGestures += flow.gestures
                gestureVariant = flow.name
                flow.keyId?.let { gestureKeyId = it }
                agentRight = flow.agentRight
            }
            if (flow.done || flow.failed) {
                gestureFlow = null
                startGestureRead()
            }
            return
        }
        val d = p.data
        when (p.id) {
            JblRace.NV_READ -> raceNv(d)
            JblRace.MMI_GET -> if (p.status == 0 && d.isNotEmpty()) {
                when (p.module) {
                    JblRace.MMI_ANC -> raceAnc(d.u8(0))
                    JblRace.NOTIFY_AGENT_BATTERY -> {
                        if (d.size >= 2) agentRight = d.u8(1) == 1
                        raceBattery(RACE_AGENT, d.u8(0))
                    }
                    JblRace.NOTIFY_PARTNER_BATTERY -> raceBattery(RACE_PARTNER, d.u8(0))
                    JblRace.NOTIFY_CASE_BATTERY -> raceCase(d.u8(0))
                }
            }
            JblRace.AGENT_CHANNEL -> if (p.status == 0 && d.isNotEmpty()) agentRight = d.u8(0) == 1
            JblRace.BATTERY -> if (p.status == 0 && d.size >= 2) raceBattery(d.u8(0), d.u8(1))
            JblRace.CASE_BATTERY -> if (p.status == 0 && d.isNotEmpty()) raceCase(d.u8(0))
            // The AB1562's AE information: two bytes, the length, then the data; past 20 bytes the model name is at
            // 29 (after the address and other fields, which PairFree does not keep).
            JblRace.AE_INFO -> if (p.status == 0 && d.size >= 3) {
                val n = d.u8(2).coerceAtMost(d.size - 3)
                val info = d.copyOfRange(3, 3 + n)
                val name = if (n > AE_MODEL_AT) info.copyOfRange(AE_MODEL_AT, minOf(n, AE_MODEL_AT + 20)) else info
                raceModelName(name.decodeToString().trimEnd('\u0000'))
                return
            }
            // The role, the text's length and the text (ASCII); the agent's is the one JBL's app shows.
            JblRace.FIRMWARE -> if (p.status == 0 && d.size >= 2 && d.u8(0) == RACE_AGENT) {
                val n = d.u8(1).coerceAtMost(d.size - 2)
                firmware = d.copyOfRange(2, 2 + n).decodeToString().trim { it <= ' ' }
            }
            JblRace.CONFIG_GET -> if (p.status == 0) {
                when (p.module) {
                    // On and off (two bytes), then the time in seconds (two bytes), low first.
                    JblRace.CONFIG_AUTO_OFF -> if (d.size >= 4) {
                        autoOffOn = d.u16(0) != 0
                        autoOffMinutes = d.u16(2) / SECONDS_PER_MINUTE
                    }
                    JblRace.CONFIG_IN_EAR -> if (d.isNotEmpty()) autoPlayPause = d.u8(0) != 0
                    else -> extras.race(p)
                }
            }
            else -> extras.race(p)
        }
        // NV keys only when short (the settings): the chip and model names are kept as text, and what else a long key
        // holds is not known.
        if (p.status == 0 || (p.id == JblRace.NV_READ && p.payload.size <= RACE_SHORT_NV)) _replies[raceKey(p)] = p.payload.copyOf()
    }

    /**
     * An NV key's data, which does not name the key: the chip name (text) first, then on older chips auto power-off
     * (on and the minutes, two bytes each) or in-ear detection (one byte).
     */
    private fun raceNv(d: ByteArray) {
        val text = d.decodeToString().trimEnd('\u0000')
        when {
            chip == null && text.length >= 4 && text.all { it in ' '..'~' } -> {
                chipName = text
                raceChip(RaceChip.of(text))
            }
            // The model name key: 20 bytes, then the model name in the next 20.
            chip != null && d.size >= RACE_MODEL_NAME_END -> raceModelName(d.copyOfRange(RACE_MODEL_NAME_START, RACE_MODEL_NAME_END).decodeToString().trimEnd('\u0000'))
            d.size == 4 && model.autoOff != null -> {
                autoOffOn = d.u16(0) != 0
                d.u16(2).takeIf { autoOffOn == true }?.let { autoOffMinutes = it }
            }
            d.size == 1 && model.autoPlayPause -> autoPlayPause = d.u8(0) != 0
        }
    }

    /**
     * The chip family is known: read what depends on it, and the model name, which on JBL's models is the product id
     * (the key differs by chip, CommonStageGetModelName; an AB1568 may be either generation, so both of its keys).
     */
    private fun raceChip(c: RaceChip) {
        chip = c
        gestureReadDue = model.airohaGestures != null
        if (model.autoOff != null) outbox += raceAutoOffRead()
        if (model.autoPlayPause) outbox += raceAutoPlayRead()
        outbox += when (c) {
            RaceChip.AB1562 -> listOf(RacePacket.of(JblRace.AE_INFO, 1, 0xFF))
            RaceChip.AB155X -> listOf(RacePacket.nvRead(JblRace.NV_MODEL_NAME_OLD, RACE_MODEL_NAME_LENGTH))
            RaceChip.AB1568 -> listOf(RacePacket.nvRead(JblRace.NV_MODEL_NAME_OLD, RACE_MODEL_NAME_LENGTH), RacePacket.nvRead(JblRace.NV_MODEL_NAME, RACE_MODEL_NAME_LENGTH))
            RaceChip.AB158X -> listOf(RacePacket.nvRead(JblRace.NV_MODEL_NAME, RACE_MODEL_NAME_LENGTH))
        }
        startGestureRead()
    }

    /** An Airoha model's touch set for one earbud ("left" or "right"), from the page JBL's app gives the model. */
    private fun airohaTouchRows(side: String): List<Setting> {
        if (gestureVariant == null) return emptyList()
        val ids = AIROHA_SIDE.getValue(side)
        if (ids.none { it in airohaGestures }) return emptyList()
        val sets = if (model.airohaGestures == SETTINGS_PAGE) SETTINGS_SETS else CARD_SETS
        return listOf(Setting.Choice(AIROHA_TOUCH_KEYS.getValue(side), airohaSetName(ids), sets))
    }

    /** The set a side's actions make, by its tap as JBL's pages name them, else its hold. */
    private fun airohaSetName(ids: List<Int>): String {
        fun name(action: Int?) = when (action) {
            null, 0 -> TOUCH_NONE
            1 -> TOUCH_VOLUME
            8, 10 -> TOUCH_PLAYBACK
            3, 9, 11, 12, 13 -> TOUCH_AMBIENT
            160 -> TOUCH_ASSISTANT
            else -> TOUCH_OTHER
        }
        val tap = name(airohaGestures[ids[0]])
        if (tap != TOUCH_NONE) return tap
        // The voice assistant set's tap writes nothing on Airoha's chips, so it reads back by its hold alone.
        return if (model.airohaGestures == SETTINGS_PAGE && airohaGestures[ids[3]] == 161 && ids.take(3).all { (airohaGestures[it] ?: 0) == 0 }) TOUCH_ASSISTANT else TOUCH_NONE
    }

    /**
     * One earbud's gestures set to [option], as JBL's page sends them (sp.a's sets on the gesture card, AppUtils.setCommandAiRoHaGesture
     * on the settings page; the second long press at 0 on Airoha models), written to the earbuds' key maps.
     */
    private fun airohaTouch(side: String, option: String): List<JblFrame>? {
        val variant = gestureVariant ?: return null
        if (gestureFlow != null || airohaEq?.let { !it.done && !it.failed } == true) return null
        var list = airohaSet(side, option) ?: return null
        // The voice assistant is on one earbud at a time on the settings page (em.o): when the other side has it, that side
        // goes to playback (the right) or noise control (the left), in the same write.
        val other = if (side == "left") "right" else "left"
        if (option == TOUCH_ASSISTANT && airohaSetName(AIROHA_SIDE.getValue(other)) == TOUCH_ASSISTANT) {
            list = list + (airohaSet(other, if (other == "right") TOUCH_PLAYBACK else TOUCH_AMBIENT) ?: return null)
        }
        val flow = JblAirohaGesture.write(variant, model.form != Form.HEADPHONES, agentRight, airohaGestures, gestureKeyId, list)
        gestureFlow = flow
        list.forEach { (g, a) -> airohaGestures[g] = a }
        return listOfNotNull(flow.start())
    }

    /** One earbud's gesture ids and actions for [option], in the order JBL's page lists them. */
    private fun airohaSet(side: String, option: String): List<Pair<Int, Int>>? {
        val (tap, double, triple, hold) = AIROHA_SIDE.getValue(side)
        val long2 = tap + 4
        val ull = if (model.gestureUll) 14 else 0
        val settings = model.airohaGestures == SETTINGS_PAGE
        return when (option) {
            TOUCH_PLAYBACK -> if (settings) listOf(tap to 8, double to 5, triple to 6, long2 to 0, hold to 161) else listOf(tap to 10, double to 5, triple to 6, hold to 161, long2 to 0)
            TOUCH_AMBIENT -> {
                val cycle = if (settings) 9 else airohaGestures[tap]?.takeIf { it in setOf(9, 11, 12, 13) } ?: model.gestureNoise
                listOf(tap to cycle, double to 4, triple to ull, hold to 161, long2 to 0)
            }
            TOUCH_VOLUME -> listOf(tap to 1, double to 2, triple to ull, hold to 161, long2 to 0)
            TOUCH_ASSISTANT -> if (settings) listOf(tap to 160, hold to 161, double to 0, triple to 0, long2 to 0) else null
            TOUCH_NONE -> if (settings) listOf(double to 0, triple to 0, tap to 0, hold to 0, long2 to 0) else listOf(tap to 0, double to 0, triple to 0, hold to 0, long2 to 0)
            else -> null
        }
    }

    /**
     * The gesture key maps' read, once the chip is known, after the reads queued before it and while no other Airoha write
     * or read is under way (their NV answers do not name the key they answer).
     */
    private fun startGestureRead() {
        val c = chip ?: return
        if (!gestureReadDue || gestureFlow != null || airohaEq?.let { !it.done && !it.failed } == true) return
        gestureReadDue = false
        val flow = JblAirohaGesture.read(c, null, model.form != Form.HEADPHONES)
        gestureFlow = flow
        flow.start()?.let { outbox += it }
    }

    /** A model name the earbuds report: four hex digits name the product, and so the model where two share a name. */
    private fun raceModelName(text: String) {
        val t = text.trim { it <= ' ' }
        modelName = t
        if (Regex("[0-9A-Fa-f]{4}").matches(t)) productIdIs(t.toInt(16))
    }

    /** A noise filter: off, noise cancelling (with or without sports mode), Ambient Aware or TalkThru; others are left alone. */
    private fun raceAnc(filter: Int) {
        if (model.modes.isEmpty()) return
        mode = when (filter) {
            0 -> null
            JblRace.ANC_FILTER_ANC, JblRace.ANC_FILTER_ANC_SPORTS -> Mode.ANC
            raceFilter(Mode.AMBIENT_AWARE) -> Mode.AMBIENT_AWARE
            raceFilter(Mode.TALK_THRU) -> Mode.TALK_THRU
            else -> return
        }
        noiseKnown = true
    }

    /** The filter of noise cancelling or a passthrough mode: the AB155x numbers its passthroughs from 5, the others from 9. */
    private fun raceFilter(m: Mode): Int {
        val old = chip == RaceChip.AB155X
        return when (m) {
            Mode.ANC -> JblRace.ANC_FILTER_ANC
            Mode.AMBIENT_AWARE -> if (old) JblRace.ANC_FILTER_AMBIENT_AWARE_AB155X else JblRace.ANC_FILTER_AMBIENT_AWARE
            Mode.TALK_THRU -> if (old) JblRace.ANC_FILTER_TALK_THRU_AB155X else JblRace.ANC_FILTER_TALK_THRU
        }
    }

    /** A battery by role (0 the agent, 1 the other earbud), shown left or right by which one the agent is. */
    private fun raceBattery(role: Int, level: Int) {
        val command = when {
            model.form == Form.HEADPHONES -> if (role == RACE_AGENT) Command.BATTERY_LEFT else return
            (role == RACE_AGENT) == agentRight -> Command.BATTERY_RIGHT
            else -> Command.BATTERY_LEFT
        }
        state = state.copy(values = state.values + (command to Value.Number(level(level))))
    }

    /** The case's battery; 0 or less is unknown, as JBL's app reads it. */
    private fun raceCase(level: Int) {
        if (model.form == Form.HEADPHONES) return
        state = state.copy(values = state.values + (Command.BATTERY_CASE to Value.Number(level.takeIf { it in 1..100 })))
    }

    /**
     * Noise control as the chip takes it: off; or on with a filter, the AB1568 and later adding the mode (0 noise
     * cancelling, 4 passthrough) and the AB1562 the mode and a sync byte (1). Null until the chip is known.
     */
    private fun raceNoise(m: Mode?): RacePacket? {
        val c = chip ?: return null
        if (m == null) return if (c == RaceChip.AB1562) RacePacket.of(JblRace.ANC, 0, JblRace.ANC_OFF, 1) else RacePacket.of(JblRace.ANC, 0, JblRace.ANC_OFF)
        val filter = raceFilter(m)
        val mode = if (m == Mode.ANC) JblRace.ANC_MODE_ANC else JblRace.ANC_MODE_PASSTHROUGH
        return when (c) {
            RaceChip.AB155X -> RacePacket.of(JblRace.ANC, 0, JblRace.ANC_ON, filter)
            RaceChip.AB1562 -> RacePacket.of(JblRace.ANC, 0, JblRace.ANC_ON, filter, mode, 1)
            RaceChip.AB1568, RaceChip.AB158X -> RacePacket.of(JblRace.ANC, 0, JblRace.ANC_ON, filter, mode)
        }
    }

    /** Auto power-off: the AB1568 and later keep it in config setting 1 (seconds), older chips in an NV key written by command 0007 (minutes). */
    private fun raceAutoOff(on: Boolean, minutes: Int): RacePacket? {
        val c = chip ?: return null
        val flag = if (on) 1 else 0
        return if (c.config) {
            val seconds = minutes * SECONDS_PER_MINUTE
            RacePacket.configSet(JblRace.CONFIG_AUTO_OFF, flag, 0, seconds and 0xFF, (seconds shr 8) and 0xFF)
        } else {
            RacePacket.of(JblRace.AUTO_OFF_OLD, flag, 0, minutes and 0xFF, (minutes shr 8) and 0xFF)
        }
    }

    private fun raceAutoOffRead(): RacePacket =
        if (chip?.config != false) RacePacket.configGet(JblRace.CONFIG_AUTO_OFF) else RacePacket.nvRead(JblRace.NV_POWER_CONTROL, RACE_POWER_CONTROL_LENGTH)

    /** Pause when taken off (in-ear detection): config setting 2 on the AB1568 and later, command 000A and an NV key on older chips. */
    private fun raceAutoPlay(on: Boolean): RacePacket? {
        val c = chip ?: return null
        return if (c.config) RacePacket.configSet(JblRace.CONFIG_IN_EAR, if (on) 1 else 0) else RacePacket.of(JblRace.IN_EAR_OLD, if (on) 1 else 0)
    }

    private fun raceAutoPlayRead(): RacePacket =
        if (chip?.config != false) RacePacket.configGet(JblRace.CONFIG_IN_EAR) else RacePacket.nvRead(JblRace.NV_IN_EAR_OLD, 1)

    private fun raceKey(p: RacePacket): String =
        "race." + p.id.toString(16).padStart(4, '0') + (p.module.takeIf { it >= 0 }?.let { "." + it.toString(16).padStart(2, '0') } ?: "") +
            (if (p.id == JblRace.BATTERY || p.id == JblRace.FIRMWARE) p.data.takeIf { it.isNotEmpty() }?.let { ".role" + it.u8(0) } ?: "" else "")

    private fun key(command: Int, part: Int? = null): String =
        command.toString(16).padStart(2, '0') + (part?.let { "." + it.toString(16).padStart(2, '0') } ?: "")

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF
    private fun ByteArray.u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)

    private companion object {
        /** The calibration JBL's app sends when it holds no design EQ (AppUtils.getCalib). */
        const val NO_DESIGN_EQ = -1f

        /** The MTU PairFree asks for on Harman's service, until the link reports the one agreed. */
        const val DEFAULT_MTU = 512
        const val HALF = 0.5f

        // Airoha models: how much of an NV key Airoha's SDK asks for, and the power control key's length.
        const val RACE_NV_LENGTH = 1000
        const val RACE_POWER_CONTROL_LENGTH = 3

        // The model name key: 100 bytes asked for, the name in bytes 20 to 39 (CommonStageGetModelName); in the
        // AB1562's AE information it starts at 29. NV answers up to this long are settings PairFree shows as they are.
        const val RACE_MODEL_NAME_LENGTH = 100
        const val RACE_MODEL_NAME_START = 20
        const val RACE_MODEL_NAME_END = 40
        const val AE_MODEL_AT = 29
        const val RACE_SHORT_NV = 8

        /** The agent (the earbud the phone talks to) and the other one, as RACE numbers them. */
        const val RACE_AGENT = 0
        const val RACE_PARTNER = 1

        // Airoha models' touch sets (docs/jbl/PROTOCOL.md section 10.1), under JBL's touch keys and set names.
        private val AIROHA_TOUCH_KEYS = mapOf("left" to JblExtras.TOUCH_LEFT, "right" to JblExtras.TOUCH_RIGHT)
        private val AIROHA_SIDE = mapOf("left" to listOf(1, 3, 7, 49), "right" to listOf(2, 4, 8, 50))
        private const val SETTINGS_PAGE = "settings"
        private const val TOUCH_PLAYBACK = JblExtras.PLAYBACK
        private const val TOUCH_AMBIENT = JblExtras.AMBIENT
        private const val TOUCH_VOLUME = JblExtras.VOLUME
        private const val TOUCH_NONE = JblExtras.NONE
        private const val TOUCH_OTHER = JblExtras.OTHER
        private const val TOUCH_ASSISTANT = "voice_assistant"
        private val CARD_SETS = listOf(TOUCH_PLAYBACK, TOUCH_AMBIENT, TOUCH_VOLUME, TOUCH_NONE)
        private val SETTINGS_SETS = listOf(TOUCH_PLAYBACK, TOUCH_AMBIENT, TOUCH_VOLUME, TOUCH_ASSISTANT, TOUCH_NONE)

        /** Airoha's firmware update commands, of which PairFree sends only the version read. */
        val RACE_FIRMWARE_UPDATE = 0x1C00..0x1CFF

        // Device info (payload offsets): name 0 to 15, product id 16 and 17, address 20 to 25, firmware 26 to 28.
        const val INFO_NAME_END = 16
        const val INFO_PID = 16
        const val INFO_MAC = 20
        const val INFO_FIRMWARE = 26
        const val MAC_BYTES = 6

        // The whole status (payload offsets after the type): auto power-off (one byte), auto play/pause, auto power-off (three bytes).
        const val ALL_AUTO_OFF = 3
        const val ALL_AUTO_PLAY = 8
        const val ALL_AUTO_OFF_2 = 10

        const val AUTO_OFF_ON = 0x80
        const val AUTO_OFF_MINUTES = 0x7F

        /** The time JBL's app writes with the Club One's auto power-off switch. */
        const val CLUB_ONE_MINUTES = 10

        const val BATTERY_SECONDARY = 3
        const val BATTERY_PRIMARY = 4
        const val BATTERY_CASE = 6
        const val NEVER = "never"

        val MODE_ORDER = listOf(Mode.ANC, Mode.AMBIENT_AWARE, Mode.TALK_THRU)
        val MODE_NUMBERS = mapOf(Mode.ANC to JblNoise.MODE_ANC, Mode.AMBIENT_AWARE to JblNoise.MODE_AMBIENT_AWARE, Mode.TALK_THRU to JblNoise.MODE_TALK_THRU)
        val KEYS = mapOf(Mode.ANC to "anc", Mode.AMBIENT_AWARE to "ambient_aware", Mode.TALK_THRU to "talk_thru")

        val REPORTED_FEATURES = setOf(
            JblFeature.PRODUCT_ID, JblFeature.FIRMWARE, JblFeature.AUTO_OFF, JblFeature.AUTO_PLAY_PAUSE,
            JblFeature.BATTERY_HEADBAND, JblFeature.BATTERY_LEFT, JblFeature.BATTERY_RIGHT, JblFeature.BATTERY_CASE,
        ) + JblExtras.FEATURE_IDS

        fun modeOf(number: Int): Mode? = MODE_NUMBERS.entries.firstOrNull { it.value == number }?.key

        // Protocol 4: a size-1 feature's status in bit 7, level in bits 0 to 6; a size-2 feature's status in bit 15.
        const val STATUS_BIT = 0x80
        const val LEVEL_BITS = 0x7F
        const val P4_STATUS16 = 0x8000
        const val P4_LEVEL16 = 0x7FFF
        const val SECONDS_PER_MINUTE = 60
        val P4_FEATURES = mapOf(Mode.ANC to JblP4.FEATURE_ANC, Mode.AMBIENT_AWARE to JblP4.FEATURE_AMBIENT_AWARE, Mode.TALK_THRU to JblP4.FEATURE_TALK_THRU)
        val P4_MODES = P4_FEATURES.entries.associate { it.value to it.key }
        val REPORTED_P4 = setOf(
            JblP4.FEATURE_PRODUCT_ID, JblP4.FEATURE_FIRMWARE, JblP4.FEATURE_BATTERY_LEFT, JblP4.FEATURE_BATTERY_RIGHT,
            JblP4.FEATURE_BATTERY_CASE, JblP4.FEATURE_AMBIENT_SOUND_CONTROL, JblP4.FEATURE_AUTO_OFF, JblP4.FEATURE_AUTO_PLAY_PAUSE,
            JblP4.FEATURE_EQ_INFO,
        ) + P4_MODES.keys + JblExtras.P4_IDS

        fun level(v: Int): Int? = v.takeIf { it <= 100 }

        /** JBL's parseBoolean: 00 off, 01 or 02 on, anything else unknown. */
        fun boolean(v: Int): Boolean? = when (v) {
            0 -> false
            1, 2 -> true
            else -> null
        }

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        fun ancNumberOf(mode: Mode?): Int? = when (mode) {
            null -> ancNumber("off")
            Mode.ANC -> ancNumber("anc")
            Mode.AMBIENT_AWARE -> ancNumber("transparency")
            Mode.TALK_THRU -> null
        }
    }
}
