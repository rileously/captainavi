package com.captainavi.app.localization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    DHIVEHI("dv", "ދިވެހި")
}

object LanguageManager {
    var currentLanguage by mutableStateOf(Language.ENGLISH)
        private set

    fun setLanguage(language: Language) {
        currentLanguage = language
    }

    fun toggleLanguage() {
        currentLanguage = if (currentLanguage == Language.ENGLISH) Language.DHIVEHI else Language.ENGLISH
    }

    val isDhivehi: Boolean
        get() = currentLanguage == Language.DHIVEHI

    val startFishingTrip: String
        get() = if (isDhivehi) "ދަތުރު ފެށުން" else "Start fishing trip"

    val endTripReturnHome: String
        get() = if (isDhivehi) "ދަތުރު ނިންމުން" else "End trip"

    val navigateToDestination: String
        get() = if (isDhivehi) "މިސްރާބު ޖެހުން" else "Navigate to"

    val clearDestination: String
        get() = if (isDhivehi) "މިސްރާބު ކެންސަލް" else "Clear destination"

    val setHome: String
        get() = if (isDhivehi) "ބަނދަރު ނޯޓުކުރުން" else "Set home"

    val dropAnchor: String
        get() = if (isDhivehi) "ނަގިލި އެއްލުން" else "Drop anchor"

    val weighAnchor: String
        get() = if (isDhivehi) "ނަގިލި ނެގުން" else "Weigh anchor"

    val map: String
        get() = if (isDhivehi) "ޗާޓު" else "Chart"

    val mob: String
        get() = if (isDhivehi) "މީހަކު ވެއްޓުން" else "MOB"

    val sos: String
        get() = if (isDhivehi) "ކުއްލި އެހީ" else "SOS"

    val returnToHome: String
        get() = if (isDhivehi) "ގެއަށް / ބަނދަރަށް" else "Return to home"

    val steerHeading: String
        get() = if (isDhivehi) "ދުއްވަންވީ މިސްރާބު" else "Steer"

    val knots: String
        get() = if (isDhivehi) "ނޮޓްސް" else "KNOTS"

    val vesselHeading: String
        get() = if (isDhivehi) "ބޯޓުގެ މިސްރާބު" else "Heading"

    val homeBearing: String
        get() = if (isDhivehi) "ބަނދަރު ހުރި ދިމާ" else "Home bearing"

    val anchorSet: String
        get() = if (isDhivehi) "ނަގިލި އެއްލާފައި" else "Anchor set"

    val anchorDragging: String
        get() = if (isDhivehi) "ސަމާލު: ނަގިލި ކަހަނީ!" else "Anchor dragging"

    val downloadOfflineMap: String
        get() = if (isDhivehi) "އޮފްލައިން ޗާޓު ޑައުންލޯޑް" else "Download offline chart"

    val shareGpx: String
        get() = if (isDhivehi) "ދަތުރުގެ ޓްރެކް ފޮނުވާ" else "Share GPX track"

    val tripActive: String
        get() = if (isDhivehi) "ދަތުރު ކުރިއަށްދަނީ" else "Trip active"

    val waitingForGps: String
        get() = if (isDhivehi) "GPS ބަލަނީ…" else "Waiting for GPS fix"

    val noGpsFix: String
        get() = if (isDhivehi) "GPS ނެތް" else "No GPS fix"

    val navigateToPrefix: String
        get() = if (isDhivehi) "މިސްރާބު" else "Navigating to"

    val tides: String
        get() = if (isDhivehi) "މަޑި" else "Tides"

    val highTide: String
        get() = if (isDhivehi) "މަޑި އެރުން" else "HIGH"

    val lowTide: String
        get() = if (isDhivehi) "މަޑި ދިޔުން" else "LOW"

    val tideRising: String
        get() = if (isDhivehi) "އެރަނީ" else "Rising"

    val tideFalling: String
        get() = if (isDhivehi) "ދަނީ" else "Falling"

    val meanSeaLevel: String
        get() = if (isDhivehi) "މެން ލެވެލް" else "vs mean sea level"

    val hanimaadhooNearNaivaadhoo: String
        get() = if (isDhivehi) "ހަނިމާދޫ · ނައިވާދޫ ކައިރި 14 NM" else "Hanimaadhoo · 14 NM from Naivaadhoo"

    val inHomeHarbour: String
        get() = if (isDhivehi) "ބަނދަރުގައި" else "In Home Port"

    val pastTides: String
        get() = if (isDhivehi) "ވޭތުވެދިޔަ މަޑި" else "Past Tides"

    val futureTides: String
        get() = if (isDhivehi) "ކުރިއަށް އޮތް މަޑި" else "Forecast Tides"

    val springTide: String
        get() = if (isDhivehi) "ދިޔަ ހަނދޫ (ބޮޑު ދިޔަ)" else "Spring Tide (Strong)"

    val neapTide: String
        get() = if (isDhivehi) "ދިޔަ ފަޅޯ (ކުޑަ ދިޔަ)" else "Neap Tide (Moderate)"

    val tidalRange: String
        get() = if (isDhivehi) "މަޑީގެ ފަރަގު" else "Tidal Range"
}
