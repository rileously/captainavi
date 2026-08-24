package com.captainavi.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.captainavi.app.data.local.entity.BreadcrumbEntity
import com.captainavi.app.data.local.entity.TripEntity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun exportAndShareGpx(
        context: Context,
        trip: TripEntity,
        breadcrumbs: List<BreadcrumbEntity>
    ) {
        try {
            val cacheDir = File(context.cacheDir, "gpx")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val tripDate = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(trip.startTime))
            val file = File(cacheDir, "FishingTrip_${tripDate}.gpx")

            val gpxContent = buildGpxXml(trip, breadcrumbs)

            FileWriter(file).use { writer ->
                writer.write(gpxContent)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Captain Avi Fishing Voyage Track - $tripDate")
                putExtra(Intent.EXTRA_TEXT, "Fishing track from Captain Avi (${String.format(Locale.US, "%.1f", trip.totalDistanceNm)} NM)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share GPX Track via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildGpxXml(trip: TripEntity, breadcrumbs: List<BreadcrumbEntity>): String {
        val title = if (trip.notes.isNotBlank()) trip.notes else "Fishing Voyage"
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="Captain Avi Marine Safety" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>$title</name>\n")
        sb.append("    <time>${isoFormat.format(Date(trip.startTime))}</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>$title</name>\n")
        sb.append("    <trkseg>\n")

        for (b in breadcrumbs) {
            sb.append("""      <trkpt lat="${b.latitude}" lon="${b.longitude}">""").append("\n")
            sb.append("        <ele>${b.altitudeMeters}</ele>\n")
            sb.append("        <time>${isoFormat.format(Date(b.timestamp))}</time>\n")
            sb.append("        <speed>${b.speedKnots}</speed>\n")
            sb.append("        <course>${b.bearingDegrees}</course>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }
}
