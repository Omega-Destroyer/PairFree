package io.github.omegadestroyer.soundpeter.protocol.soundcore

/** The noise control choice, byte 0 of the sound modes block. */
public enum class AmbientMode(public val value: Int) {
    NOISE_CANCELLING(0),
    TRANSPARENCY(1),
    NORMAL(2),
    ;

    public companion object {
        public fun of(value: Int): AmbientMode? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The Space A40's (A3936) reply to [SoundcoreCommand.STATE], field by field as OpenSCQ30 reads
 * it (docs/soundcore/PROTOCOL.md section 6). The A40 sends 143 bytes; OpenSCQ30 knows the first
 * [KNOWN_SIZE] and the rest is kept in [trailing].
 */
public class A3936State private constructor(private val body: ByteArray) {
    /** Which earbud talks to the phone. */
    public val hostDevice: Int get() = u8(TWS)
    public val bothConnected: Boolean get() = u8(TWS + 1) != 0

    /** Battery levels from 0 to 5. */
    public val batteryLeft: Int get() = u8(BATTERY)
    public val batteryRight: Int get() = u8(BATTERY + 1)
    public val chargingLeft: Boolean get() = u8(BATTERY + 2) != 0
    public val chargingRight: Boolean get() = u8(BATTERY + 3) != 0

    public val firmwareLeft: String get() = ascii(FIRMWARE, 5)
    public val firmwareRight: String get() = ascii(FIRMWARE + 5, 5)

    /** The model number, then the earbuds' Bluetooth address: mask it before sharing. */
    public val serialNumber: String get() = ascii(SERIAL, 16)

    /** The EQ preset; [SoundcoreEq.CUSTOM_PRESET] for a custom curve. */
    public val eqPreset: Int get() = u8(EQ) or (u8(EQ + 1) shl 8)

    /** The custom curve in tenths of a dB. While a preset is on, the A40 sends zero bytes here. */
    public val eqLeft: IntArray get() = IntArray(SoundcoreEq.BANDS) { SoundcoreEq.toTenths(body[EQ + 2 + it]) }
    public val eqRight: IntArray get() = IntArray(SoundcoreEq.BANDS) { SoundcoreEq.toTenths(body[EQ + 12 + it]) }

    public val ageRange: Int get() = u8(AGE)

    /** Which noise modes the noise-control gesture cycles through. */
    public val soundModeCycle: Int get() = u8(CYCLE)

    /** The 6-byte sound modes block, as [SoundcoreCommand.SET_SOUND_MODES] takes it. */
    public val soundModes: ByteArray get() = body.copyOfRange(MODES, MODES + 6)
    public val ambientMode: AmbientMode? get() = AmbientMode.of(u8(MODES))

    public val touchTone: Boolean get() = u8(TONE) != 0
    public val caseBattery: Int get() = u8(CASE)
    public val color: Int get() = u8(COLOR)
    public val ldac: Boolean get() = u8(LDAC) != 0
    public val multipoint: Boolean get() = u8(MULTIPOINT) != 0
    public val autoPowerOff: Boolean get() = u8(AUTO_POWER_OFF) != 0
    public val autoPowerOffIndex: Int get() = u8(AUTO_POWER_OFF + 1)
    public val gamingMode: Boolean get() = u8(GAMING) != 0

    /** Bytes after the fields OpenSCQ30 knows. */
    public val trailing: ByteArray get() = body.copyOfRange(KNOWN_SIZE, body.size)

    /** The sound modes block with the noise control choice changed. */
    public fun soundModesWith(mode: AmbientMode): ByteArray = soundModes.also { it[0] = mode.value.toByte() }

    /**
     * The [SoundcoreCommand.EQUALIZER_HEAR_ID] body for an EQ: [preset] ([SoundcoreEq.CUSTOM_PRESET]
     * for a custom curve) and each earbud's bands in tenths of a dB. The HearID data is passed on
     * from this state unchanged, as the Soundcore app does. [gender] is FF in every recorded write.
     */
    public fun equalizerWrite(preset: Int, left: IntArray, right: IntArray, gender: Int = 0xFF): ByteArray {
        require(left.size == SoundcoreEq.BANDS && right.size == SoundcoreEq.BANDS) { "need ${SoundcoreEq.BANDS} bands" }
        val out = ArrayList<Byte>(92)
        out += preset.toByte()
        out += (preset shr 8).toByte()
        out += hearId(HEAR_GENRE, 2)
        out += SoundcoreEq.bytes(left).toList()
        out += SoundcoreEq.bytes(right).toList()
        out += gender.toByte()
        out += body[AGE]
        out += 0.toByte() // not known; 00 in every recorded write
        out += hearId(HEAR_CURVE, 20)
        out += hearId(HEAR_TIME, 4)
        out += hearId(HEAR_TYPE, 1)
        out += hearId(HEAR_CUSTOM, 20)
        out += SoundcoreEq.bytes(SoundcoreEq.smooth(left)).toList()
        out += SoundcoreEq.bytes(SoundcoreEq.smooth(right)).toList()
        return out.toByteArray()
    }

    private fun hearId(offset: Int, size: Int): List<Byte> = body.copyOfRange(HEAR_ID + offset, HEAR_ID + offset + size).toList()

    private fun u8(at: Int): Int = body[at].toInt() and 0xFF

    private fun ascii(at: Int, size: Int): String = body.copyOfRange(at, at + size).decodeToString()

    public companion object {
        private const val TWS = 0
        private const val BATTERY = 2
        private const val FIRMWARE = 6
        private const val SERIAL = 16
        private const val EQ = 32
        private const val AGE = 54
        private const val HEAR_ID = 55

        // Inside the 48 HearID bytes: enabled (1), curve (20), time (4), type (1), custom curve (20), music genre (2).
        private const val HEAR_CURVE = 1
        private const val HEAR_TIME = 21
        private const val HEAR_TYPE = 25
        private const val HEAR_CUSTOM = 26
        private const val HEAR_GENRE = 46

        private const val BUTTONS = 104
        private const val CYCLE = BUTTONS + 12
        private const val MODES = CYCLE + 1
        private const val TONE = MODES + 6
        private const val CASE = TONE + 1
        private const val COLOR = CASE + 1
        private const val LDAC = COLOR + 1
        private const val MULTIPOINT = LDAC + 1
        private const val AUTO_POWER_OFF = MULTIPOINT + 1
        private const val GAMING = AUTO_POWER_OFF + 2

        /** The bytes OpenSCQ30's A3936 layout covers. */
        public const val KNOWN_SIZE: Int = GAMING + 1

        public fun parse(body: ByteArray): A3936State? = if (body.size >= KNOWN_SIZE) A3936State(body.copyOf()) else null
    }
}
