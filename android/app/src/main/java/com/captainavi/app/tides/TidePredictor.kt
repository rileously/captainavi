package com.captainavi.app.tides

import kotlin.math.cos
import kotlin.math.sin

/**
 * Harmonic tide prediction for UHSLC Hanimaadhoo (station 117 / IOC `hani`).
 *
 * The offline fallback was fitted from the station-117 record for the UHSLC
 * 2002-2020 datum epoch using the same 68 UTide constituents documented by
 * UHSLC. Bundled UHSLC predictions take precedence when they cover the date.
 * Heights are astronomical predictions and exclude weather-driven residuals.
 */
data class TideConstituent(
    val name: String,
    val speedDegPerHour: Double,
    val cosineMeters: Double,
    val sineMeters: Double,
) {
    val omegaRadPerHour: Double = Math.toRadians(speedDegPerHour)
}

data class TideExtreme(
    val epochMillis: Long,
    val heightMslMeters: Double,
    val isHigh: Boolean,
)

data class TideSnapshot(
    val epochMillis: Long,
    val heightMslMeters: Double,
    val rising: Boolean,
    val nextHigh: TideExtreme?,
    val nextLow: TideExtreme?,
    val upcoming: List<TideExtreme>,
)

data class OfficialTideSample(
    val epochMillis: Long,
    val heightStationDatumMeters: Double,
)

object TideStation {
    const val uhslcId = 117
    const val iocCode = "hani"
    const val name = "Hanimaadhoo B"
    const val latitude = 6.76444
    const val longitude = 73.17250
    const val naivaadhooLatitude = 6.74722
    const val naivaadhooLongitude = 72.93333

    /** UHSLC 2002-2020 tidal datums, metres above station datum. */
    const val meanSeaLevelMeters = 1.030
    const val meanLowerLowWaterMeters = 0.611520709050625
    const val lowestAstronomicalTideMeters = 0.252
}

object TidePredictor {
    // Fixed-coefficient approximation to the nodally corrected UTide solution
    // over 2023-2029. Bundled official samples are used for the current window.
    private const val harmonicReferenceEpochMillis = 1_767_225_600_000L // 2026-01-01T00:00Z
    private const val harmonicMeanStationDatumMeters = 1.02813492139

