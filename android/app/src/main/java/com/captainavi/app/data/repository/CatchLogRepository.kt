package com.captainavi.app.data.repository

import com.captainavi.app.data.local.dao.CatchLogDao
import com.captainavi.app.data.local.entity.CatchLogEntity
import kotlinx.coroutines.flow.Flow

class CatchLogRepository(
    private val catchLogDao: CatchLogDao,
) {
    fun getForTrip(tripId: String): Flow<List<CatchLogEntity>> =
        catchLogDao.getForTrip(tripId)

    fun totalFishForTrip(tripId: String): Flow<Int> =
        catchLogDao.totalFishForTrip(tripId)

    suspend fun getForTripList(tripId: String): List<CatchLogEntity> =
        catchLogDao.getForTripList(tripId)

    suspend fun logCatch(
        tripId: String,
        species: String,
        count: Int,
        habitat: FishHabitat = FishHabitat.OTHER,
        notes: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): CatchLogEntity {
        val cleanedSpecies = species.trim()
        require(cleanedSpecies.isNotEmpty()) { "Species is required" }
        require(count >= 1) { "Count must be at least 1" }
        val entry = CatchLogEntity(
            tripId = tripId,
            species = cleanedSpecies,
            habitat = habitat.id,
            count = count,
            notes = notes.trim(),
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
        )
        catchLogDao.insert(entry)
        return entry
    }

    suspend fun delete(id: String) {
        catchLogDao.deleteById(id)
    }

    companion object {
        @Deprecated("Use MaldivesFishCatalog / FishHabitat instead", ReplaceWith("MaldivesFishCatalog"))
        val SPECIES_PRESETS: List<String> =
            MaldivesFishCatalog.FALLBACK.map { it.commonName } + "Other"
    }
}

/** One-line summary for logbook cards, e.g. "6 fish · 4 ocean · 2 reef". */
fun summarizeCatches(catches: List<CatchLogEntity>): String {
    if (catches.isEmpty()) return ""
    val total = catches.sumOf { it.count.coerceAtLeast(0) }
    val ocean = catches.filter { FishHabitat.fromId(it.habitat) == FishHabitat.OCEAN }
        .sumOf { it.count.coerceAtLeast(0) }
    val reef = catches.filter { FishHabitat.fromId(it.habitat) == FishHabitat.REEF }
        .sumOf { it.count.coerceAtLeast(0) }
    val fishLabel = if (total == 1) "1 fish" else "$total fish"
    val habitatPart = buildList {
        if (ocean > 0) add("$ocean ocean")
        if (reef > 0) add("$reef reef")
    }.joinToString(" · ")
    val species = catches.map { it.species }.distinct().take(2)
    val more = (catches.map { it.species }.distinct().size - species.size).coerceAtLeast(0)
    val speciesPart = buildString {
        append(species.joinToString(", "))
        if (more > 0) append(" +$more")
    }
    return buildString {
        append(fishLabel)
        if (habitatPart.isNotBlank()) {
            append(" · ")
            append(habitatPart)
        }
        if (speciesPart.isNotBlank()) {
            append(" · ")
            append(speciesPart)
        }
    }
}
