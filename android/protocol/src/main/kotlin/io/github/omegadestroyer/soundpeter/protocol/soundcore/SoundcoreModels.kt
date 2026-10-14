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

    /**
     * The model a Bluetooth name belongs to, matched against the names in data/soundcore/devices.json
     * (from OpenSCQ30), ignoring case. For earbuds whose service UUID does not name the model.
     */
    public fun fromName(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        return SOUNDCORE_MODEL_TABLE.firstOrNull { m -> m.names.any { it.equals(n, ignoreCase = true) } }?.code
    }

    /** True for a Soundcore vendor service UUID, whatever model it names. */
    public fun isVendorService(uuid: String): Boolean =
        uuid.length == 36 && uuid.lowercase().startsWith(SoundcoreWire.VENDOR_SERVICE_PREFIX)

    /** A paired device PairFree can control: its model and the RFCOMM service to open, null for the standard serial port. */
    public data class Match(val model: String, val service: String?)

    /**
     * The model of a paired device and the service to open, or null when PairFree has no layout for it.
     * The service is chosen as OpenSCQ30 chooses it: the vendor service when the device has one, else the
     * standard serial port, unless the model fixes its own. The model is the one the vendor service names,
     * else the one the Bluetooth name belongs to.
     */
    public fun identify(name: String, serviceUuids: List<String>): Match? {
        val vendor = serviceUuids.firstOrNull(::isVendorService)
        val model = vendor?.let(::fromServiceUuid)?.takeIf { SoundcoreLayout.of(it) != null } ?: fromName(name)
        val layout = SoundcoreLayout.of(model) ?: return null
        return Match(layout.model, layout.serviceUuid ?: vendor)
    }
}
