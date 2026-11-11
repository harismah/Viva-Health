package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A half-width vital sign: icon and uppercase label, a large value with its unit, and a status
 * chip. Twin of `VivaVitalsTile.swift`.
 */
@Composable
fun VivaVitalsTile(
    icon: ImageVector,
    label: String,
    value: String?,
    status: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    statusColor: Color = VivaStyle.textPrimary,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VivaStyle.cardRadius))
            .vivaPressable(onClick)
            .background(VivaStyle.card)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.defaultMinSize(minHeight = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(18.dp))
            }
            Text(
                label.uppercase(),
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                color = VivaStyle.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value ?: "--", Modifier.alignByBaseline(), style = VivaFont.number(34), color = VivaStyle.textPrimary, maxLines = 1, softWrap = false)
            if (unit != null && value != null) {
                Text(
                    unit, Modifier.alignByBaseline(),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = VivaStyle.textPrimary, maxLines = 1,
                )
            }
        }
        // An in-range reading reads as done at a glance: green tint, green text, a tick. Anything else
        // keeps the quiet chip with a dot in its status colour.
        val inRange = statusColor == VivaStyle.positive
        val toned = inRange || statusColor == VivaStyle.warning
        Row(
            Modifier
                .height(22.dp)
                .background(if (toned) statusColor.copy(alpha = 0.16f) else VivaStyle.chip, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (inRange) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
            } else {
                Box(Modifier.size(5.dp).background(statusColor, CircleShape))
            }
            Text(
                status, style = VivaFont.number(11, FontWeight.Medium),
                color = if (toned) statusColor else VivaStyle.textSecondary, maxLines = 1,
            )
        }
    }
}
