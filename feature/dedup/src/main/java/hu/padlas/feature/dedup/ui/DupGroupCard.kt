package hu.padlas.feature.dedup.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import hu.padlas.core.common.format.Format
import hu.padlas.core.database.entity.DupKind
import hu.padlas.core.design.component.NumericText
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasCard
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.motion.staggerDelayMs
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TILE = 88.dp
private val FAN_OFFSET = 20.dp
private const val FAN_ROTATION = 6f
private val WINNER_LIFT = 8.dp
private const val WINNER_KICK = 1.03f
private const val COLLAPSE_STEP_MS = 40L

private enum class CardPhase { FANNED, COLLAPSING, SETTLED }

/**
 * Signature animation 3. The members enter fanned out like cards held in a hand; on a
 * decision the winner rises, the losers physically slide into its position one after the
 * other, and the reclaimed size rolls up underneath. Nothing here loops: every spring
 * settles and the card stands still afterwards.
 */
@Composable
internal fun DupGroupCard(
    group: DupGroupUi,
    resetToken: Int,
    onResolve: (String) -> Unit,
    onPickWinner: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = haptics()
    val reduced = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val weight = motionSpec(Motion.weight<Float>())
    val settle = motionSpec(Motion.settle<Float>())
    val snapback = motionSpec(Motion.snapback<Float>())
    val entranceDelays = group.members.indices.map { staggerDelayMs(it).toLong() }
    val step = if (reduced) 0L else COLLAPSE_STEP_MS

    val spread = remember(group.id) { group.members.map { Animatable(0f) } }
    val lift = remember(group.id) { Animatable(0f) }
    val kick = remember(group.id) { Animatable(1f) }
    var phase by remember(group.id) { mutableStateOf(CardPhase.FANNED) }
    var reclaimed by remember(group.id) { mutableStateOf(false) }

    LaunchedFan(group.id, spread, entranceDelays, weight)

    LaunchedReset(resetToken, phase == CardPhase.SETTLED) {
        reclaimed = false
        kick.snapTo(1f)
        spread.forEachIndexed { index, animatable ->
            launch {
                delay(entranceDelays[index])
                animatable.animateTo(1f, weight)
            }
        }
        lift.animateTo(0f, settle)
        phase = CardPhase.FANNED
    }

    val resolve = {
        if (phase == CardPhase.FANNED) {
            phase = CardPhase.COLLAPSING
            scope.launch {
                lift.animateTo(1f, settle)
                val loserIndices = group.members.indices.filter { !group.members[it].isWinner }
                loserIndices.forEachIndexed { order, index ->
                    hapticFeedback?.tick(fadingTick(order))
                    if (order == loserIndices.lastIndex) {
                        spread[index].animateTo(0f, weight)
                    } else {
                        launch { spread[index].animateTo(0f, weight) }
                        delay(step)
                    }
                }
                kick.animateTo(WINNER_KICK, snapback)
                kick.animateTo(1f, snapback)
                reclaimed = true
                phase = CardPhase.SETTLED
                onResolve(group.id)
            }
        }
    }

    PadlasCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            GroupHeader(group)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TILE + Space.xl),
                contentAlignment = Alignment.Center,
            ) {
                val loserIndices = group.members.indices.filter { !group.members[it].isWinner }
                loserIndices.forEachIndexed { order, index ->
                    val member = group.members[index]
                    MemberTile(
                        member = member,
                        spread = spread[index].value,
                        slot = slotOf(order, loserIndices.size),
                        pickable = group.kind == DupKind.SIMILAR,
                        onPick = { onPickWinner(group.id, member.assetId) },
                    )
                }
                group.winner?.let { winner ->
                    WinnerTile(
                        member = winner,
                        lift = lift.value,
                        kick = kick.value,
                        pickable = group.kind == DupKind.SIMILAR,
                        onPick = { onPickWinner(group.id, winner.assetId) },
                    )
                }
            }
            ReclaimedLine(group.reclaimableBytes, reclaimed)
            PadlasButton(
                text = DedupCopy.groupAction(group.kind),
                onClick = resolve,
                enabled = phase == CardPhase.FANNED && group.losers.isNotEmpty(),
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupHeader(group: DupGroupUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DedupCopy.groupSummary(group.members.size, group.losers.size),
            style = PadlasTheme.type.label,
            color = Ink.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NumericText(text = Format.bytes(group.reclaimableBytes), color = Ink.Accent)
    }
}

/** The freed size appears only after the collapse, in rolling digits. */
@Composable
private fun ReclaimedLine(bytes: Long, visible: Boolean) {
    if (!visible) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        RollingNumber(
            value = Format.bytesValue(bytes),
            style = PadlasTheme.type.numericLarge,
            color = Ink.Accent,
        )
        Text(
            text = " ${Format.bytesUnit(bytes)}",
            style = PadlasTheme.type.numeric,
            color = Ink.Accent,
        )
    }
}

@Composable
private fun MemberTile(
    member: DupMemberUi,
    spread: Float,
    slot: Float,
    pickable: Boolean,
    onPick: () -> Unit,
) {
    Tile(
        member = member,
        pickable = pickable,
        onPick = onPick,
        modifier = Modifier.graphicsLayer {
            translationX = FAN_OFFSET.toPx() * slot * spread
            translationY = WINNER_LIFT.toPx() * abs(slot) * spread * 0.5f
            rotationZ = FAN_ROTATION * slot * spread
        },
    )
}

@Composable
private fun WinnerTile(
    member: DupMemberUi,
    lift: Float,
    kick: Float,
    pickable: Boolean,
    onPick: () -> Unit,
) {
    Tile(
        member = member,
        pickable = pickable,
        onPick = onPick,
        frameAlpha = lift,
        modifier = Modifier.graphicsLayer {
            translationY = -WINNER_LIFT.toPx() * lift
            scaleX = kick
            scaleY = kick
        },
    )
}

@Composable
private fun Tile(
    member: DupMemberUi,
    pickable: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    frameAlpha: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    val frame = if (member.isWinner) Ink.Accent.copy(alpha = 0.35f + 0.65f * frameAlpha) else Ink.Hairline
    Box(
        modifier = modifier
            .size(TILE)
            .clip(Shapes.card)
            .background(Ink.SurfaceHigh, Shapes.card)
            .border(Dp.Hairline, frame, Shapes.card)
            .then(
                if (pickable) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onPick)
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model = member.uri,
            contentDescription = member.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(TILE).clip(Shapes.card),
        )
        if (member.isWinner) {
            Text(
                text = DedupCopy.WINNER_BADGE,
                style = PadlasTheme.type.micro,
                color = Ink.Accent,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xs),
            )
        }
    }
}

private fun slotOf(order: Int, count: Int): Float {
    if (count <= 1) return 0f
    val half = (count - 1) / 2f
    return (order - half) / half
}

/** Every tick quieter than the last: the group is settling, not being hit. */
private fun fadingTick(order: Int): Float = (0.5f - order * 0.08f).coerceAtLeast(0.12f)
