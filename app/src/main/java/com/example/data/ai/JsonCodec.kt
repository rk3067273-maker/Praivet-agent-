package com.example.data.ai

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

object JsonCodec {
    private val moshi: Moshi = Moshi.Builder().build()

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java
    )
    private val listType = Types.newParameterizedType(
        List::class.java,
        Any::class.java
    )

    val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(mapType)
    val listAdapter: JsonAdapter<List<Any?>> = moshi.adapter(listType)

    fun parseObjectOrNull(raw: String?): Map<String, Any?>? {
        if (raw.isNullOrBlank()) return null
        return try {
            mapAdapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun parseArrayOrNull(raw: String?): List<Any?>? {
        if (raw.isNullOrBlank()) return null
        return try {
            listAdapter.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun toJson(map: Map<String, Any?>): String = mapAdapter.toJson(map)

    fun toJson(list: List<Any?>): String = listAdapter.toJson(list)
}
