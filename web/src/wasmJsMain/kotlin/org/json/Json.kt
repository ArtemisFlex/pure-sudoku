package org.json

// A small, pure-Kotlin stand-in for Android's org.json, covering what PureGames uses, so the app's
// save code compiles unchanged for the browser. Same names, same behaviour for those calls.

class JSONException(message: String) : RuntimeException(message)

private fun num(v: Any?, what: String): Number = when (v) {
    is Number -> v
    is String -> v.toDoubleOrNull() ?: throw JSONException("$what is not a number")
    else -> throw JSONException("$what is not a number")
}

private fun bool(v: Any?, what: String): Boolean = when (v) {
    is Boolean -> v
    "true" -> true
    "false" -> false
    else -> throw JSONException("$what is not a boolean")
}

private fun Number.asInt(): Int = if (this is Double || this is Float) toDouble().toInt() else toLong().toInt()

class JSONObject() {
    private val map = LinkedHashMap<String, Any?>()

    constructor(text: String) : this() {
        val v = Parser(text).value()
        if (v !is JSONObject) throw JSONException("Not a JSON object")
        map.putAll(v.map)
    }

    fun put(key: String, value: Any?): JSONObject { if (value == null) map.remove(key) else map[key] = value; return this }
    fun put(key: String, value: Int): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Long): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Double): JSONObject { map[key] = value; return this }
    fun put(key: String, value: Boolean): JSONObject { map[key] = value; return this }

    private fun need(key: String): Any = map[key] ?: throw JSONException("No value for $key")
    fun get(key: String): Any = need(key)
    fun opt(key: String): Any? = map[key]
    fun has(key: String) = map.containsKey(key)
    fun isNull(key: String) = map[key] == null || map[key] == NULL
    fun length() = map.size
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun remove(key: String): Any? = map.remove(key)

    fun getInt(key: String) = num(need(key), key).asInt()
    fun getLong(key: String) = num(need(key), key).toLong()
    fun getDouble(key: String) = num(need(key), key).toDouble()
    fun getBoolean(key: String) = bool(need(key), key)
    fun getString(key: String): String = need(key).let { if (it is String) it else render(it) }
    fun getJSONObject(key: String) = need(key) as? JSONObject ?: throw JSONException("$key is not an object")
    fun getJSONArray(key: String) = need(key) as? JSONArray ?: throw JSONException("$key is not an array")

    fun optInt(key: String, fallback: Int = 0) = runCatching { getInt(key) }.getOrDefault(fallback)
    fun optLong(key: String, fallback: Long = 0L) = runCatching { getLong(key) }.getOrDefault(fallback)
    fun optDouble(key: String, fallback: Double = Double.NaN) = runCatching { getDouble(key) }.getOrDefault(fallback)
    fun optBoolean(key: String, fallback: Boolean = false) = runCatching { getBoolean(key) }.getOrDefault(fallback)
    fun optString(key: String, fallback: String = ""): String =
        map[key]?.let { if (it == NULL) null else if (it is String) it else render(it) } ?: fallback
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray

    override fun toString(): String = buildString {
        append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) append(',')
            first = false
            quote(k, this); append(':'); write(v, this)
        }
        append('}')
    }

    companion object {
        val NULL: Any = object { override fun toString() = "null" }
    }
}

class JSONArray() {
    private val list = ArrayList<Any?>()

    constructor(text: String) : this() {
        val v = Parser(text).value()
        if (v !is JSONArray) throw JSONException("Not a JSON array")
        list.addAll(v.list)
    }

    constructor(items: Collection<*>) : this() { list.addAll(items) }

    fun put(value: Any?): JSONArray { list += value; return this }
    fun put(value: Int): JSONArray { list += value; return this }
    fun put(value: Long): JSONArray { list += value; return this }
    fun put(value: Double): JSONArray { list += value; return this }
    fun put(value: Boolean): JSONArray { list += value; return this }
    fun put(index: Int, value: Any?): JSONArray { while (list.size <= index) list += null; list[index] = value; return this }

    fun length() = list.size
    private fun need(i: Int): Any = list.getOrNull(i) ?: throw JSONException("No value at $i")
    fun get(i: Int): Any = need(i)
    fun opt(i: Int): Any? = list.getOrNull(i)
    fun isNull(i: Int) = list.getOrNull(i) == null || list[i] == JSONObject.NULL
    fun remove(i: Int): Any? = if (i in list.indices) list.removeAt(i) else null

