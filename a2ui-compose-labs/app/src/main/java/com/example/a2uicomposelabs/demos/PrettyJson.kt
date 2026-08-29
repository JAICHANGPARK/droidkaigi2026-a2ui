package com.example.a2uicomposelabs.demos

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val Pretty = Json { prettyPrint = true }

internal fun prettyJson(obj: JsonObject): String =
    Pretty.encodeToString(JsonObject.serializer(), obj)
