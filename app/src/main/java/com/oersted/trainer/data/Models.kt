package com.oersted.trainer.data

import kotlinx.serialization.Serializable

/** Mirrors src/data/systems.ts SystemDef. */
@Serializable
data class SystemDef(
    val id: String,
    val ata: String,
    val title: String,
    val short: String,
    val blurb: String,
    val power: String,
    val panel: String,
    val accent: String,
)

/** Mirrors src/data/components.ts Part. x/y are schematic coordinates from the web app. */
@Serializable
data class Part(
    val id: String,
    val name: String,
    val system: String,
    val ata: String,
    val group: String,
    val function: String,
    val flow: String,
    val relationships: String,
    val specs: List<String>,
    val controls: String,
    val x: Double,
    val y: Double,
)

@Serializable
data class AmmChapter(
    val ata: String,
    val title: String,
    val file: String,
    val trainer: Boolean = false,
)

@Serializable
data class AmmMeta(
    val book: String,
    val revision: String,
    val date: String,
    val model: String,
    val customer: String,
    val source: String,
)

@Serializable
data class AmmFile(
    val meta: AmmMeta,
    val chapters: List<AmmChapter>,
)
