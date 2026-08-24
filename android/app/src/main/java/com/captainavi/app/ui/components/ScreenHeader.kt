package com.captainavi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.captainavi.app.ui.theme.MarineTheme

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    val colors = MarineTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .border(1.dp, colors.accent.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CA",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "CAPTAIN AVI",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .background(colors.card, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun marineTextFieldColors(): TextFieldColors {
    val colors = MarineTheme.colors
    return TextFieldDefaults.colors(
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
        disabledContainerColor = colors.surface,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedIndicatorColor = colors.accent,
        unfocusedIndicatorColor = colors.border,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.accent,
        focusedPlaceholderColor = colors.textMuted,
        unfocusedPlaceholderColor = colors.textMuted
    )
}
