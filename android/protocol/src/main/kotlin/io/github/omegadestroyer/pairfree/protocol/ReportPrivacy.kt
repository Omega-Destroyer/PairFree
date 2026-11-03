package io.github.omegadestroyer.pairfree.protocol

/**
 * What a report posted in a public GitHub issue may say about the user's devices. The Bluetooth name stays,
 * since it shows how PairFree found the earbuds (docs/DESIGN.md, Finding the earbuds), but not the owner's
 * name a phone or an iPhone puts in it, nor anything taken from an address.
 */
public object ReportPrivacy {
    /**
     * [name] for a report: the owner's name in "Tyler's Pixel Buds Pro" or "AirPods Pro de Tyler" as XXX,
     * the four address digits Samsung adds ("Galaxy Buds2 Pro (A1B2)") and any Bluetooth address masked.
     * A name the owner typed in full ("Tyler Buds") cannot be told from a model name and stays.
     */
    public fun name(name: String): String {
        var out = addresses(name)
        out = OWNER_FIRST.replace(out) { "XXX${it.groupValues[1]}s " }
        out = OWNER_LAST.replace(out) { " ${it.groupValues[1]} XXX" }
        return SAMSUNG_DIGITS.replace(out, "(xxxx)")
    }

    /** [text] with the last three bytes of any Bluetooth address in it as XX, as data/soundpeats/reports/README.md asks. */
    public fun addresses(text: String): String = ADDRESS.replace(text) { m ->
        val sep = m.groupValues[1]
        m.value.split(sep).take(3).joinToString(sep) + List(3) { "${sep}XX" }.joinToString("")
    }

    // English "Tyler's" (any apostrophe), and the forms Apple and Samsung use in other languages.
    private val OWNER_FIRST = Regex("""^.+?(['’`])s\s+""")
    private val OWNER_LAST = Regex("""\s+(de|von|van|di|da|do|du)\s+.+$""", RegexOption.IGNORE_CASE)
    private val SAMSUNG_DIGITS = Regex("""\([0-9A-Fa-f]{4}\)""")
    private val ADDRESS = Regex("""\b[0-9A-Fa-f]{2}([:-])[0-9A-Fa-f]{2}(?:\1[0-9A-Fa-f]{2}){4}\b""")
}
