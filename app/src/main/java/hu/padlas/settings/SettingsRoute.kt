package hu.padlas.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.padlas.BuildConfig
import hu.padlas.core.design.component.ConfirmDialog
import hu.padlas.core.design.component.IconBubble
import hu.padlas.core.design.component.ListRow
import hu.padlas.core.design.component.PadlasSwitch
import hu.padlas.core.design.component.PadlasTopBar
import hu.padlas.core.design.component.Pill
import hu.padlas.core.design.component.SectionHeader
import hu.padlas.core.design.component.SelectionMark
import hu.padlas.core.design.component.Toast
import hu.padlas.core.design.motion.Motion
import hu.padlas.core.design.motion.motionSpec
import hu.padlas.core.design.theme.Ink
import hu.padlas.core.design.theme.auroraBackground
import hu.padlas.core.design.theme.PadlasTheme
import hu.padlas.core.design.theme.Shapes
import hu.padlas.core.design.theme.Space
import hu.padlas.core.media.MediaOps
import hu.padlas.core.media.StorageProbe
import hu.padlas.feature.index.IndexScheduler
import hu.padlas.feature.sort.DeleteMode
import hu.padlas.feature.sort.SwipePreferences
import hu.padlas.feature.sort.SwipeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class Access(
    val media: Boolean = false,
    val allFiles: Boolean = false,
    val usage: Boolean = false,
    val notifications: Boolean = false,
    val manageMedia: Boolean = false,
)