    private val constituents = listOf(
        TideConstituent("SA", 0.041066677395, 0.02690219414, 0.06182903711),
        TideConstituent("SSA", 0.082137279494, 0.01641402092, 0.00653775380),
        TideConstituent("MSM", 0.471521049847, -0.00133967592, 0.00039579378),
        TideConstituent("MM", 0.544374708788, 0.00721092803, 0.00192391403),
        TideConstituent("MSF", 1.015895758635, 0.00112246180, -0.00042287621),
        TideConstituent("MF", 1.098033038128, -0.00969637607, -0.01218860206),
        TideConstituent("ALP1", 12.382765134196, -0.00092399666, -0.00090003379),
        TideConstituent("2Q1", 12.854286184043, 0.00226996585, -0.00367441514),
        TideConstituent("SIG1", 12.927139842984, -0.00301561756, -0.00351676837),
        TideConstituent("Q1", 13.398660892831, 0.01576522642, -0.01796964317),
        TideConstituent("RHO1", 13.471514551771, -0.00272687442, -0.00364389457),
        TideConstituent("O1", 13.943035601618, 0.04805980885, -0.09151639678),
        TideConstituent("TAU1", 14.025172881112, -0.00035713660, -0.00173138251),
        TideConstituent("BET1", 14.414556651465, -0.00055931230, -0.00017939875),
        TideConstituent("NO1", 14.496693930959, 0.00754228804, -0.00165323643),
        TideConstituent("CHI1", 14.569547589900, -0.00062484683, -0.00163101646),
        TideConstituent("PI1", 14.917864682858, 0.00268719938, 0.00113199971),
        TideConstituent("P1", 14.958931360253, 0.05370625460, -0.01258558251),
        TideConstituent("S1", 15.000001962352, 0.00405581217, -0.00311996221),
        TideConstituent("K1", 15.041068639747, 0.16490195750, -0.10749171082),
        TideConstituent("PSI1", 15.082135317142, -0.00126849854, 0.00165971460),
        TideConstituent("PHI1", 15.123205919240, -0.00231060934, 0.00440821657),
        TideConstituent("THE1", 15.512589689594, 0.00187753905, 0.00193368804),
        TideConstituent("J1", 15.585443348535, 0.01079597643, -0.00610360657),
        TideConstituent("SO1", 16.056964398382, 0.00067600146, 0.00207842986),
        TideConstituent("OO1", 16.139101677875, -0.00970588993, -0.00799261265),
        TideConstituent("UPS1", 16.683476386663, -0.00129619016, -0.00163734964),
        TideConstituent("OQ2", 27.350980115002, 0.00021719894, -0.00016013021),
        TideConstituent("EPS2", 27.423833773943, -0.00029638437, 0.00060051863),
        TideConstituent("2N2", 27.895354823790, 0.00149986810, 0.00335957107),
        TideConstituent("MU2", 27.968208482731, 0.00005153770, 0.00094671305),
        TideConstituent("N2", 28.439729532578, -0.01294054236, 0.03832543000),
        TideConstituent("NU2", 28.512583191518, 0.00728374890, 0.00477913548),
        TideConstituent("GAM2", 28.911250582424, 0.00116615157, 0.00021703643),
        TideConstituent("H1", 28.943037563971, 0.00074919335, 0.00408752228),
        TideConstituent("M2", 28.984104241365, -0.14722251350, 0.17002400026),
        TideConstituent("H2", 29.025170918760, -0.00012771951, 0.00044077302),
        TideConstituent("MKS2", 29.066241520859, 0.00063643052, 0.00041045433),
        TideConstituent("LDA2", 29.455625291212, 0.00165831579, 0.00177376190),
        TideConstituent("L2", 29.528478950153, 0.00599696486, -0.00450496364),
        TideConstituent("T2", 29.958933322605, -0.00429547747, -0.00726398143),
        TideConstituent("S2", 30.000000000000, -0.04319814269, -0.10301620458),
        TideConstituent("R2", 30.041066677395, 0.00093774650, 0.00066507805),
        TideConstituent("K2", 30.082137279494, 0.02848636701, 0.02211999624),
        TideConstituent("MSN2", 30.544374708788, -0.00052521427, 0.00027558931),
        TideConstituent("ETA2", 30.626511988281, 0.00073855773, 0.00077379890),
        TideConstituent("MO3", 42.927139842984, 0.00081180923, -0.00070888572),
        TideConstituent("M3", 43.476156362048, -0.00293200928, -0.00053847538),
        TideConstituent("SO3", 43.943035601618, -0.00037628954, 0.00073103232),
        TideConstituent("MK3", 44.025172881112, 0.00089871519, 0.00054713049),
        TideConstituent("SK3", 45.041068639747, -0.00007623618, 0.00174313391),
        TideConstituent("MN4", 57.423833773943, -0.00000983909, -0.00153053528),
        TideConstituent("M4", 57.968208482731, 0.00179220238, -0.00033115068),
        TideConstituent("SN4", 58.439729532578, 0.00024171084, 0.00012372140),
        TideConstituent("MS4", 58.984104241365, -0.00019267287, 0.00041990694),
        TideConstituent("MK4", 59.066241520859, -0.00018468651, 0.00020779534),
        TideConstituent("S4", 60.000000000000, -0.00130678360, 0.00000000000),
        TideConstituent("SK4", 60.082137279494, 0.00017449165, -0.00025217514),
        TideConstituent("2MK5", 73.009277122477, 0.00002566569, -0.00023119934),
        TideConstituent("2SK5", 75.041068639747, 0.00028968336, 0.00007560796),
        TideConstituent("2MN6", 86.407938015308, 0.00021448959, -0.00013339268),
        TideConstituent("M6", 86.952312724096, 0.00000719607, 0.00005098726),
        TideConstituent("2MS6", 87.968208482731, 0.00058032386, -0.00047272093),
        TideConstituent("2MK6", 88.050345762224, -0.00003507598, 0.00019056232),
        TideConstituent("2SM6", 88.984104241365, -0.00006468461, 0.00023124061),
        TideConstituent("MSK6", 89.066241520859, -0.00013157781, -0.00022695913),
        TideConstituent("3MK7", 101.993381363843, 0.00001810637, -0.00003256999),
        TideConstituent("M8", 115.936416965461, -0.00011096043, -0.00000815653),
    )

