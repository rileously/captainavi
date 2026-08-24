package com.captainavi.app.ui.screens.tides

/** Maps a local hour to the full-width, edge-aligned daily chart strip. */
internal fun tideChartX(hour: Float, width: Float): Float = hour / 24f * width
