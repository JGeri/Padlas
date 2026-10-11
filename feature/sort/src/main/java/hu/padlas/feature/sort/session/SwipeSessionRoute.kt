package hu.padlas.feature.sort.session

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.PadlasButton
import hu.padlas.core.design.component.PadlasIconButton
import hu.padlas.core.design.component.RollingNumber
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.glass
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.morphBounds
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.feature.sort.DeleteMode
import hu.padlas.feature.sort.SwipeAction
import hu.padlas.feature.sort.ui.SwipeCardView
import hu.padlas.feature.sort.ui.SwipeDeck
import hu.padlas.feature.sort.ui.ZoomViewer
import hu.padlas.feature.sort.ui.rememberSwipeDeckState
import hu.padlas.feature.sort.ui.rememberSystemRequest

/**
 * The swipe run. Left marks for deletion, right keeps, up makes a favourite. Nothing is
 * deleted while swiping: the marked pile leaves through one system dialog, either when the
 * deck runs out or whenever the delete pill in the corner is pressed.
 */
@Composable
fun SwipeSessionRoute(
    onClose: () -> Unit,
    onNewSession: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SwipeSessionViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deck = rememberSwipeDeckState()

    var requestKind by remember { mutableStateOf(RequestKind.DELETE) }
    val launch = rememberSystemRequest { confirmed -> viewModel.onRequestResult(requestKind, confirmed) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.Launch -> {
                    requestKind = event.kind
                    launch(event.sender)
                }
            }
        }
    }

    val player = remember { ExoPlayer.Builder(context).build().apply { volume = 0f } }
    DisposableEffect(player) { onDispose { player.release() } }
    var muted by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (player.mediaItemCount > 0) player.play() }

    var confirmDelete by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<Uri?>(null) }
    val onDeleteNow = { if (state.skipsSystemDialog) confirmDelete = true else viewModel.deleteNow() }

    Box(modifier = modifier.fillMaxSize().auroraBackground()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            SessionTopBar(state = state, onClose = onClose, onDeleteNow = onDeleteNow)
            DecisionBar(
                kept = state.kept,
                favorites = state.favorited,
                deleted = state.deleted,
                total = state.total,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            // This box exists from the first frame, loading or not: it is what the month tile
            // (or folder row, or start button) grows into, and what it shrinks back from.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .let { base -> state.heroKey?.let { base.morphBounds(it) } ?: base },
            ) {
                AnimatedContent(
                    targetState = when {
                        state.loading -> Phase.LOADING
                        state.cards.isEmpty() -> Phase.EMPTY
                        state.finished -> Phase.DONE
                        else -> Phase.DECK
                    },
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(spring(0.8f, Spring.StiffnessMediumLow), initialScale = 0.92f)) togetherWith
                            (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.96f))
                    },
                    label = "sessionPhase",
                ) { phase ->
                    when (phase) {
                        Phase.LOADING -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = Space.m, vertical = 6.dp)
                                .glass(Shapes.hero),
                        )

                        Phase.EMPTY -> EmptyState(
                            title = "Nincs mit átnézni",
                            text = "Ebben a válogatásban minden elemről döntöttél már. Válassz másik hónapot vagy mappát.",
                            icon = Icons.Rounded.DoneAll,
                            tint = Ink.Success,
                            actionLabel = "Új válogatás",
                            onAction = onNewSession,
                        )

                        Phase.DONE -> SessionSummary(
                            state = state,
                            onDelete = onDeleteNow,
                            onReview = onOpenPending,
                            onOpenTrash = onOpenTrash,
                            onSyncFavorites = viewModel::syncFavorites,
                            onNewSession = onNewSession,
                            onClose = onClose,
                        )

                        Phase.DECK -> SwipeDeck(
                            cards = state.window,
                            state = deck,
                            undo = state.undo,
                            onSwiped = viewModel::onSwiped,
                            onTap = { card ->
                                if (card.isVideo) {
                                    if (player.isPlaying) player.pause() else player.play()
                                } else {
                                    zoom = card.uri
                                }
                            },
                            dealIn = state.heroKey == null,
                            modifier = Modifier.fillMaxSize(),
                        ) { card, isTop, overlay, cardModifier ->
                            SwipeCardView(
                                card = card,
                                isTop = isTop,
                                overlay = overlay,
                                player = if (isTop) player else null,
                                muted = muted,
                                onToggleMute = { muted = !muted },
                                modifier = cardModifier,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = !state.loading && !state.finished && state.cards.isNotEmpty(),
                enter = slideInVertically(spring(0.7f, Spring.StiffnessMediumLow)) { it } + fadeIn(),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                ActionButtons(
                    canUndo = state.canUndo,
                    onUndo = viewModel::undo,
                    onSwipe = deck::swipe,
                )
            }
        }

        AnimatedVisibility(visible = state.showHint, enter = fadeIn(), exit = fadeOut()) {
            GestureHint(onDismiss = viewModel::dismissHint)
        }

        Toast(
            message = state.message,
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp),
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "${Format.count(state.pending.itemCount)} elem törlése?",
            text = deleteExplanation(state),
            confirmLabel = "Törlés",
            destructive = true,
            icon = Icons.Rounded.Delete,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteNow()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    zoom?.let { uri -> ZoomViewer(uri = uri, onDismiss = { zoom = null }) }
}