    @Volatile
    private var officialSamples: List<OfficialTideSample> = emptyList()

    @Volatile
    private var officialExtremes: List<TideExtreme> = emptyList()

    fun installOfficialData(
        samples: List<OfficialTideSample>,
        extremes: List<TideExtreme>,
    ) {
        officialSamples = samples.sortedBy { it.epochMillis }
        officialExtremes = extremes.sortedBy { it.epochMillis }
    }

    fun heightAbsoluteMeters(epochMillis: Long): Double {
        interpolateOfficialHeight(epochMillis)?.let { return it }
        val hours = (epochMillis - harmonicReferenceEpochMillis) / 3_600_000.0
        var height = harmonicMeanStationDatumMeters
        for (c in constituents) {
            val wt = c.omegaRadPerHour * hours
            height += c.cosineMeters * cos(wt) + c.sineMeters * sin(wt)
        }
        return height
    }

    private fun interpolateOfficialHeight(epochMillis: Long): Double? {
        val samples = officialSamples
        if (samples.size < 2 || epochMillis < samples.first().epochMillis || epochMillis > samples.last().epochMillis) {
            return null
        }
        val exact = samples.binarySearch { it.epochMillis.compareTo(epochMillis) }
        if (exact >= 0) return samples[exact].heightStationDatumMeters
        val upper = -exact - 1
        val lower = upper - 1
        if (lower < 0 || upper >= samples.size) return null

        val p1 = samples[lower]
        val p2 = samples[upper]
        val duration = (p2.epochMillis - p1.epochMillis).toDouble()
        if (duration <= 0.0) return p1.heightStationDatumMeters
        val x = (epochMillis - p1.epochMillis) / duration

        // Cubic Hermite interpolation keeps the hourly UHSLC samples exact while
        // drawing a smooth curve between them.
        val p0 = samples[(lower - 1).coerceAtLeast(0)]
        val p3 = samples[(upper + 1).coerceAtMost(samples.lastIndex)]
        val m1 = (p2.heightStationDatumMeters - p0.heightStationDatumMeters) /
            (p2.epochMillis - p0.epochMillis).coerceAtLeast(1L) * duration
        val m2 = (p3.heightStationDatumMeters - p1.heightStationDatumMeters) /
            (p3.epochMillis - p1.epochMillis).coerceAtLeast(1L) * duration
        val x2 = x * x
        val x3 = x2 * x
        return (2 * x3 - 3 * x2 + 1) * p1.heightStationDatumMeters +
            (x3 - 2 * x2 + x) * m1 +
            (-2 * x3 + 3 * x2) * p2.heightStationDatumMeters +
            (x3 - x2) * m2
    }

    fun heightMslMeters(epochMillis: Long): Double =
        heightAbsoluteMeters(epochMillis) - TideStation.meanSeaLevelMeters

