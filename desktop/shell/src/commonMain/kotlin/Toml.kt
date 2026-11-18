package io.github.omegadestroyer.pairfree.shell

/**
 * The small part of TOML the settings file uses: `[section]` headers, `key = value` lines, `#` comments, and values that are
 * a basic string ("..." with escapes), a literal string ('...'), a whole number or true/false. Arrays and tables of arrays are
 * not read: the file holds the equalizer's JSON texts as strings. A line it cannot read is reported in `notes` and left out.
 */
class TomlDocument private constructor(private val sections: LinkedHashMap<String, LinkedHashMap<String, String>>) {
    constructor() : this(LinkedHashMap())

    /** The value as text, whatever its kind in the file; null where absent. */
    operator fun get(section: String, key: String): String? = sections[section]?.get(key)?.let(::decode)

    fun set(section: String, key: String, value: String) {
        sections.getOrPut(section) { LinkedHashMap() }[key] = encodeString(value)
    }

    fun setNumber(section: String, key: String, value: Long) {
        sections.getOrPut(section) { LinkedHashMap() }[key] = value.toString()
    }

    fun setBoolean(section: String, key: String, value: Boolean) {
        sections.getOrPut(section) { LinkedHashMap() }[key] = value.toString()
    }

    fun keys(section: String): List<String> = sections[section]?.keys?.toList() ?: emptyList()

    fun remove(section: String, key: String) {
        sections[section]?.remove(key)
    }

    /** The file's text: a header, then each section in the order it was first seen. Comments of the user's are not kept. */
    fun write(header: String = ""): String {
        val out = StringBuilder()
        header.lines().filter { it.isNotEmpty() }.forEach { out.append("# ").append(it).append('\n') }
        for ((name, values) in sections) {
            if (values.isEmpty()) continue
            if (out.isNotEmpty()) out.append('\n')
            out.append('[').append(name).append("]\n")
            for ((key, raw) in values) out.append(keyText(key)).append(" = ").append(raw).append('\n')
        }
        return out.toString()
    }

    companion object {
        private val bare = Regex("[A-Za-z0-9_-]+")

        private fun keyText(key: String) = if (bare.matches(key)) key else encodeString(key)

        /** A string as the file holds it: literal when nothing needs escaping, so the equalizer's JSON reads as it is. */
        internal fun encodeString(value: String): String {
            if (value.none { it == '\'' || it == '\n' || it == '\r' || it.code < 32 || it.code == 127 }) return "'$value'"
            val sb = StringBuilder("\"")
            for (c in value) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 32 || c.code == 127 -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
            return sb.append('"').toString()
        }

        private fun decode(raw: String): String {
            if (raw.length >= 2 && raw.first() == '\'' && raw.last() == '\'') return raw.substring(1, raw.length - 1)
            if (raw.length < 2 || raw.first() != '"' || raw.last() != '"') return raw
            val sb = StringBuilder()
            var i = 1
            val end = raw.length - 1
            while (i < end) {
                val c = raw[i]
                if (c != '\\' || i + 1 >= end) {
                    sb.append(c)
                    i++
                    continue
                }
                when (val n = raw[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'u' -> {
                        val hex = raw.substring(i + 2, minOf(i + 6, end))
                        sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
            }
            return sb.toString()
        }

        /** Reads a file's text. A line that is no section, comment or `key = value` is noted in [notes] and left out. */
        fun parse(text: String, notes: MutableList<String>? = null): TomlDocument {
            val sections = LinkedHashMap<String, LinkedHashMap<String, String>>()
            var current: LinkedHashMap<String, String>? = null
            for ((index, rawLine) in text.lines().withIndex()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.startsWith("[") && line.endsWith("]") && !line.startsWith("[[")) {
                    current = sections.getOrPut(line.substring(1, line.length - 1).trim()) { LinkedHashMap() }
                    continue
                }
                val eq = findEquals(line)
                if (eq <= 0 || current == null) {
                    notes?.add("line ${index + 1}: not read")
                    continue
                }
                val key = decode(line.substring(0, eq).trim())
                current[key] = stripComment(line.substring(eq + 1).trim())
            }
            return TomlDocument(sections)
        }

        /** The `=` that ends the key, which may be quoted. */
        private fun findEquals(line: String): Int {
            var quote: Char? = null
            for ((i, c) in line.withIndex()) {
                if (quote != null) {
                    if (c == quote) quote = null
                } else if (c == '"' || c == '\'') {
                    quote = c
                } else if (c == '=') {
                    return i
                }
            }
            return -1
        }

        /** A value's trailing `# comment`, which is outside any string. */
        private fun stripComment(value: String): String {
            var quote: Char? = null
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (quote != null) {
                    if (c == '\\' && quote == '"') i++ else if (c == quote) quote = null
                } else if (c == '"' || c == '\'') {
                    quote = c
                } else if (c == '#') {
                    return value.substring(0, i).trim()
                }
                i++
            }
            return value
        }
    }
}
