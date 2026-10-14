package io.github.omegadestroyer.soundpeter.protocol.soundcore

import io.github.omegadestroyer.soundpeter.protocol.Model

/** Telling Soundcore models apart before connecting (docs/soundcore/PROTOCOL.md section 4). */
public object SoundcoreModels {
    /** Every Soundcore model in data/soundcore/devices.json. */
    public val ALL: List<Model> get() = SOUNDCORE_MODEL_TABLE

    /** The table entry for a model number such as `A3936`, or null. */
    public fun model(code: String?): Model? = SOUNDCORE_MODEL_TABLE.firstOrNull { it.code == code }

    /**
     * The model the earbuds' vendor service UUID names: `A3936` from
     * `0cf12d31-fac3-4553-bd80-d6832e7b3936`. Null for any other UUID. Both models seen so far
     * (A3116, A3936) end their UUID in `b` and the model number; other forms are not known yet.
     */
    public fun fromServiceUuid(uuid: String): String? {
        val u = uuid.lowercase()
        if (u.length != 36 || !u.startsWith(SoundcoreWire.VENDOR_SERVICE_PREFIX)) return null
        val suffix = u.substring(SoundcoreWire.VENDOR_SERVICE_PREFIX.length)
        if (suffix.length != 5 || suffix[0] != 'b' || !suffix.drop(1).all { it in '0'..'9' }) return null
        return "A" + suffix.drop(1)
    }

    /** True for a Soundcore vendor service UUID, whatever model it names. */
    public fun isVendorService(uuid: String): Boolean =
        uuid.length == 36 && uuid.lowercase().startsWith(SoundcoreWire.VENDOR_SERVICE_PREFIX)
}
