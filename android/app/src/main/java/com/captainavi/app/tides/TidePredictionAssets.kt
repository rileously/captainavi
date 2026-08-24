package com.captainavi.app.tides

import android.content.Context
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Loads the bundled UHSLC station-117 prediction snapshot and tide table. */
object TidePredictionAssets {
    private const val RAPID_ASSET = "tides_hanimaadhoo_117_rapid.csv"
    private const val HIGH_LOW_ASSET = "tides_hanimaadhoo_117_highlow_2023_2029.csv"

    private val hourlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH", Locale.US)
    private val extremeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.US)

    fun install(context: Context): Boolean = runCatching {
        val samples = loadRapidPredictions(context)
        val extremes = loadPublishedExtremes(context)
        require(samples.isNotEmpty()) { "UHSLC station-117 prediction asset is empty" }
        require(extremes.isNotEmpty()) { "UHSLC station-117 high/low asset is empty" }
        TidePredictor.installOfficialData(samples, extremes)
        true
    }.getOrDefault(false)

    private fun loadRapidPredictions(context: Context): List<OfficialTideSample> {
        val samples = ArrayList<OfficialTideSample>(3_000)
        var mllwStationDatum = TideStation.meanLowerLowWaterMeters
        context.assets.open(RAPID_ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                val columns = line.trim().split(',')
                if (columns.size < 2) return@forEach
                columns.getOrNull(6)?.toDoubleOrNull()?.let { mllwStationDatum = it }
                val predictionMllw = columns[1].toDoubleOrNull() ?: return@forEach
                val epochMillis = runCatching {
                    LocalDateTime.parse(columns[0], hourlyFormatter)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                }.getOrNull() ?: return@forEach
                samples += OfficialTideSample(
                    epochMillis = epochMillis,
                    heightStationDatumMeters = predictionMllw + mllwStationDatum,
                )
            }
        }
        return samples
    }

    private fun loadPublishedExtremes(context: Context): List<TideExtreme> {
        val extremes = ArrayList<TideExtreme>(11_000)
        context.assets.open(HIGH_LOW_ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                val columns = line.trim().split(',')
                if (columns.size < 3) return@forEach
                val heightStationMm = columns[1].toDoubleOrNull() ?: return@forEach
                val epochMillis = runCatching {
                    LocalDateTime.parse(columns[0], extremeFormatter)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                }.getOrNull() ?: return@forEach
                extremes += TideExtreme(
                    epochMillis = epochMillis,
                    heightMslMeters = heightStationMm / 1_000.0 - TideStation.meanSeaLevelMeters,
                    isHigh = columns[2].equals("High Tide", ignoreCase = true),
                )
            }
        }
        return extremes
    }
}
