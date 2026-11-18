package io.github.omegadestroyer.pairfree.protocol.skullcandy

/**
 * Which Skullcandy product a paired device is, from its Bluetooth name (docs/skullcandy/PROTOCOL.md sections 9 to 12). The products are the app's own list
 * ([SkullcandyProducts], generated from data/skullcandy/devices.json); a name matches a product's name, one of its alternate names, one of the names its
 * SDK configuration answers to, or its model id, whole and in any case, with or without a leading "Skullcandy". The classic and Bluetooth LE entries of one name
 * (the Crusher Evo, Indy Evo and others have both) are told apart by the connection, which this class does not know: [fromName] gives the classic one when there are two,
 * as that is the one controlled over the serial channel.
 */
public object SkullcandyModels {
    private fun norm(s: String): String = s.trim().lowercase().removePrefix("skullcandy").trim().filter { it.isLetterOrDigit() || it == '+' }

    private val byName: Map<String, List<SkullcandyProduct>> by lazy {
        val map = LinkedHashMap<String, MutableList<SkullcandyProduct>>()
        for (p in SkullcandyProducts.ALL) {
            val names = listOf(p.name) + p.alternateNames + p.sdkNames + p.modelIds
            for (n in names) {
                val key = norm(n)
                if (key.isNotEmpty()) map.getOrPut(key) { mutableListOf() }.let { if (p !in it) it += p }
            }
        }
        map
    }

    /** The product the name is, or null when it names none or several different ones (the Dime 2 and Dime XT2 share a model id, for example). */
    public fun fromName(name: String): SkullcandyProduct? {
        val found = byName[norm(name)] ?: return null
        if (found.size == 1) return found[0]
        // The same name on a classic and a Bluetooth LE entry: the classic one.
        val classic = found.filter { "bluetoothClassic" in it.android }
        return if (classic.size == 1 && found.map { it.name }.distinct().size == 1) classic[0] else null
    }

    public fun byId(id: Int): SkullcandyProduct? = SkullcandyProducts.ALL.firstOrNull { it.id == id }
}
