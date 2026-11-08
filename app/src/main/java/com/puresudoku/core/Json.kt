package com.puresudoku.core

import org.json.JSONArray
import org.json.JSONObject

fun IntArray.toJson(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }
fun JSONArray.toIntArray(): IntArray = IntArray(length()) { getInt(it) }
fun JSONObject.ints(key: String): IntArray = getJSONArray(key).toIntArray()
fun BooleanArray.toJson(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }
fun JSONArray.toBooleanArray(): BooleanArray = BooleanArray(length()) { getBoolean(it) }
