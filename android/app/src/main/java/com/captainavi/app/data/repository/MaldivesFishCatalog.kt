package com.captainavi.app.data.repository

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class FishHabitat(val id: String, val label: String) {
    OCEAN("OCEAN", "Ocean fish"),
    REEF("REEF", "Reef fish"),
    OTHER("OTHER", "Other"),
    ;

    companion object {
        fun fromId(raw: String?): FishHabitat =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

@Serializable
data class MaldivesFishSpecies(
    val id: String,
    val commonName: String,
    val scientificName: String = "",
    val localName: String = "",
)

@Serializable
data class MaldivesFishHabitatGroup(
    val id: String,
    val label: String,
    val plan: String = "",
    val species: List<MaldivesFishSpecies> = emptyList(),
)

@Serializable
data class MaldivesFishCatalogAsset(
    val version: Int,
    val source: String,
    val note: String = "",
    val habitats: List<MaldivesFishHabitatGroup> = emptyList(),
)

data class CatalogSpecies(
    val habitat: FishHabitat,
    val id: String,
    val commonName: String,
    val scientificName: String,
    val localName: String,
    val plan: String,
) {
    val chipLabel: String
        get() = if (localName.isNotBlank()) "$commonName · $localName" else commonName

    /** Offline reference photo under assets/, or null for “other” placeholders. */
    val imageAssetPath: String?
        get() = when {
            id.endsWith("_other") -> null
            else -> "fish/$id.jpg"
        }

    val isOther: Boolean
        get() = id.endsWith("_other") || commonName.contains("(other)", ignoreCase = true)
}

/**
 * Offline Maldives fish catalog aligned with MoFMRA fishery management plans:
 * tuna-plan species → Ocean; reef-plan annex species → Reef.
 */
object MaldivesFishCatalog {
    const val ASSET_NAME = "maldives_fish_species_v1.json"
    private const val ASSET_FORMAT_VERSION = 2

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<CatalogSpecies>? = null

    fun species(context: Context): List<CatalogSpecies> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }
    }

    fun speciesFor(context: Context, habitat: FishHabitat): List<CatalogSpecies> =
        species(context).filter { it.habitat == habitat }

    fun resolveHabitat(context: Context, commonName: String): FishHabitat {
        val key = commonName.trim().lowercase()
        if (key.isEmpty()) return FishHabitat.OTHER
        return species(context).firstOrNull { entry ->
            entry.commonName.equals(commonName.trim(), ignoreCase = true) ||
                entry.localName.equals(commonName.trim(), ignoreCase = true)
        }?.habitat ?: FishHabitat.OTHER
    }

    fun resolveSpecies(context: Context, commonName: String): CatalogSpecies? {
        val key = commonName.trim()
        if (key.isEmpty()) return null
        return species(context).firstOrNull { entry ->
            entry.commonName.equals(key, ignoreCase = true) ||
                entry.localName.equals(key, ignoreCase = true)
        }
    }

    /** Resolve stored fish-spot common names into catalog entries (unknown names kept as OTHER). */
    fun resolveSpeciesList(context: Context, commonNames: List<String>): List<CatalogSpecies> {
        return commonNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .map { name ->
                resolveSpecies(context, name) ?: CatalogSpecies(
                    habitat = FishHabitat.OTHER,
                    id = "spot_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                    commonName = name,
                    scientificName = "",
                    localName = "",
                    plan = "Fish spot",
                )
            }
    }

    private fun load(context: Context): List<CatalogSpecies> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            val asset = json.decodeFromString<MaldivesFishCatalogAsset>(reader.readText())
            require(asset.version == ASSET_FORMAT_VERSION) {
                "Unsupported Maldives fish catalog version ${asset.version}"
            }
            asset.habitats.flatMap { group ->
                val habitat = FishHabitat.fromId(group.id).takeIf {
                    it == FishHabitat.OCEAN || it == FishHabitat.REEF
                } ?: return@flatMap emptyList()
                group.species.map { species ->
                    CatalogSpecies(
                        habitat = habitat,
                        id = species.id,
                        commonName = species.commonName,
                        scientificName = species.scientificName,
                        localName = species.localName,
                        plan = group.plan,
                    )
                }
            }
        }
    }.getOrElse { FALLBACK }

    /** Hardcoded fallback if the asset is missing — same split as the JSON. */
    val FALLBACK: List<CatalogSpecies> = listOf(
        CatalogSpecies(FishHabitat.OCEAN, "skipjack", "Skipjack tuna", "Katsuwonus pelamis", "Kalhubilamas", "Maldives Tuna Fishery Management Plan"),
        CatalogSpecies(FishHabitat.OCEAN, "yellowfin", "Yellowfin tuna", "Thunnus albacares", "Kanneli", "Maldives Tuna Fishery Management Plan"),
        CatalogSpecies(FishHabitat.OCEAN, "bigeye", "Bigeye tuna", "Thunnus obesus", "Loabodu kanneli", "Maldives Tuna Fishery Management Plan"),
        CatalogSpecies(FishHabitat.OCEAN, "frigate", "Frigate tuna", "Auxis thazard", "Ragondi", "Maldives Tuna Fishery Management Plan"),
        CatalogSpecies(FishHabitat.OCEAN, "kawakawa", "Kawakawa", "Euthynnus affinis", "Latti", "Maldives Tuna Fishery Management Plan"),
        CatalogSpecies(FishHabitat.OCEAN, "mahimahi", "Mahimahi / Dorado", "Coryphaena hippurus", "", "Ocean pelagic (common catch)"),
        CatalogSpecies(FishHabitat.OCEAN, "sailfish", "Sailfish", "Istiophorus platypterus", "", "Ocean pelagic (common catch)"),
        CatalogSpecies(FishHabitat.OCEAN, "marlin", "Blue marlin", "Makaira nigricans", "", "Ocean pelagic (common catch)"),
        CatalogSpecies(FishHabitat.OCEAN, "swordfish", "Swordfish", "Xiphias gladius", "", "Ocean pelagic (common catch)"),
        CatalogSpecies(FishHabitat.OCEAN, "spanish_mackerel", "Narrow-barred Spanish mackerel", "Scomberomorus commerson", "", "Ocean pelagic (common catch)"),
        CatalogSpecies(FishHabitat.OCEAN, "ocean_other", "Ocean fish (other)", "", "", "Maldives Tuna Fishery Management Plan"),
        CatalogSpecies(FishHabitat.REEF, "raiymas", "Two-spot red snapper", "Lutjanus bohar", "Raiymas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "ginimas", "Humpback red snapper", "Lutjanus gibbus", "Ginimas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "giulhu", "Green jobfish", "Aprion virescens", "Giulhu", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "rusty_jobfish", "Rusty jobfish", "Aphareus rutilans", "Rankarumas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "foniyamas", "Black and white snapper", "Macolor niger", "Foniyamas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "one_spot_snapper", "One-spot snapper", "Lutjanus monostigma", "Filolhu", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "bluestripe_snapper", "Bluestripe snapper", "Lutjanus kasmira", "Dhon reen’dhoomas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "blacktail_snapper", "Blacktail snapper", "Lutjanus fulvus", "Dhon' mas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "emperor_red_snapper", "Emperor red snapper", "Lutjanus sebae", "Maa ginimas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "longface_emperor", "Longface emperor", "Lethrinus olivaceus", "Kashi thun filolhu", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "thumbprint_emperor", "Thumbprint emperor", "Lethrinus harak", "Lah' filolhu", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "spangled_emperor", "Spangled emperor", "Lethrinus nebulosus", "", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "rainbow_runner", "Rainbow runner", "Elagatis bipinnulata", "Maaniyamas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "bluefin_trevally", "Bluefin trevally", "Caranx melampygus", "Fani han’dhi", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "bigeye_trevally", "Bigeye trevally", "Caranx sexfasciatus", "Haluvimas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "giant_trevally", "Giant trevally", "Caranx ignobilis", "Muda han’dhi", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "black_trevally", "Black trevally", "Caranx lugubris", "Kalha han’dhi", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "golden_trevally", "Golden trevally", "Gnathanodon speciosus", "Libaas han’dhi", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "almaco_jack", "Almaco jack", "Seriola rivoliana", "An’dhunmas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "queenfish", "Doublespotted queenfish", "Scomberoides lysan", "Kashi vaali", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "wahoo", "Wahoo", "Acanthocybium solandri", "Kurumas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "dogtooth", "Dogtooth tuna", "Gymnosarda unicolor", "Voshimas", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "barracuda", "Great barracuda", "Sphyraena barracuda", "Tholhi", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "coral_trout", "Coral trout", "Plectropomus leopardus", "", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "peacock_grouper", "Peacock grouper", "Cephalopholis argus", "", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "sweetlips", "Oriental sweetlips", "Plectorhinchus vittatus", "Kan'du guruva", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "napoleon_wrasse", "Napoleon wrasse", "Cheilinus undulatus", "Maa hulhun'bu landa", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "parrotfish", "Parrotfish", "Scarus ghobban", "Landa", "Maldives Reef Fishery Management Plan (Annex 2)"),
        CatalogSpecies(FishHabitat.REEF, "reef_other", "Reef fish (other)", "", "", "Maldives Reef Fishery Management Plan (Annex 2)"),
    )
}