private enum class Phase { LOADING, EMPTY, DONE, DECK }

internal fun deleteExplanation(state: SessionUiState): String =
    if (state.deleteMode == DeleteMode.PERMANENT) {
        "${Format.bytes(state.pending.totalBytes)} véglegesen törlődik. Ez nem vonható vissza."
    } else {
        "${Format.bytes(state.pending.totalBytes)} a rendszer kukájába kerül, 30 napig visszaállítható."
    }

@Composable
private fun SessionTopBar(state: SessionUiState, onClose: () -> Unit, onDeleteNow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadlasIconButton(icon = Icons.Rounded.Close, contentDescription = "Bezárás", onClick = onClose)
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.title,
                style = PadlasTheme.type.title,
                color = Ink.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollingNumber(
                        value = Format.count(minOf(state.index + 1, state.total)),
                        style = PadlasTheme.type.numeric,
                        color = Ink.TextSecondary,
                    )
                    Text(text = " / ${Format.count(state.total)}", style = PadlasTheme.type.numeric, color = Ink.TextSecondary)
                }
            }
        }
        AnimatedVisibility(
            visible = state.pending.itemCount > 0,
            enter = scaleIn(spring(0.5f, 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            DeletePill(
                count = state.pending.itemCount,
                bytes = state.pending.totalBytes,
                busy = state.busy,
                onClick = onDeleteNow,
            )
        }
    }
}

/**
 * Always in reach, and it jumps every time something new lands in it: everything marked
 * so far can leave right now, not only at the end.
 */
@Composable
private fun DeletePill(count: Int, bytes: Long, busy: Boolean, onClick: () -> Unit) {
    val bounce = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(count) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(count) {
        if (count > last && !reduced) {
            bounce.snapTo(1.22f)
            bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
        }
        last = count
    }
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .shadow(16.dp, Shapes.pill, ambientColor = Ink.Danger, spotColor = Ink.Danger)
            .glass(Shapes.pill, Ink.DangerSoft, Ink.Danger.copy(alpha = 0.6f))
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Delete, contentDescription = "Törlés most", tint = Ink.Danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Törlés · ", style = PadlasTheme.type.label, color = Ink.Text, maxLines = 1)
                RollingNumber(value = Format.count(count), style = PadlasTheme.type.label, color = Ink.Text)
            }
            Text(text = Format.bytes(bytes), style = PadlasTheme.type.micro, color = Ink.Danger, maxLines = 1)
        }
    }
}

/**
 * Progress told in decisions: the bar fills with the colour of each choice in proportion,
 * so a glance shows both how far along the deck is and what it has been turning into.
 */