@Immutable
data class SettingsUiState(
    val liveBackground: Boolean = true,
    val deleteMode: DeleteMode = DeleteMode.TRASH,
    val deleteAtEnd: Boolean = true,
    val access: Access = Access(),
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SwipePreferences,
    private val appearance: AppearancePreferences,
    private val swipeRepository: SwipeRepository,
    private val storageProbe: StorageProbe,
    private val mediaOps: MediaOps,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val access = MutableStateFlow(readAccess())
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        combine(preferences.deleteMode, preferences.deleteAtEnd, appearance.liveBackground, ::Triple),
        access,
        message,
    ) { (mode, atEnd, live), currentAccess, text ->
        SettingsUiState(live, mode, atEnd, currentAccess, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() {
        access.value = readAccess()
    }

    fun setDeleteMode(mode: DeleteMode) = preferences.setDeleteMode(mode)

    fun setLiveBackground(enabled: Boolean) = appearance.setLiveBackground(enabled)

    fun setDeleteAtEnd(enabled: Boolean) = preferences.setDeleteAtEnd(enabled)

    fun forgetReviewed() {
        viewModelScope.launch {
            swipeRepository.forgetReviewed()
            message.value = "Minden elem újra átnézhető"
        }
    }

    /** INV-7: one switch stops every background job, immediately and visibly. */
    fun stopAllWork() {
        WorkManager.getInstance(context).cancelAllWork()
        message.value = "Minden háttérmunka leállt"
    }

    fun rescan() {
        IndexScheduler.enqueueFullScan(context)
        message.value = "A galéria újraolvasása elindult"
    }

    fun consumeMessage() = message.update { null }

    private fun readAccess(): Access = Access(
        media = granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO),
        allFiles = storageProbe.hasAllFilesAccess(),
        usage = storageProbe.hasUsageStatsPermission(),
        notifications = granted(Manifest.permission.POST_NOTIFICATIONS),
        manageMedia = mediaOps.canManageMedia(),
    )

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmForget by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Box(modifier = modifier.fillMaxSize().auroraBackground().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            PadlasTopBar(title = "Beállítások", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = Space.page, end = Space.page, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                item { SectionHeader(title = "Megjelenés") }
                item {
                    ListRow(
                        title = "Élő háttér",
                        subtitle = "A háttér színei lassan mozognak. Kikapcsolva állóképes, és kíméli az akkut.",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = { viewModel.setLiveBackground(!state.liveBackground) },
                        showChevron = false,
                        trailing = { PadlasSwitch(checked = state.liveBackground, onCheckedChange = viewModel::setLiveBackground) },
                    )
                }

                item { SectionHeader(title = "Törlés") }
                item {
                    ModeOption(
                        title = "Kukába helyezés",
                        text = "30 napig visszaállítható. A hely a kuka ürítésekor szabadul fel.",
                        icon = Icons.Rounded.DeleteOutline,
                        selected = state.deleteMode == DeleteMode.TRASH,
                        onClick = { viewModel.setDeleteMode(DeleteMode.TRASH) },
                    )
                }
                item {
                    ModeOption(
                        title = "Végleges törlés",
                        text = "Azonnal felszabadítja a helyet. Nem vonható vissza.",
                        icon = Icons.Rounded.DeleteForever,
                        selected = state.deleteMode == DeleteMode.PERMANENT,
                        onClick = { viewModel.setDeleteMode(DeleteMode.PERMANENT) },
                    )
                }
                item {
                    ListRow(
                        title = "Törlés a pakli végén",
                        subtitle = "Ha a végére értél, rögtön törli a megjelölteket.",
                        icon = Icons.Rounded.DoneAll,
                        onClick = { viewModel.setDeleteAtEnd(!state.deleteAtEnd) },
                        showChevron = false,
                        trailing = { PadlasSwitch(checked = state.deleteAtEnd, onCheckedChange = viewModel::setDeleteAtEnd) },
                    )
                }
                item {
                    ListRow(
                        title = "Törlés rákérdezés nélkül",
                        subtitle = if (state.access.manageMedia) {
                            "Bekapcsolva: a rendszer nem kérdez rá minden kötegre, az app saját megerősítést kér."
                        } else {
                            "A rendszer most minden törlésnél külön rákérdez. A „Médiakezelés” engedéllyel ez elmarad."
                        },
                        icon = Icons.Rounded.VerifiedUser,
                        iconTint = Ink.Info,
                        trailing = { StatusPill(state.access.manageMedia) },
                        onClick = { context.openSettings(Settings.ACTION_REQUEST_MANAGE_MEDIA, withPackage = true) },
                    )
                }

                item { SectionHeader(title = "Hozzáférések") }
                item {
                    AccessRow("Fotók és videók", "A válogatáshoz és a javaslatokhoz", Icons.Rounded.PhotoLibrary, state.access.media) {
                        context.openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true)
                    }
                }
                item {
                    AccessRow("Teljes fájlhozzáférés", "A tárhely-elemzőhöz és a fájlok törléséhez", Icons.Rounded.FolderOpen, state.access.allFiles) {
                        context.openSettings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, withPackage = true)
                    }
                }
                item {
                    AccessRow("Használati adatok", "Az appok méretéhez és gyorsítótárához", Icons.Rounded.QueryStats, state.access.usage) {
                        context.openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS, withPackage = false)
                    }
                }
                item {
                    AccessRow("Értesítések", "Hosszú háttérmunkák állapota", Icons.Rounded.Notifications, state.access.notifications) {
                        context.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, withPackage = false)
                    }
                }

                item { SectionHeader(title = "Válogatás") }
                item {
                    ListRow(
                        title = "Átnézett elemek újra",
                        subtitle = "A megtartott és kedvenc döntések törlődnek, minden újra előkerül.",
                        icon = Icons.Rounded.RestartAlt,
                        iconTint = Ink.Warning,
                        onClick = { confirmForget = true },
                    )
                }

                item { SectionHeader(title = "Háttérmunka") }
                item {
                    ListRow(
                        title = "Galéria újraolvasása",
                        subtitle = "Ha valami hiányzik vagy elavult a listákban.",
                        icon = Icons.Rounded.Refresh,
                        onClick = viewModel::rescan,
                    )
                }
                item {
                    ListRow(
                        title = "Minden háttérmunka leállítása",
                        subtitle = "Feltöltés, tömörítés, ujjlenyomat-készítés — azonnal.",
                        icon = Icons.Rounded.StopCircle,
                        iconTint = Ink.Danger,
                        onClick = { confirmStop = true },
                    )
                }

                item { SectionHeader(title = "Névjegy") }
                item {
                    ListRow(
                        title = "Padlás",
                        subtitle = "Galéria-válogató és tárhelykezelő",
                        icon = Icons.Rounded.Info,
                        value = BuildConfig.VERSION_NAME,
                        showChevron = false,
                    )
                }
            }
        }
        Toast(
            message = state.message,
            onShown = viewModel::consumeMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.xl),
        )
    }

    if (confirmForget) {
        ConfirmDialog(
            title = "Minden újra átnézhető legyen?",
            text = "A törlésre jelölt elemek megmaradnak, csak a megtartott és kedvenc döntések törlődnek.",
            confirmLabel = "Igen, újrakezdem",
            icon = Icons.Rounded.RestartAlt,
            onConfirm = {
                confirmForget = false
                viewModel.forgetReviewed()
            },
            onDismiss = { confirmForget = false },
        )
    }
    if (confirmStop) {
        ConfirmDialog(
            title = "Leállítsam a háttérmunkát?",
            text = "A folyamatban lévő feltöltések és tömörítések megszakadnak. Később újraindíthatók.",
            confirmLabel = "Leállítás",
            destructive = true,
            icon = Icons.Rounded.StopCircle,
            onConfirm = {
                confirmStop = false
                viewModel.stopAllWork()
            },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun ModeOption(title: String, text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Accent else Ink.Hairline,
        animationSpec = motionSpec(Motion.settle()),
        label = "modeOutline",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(if (selected) Ink.AccentSoft else Ink.Surface)
            .border(1.dp, outline, Shapes.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon, tint = if (selected) Ink.Accent else Ink.TextSecondary)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Space.m),
        ) {
            Text(text = title, style = PadlasTheme.type.bodyStrong, color = Ink.Text)
            Text(text = text, style = PadlasTheme.type.caption, color = Ink.TextSecondary)
        }
        SelectionMark(selected = selected)
        Spacer(Modifier.width(Space.xs))
    }
}

@Composable
private fun AccessRow(title: String, subtitle: String, icon: ImageVector, granted: Boolean, onClick: () -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = if (granted) Ink.Success else Ink.Warning,
        trailing = { StatusPill(granted) },
        onClick = onClick,
    )
}

@Composable
private fun StatusPill(granted: Boolean) {
    Pill(
        text = if (granted) "Megadva" else "Hiányzik",
        color = if (granted) Ink.Success else Ink.Warning,
        background = if (granted) Ink.SuccessSoft else Ink.WarningSoft,
    )
}

private fun Context.openSettings(action: String, withPackage: Boolean) {
    val intent = Intent(action).apply {
        if (withPackage) data = Uri.parse("package:$packageName")
        if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
