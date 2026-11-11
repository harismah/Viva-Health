package com.noop.ui.viva

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.ui.BrandMark

// The Viva page chrome: floating tab pill, coach button, coach bar and the detail-screen frame.
// Twins of StrandiOS/App/VivaTabBar.swift, Strand/Screens/VivaCoachBar.swift and
// Strand/Screens/VivaDetailScaffold.swift.

private val coachRingBrush: Brush
    get() = Brush.linearGradient(listOf(VivaStyle.coachRing, VivaStyle.link))

/** One destination in the tab pill. [tag] is the shell's own tab id, so the pill never renumbers a tab. */
data class VivaTabItem(val tag: Int, val title: String, val icon: ImageVector)

object VivaTabBar {
    /** Height of the pill; screens reserve this plus the bottom margin under their scroll content. */
    val height: Dp = 62.dp

    /** Home, Health and More, with the same tags the iPhone shell uses. */
    val items: List<VivaTabItem> = listOf(
        VivaTabItem(tag = 0, title = "Home", icon = Icons.Outlined.Home),
        VivaTabItem(tag = 1, title = "Health", icon = Icons.Outlined.MonitorHeart),
        VivaTabItem(tag = 4, title = "More", icon = Icons.Rounded.Menu),
    )
}

/**
 * The floating bottom chrome: a pill holding the tab destinations and, beside it, the round coach
 * button.
 */
@Composable
fun VivaTabBar(
    selection: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    items: List<VivaTabItem> = VivaTabBar.items,
    showsCoach: Boolean = true,
    onCoach: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pill = RoundedCornerShape(26.dp)
        Row(
            Modifier
                .weight(1f)
                .shadow(12.dp, pill, clip = false)
                .background(VivaStyle.tabBar, pill)
                .border(0.5.dp, VivaStyle.divider, pill)
                .padding(horizontal = 10.dp),
        ) {
            items.forEach { item ->
                val isSelected = selection == item.tag
                val tint = if (isSelected) VivaStyle.textPrimary else VivaStyle.textTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .height(VivaTabBar.height)
                        .vivaPressable { onSelect(item.tag) }
                        .semantics(mergeDescendants = true) { role = Role.Tab; selected = isSelected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(item.title, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = tint, maxLines = 1)
                }
            }
        }
        if (showsCoach) VivaCoachButton(onClick = onCoach)
    }
}

/** The round coach entry point that sits beside the tab pill and floats alone on info screens. */
@Composable
fun VivaCoachButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .size(VivaTabBar.height)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .vivaPressable(onClick)
            .background(VivaStyle.coachButton)
            .semantics { role = Role.Button; contentDescription = "Coach" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(32.dp).border(1.5.dp, coachRingBrush, CircleShape))
        BrandMark(size = 17.dp)
    }
}

/**
 * The coach line pinned to the bottom of a detail screen: a ringed plus, one line of the coach's
 * current note, and a mic glyph. Tapping anywhere opens the coach.
 */
@Composable
fun VivaCoachBar(message: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(10.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .vivaPressable(onClick)
            .background(VivaStyle.coachButton)
            .border(0.8.dp, VivaStyle.coachRing.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 9.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Coach" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).border(1.5.dp, coachRingBrush, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(18.dp))
        }
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 15.sp),
            color = VivaStyle.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = VivaStyle.textPrimary, modifier = Modifier.size(19.dp).padding(end = 1.dp))
    }
}

enum class VivaBarLeading { BACK, CLOSE }
enum class VivaBarTrailing { NONE, INFO, GEAR }

/**
 * Page chrome for a pushed Viva screen: the canvas, a custom top bar (back chevron, centred
 * uppercase title, optional info or settings button) and a scrolling column of cards, with an
 * optional coach bar pinned above the system navigation.
 */
@Composable
fun VivaDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    leading: VivaBarLeading = VivaBarLeading.BACK,
    trailing: VivaBarTrailing = VivaBarTrailing.NONE,
    onTrailing: (() -> Unit)? = null,
    coachMessage: String? = null,
    onCoach: (() -> Unit)? = null,
    spacing: Dp = VivaStyle.cardGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    val showsCoach = coachMessage != null && onCoach != null
    VivaCanvas(modifier) {
        Column(Modifier.fillMaxSize()) {
            VivaTopBar(title, leading, onBack, trailing, onTrailing)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VivaStyle.pagePadding)
                    .padding(top = 8.dp, bottom = if (showsCoach) 96.dp else 32.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
        if (coachMessage != null && onCoach != null) {
            VivaCoachBar(
                message = coachMessage,
                onClick = onCoach,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun VivaTopBar(
    title: String,
    leading: VivaBarLeading,
    onBack: () -> Unit,
    trailing: VivaBarTrailing,
    onTrailing: (() -> Unit)?,
) {
    Box(
        Modifier.fillMaxWidth().background(VivaStyle.canvasTop).statusBarsPadding().height(52.dp).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title.uppercase(), style = VivaFont.barTitle, color = VivaStyle.textPrimary, maxLines = 1)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val back = leading == VivaBarLeading.BACK
            BarButton(
                icon = if (back) Icons.Rounded.ChevronLeft else Icons.Rounded.Close,
                description = if (back) "Back" else "Close",
                tint = VivaStyle.textPrimary,
                iconSize = if (back) 30.dp else 24.dp,
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            if (trailing != VivaBarTrailing.NONE && onTrailing != null) {
                val info = trailing == VivaBarTrailing.INFO
                BarButton(
                    icon = if (info) Icons.Outlined.Info else Icons.Outlined.Settings,
                    description = if (info) "About this screen" else "Settings",
                    tint = VivaStyle.textSecondary,
                    iconSize = 24.dp,
                    onClick = onTrailing,
                )
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, iconSize: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).vivaPressable(onClick).semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** The body of a bottom sheet of plain explanation: a heading and paragraphs on the canvas. */
@Composable
fun VivaInfoSheetContent(title: String, paragraphs: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(VivaStyle.canvasBottom)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = VivaFont.sectionTitle, color = VivaStyle.textPrimary)
        paragraphs.forEach { paragraph ->
            Text(paragraph, style = VivaFont.body, color = VivaStyle.textSecondary)
        }
    }
}

/**
 * Hosts a pushed Viva screen inside the app shell. The shell has already padded for the status bar,
 * so the inset is marked consumed here and the screen's own top bar does not pad for it twice.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VivaInShell(content: @Composable () -> Unit) {
    Box(Modifier.consumeWindowInsets(WindowInsets.statusBars)) { content() }
}
