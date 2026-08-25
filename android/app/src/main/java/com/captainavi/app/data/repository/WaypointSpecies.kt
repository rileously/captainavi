package com.captainavi.app.data.repository

import com.captainavi.app.data.local.entity.WaypointEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val speciesJson = Json { ignoreUnknownKeys = true }

/** Encode selected species common names for [WaypointEntity.targetSpeciesJson]. */
fun encodeTargetSpecies(species: List<String>): String {
    val cleaned = species.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return speciesJson.encodeToString(cleaned)
}

/** Decode [WaypointEntity.targetSpeciesJson] into common names. */
fun decodeTargetSpecies(raw: String?): List<String> {
    if (raw.isNullOrBlank() || raw == "[]") return emptyList()
    return runCatching { speciesJson.decodeFromString<List<String>>(raw) }
        .getOrDefault(emptyList())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

fun WaypointEntity.targetSpecies(): List<String> = decodeTargetSpecies(targetSpeciesJson)

fun WaypointEntity.withTargetSpecies(species: List<String>): WaypointEntity =
    copy(targetSpeciesJson = encodeTargetSpecies(species))

fun summarizeTargetSpecies(species: List<String>, limit: Int = 3): String {
    if (species.isEmpty()) return ""
    val shown = species.take(limit)
    val more = (species.size - shown.size).coerceAtLeast(0)
    return buildString {
        append(shown.joinToString(", "))
        if (more > 0) append(" +$more")
    }
}
