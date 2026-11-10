package io.github.numberonebot.chairplay.ui

/**
 * The little JSON the UI's API speaks: objects (maps with string keys), lists, strings, numbers, booleans and null.
 * Whole numbers read as Long, others as Double. Not worth a library for a handful of flat messages.
 */
internal object Json {
    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(value, out)
            is Boolean -> out.append(value)
            is Int, is Long, is Short, is Byte -> out.append(value)
            is Number -> {
                val d = value.toDouble()
                require(d.isFinite()) { "JSON has no $d" }
                out.append(value)
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(key as String, out)
                    out.append(':')
                    write(item, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    write(item, out)
                }
                out.append(']')
            }
            is Enum<*> -> writeString(value.name.lowercase(), out)
            else -> throw IllegalArgumentException("cannot write ${value.javaClass.simpleName} as JSON")
        }
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                // Control characters, and the line separators JavaScript once refused inside strings.
                c < ' ' || c == '\u2028' || c == '\u2029' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    /** Reads one JSON value; anything else, or anything after it, is an IllegalArgumentException. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipWhitespace()
        require(reader.atEnd()) { "unexpected text after the JSON value at ${reader.position}" }
        return value
    }

    private class Reader(private val text: String) {
        var position = 0
            private set

        fun atEnd() = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position] in " \t\r\n") position++
        }

        private fun peek(): Char {
            require(position < text.length) { "the JSON ends early" }
            return text[position]
        }

        private fun expect(c: Char) {
            require(peek() == c) { "expected '$c' at $position" }
            position++
        }

        fun value(): Any? {
            skipWhitespace()
            return when (val c = peek()) {
                '{' -> obj()
                '[' -> list()
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') number() else throw IllegalArgumentException("unexpected '$c' at $position")
            }
        }

        private fun word(word: String, value: Any?): Any? {
            require(text.startsWith(word, position)) { "unexpected text at $position" }
            position += word.length
            return value
        }

        private fun obj(): Map<String, Any?> {
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = string()
                skipWhitespace()
                expect(':')
                map[key] = value()
                skipWhitespace()
                if (peek() == ',') {
                    position++
                    continue
                }
                expect('}')
                return map
            }
        }

        private fun list(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return list
            }
            while (true) {
                list += value()
                skipWhitespace()
                if (peek() == ',') {
                    position++
                    continue
                }
                expect(']')
                return list
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = peek()
                position++
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        val e = peek()
                        position++
                        when (e) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(position + 4 <= text.length) { "the JSON ends early" }
                                val code = text.substring(position, position + 4).toIntOrNull(16)
                                    ?: throw IllegalArgumentException("bad \\u escape at $position")
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw IllegalArgumentException("bad escape at $position")
                        }
                    }
                    c < ' ' -> throw IllegalArgumentException("control character in a string at $position")
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Number {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && text[position] in "0123456789.eE+-") position++
            val raw = text.substring(start, position)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("bad number at $start")
        }
    }
}
