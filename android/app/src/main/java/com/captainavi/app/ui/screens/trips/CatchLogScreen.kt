package com.captainavi.app.ui.screens.trips

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.captainavi.app.CaptainAviApp
import com.captainavi.app.data.local.entity.CatchLogEntity
import com.captainavi.app.data.local.entity.TripStatus
import com.captainavi.app.data.repository.CatalogSpecies
import com.captainavi.app.data.repository.FishHabitat
import com.captainavi.app.data.repository.MaldivesFishCatalog
import com.captainavi.app.service.MarineLocationService
import com.captainavi.app.ui.theme.MarineTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CatchLogScreen(
    modifier: Modifier = Modifier,
    onStartTripHint: () -> Unit = {},
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as CaptainAviApp
    val scope = rememberCoroutineScope()
    val activeTrip by app.tripRepository.getActiveTrip().collectAsState(initial = null)
    val telemetry by MarineLocationService.telemetry.collectAsState()
    val emptyCatches = remember {
        kotlinx.coroutines.flow.flowOf(emptyList<CatchLogEntity>())
    }
    val catchFlow = remember(activeTrip?.id) {
        activeTrip?.id?.let { app.catchLogRepository.getForTrip(it) } ?: emptyCatches
    }
    val catches by catchFlow.collectAsState(initial = emptyList())

    var habitat by remember { mutableStateOf(FishHabitat.OCEAN) }
    val habitatSpecies = remember(habitat, context) {
        MaldivesFishCatalog.speciesFor(context, habitat)
    }
    var selectedSpeciesId by remember { mutableStateOf(habitatSpecies.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(habitat) {
        selectedSpeciesId = habitatSpecies.firstOrNull()?.id.orEmpty()
    }
    var customSpecies by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var showNotes by remember { mutableStateOf(false) }
    var showTripLog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selected = habitatSpecies.firstOrNull { it.id == selectedSpeciesId }
        ?: habitatSpecies.firstOrNull()
    val isOther = selected?.isOther == true
    val resolvedSpecies = if (isOther) {
        customSpecies.trim().ifBlank { selected?.commonName.orEmpty() }
    } else {
        selected?.commonName.orEmpty()
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val tripFish = catches.sumOf { it.count }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Catch",
                style = MaterialTheme.typography.titleMedium,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    activeTrip == null -> "Idle"
                    tripFish == 0 -> "0 fish"
                    else -> "$tripFish fish"
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
        }

        val trip = activeTrip
        if (trip == null || trip.status != TripStatus.ACTIVE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.SetMeal, null, tint = colors.accent, modifier = Modifier.size(40.dp))
                    Text(
                        "Start a fishing trip on the Helm to log catches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onStartTripHint,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    ) {
                        Text("Go to Helm", color = colors.onAccent)
                    }
                }
            }
            return@Column
        }

        HabitatTabRow(
            selected = habitat,
            onSelect = {
                habitat = it
                error = null
            },
            compact = true,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(bottom = 2.dp),
        ) {
            items(habitatSpecies, key = { it.id }) { species ->
                SpeciesPhotoCard(
                    species = species,
                    selected = species.id == selectedSpeciesId,
                    onClick = {
                        selectedSpeciesId = species.id
                        error = null
                    },
                    compact = true,
                )
            }
        }

        if (isOther) {
            OutlinedTextField(
                value = customSpecies,
                onValueChange = {
                    customSpecies = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                label = {
                    Text(
                        if (habitat == FishHabitat.OCEAN) "Ocean species" else "Reef species",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }

        // Compact action strip: count + selected name + save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { count = (count - 1).coerceAtLeast(1) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Remove, "Fewer", tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Text(
                "$count",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.width(28.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { count = (count + 1).coerceAtMost(999) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Default.Add, "More", tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    resolvedSpecies.ifBlank { "Select fish" },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                selected?.localName?.takeIf { it.isNotBlank() && !isOther }?.let { local ->
                    Text(
                        local,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = {
                    when {
                        resolvedSpecies.isBlank() -> error = "Pick a species"
                        count < 1 -> error = "Count ≥ 1"
                        else -> scope.launch {
                            runCatching {
                                app.catchLogRepository.logCatch(
                                    tripId = trip.id,
                                    species = resolvedSpecies,
                                    habitat = habitat,
                                    count = count,
                                    notes = notes.trim(),
                                    latitude = telemetry.latitude.takeIf { telemetry.hasGpsFix },
                                    longitude = telemetry.longitude.takeIf { telemetry.hasGpsFix },
                                )
                            }.onSuccess {
                                Toast.makeText(
                                    context,
                                    "Logged $count × $resolvedSpecies",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                count = 1
                                notes = ""
                                customSpecies = ""
                                error = null
                            }.onFailure {
                                error = it.message ?: "Could not save"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text("Save", color = colors.onAccent, style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { showNotes = !showNotes },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    if (showNotes) "Hide note" else "Note",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (catches.isNotEmpty()) {
                TextButton(
                    onClick = { showTripLog = !showTripLog },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        "Trip (${catches.size})",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Icon(
                        if (showTripLog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (showNotes) {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(120) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                label = { Text("Note", style = MaterialTheme.typography.labelSmall) },
            )
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.emergency)
        }

        if (showTripLog && catches.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(catches, key = { it.id }) { entry ->
                    val habitatLabel = FishHabitat.fromId(entry.habitat)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.card, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${entry.count}× ${entry.species} · ${timeFormat.format(Date(entry.timestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (habitatLabel) {
                                FishHabitat.OCEAN -> colors.accent
                                FishHabitat.REEF -> colors.success
                                FishHabitat.OTHER -> colors.textPrimary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { scope.launch { app.catchLogRepository.delete(entry.id) } },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = colors.emergency,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HabitatTabRow(
    selected: FishHabitat,
    onSelect: (FishHabitat) -> Unit,
    compact: Boolean = false,
) {
    val colors = MarineTheme.colors
    val vPad = if (compact) 6.dp else 10.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(FishHabitat.OCEAN, FishHabitat.REEF).forEach { option ->
            val active = selected == option
            val accent = if (option == FishHabitat.OCEAN) colors.accent else colors.success
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accent.copy(alpha = 0.22f) else colors.card)
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) accent else colors.card,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = vPad),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) accent else colors.textSecondary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun SpeciesPhotoCard(
    species: CatalogSpecies,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val colors = MarineTheme.colors
    val context = LocalContext.current
    val bitmap = remember(species.imageAssetPath) {
        species.imageAssetPath?.let { path ->
            runCatching {
                context.assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    val borderColor = when {
        selected && species.habitat == FishHabitat.REEF -> colors.success
        selected -> colors.accent
        else -> colors.border
    }
    val radius = if (compact) 8.dp else 12.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 0.92f else 1.35f)
            .clip(RoundedCornerShape(radius))
            .background(colors.card)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(radius))
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = species.commonName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = if (species.isOther) {
                    Icons.AutoMirrored.Filled.HelpOutline
                } else {
                    Icons.Default.SetMeal
                },
                contentDescription = null,
                tint = if (species.habitat == FishHabitat.REEF) colors.success else colors.accent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (compact) 22.dp else 36.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Text(
                text = species.commonName,
                color = Color.White,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (compact) 12.sp else 14.sp,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(borderColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("✓", color = colors.onAccent, fontSize = 10.sp)
            }
        }
    }
}
