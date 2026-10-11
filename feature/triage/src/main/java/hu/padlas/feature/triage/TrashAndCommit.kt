package hu.padlas.feature.triage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.hairlineBottom
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space

/**
 * INV-2, said out loud three times. A file in any bin still occupies the bytes, the two
 * system bins do not know about each other, and the pending batch has not moved at all -
 * this is the lie the category is built on, so the screen states it per row.
 */
@Composable
internal fun TrashSection(
    trash: TrashState,
    pendingCount: Int,
    pendingBytes: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Space.xxl)) {
        SectionHeader(title = "Ahol még foglalt a hely")
        PadlasCard(modifier = Modifier.fillMaxWidth().padding(top = Space.m), contentPadding = 0.dp) {
            Column {
                TrashRow(
                    icon = Icons.Rounded.DeleteOutline,
                    tint = Ink.Info,
                    title = "Rendszerkuka",
                    detail = "30 nap után magától ürül, addig foglalja a helyet.",
                    bytes = trash.systemBytes,
                    divider = true,
                )
                TrashRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    tint = Ink.Accent,
                    title = "Galéria kukája",
                    detail = "Külön kuka, a Galéria appban kell üríteni.",
                    bytes = trash.galleryBytes,
                    divider = true,
                )
                TrashRow(
                    icon = Icons.Rounded.Checklist,
                    tint = Ink.Danger,
                    title = "Kijelölve, még nem törölve",
                    detail = "${Format.count(pendingCount)} fájl. A törlés gomb megnyomásáig a helyén marad.",
                    bytes = pendingBytes,
                    divider = false,
                )
            }
        }
    }
}

@Composable
private fun TrashRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String,
    bytes: Long,
    divider: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.hairlineBottom() else Modifier)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon, tint = tint, size = 36.dp, iconSize = 18.dp)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PadlasTheme.type.bodyStrong,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = detail, style = PadlasTheme.type.caption, color = Ink.TextSecondary)
        }
        Spacer(Modifier.width(Space.s))
        Text(text = Format.bytes(bytes), style = PadlasTheme.type.numeric, color = Ink.Text, maxLines = 1)
    }
}

/**
 * Spec 6.6 ⑥: the copy names the irreversibility before the finger goes down; the system
 * dialog that follows is the one batched confirmation (INV-3). The figures roll with every
 * tap in the grid, so the bar always shows the batch as it stands.
 */
@Composable
internal fun CommitBar(
    count: Int,
    bytes: Long,
    busy: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        BarFigures(count = count, noun = "fájl törlésre", bytes = bytes)
        AnimatedContent(
            targetState = count == 0,
            transitionSpec = { fadeIn(finite(reduced)) togetherWith fadeOut(finite(reduced)) },
            label = "commitNote",
        ) { nothing ->
            Text(
                text = if (nothing) {
                    "Mindent megtartasz, nincs mit törölni."
                } else {
                    "Véglegesen törlődnek. A telefon még egyszer rákérdez."
                },
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PadlasButton(
            text = if (count == 0) "Nincs kijelölt fájl" else "Kijelöltek törlése",
            onClick = onConfirm,
            enabled = count > 0 && !busy,
            icon = Icons.Rounded.Delete,
            style = ButtonStyle.Destructive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
