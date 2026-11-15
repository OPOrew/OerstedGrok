package com.oersted.trainer.data

import android.content.Context
import kotlinx.serialization.json.Json

/** Loads static content exported from the web app into the assets/data folder as JSON files. */
class Repository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun text(name: String): String =
        context.assets.open("data/$name").bufferedReader().use { it.readText() }

    val systems: List<SystemDef> by lazy { json.decodeFromString(text("systems.json")) }
    val parts: List<Part> by lazy { json.decodeFromString(text("parts.json")) }
    val amm: AmmFile by lazy { json.decodeFromString(text("amm.json")) }
}
