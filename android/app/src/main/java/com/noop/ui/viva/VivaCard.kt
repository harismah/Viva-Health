package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What the trailing glyph on a card says a tap does. */
enum class VivaCardAccessory { NONE, CHEVRON, INFO, EXPAND }

private val VivaCardAccessory.icon: ImageVector?
    get() = when (this) {
        VivaCardAccessory.NONE -> null
        VivaCardAccessory.CHEVRON -> Icons.Rounded.ChevronRight
        VivaCardAccessory.INFO -> Icons.Outlined.Info
        VivaCardAccessory.EXPAND -> Icons.Rounded.OpenInFull
    }

/**
 * The flat card every Viva screen is built from: one fill, no rim, an optional uppercase title and
 * a trailing glyph that says what a tap does. Twin of `VivaCard.swift`.
 */
@Composable
fun VivaCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accessory: VivaCardAccessory = VivaCardAccessory.NONE,
    fill: Color = VivaStyle.card,
    padding: Dp = VivaStyle.cardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(VivaStyle.cardRadius)
    val accessoryIcon = accessory.icon
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.vivaPressable(onClick) else Modifier)
            .background(fill)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (title != null || accessoryIcon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One weighted slot takes all the spare width, so the glyph sits flush right.
                if (title != null) VivaCardTitle(title, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (accessoryIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(accessoryIcon, contentDescription = null, tint = VivaStyle.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        content()
    }
}

/** The trailing action a section header can carry. */
data class VivaSectionAction(val title: String, val style: Style, val handler: () -> Unit) {
    enum class Style { CHIP, PLUS, LINK }
}

/** Large mixed-case page section title with an optional trailing action. */
@Composable
fun VivaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: VivaSectionAction? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = VivaFont.sectionTitle, color = VivaStyle.textPrimary)
            if (subtitle != null) Text(subtitle, style = VivaFont.caption, color = VivaStyle.textSecondary)
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .vivaPressable(action.handler)
                    .semantics { role = Role.Button; contentDescription = action.title },
            ) {
                when (action.style) {
                    VivaSectionAction.Style.PLUS -> Box(
                        Modifier.size(36.dp).background(VivaStyle.textPrimary, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                    VivaSectionAction.Style.CHIP -> Row(
                        Modifier
                            .height(24.dp)
                            .background(
                                Brush.horizontalGradient(listOf(VivaStyle.chipGradientStart, VivaStyle.chipGradientEnd)),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(action.title.uppercase(), style = VivaFont.action, color = VivaStyle.textPrimary)
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(13.dp))
                    }
                    VivaSectionAction.Style.LINK -> VivaLinkLabel(action.title, color = VivaStyle.textPrimary)
                }
            }
        }
    }
}

/** Uppercase text link with a trailing arrow (`OPEN SLEEP DETAILS →`). */
@Composable
fun VivaLinkLabel(title: String, modifier: Modifier = Modifier, color: Color = VivaStyle.link) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = VivaFont.action, color = color)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
    }
}

/** Full-width quiet button inside a card (`EDIT ALARM`, `ADD ACTIVITY`). */
@Composable
fun VivaCardButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .vivaPressable(onClick)
            .background(VivaStyle.chip)
            .semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(16.dp))
        Text(title.uppercase(), style = VivaFont.action, color = VivaStyle.textPrimary)
    }
}
