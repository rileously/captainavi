package com.captainavi.app.data.repository

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EmergencyPhoneContact(
    val serviceLabel: String,
    val organization: String,
    val phones: List<String>,
    val sourceLabel: String,
    val sourceUrl: String,
)

@Serializable
data class IslandEmergencyContacts(
    val islandId: Int,
    val islandName: String,
    val atoll: String,
    val council: EmergencyPhoneContact? = null,
    val health: EmergencyPhoneContact? = null,
)

data class IslandEmergencyDirectoryData(
    val snapshotDate: String,
    val contactsByIslandId: Map<Int, IslandEmergencyContacts>,
)

class IslandEmergencyContactDirectory(context: Context) {
    private val directory = runCatching {
        context.applicationContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            parseIslandEmergencyContactDirectory(reader.readText())
        }
    }.getOrElse {
        IslandEmergencyDirectoryData(snapshotDate = "", contactsByIslandId = emptyMap())
    }

    val snapshotDate: String
        get() = directory.snapshotDate

    fun contactsFor(island: IslandPlace): IslandEmergencyContacts? =
        directory.contactsByIslandId[island.id]

    companion object {
        private const val ASSET_NAME = "island_emergency_contacts_v1.json"
    }
}

internal fun parseIslandEmergencyContactDirectory(
    encoded: String,
    json: Json = Json { ignoreUnknownKeys = true },
): IslandEmergencyDirectoryData {
    val asset = json.decodeFromString<IslandEmergencyContactAsset>(encoded)
    require(asset.version == ASSET_FORMAT_VERSION) {
        "Unsupported island emergency contact version ${asset.version}"
    }
    require(asset.count == asset.contacts.size) { "Emergency contact asset count mismatch" }
    require(asset.contacts.map(IslandEmergencyContacts::islandId).distinct().size == asset.contacts.size) {
        "Emergency contact asset contains duplicate island IDs"
    }
    require(asset.contacts.all { contact ->
        listOfNotNull(contact.council, contact.health).all { service ->
            service.phones.isNotEmpty() && service.phones.all(::isDialableMaldivesNumber)
        }
    }) { "Emergency contact asset contains an invalid phone number" }

    return IslandEmergencyDirectoryData(
        snapshotDate = asset.snapshotDate,
        contactsByIslandId = asset.contacts.associateBy(IslandEmergencyContacts::islandId),
    )
}

internal fun isDialableMaldivesNumber(phone: String): Boolean =
    phone.length in 4..7 && phone.all(Char::isDigit)

private const val ASSET_FORMAT_VERSION = 1

@Serializable
private data class IslandEmergencyContactAsset(
    val version: Int,
    val snapshotDate: String,
    val count: Int,
    val contacts: List<IslandEmergencyContacts>,
)
