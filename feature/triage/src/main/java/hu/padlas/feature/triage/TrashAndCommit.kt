package hu.padlas.feature.triage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.HoldToConfirm
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.SectionLabel
import hu.padlas.core.design.component.raisedSurface
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
        SectionLabel(text = "Kuka")
        PadlasCard(modifier = Modifier.fillMaxWidth().padding(top = Space.s)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                TrashRow(
                    title = "Rendszerkuka",
                    detail = "30 nap múlva ürül magától. A hely addig foglalt.",
                    bytes = trash.systemBytes,
                )
                TrashRow(
                    title = "Galéria kukája",
                    detail = "Külön kuka, külön kell üríteni. A hely foglalt.",
                    bytes = trash.galleryBytes,
                )
                TrashRow(
                    title = "Függő köteg",
                    detail = "${Format.count(pendingCount)} fájl kijelölve, még egyik sem törlődött. A hely foglalt.",
                    bytes = pendingBytes,
                )
            }
        }
    }
}

@Composable
private fun TrashRow(title: String, detail: String, bytes: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = PadlasTheme.type.body, color = Ink.Text)
            NumericText(text = Format.bytes(bytes), color = Ink.Accent)
        }
        Text(
            text = detail,
            style = PadlasTheme.type.micro,
            color = Ink.TextTertiary,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/**
 * Spec 6.6 ⑥: the label carries the count, never a verb, and the copy names the
 * irreversibility before the finger goes down.
 */
@Composable
internal fun CommitBar(
    count: Int,
    bytes: Long,
    busy: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .raisedSurface(background = Ink.SurfaceHigh)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(
            text = if (count == 0) {
                "Mindent megtartasz. Nincs mit törölni."
            } else {
                "${Format.count(count)} fájl végleges törlése. Nem vonható vissza."
            },
            style = PadlasTheme.type.body,
            color = Ink.Text,
        )
        if (count > 0) {
            NumericText(text = "${Format.bytes(bytes)} szabadul fel", color = Ink.TextSecondary)
        }
        HoldToConfirm(
            label = "${Format.count(count)} fájl",
            onConfirm = onConfirm,
            enabled = count > 0 && !busy,
            holdMillis = HOLD_MS,
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
        )
    }
}

private const val HOLD_MS = 2_000
internal val CommitBarInset = 180.dp
