package io.github.omegadestroyer.pairfree.protocol.bose

/**
 * The serial service BMAP runs on (docs/bose/PROTOCOL.md section 1). Bose's app opens the standard serial port and lets the phone's service lookup find the channel
 * (`SppConnectionManager`). The QuietComfort Earbuds (product 402F, tried with bosectl) list no standard serial port, only Bose's own BMAP service
 * `00000000-deca-fade-deca-deafdecacaff`, which bosectl names as the SDP record to look for (issue #69: PairFree opened only the standard one and failed).
 */
public object BoseService {
    public const val SPP: String = "00001101-0000-1000-8000-00805f9b34fb"
    public const val BMAP: String = "00000000-deca-fade-deca-deafdecacaff"

    /** The service to open for a device that lists [uuids]: BMAP's when it is listed and the standard serial port is not, else null (the standard one). */
    public fun forServices(uuids: List<String>): String? {
        val has = uuids.map { it.lowercase() }
        return if (BMAP in has && SPP !in has) BMAP else null
    }
}
