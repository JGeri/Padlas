package hu.padlas.feature.dedup.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.design.component.ButtonSize
import hu.padlas.core.design.component.ButtonStyle
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay

private val TILE = 96.dp
private const val FOLD_STEP_MS = 40L
private const val FOLDED_ALPHA = 0.22f
private const val FOLDED_SCALE = 0.84f

/**
 * One group: every member in a row, the keeper ringed and labelled. While the losers wait
 * behind the system dialog they fold away one after the other; a cancelled dialog brings
 * them back on the snapback spring, so the card always shows what is really on the phone.
 */
@Composable
internal fun DupGroupCard(
    group: DupGroupUi,
    pending: Boolean,
    enabled: Boolean,
    onResolve: (String) -> Unit,
    onPickWinner: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = haptics()
    val pickable = group.kind == DupKind.SIMILAR && !pending
    val loserOrder = remember(group.members) {
        group.members
            .filter { !it.isWinner && !it.isProtected }
            .mapIndexed { order, member -> member.assetId to order }
            .toMap()
    }

    PadlasCard(modifier = modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.l)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Space.l, end = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DedupCopy.groupTitle(group.kind, group.members.size),
                        style = PadlasTheme.type.bodyStrong,
                        color = Ink.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DedupCopy.groupSubtitle(group.losers.size, group.reclaimableBytes),
                        style = PadlasTheme.type.caption,
                        color = Ink.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Space.s))
                PadlasButton(
                    text = DedupCopy.GROUP_ACTION,
                    onClick = {
                        hapticFeedback?.collapse()
                        onResolve(group.id)
                    },
                    enabled = enabled && !pending && group.losers.isNotEmpty(),
                    icon = Icons.Rounded.Delete,
                    style = ButtonStyle.Secondary,
                    size = ButtonSize.Medium,
                )
            }
            Spacer(Modifier.height(Space.m))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(group.members, key = { member -> member.assetId }) { member ->
                    MemberTile(
                        member = member,
                        foldOrder = loserOrder[member.assetId],
                        folded = pending,
                        pickable = pickable,
                        onPick = {
                            hapticFeedback?.tick()
                            onPickWinner(group.id, member.assetId)
                        },
                    )
                }
            }
            if (group.kind == DupKind.SIMILAR) {
                Spacer(Modifier.height(Space.m))
                Text(
                    text = DedupCopy.PICK_KEEPER,
                    style = PadlasTheme.type.caption,
                    color = Ink.TextTertiary,
                    modifier = Modifier.padding(horizontal = Space.l),
                )
            }
        }
    }
}

@Composable
private fun MemberTile(
    member: DupMemberUi,
    foldOrder: Int?,
    folded: Boolean,
    pickable: Boolean,
    onPick: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val fold = remember { Animatable(0f) }
    val foldIn = motionSpec(Motion.settle<Float>())
    val foldOut = motionSpec(Motion.snapback<Float>())
    LaunchedEffect(folded, foldOrder) {
        if (foldOrder == null) {
            fold.snapTo(0f)
            return@LaunchedEffect
        }
        if (folded) {
            if (!reduced) delay(foldOrder * FOLD_STEP_MS)
            fold.animateTo(1f, foldIn)
        } else {
            fold.animateTo(0f, foldOut)
        }
    }
    val ring by animateColorAsState(
        targetValue = if (member.isWinner) Ink.Success else Color.Transparent,
        animationSpec = motionSpec(Motion.settle()),
        label = "keeperRing",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(TILE)
            .graphicsLayer {
                val f = fold.value
                alpha = 1f - (1f - FOLDED_ALPHA) * f
                scaleX = 1f - (1f - FOLDED_SCALE) * f
                scaleY = scaleX
            }
            .clip(Shapes.tile)
            .background(Ink.SurfaceHigh)
            .then(
                if (pickable) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.RadioButton,
                        onClickLabel = DedupCopy.KEEP_BADGE,
                        onClick = onPick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model = member.uri,
            contentDescription = member.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Ink.Background.copy(alpha = 0.7f)),
                ),
        )
        Text(
            text = Format.bytes(member.sizeBytes),
            style = PadlasTheme.type.micro,
            color = Ink.Text,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 5.dp),
        )
        when {
            member.isWinner -> Pill(
                text = DedupCopy.KEEP_BADGE,
                icon = Icons.Rounded.Check,
                color = Ink.Background,
                background = Ink.Success,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
            )

            member.isProtected -> Pill(
                text = DedupCopy.PROTECTED_BADGE,
                icon = Icons.Rounded.Shield,
                color = Ink.Text,
                background = Ink.Info.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
            )

            else -> Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(22.dp)
                    .clip(Shapes.pill)
                    .background(Ink.Background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = Ink.Text,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .border(2.dp, ring, Shapes.tile),
        )
    }
}
