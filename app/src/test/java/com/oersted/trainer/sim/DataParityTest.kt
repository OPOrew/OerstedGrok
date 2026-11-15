package com.oersted.trainer.sim

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the exported JSON still matches the counts of the web source (systems 15, parts 183, AMM 42). */
class DataParityTest {
    private val dataDir = listOf("src/main/assets/data", "app/src/main/assets/data").map(::File).first { it.exists() }

    @Test fun exportCounts() {
        val j = Json.parseToJsonElement(File(dataDir, "systems.json").readText())
        assertEquals(15, j.jsonArray.size)
        assertEquals(183, Json.parseToJsonElement(File(dataDir, "parts.json").readText()).jsonArray.size)
        assertEquals(42, Json.parseToJsonElement(File(dataDir, "amm.json").readText()).jsonObject["chapters"]!!.jsonArray.size)
        assertTrue(true)
    }
}