@Composable
private fun DecisionBar(kept: Int, favorites: Int, deleted: Int, total: Int, modifier: Modifier = Modifier) {
    val denominator = total.coerceAtLeast(1).toFloat()
    val keep by animateFloatAsState(kept / denominator, motionSpec(Motion.rest()), label = "barKeep")
    val fav by animateFloatAsState(favorites / denominator, motionSpec(Motion.rest()), label = "barFav")
    val del by animateFloatAsState(deleted / denominator, motionSpec(Motion.rest()), label = "barDel")
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(Ink.SurfaceHigh, cornerRadius = radius)
        val gap = 2.dp.toPx()
        var x = 0f
        listOf(keep to Ink.Success, fav to Ink.Favorite, del to Ink.Danger).forEach { (share, color) ->
            val w = share * size.width
            if (w > 0.5f) {
                drawRoundRect(color, topLeft = Offset(x, 0f), size = Size((w - gap).coerceAtLeast(2f), size.height), cornerRadius = radius)
                x += w
            }
        }
    }
}

@Composable
private fun ActionButtons(canUndo: Boolean, onUndo: () -> Unit, onSwipe: (SwipeAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.s, bottom = Space.l),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundAction(Icons.AutoMirrored.Rounded.Undo, "Vissza", Ink.TextSecondary, Ink.Surface, 52.dp, glow = false, enabled = canUndo, onClick = onUndo)
        RoundAction(Icons.Rounded.Close, "Törlés", Ink.Danger, Ink.DangerSoft, 72.dp, onClick = { onSwipe(SwipeAction.DELETE) })
        RoundAction(Icons.Rounded.Star, "Kedvenc", Ink.Favorite, Ink.FavoriteSoft, 58.dp, onClick = { onSwipe(SwipeAction.FAVORITE) })
        RoundAction(Icons.Rounded.Check, "Megtartom", Ink.Success, Ink.SuccessSoft, 72.dp, onClick = { onSwipe(SwipeAction.KEEP) })
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    background: Color,
    size: Dp,
    glow: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PadlasIconButton(
            icon = icon,
            contentDescription = label,
            onClick = onClick,
            tint = tint,
            background = background,
            size = size,
            iconSize = size * 0.44f,
            enabled = enabled,
            modifier = if (glow && enabled) {
                Modifier.shadow(20.dp, Shapes.pill, ambientColor = tint, spotColor = tint)
            } else {
                Modifier
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = PadlasTheme.type.micro,
            color = if (enabled) Ink.TextSecondary else Ink.TextTertiary,
        )
    }
}

@Composable
private fun GestureHint(onDismiss: () -> Unit) {
    val haptic = haptics()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(Space.xl)
                .glass(Shapes.sheet, Ink.SurfaceSolid, Ink.HairlineStrong)
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Így működik", style = PadlasTheme.type.title, color = Ink.Text)
            Spacer(Modifier.height(Space.l))
            HintRow(Icons.Rounded.Close, Ink.Danger, "Húzd balra", "törlésre jelöli")
            HintRow(Icons.Rounded.Check, Ink.Success, "Húzd jobbra", "megtartod")
            HintRow(Icons.Rounded.Star, Ink.Favorite, "Húzd felfelé", "kedvenc lesz")
            Spacer(Modifier.height(Space.s))
            Text(
                text = "Koppintással nagyíthatod a képet, a videót megállíthatod. A megjelölt elemek a pakli végén egyetlen rendszer-párbeszédben törlődnek, de a jobb felső gombbal bármikor törölheted őket.",
                style = PadlasTheme.type.caption,
                color = Ink.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.xl))
            PadlasButton(
                text = "Kezdjük",
                onClick = {
                    haptic?.tick(0.3f)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, tint: Color, action: String, result: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(Shapes.pill)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Space.m))
        Text(text = action, style = PadlasTheme.type.bodyStrong, color = Ink.Text)
        Spacer(Modifier.width(6.dp))
        Text(text = result, style = PadlasTheme.type.body, color = Ink.TextSecondary)
    }
}
