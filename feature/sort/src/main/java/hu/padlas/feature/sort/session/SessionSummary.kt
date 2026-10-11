package hu.padlas.feature.sort.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.StatTile
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import hu.padlas.feature.sort.DeleteMode

@Composable
internal fun SessionSummary(
    state: SessionUiState,
    onDelete: () -> Unit,
    onReview: () -> Unit,
    onOpenTrash: () -> Unit,
    onSyncFavorites: () -> Unit,
    onNewSession: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.page, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        IconBubble(icon = Icons.Rounded.DoneAll, tint = Ink.Success, size = 76.dp, iconSize = 38.dp)
        Text(text = "Kész!", style = PadlasTheme.type.headline, color = Ink.Text)
        Text(
            text = "${Format.count(state.total)} elemet néztél át.",
            style = PadlasTheme.type.body,
            color = Ink.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                label = "Megtartva",
                value = Format.count(state.kept),
                icon = Icons.Rounded.Check,
                tint = Ink.Success,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Kedvenc",
                value = Format.count(state.favorited),
                icon = Icons.Rounded.Star,
                tint = Ink.Favorite,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Törlésre",
                value = Format.count(state.deleted),
                caption = Format.bytes(state.deletedBytes),
                icon = Icons.Rounded.DeleteOutline,
                tint = Ink.Danger,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.pending.itemCount > 0) {
            PadlasCard(modifier = Modifier.fillMaxWidth(), background = Ink.Surface, border = Ink.Danger.copy(alpha = 0.35f)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(icon = Icons.Rounded.Delete, tint = Ink.Danger)
                        Column(Modifier.padding(start = Space.m).weight(1f)) {
                            Text(
                                text = "${Format.count(state.pending.itemCount)} elem vár törlésre",
                                style = PadlasTheme.type.bodyStrong,
                                color = Ink.Text,
                            )
                            Text(
                                text = deleteExplanation(state),
                                style = PadlasTheme.type.caption,
                                color = Ink.TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    PadlasButton(
                        text = "Törlés · ${Format.bytes(state.pending.totalBytes)}",
                        onClick = onDelete,
                        style = ButtonStyle.Destructive,
                        icon = Icons.Rounded.Delete,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PadlasButton(
                        text = "Átnézem előtte",
                        onClick = onReview,
                        style = ButtonStyle.Secondary,
                        icon = Icons.Rounded.GridView,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else if (state.freedBytes > 0) {
            PadlasCard(modifier = Modifier.fillMaxWidth(), border = Ink.Success.copy(alpha = 0.35f)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(icon = Icons.Rounded.CheckCircle, tint = Ink.Success)
                        Column(Modifier.padding(start = Space.m).weight(1f)) {
                            Text(
                                text = if (state.freedPermanently) {
                                    "${Format.bytes(state.freedBytes)} felszabadult"
                                } else {
                                    "${Format.bytes(state.freedBytes)} a kukában"
                                },
                                style = PadlasTheme.type.bodyStrong,
                                color = Ink.Text,
                            )
                            if (!state.freedPermanently) {
                                Text(
                                    text = "A rendszer 30 nap múlva véglegesen törli. Ha most kell a hely, ürítsd a kukát.",
                                    style = PadlasTheme.type.caption,
                                    color = Ink.TextSecondary,
                                )
                            }
                        }
                    }
                    if (!state.freedPermanently && state.deleteMode == DeleteMode.TRASH) {
                        PadlasButton(
                            text = "Kuka megnyitása",
                            onClick = onOpenTrash,
                            style = ButtonStyle.Secondary,
                            icon = Icons.Rounded.DeleteOutline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (state.unsyncedFavorites > 0) {
            ListRow(
                title = "${Format.count(state.unsyncedFavorites)} kedvenc a Galériába",
                subtitle = "Megjelölöm őket kedvencként a telefon galériájában is.",
                icon = Icons.Rounded.AddCircle,
                iconTint = Ink.Favorite,
                onClick = onSyncFavorites,
            )
        }

        Spacer(Modifier.height(Space.s))
        PadlasButton(text = "Új válogatás", onClick = onNewSession, modifier = Modifier.fillMaxWidth())
        PadlasButton(text = "Bezárás", onClick = onClose, style = ButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
    }
}
