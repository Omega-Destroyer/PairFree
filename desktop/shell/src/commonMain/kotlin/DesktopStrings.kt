package io.github.omegadestroyer.pairfree.shell

/**
 * The text of the desktop apps in one language: every entry of the strings file the translators work from, with the desktop's own
 * wording where a computer differs from a phone (docs/DESKTOP.md). A key is looked for in this order: the language's desktop
 * wording, the language's text, English's desktop wording, English. A key no file holds is shown as its name, so a missing text is
 * seen at once and never crashes a screen.
 */
class DesktopStrings(tag: String?) {
    /** The language in use, as a tag from [languages] ("en" when the one asked for is not built in). */
    val tag: String = resolve(tag)

    private val chain: List<RawTexts> = buildList {
        val (main, desktop) = StringTables.load(this@DesktopStrings.tag)!!
        add(desktop)
        add(main)
        if (this@DesktopStrings.tag != "en") {
            val (enMain, enDesktop) = StringTables.load("en")!!
            add(enDesktop)
            add(enMain)
        }
    }

    /** The text of [key], with `%s`, `%d` and `%1$s`-style places filled from [args] in order. */
    fun text(key: String, vararg args: Any?): String {
        val raw = chain.firstNotNullOfOrNull { it.strings[key] } ?: return key
        return format(raw, args)
    }

    /** The text of a plural: [key] in the form [count] takes. Every built-in language here has `one` and `other`. */
    fun plural(key: String, count: Int, vararg args: Any?): String {
        val forms = chain.firstNotNullOfOrNull { it.plurals[key] } ?: return key
        val raw = (if (count == 1) forms["one"] else null) ?: forms["other"] ?: forms.values.firstOrNull() ?: return key
        return format(raw, if (args.isEmpty()) arrayOf<Any?>(count) else args)
    }

    companion object {
        /** The languages built in, as tags, English first. */
        val languages: List<String> get() = StringTables.tags

        /** The built-in language for a tag the user chose or the computer reports ("pt-BR", "fa_IR", null): the whole tag, else its language, else English. */
        fun resolve(tag: String?): String {
            val t = tag?.replace('_', '-') ?: return "en"
            languages.firstOrNull { it.equals(t, ignoreCase = true) }?.let { return it }
            val base = t.substringBefore('-')
            return languages.firstOrNull { it.equals(base, ignoreCase = true) } ?: "en"
        }

        /** Android's format places: `%s` and `%d` in order, `%1$s` by number, `%%` for a percent sign. */
        internal fun format(raw: String, args: Array<out Any?>): String {
            if (!raw.contains('%')) return raw
            val out = StringBuilder()
            var next = 0
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c != '%' || i + 1 >= raw.length) { out.append(c); i++; continue }
                val n = raw[i + 1]
                if (n == '%') { out.append('%'); i += 2; continue }
                var j = i + 1
                while (j < raw.length && raw[j].isDigit()) j++
                if (j > i + 1 && j < raw.length && raw[j] == '$') {
                    val index = raw.substring(i + 1, j).toInt() - 1
                    val type = raw.getOrNull(j + 1)
                    if (type == 's' || type == 'd') { out.append(args.getOrNull(index)?.toString() ?: ""); i = j + 2; continue }
                } else if (n == 's' || n == 'd') {
                    out.append(args.getOrNull(next++)?.toString() ?: "")
                    i += 2
                    continue
                }
                out.append(c)
                i++
            }
            return out.toString()
        }
    }
}
