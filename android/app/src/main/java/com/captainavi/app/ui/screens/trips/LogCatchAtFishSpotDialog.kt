package com.captainavi.app.ui.screens.trips

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.local.entity.WaypointEntity
import com.captainavi.app.data.repository.CatalogSpecies
import com.captainavi.app.data.repository.MaldivesFishCatalog
import com.captainavi.app.data.repository.targetSpecies
import com.captainavi.app.ui.theme.MarineTheme
import kotlinx.coroutines.launch

/**
 * Quick catch log limited to a fish spot's target species.
 * Tap a species tile to log immediately (default count); adjust count first if needed.
 */
@Composable
fun LogCatchAtFishSpotDialog(
    waypoint: WaypointEntity,
    tripId: String,
    onDismiss: () -> Unit,
    onLogged: (species: String, count: Int) -> Unit = { _, _ -> },
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val spotSpecies = remember(waypoint.id, waypoint.targetSpeciesJson, context) {
        MaldivesFishCatalog.resolveSpeciesList(context, waypoint.targetSpecies())
    }
    var count by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var lastLogged by remember { mutableStateOf<String?>(null) }

    fun logSpecies(species: CatalogSpecies) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching {
                app.catchLogRepository.logCatch(
                    tripId = tripId,
                    species = species.commonName,
                    habitat = species.habitat,
                    count = count,
                    notes = "at ${waypoint.name}",
                    latitude = waypoint.latitude,
                    longitude = waypoint.longitude,
                )
            }.onSuccess {
                lastLogged = "${count}× ${species.commonName}"
                onLogged(species.commonName, count)
                Toast.makeText(context, "Logged $lastLogged", Toast.LENGTH_SHORT).show()
                count = 1
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Could not log catch",
                    Toast.LENGTH_LONG,
                ).show()
            }
            busy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.72f)
                .background(colors.background, RoundedCornerShape(16.dp))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Log catch · ${waypoint.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.caution,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (spotSpecies.isEmpty()) {
                        "No species on this spot — edit the mark first."
                    } else {
                        "Tap a fish to log (spot species only)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )

                if (spotSpecies.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.card, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        IconButton(
                            onClick = { count = (count - 1).coerceAtLeast(1) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Remove, "Fewer", tint = colors.accent)
                        }
                        Text(
                            "$count",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(
                            onClick = { count = (count + 1).coerceAtMost(999) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Add, "More", tint = colors.accent)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(spotSpecies, key = { it.id }) { species ->
                            SpeciesPhotoCard(
                                species = species,
                                selected = false,
                                onClick = { logSpecies(species) },
                                compact = true,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Set Ocean/Reef fish on this spot, then log from the chart.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                lastLogged?.let {
                    Text(
                        "Last: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.success,
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