    fun snapshot(epochMillis: Long): TideSnapshot {
        val height = heightMslMeters(epochMillis)
        val rising = heightMslMeters(epochMillis + 10 * 60_000L) > height
        val upcoming = extremaBetween(epochMillis, epochMillis + 30 * 3_600_000L)
            .filter { it.epochMillis > epochMillis + 90_000L }
            .take(4)
        return TideSnapshot(
            epochMillis = epochMillis,
            heightMslMeters = height,
            rising = rising,
            nextHigh = upcoming.firstOrNull { it.isHigh },
            nextLow = upcoming.firstOrNull { !it.isHigh },
            upcoming = upcoming,
        )
    }

    fun extremaBetween(fromMillis: Long, toMillis: Long): List<TideExtreme> {
        val published = officialExtremes
        if (published.isNotEmpty()) {
            val coveragePadding = 12 * 3_600_000L
            if (fromMillis >= published.first().epochMillis - coveragePadding &&
                toMillis <= published.last().epochMillis + coveragePadding
            ) {
                return published.filter { it.epochMillis in fromMillis..toMillis }
            }
        }

        val stepMs = 10 * 60_000L
        val start = fromMillis - stepMs
        val end = toMillis + stepMs
        val count = ((end - start) / stepMs).toInt() + 1
        val heights = DoubleArray(count) { i -> heightMslMeters(start + i * stepMs) }
        val found = ArrayList<TideExtreme>()
        for (i in 1 until count - 1) {
            val t = start + i * stepMs
            if (t !in fromMillis..toMillis) continue
            val previous = heights[i - 1]
            val current = heights[i]
            val next = heights[i + 1]
            val isHigh = current >= previous && current > next
            val isLow = current <= previous && current < next
            if (isHigh || isLow) found += refineExtreme(t, isHigh)
        }
        return found
    }

    private fun refineExtreme(centerMillis: Long, isHigh: Boolean): TideExtreme {
        var bestT = centerMillis
        var bestH = heightMslMeters(centerMillis)
        val window = 15 * 60_000L
        val fine = 60_000L
        var t = centerMillis - window
        while (t <= centerMillis + window) {
            val h = heightMslMeters(t)
            if (if (isHigh) h > bestH else h < bestH) {
                bestH = h
                bestT = t
            }
            t += fine
        }
        return TideExtreme(bestT, bestH, isHigh)
    }

    const val LAT_OFFSET_METERS = TideStation.meanSeaLevelMeters - TideStation.lowestAstronomicalTideMeters

    fun heightLatMeters(epochMillis: Long): Double =
        heightAbsoluteMeters(epochMillis) - TideStation.lowestAstronomicalTideMeters

    fun getMoonPhase(epochMillis: Long): MoonPhaseInfo {
        val baseNewMoon = 947182440000L // Jan 6, 2000 18:14 UTC
        val synodicDays = 29.530588853
        val daysSinceBase = (epochMillis - baseNewMoon) / 86_400_000.0
        val phaseDays = ((daysSinceBase % synodicDays) + synodicDays) % synodicDays
        val fraction = phaseDays / synodicDays
        val illumination = ((0.5 * (1.0 - cos(fraction * 2.0 * Math.PI))) * 100.0).toInt().coerceIn(0, 100)
        val isWaxing = fraction < 0.5
        val phaseName = when {
            fraction < 0.03 || fraction > 0.97 -> "New Moon"
            fraction < 0.22 -> "Waxing Crescent"
            fraction < 0.28 -> "First Quarter"
            fraction < 0.47 -> "Waxing Gibbous"
            fraction < 0.53 -> "Full Moon"
            fraction < 0.72 -> "Waning Gibbous"
            fraction < 0.78 -> "Last Quarter"
            else -> "Waning Crescent"
        }
        return MoonPhaseInfo(
            illuminationPct = illumination,
            phaseName = phaseName,
            isWaxing = isWaxing,
            shortLabel = "$illumination% ${if (isWaxing) "Waxing" else "Waning"}"
        )
    }
}

data class MoonPhaseInfo(
    val illuminationPct: Int,
    val phaseName: String,
    val isWaxing: Boolean,
    val shortLabel: String
)