    fun getInt(i: Int) = num(need(i), "[$i]").asInt()
    fun getLong(i: Int) = num(need(i), "[$i]").toLong()
    fun getDouble(i: Int) = num(need(i), "[$i]").toDouble()
    fun getBoolean(i: Int) = bool(need(i), "[$i]")
    fun getString(i: Int): String = need(i).let { if (it is String) it else render(it) }
    fun getJSONObject(i: Int) = need(i) as? JSONObject ?: throw JSONException("[$i] is not an object")
    fun getJSONArray(i: Int) = need(i) as? JSONArray ?: throw JSONException("[$i] is not an array")

    fun optInt(i: Int, fallback: Int = 0) = runCatching { getInt(i) }.getOrDefault(fallback)
    fun optLong(i: Int, fallback: Long = 0L) = runCatching { getLong(i) }.getOrDefault(fallback)
    fun optBoolean(i: Int, fallback: Boolean = false) = runCatching { getBoolean(i) }.getOrDefault(fallback)
    fun optString(i: Int, fallback: String = ""): String = list.getOrNull(i)?.let { if (it is String) it else render(it) } ?: fallback
    fun optJSONObject(i: Int): JSONObject? = list.getOrNull(i) as? JSONObject
    fun optJSONArray(i: Int): JSONArray? = list.getOrNull(i) as? JSONArray

    override fun toString(): String = buildString {
        append('[')
        list.forEachIndexed { k, v -> if (k > 0) append(','); write(v, this) }
        append(']')
    }
}

private fun render(v: Any?): String = buildString { write(v, this) }

private fun write(v: Any?, sb: StringBuilder) {
    when (v) {
        null -> sb.append("null")
        JSONObject.NULL -> sb.append("null")
        is String -> quote(v, sb)
        is Boolean, is Int, is Long, is Short, is Byte -> sb.append(v.toString())
        is Double -> sb.append(
            if (!v.isInfinite() && !v.isNaN() && v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        )
        is Float -> write(v.toDouble(), sb)
        is JSONObject -> sb.append(v.toString())
        is JSONArray -> sb.append(v.toString())
        is Collection<*> -> sb.append(JSONArray(v).toString())
        else -> quote(v.toString(), sb)
    }
}

private fun quote(s: String, sb: StringBuilder) {
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c.code < 0x20) sb.append("\\u").append(c.code.toString(16).padStart(4, '0')) else sb.append(c)
        }
    }
    sb.append('"')
}

private class Parser(val s: String) {
    var i = 0

    fun value(): Any? {
        ws()
        if (i >= s.length) throw JSONException("Unexpected end of JSON")
        val c = s[i]
        return when {
            c == '{' -> obj()
            c == '[' -> arr()
            c == '"' -> str()
            c == 't' -> word("true", true)
            c == 'f' -> word("false", false)
            c == 'n' -> word("null", JSONObject.NULL)
            c == '-' || c.isDigit() -> number()
            else -> throw JSONException("Unexpected character at $i")
        }
    }

    fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    fun word(w: String, v: Any): Any {
        if (!s.startsWith(w, i)) throw JSONException("Bad token at $i")
        i += w.length
        return v
    }

    fun obj(): JSONObject {
        val o = JSONObject(); i++; ws()
        if (s[i] == '}') { i++; return o }
        while (true) {
            ws(); val k = str(); ws()
            if (s[i] != ':') throw JSONException("Expected colon at $i")
            i++
            o.put(k, value()); ws()
            when (s[i]) {
                ',' -> i++
                '}' -> { i++; return o }
                else -> throw JSONException("Expected comma or brace at $i")
            }
        }
    }

    fun arr(): JSONArray {
        val a = JSONArray(); i++; ws()
        if (s[i] == ']') { i++; return a }
        while (true) {
            a.put(value()); ws()
            when (s[i]) {
                ',' -> i++
                ']' -> { i++; return a }
                else -> throw JSONException("Expected comma or bracket at $i")
            }
        }
    }

    fun str(): String {
        if (s[i] != '"') throw JSONException("Expected string at $i")
        i++
        val sb = StringBuilder()
        while (true) {
            val c = s[i++]
            if (c == '"') return sb.toString()
            if (c != '\\') { sb.append(c); continue }
            when (val e = s[i++]) {
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                else -> sb.append(e)
            }
        }
    }

    fun number(): Number {
        val start = i
        while (i < s.length && (s[i].isDigit() || s[i] == '+' || s[i] == '-' || s[i] == '.' || s[i] == 'e' || s[i] == 'E')) i++
        val t = s.substring(start, i)
        if (t.any { it == '.' || it == 'e' || it == 'E' }) return t.toDouble()
        val l = t.toLongOrNull() ?: return t.toDouble()
        return if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
    }
}
