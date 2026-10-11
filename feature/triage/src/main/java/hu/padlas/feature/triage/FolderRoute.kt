package hu.padlas.feature.triage

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.padlas.core.common.format.Format
import hu.padlas.core.design.component.EmptyState
import hu.padlas.core.design.component.GlassScreen
import hu.padlas.core.design.component.PadlasTextButton
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.PinnedActionBar
import hu.padlas.core.design.component.ReclaimHero
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.component.ZoomHost
import hu.padlas.core.design.component.ZoomTarget
import hu.padlas.core.design.component.navigationBarBottom
import hu.padlas.core.design.component.rememberZoomState
import hu.padlas.core.design.component.topBarClearance
import hu.padlas.core.design.motion.LocalReducedMotion
import hu.padlas.core.design.motion.entrance
import hu.padlas.core.design.motion.finite
import hu.padlas.core.design.motion.haptics
import hu.padlas.core.design.motion.itemMotion
import hu.padlas.core.design.motion.popIn
import hu.padlas.core.design.motion.rememberEntrance
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Space
import kotlinx.coroutines.delay

/**
 * One folder, everything preselected for deletion. A thirty second pass over WhatsApp
 * Images is the point; anything slower and the folder never gets cleared at all.
 */
@Composable
fun FolderRoute(bucketId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: FolderViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = haptics()

    // The fall-away timer and the back button can both fire; popping twice would take the
    // overview with it.
    var left by remember { mutableStateOf(false) }
    val leave = {
        if (!left) {
            left = true
            onBack()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }

    BackHandler { leave() }

    LaunchedEffect(bucketId) { viewModel.load(bucketId) }

    LaunchedEffect(state.request) {
        val request = state.request ?: return@LaunchedEffect
        launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    LaunchedEffect(state.falling) {
        if (!state.falling) return@LaunchedEffect
        haptics?.commit()
        delay(FALL_AWAY_MS)
        leave()
    }

    val hasContent = !state.loading && state.items.isNotEmpty()
    val navBottom = navigationBarBottom()
    val allKept = state.discardCount == 0
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    val zoom = rememberZoomState()
    val entrance = rememberEntrance()
    val reduced = LocalReducedMotion.current
    val title = state.title.ifBlank { "Mappa" }

    ZoomHost(
        state = zoom,
        picture = { target, placeholder, pictureModifier -> ZoomPicture(target, placeholder, pictureModifier) },
        modifier = modifier.fillMaxSize(),
        actions = { target ->
            val id = target.key as? Long
            if (id != null) {
                KeepVerdict(kept = id in state.kept, locked = false, onToggle = { viewModel.toggleKeep(id) })
            }
        },
    ) {
        GlassScreen(
            scrolled = scrolled,
            topBar = {
                PadlasTopBar(
                    title = title,
                    onBack = leave,
                    subtitle = if (hasContent) {
                        "${Format.count(state.discardCount)} / ${Format.count(state.items.size)} kijelölve · " +
                            Format.bytes(state.discardBytes)
                    } else {
                        null
                    },
                )
            },
            overlay = { haze ->
                PinnedActionBar(visible = hasContent, haze = haze, modifier = Modifier.align(Alignment.BottomCenter)) {
                    CommitBar(
                        count = state.discardCount,
                        bytes = state.discardBytes,
                        busy = state.committing || state.falling,
                        onConfirm = viewModel::requestDelete,
                    )
                }
                Toast(
                    message = state.message,
                    onShown = viewModel::dismissMessage,
                    icon = Icons.Rounded.Info,
                    tint = Ink.Info,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBottom + if (hasContent) CommitBarClearance else Space.l),
                )
            },
        ) {
            when {
                state.loading -> Unit
                state.items.isEmpty() -> EmptyState(
                    title = "Üres mappa",
                    text = "Ebben a mappában nincs több kép vagy videó.",
                    icon = Icons.Rounded.FolderOpen,
                    actionLabel = "Vissza",
                    onAction = leave,
                    modifier = Modifier.padding(top = topBarClearance() + Space.xxl),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.page,
                        end = Space.page,
                        top = topBarClearance() + Space.s,
                        bottom = navBottom + CommitBarClearance,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                    verticalArrangement = Arrangement.spacedBy(TileSpacing),
                ) {
                    item(key = "hero", span = { GridItemSpan(maxLineSpan) }) {
                        ReclaimHero(
                            label = "Felszabadítható",
                            value = Format.bytesValue(state.discardBytes),
                            unit = Format.bytesUnit(state.discardBytes),
                            caption = "Minden ki van jelölve törlésre. Koppints arra, amit megtartanál, " +
                                "hosszan nyomva pedig nagyban is megnézheted.",
                            modifier = Modifier.entrance(entrance).padding(bottom = Space.m),
                        )
                    }
                    item(key = "tools", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.entrance(entrance).fillMaxWidth().padding(bottom = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${Format.count(state.items.size)} fájl a mappában",
                                style = PadlasTheme.type.label,
                                color = Ink.TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(Space.s))
                            AnimatedContent(
                                targetState = allKept,
                                transitionSpec = { popIn(reduced) togetherWith fadeOut(finite(reduced)) },
                                label = "folderBulk",
                            ) { kept ->
                                if (kept) {
                                    PadlasTextButton(
                                        text = "Mind törlése",
                                        onClick = viewModel::discardAll,
                                        color = Ink.Danger,
                                    )
                                } else {
                                    PadlasTextButton(text = "Mind marad", onClick = viewModel::keepAll)
                                }
                            }
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        ShotTile(
                            item = item,
                            kept = item.id in state.kept,
                            falling = state.falling,
                            onToggle = { viewModel.toggleKeep(item.id) },
                            onView = {
                                zoom.open(
                                    ZoomTarget(
                                        key = item.id,
                                        model = item.model,
                                        title = title,
                                        caption = Format.bytes(item.sizeBytes),
                                    ),
                                )
                            },
                            modifier = Modifier.entrance(entrance).then(itemMotion()),
                        )
                    }
                }
            }
        }
    }
}
