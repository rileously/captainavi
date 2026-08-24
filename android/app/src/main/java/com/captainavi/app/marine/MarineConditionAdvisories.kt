package com.captainavi.app.marine

import com.captainavi.app.data.remote.MarineConditions

/** Plain-language model advisories. These are not official marine warnings. */
fun marineConditionAdvisories(conditions: MarineConditions): List<String> = buildList {
    if ((conditions.waveHeightMeters ?: 0.0) >= 2.5) add("Wave height is 2.5 m or higher")
    if ((conditions.windGustKnots ?: 0.0) >= 25.0) add("Wind gusts are 25 kt or higher")
    if ((conditions.visibilityMeters ?: Double.MAX_VALUE) < 2_000.0) add("Visibility is below 2 km")
    if ((conditions.weatherCode ?: 0) in 95..99) add("Thunderstorm conditions indicated")
}

fun weatherCodeLabel(code: Int?): String = when (code) {
    null -> "Unknown"
    0 -> "Clear"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Rain showers"
    in 85..86 -> "Snow showers"
    in 95..99 -> "Thunderstorm"
    else -> "Mixed conditions"
}
