# PADLÁS — module contract (build-time source of truth)

Read this before writing a line. It fixes the API surface every module may rely on.
The product spec is `padlas-spec.md`; this file is only about code seams.

## Stack (already configured, do not change)

Gradle 8.14.3 · AGP 8.13.2 · Kotlin 2.2.20 · KSP 2.2.20-2.0.4 · Hilt 2.57.2 ·
Compose BOM 2025.09.01 (Material 3) · Room 2.8.1 · WorkManager 2.10.5 ·
Media3 1.8.1 · Coil 3.3.0 · ML Kit text 16.0.1 / barcode 17.3.0 · OkHttp 4.12.0 ·
minSdk 34 · compileSdk/targetSdk 36 · JVM target 17 · package root `hu.padlas`.

Dependencies are declared per module in `<module>/build.gradle.kts` and versions live in
`gradle/libs.versions.toml`. **Never edit another module's build file**; if you genuinely
need a dependency that is missing, say so in your final report instead.

## Rules

1. Kotlin only, no Java. Coroutines + Flow everywhere, no RxJava, no LiveData.
2. Hilt for DI: `@Inject constructor`, `@HiltViewModel`, `@Singleton` where the object
   holds state. Workers use `@HiltWorker` + `@AssistedInject`.
3. All I/O on `@IoDispatcher`, hashing on `@HashDispatcher` (see `core:common`).
4. UI text is **Hungarian**, active verbs, sentence case, numbers always through
   `hu.padlas.core.common.format.Format`. Never hardcode a byte string.
5. Comments explain *why*, never *what*. No file headers, no banner comments, no TODOs.
6. No `!!`, no `GlobalScope`, no `runBlocking` outside tests.
7. Every destructive path must satisfy the invariants in spec chapter 4 (INV-1..INV-7).
   In particular: automation never deletes permanently, `SECRET` is never offered for
   deletion, and permanent deletion is always batched through one system dialog.
8. Do not create files outside the module you were assigned.
9. Compose: no infinite animations, no shimmer, no ripple. Motion comes from
   `hu.padlas.core.design.motion.Motion` springs only.

## core:common (done)

```kotlin
package hu.padlas.core.common.di
annotation class IoDispatcher / DefaultDispatcher / HashDispatcher / ApplicationScope

package hu.padlas.core.common.format
object Format {
    fun bytes(value: Long): String          // "12,4 GB"
    fun bytesValue(value: Long): String     // "12,4"
    fun bytesUnit(value: Long): String      // "GB"
    fun count(value: Long): String          // "2 341"
    fun count(value: Int): String
    fun percent(fraction: Float): String
    fun duration(millis: Long): String
}

package hu.padlas.core.common.result
sealed interface Outcome<out T> { data class Success<T>; data class Failure(message, cause) }
inline fun <T> outcomeOf(message: String = "...", block: () -> T): Outcome<T>
```

## core:database (done — read the sources, do not change them)

`hu.padlas.core.database.entity`: `Asset`, `AssetState`, `ValueClass`, `ShotKind`,
`DupKind`, `JobState`, `RuleAction`, `AuditAction`, `ClassifiedBy`, `DupGroupEntity`,
`DupMemberEntity`, `TranscodeJobEntity`, `UploadJobEntity`, `RuleEntity`, `AuditEntry`,
`CardEntity`, `AssetSearchEntity`, `StorageSnapshotEntity`, `BucketSnapshotEntity`.

`hu.padlas.core.database.dao`: `AssetDao`, `DupDao`, `TranscodeJobDao`, `UploadJobDao`,
`RuleDao`, `AuditDao`, `CardDao`, `SearchDao`, `StorageDao` — all injectable directly.

`hu.padlas.core.database.model`: `BucketAggregate`, `PathAggregate`, `ClassAggregate`,
`ShotKindAggregate`, `StateAggregate`, `MediaTypeAggregate`, `HashRow`,
`QuickHashCandidate`, `DupGroupWithMembers`, `DupMemberAsset`, `SizeAndCount`.

## core:media (owner: media agent) — API other modules may use

```kotlin
package hu.padlas.core.media

class MediaStoreScanner @Inject constructor(...) {
    fun scan(sinceDateModified: Long = 0L): Flow<List<Asset>>   // emits batches of 500
    suspend fun currentIds(): Set<Long>
    suspend fun assetById(mediaStoreId: Long): Asset?
}

object AssetClassifier {
    fun classify(candidate: ClassificationInput): Classification
}
data class ClassificationInput(
    val relativePath: String, val displayName: String, val mimeType: String,
    val width: Int, val height: Int, val hasCameraExif: Boolean, val isFavorite: Boolean,
    val isVideo: Boolean,
)
data class Classification(val valueClass: ValueClass, val classifiedBy: String)

class ExifReader @Inject constructor(...) {
    suspend fun read(uri: Uri): ExifData
}
data class ExifData(
    val dateTaken: Long?, val hasCameraTags: Boolean, val latitude: Double?,
    val longitude: Double?, val rotationDegrees: Int,
)

class Hasher @Inject constructor(...) {
    suspend fun quickHash(uri: Uri, sizeBytes: Long): String?
    suspend fun sha256(uri: Uri): String?
    suspend fun md5(uri: Uri): String?
    suspend fun perceptual(uri: Uri): PerceptualResult?   // dHash + blur in one decode
    companion object { fun hammingDistance(a: Long, b: Long): Int }
}
data class PerceptualResult(val dHash: Long, val blurScore: Float)

class ThumbnailStore @Inject constructor(...) {
    suspend fun thumbnail(uri: Uri, size: Int = 256): Bitmap?
    suspend fun writeGhostThumbnail(assetId: Long, uri: Uri): String?  // returns file path
    suspend fun writeCardThumbnail(cardId: String, uri: Uri): String?
    fun ghostThumbnailFile(assetId: Long): File
}

class MediaOps @Inject constructor(...) {
    fun trashRequest(uris: List<Uri>): IntentSender        // MediaStore.createTrashRequest
    fun untrashRequest(uris: List<Uri>): IntentSender
    fun deleteRequest(uris: List<Uri>): IntentSender       // one dialog for the whole batch
    fun uriFor(mediaStoreId: Long, isVideo: Boolean): Uri
    suspend fun openInputStream(uri: Uri): InputStream?
    suspend fun fileSize(uri: Uri): Long
    suspend fun trashedBytes(): Long                        // MediaStore trash, still occupied
}

class StorageProbe @Inject constructor(...) {
    suspend fun volume(): VolumeInfo
    suspend fun appUsage(): List<AppUsage>   // needs PACKAGE_USAGE_STATS, empty list without it
    suspend fun treeUnder(path: String, maxDepth: Int = 2): FileNode
    fun hasUsageStatsPermission(): Boolean
    fun hasAllFilesAccess(): Boolean
}
data class VolumeInfo(val totalBytes: Long, val freeBytes: Long, val usedBytes: Long)
data class AppUsage(val packageName: String, val label: String, val appBytes: Long,
                    val dataBytes: Long, val cacheBytes: Long)
data class FileNode(val name: String, val path: String, val sizeBytes: Long,
                    val isDirectory: Boolean, val children: List<FileNode>)

class OneDriveGuard @Inject constructor(...) {   // spec 4.1
    fun isSamsungOneDriveSyncLikely(): Boolean
    suspend fun samsungGalleryTrashBytes(): Long
}
```

## core:design (owner: design agent) — API other modules may use

Already written: `theme.Ink`, `theme.Spectrum`, `theme.PadlasTheme`, `theme.PadlasTypography`
(`PadlasTheme.type.display/title/body/label/micro/numeric/numericLarge`),
`theme.Shapes`, `theme.Radius`, `theme.Space`, `theme.SquircleShape`, `Modifier.grain()`,
`motion.Motion`, `motion.motionSpec`, `motion.staggerDelayMs`, `motion.LocalReducedMotion`,
`motion.PadlasHaptics` + `motion.LocalHaptics` + `motion.haptics()`,
`component.PadlasCard`, `Modifier.raisedSurface`, `Modifier.hairlineBottom`.

To be added by the design agent (other modules code against exactly these):

```kotlin
package hu.padlas.core.design.component

@Composable fun RollingNumber(value: String, style: TextStyle, modifier: Modifier = Modifier,
                              color: Color = Ink.Paper)
@Composable fun SectionLabel(text: String, modifier: Modifier = Modifier)   // micro, uppercase
@Composable fun NumericText(text: String, modifier: Modifier = Modifier,
                            color: Color = Ink.Faded, large: Boolean = false)
@Composable fun PadlasTopBar(title: String, modifier: Modifier = Modifier,
                             onBack: (() -> Unit)? = null, action: @Composable () -> Unit = {})
@Composable fun PadlasButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                             enabled: Boolean = true, destructive: Boolean = false)
@Composable fun PadlasTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun HoldToConfirm(label: String, onConfirm: () -> Unit, modifier: Modifier = Modifier,
                              holdMillis: Int = 2000, enabled: Boolean = true)
@Composable fun StorageGauge(segments: List<GaugeSegment>, freeBytes: Long, totalBytes: Long,
                             modifier: Modifier = Modifier)
data class GaugeSegment(val label: String, val bytes: Long, val color: Color)
@Composable fun ActionBar(title: String, detail: String, direction: ActionDirection,
                          onClick: () -> Unit, modifier: Modifier = Modifier, index: Int = 0)
enum class ActionDirection { UP, DOWN, COMPRESS, NONE }
@Composable fun EmptyState(text: String, actionLabel: String? = null,
                           onAction: (() -> Unit)? = null, modifier: Modifier = Modifier)
@Composable fun SelectableTile(model: Any?, selected: Boolean, onToggle: () -> Unit,
                               modifier: Modifier = Modifier, badge: String? = null,
                               onLongPress: (() -> Unit)? = null)
@Composable fun GhostTile(model: Any?, modifier: Modifier = Modifier)
@Composable fun StaggeredColumnItem(index: Int, content: @Composable () -> Unit)
@Composable fun ProgressStrip(fraction: Float, label: String, modifier: Modifier = Modifier)
@Composable fun Chip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null,
                     modifier: Modifier = Modifier)
```

`SelectableTile`/`GhostTile` take a Coil model (`Uri`, `File`, `String`, or `null`).

## Feature modules

Every feature exposes one entry composable and keeps its ViewModel internal:

| module | entry composable |
|---|---|
| `:feature:index` | `IndexRoute(onFinished: () -> Unit, modifier: Modifier = Modifier)` |
| `:feature:map` | `MapRoute(onBack: () -> Unit, modifier: Modifier = Modifier)` |
| `:feature:dedup` | `DedupRoute(onBack: () -> Unit, modifier: Modifier = Modifier)` |
| `:feature:triage` | `DiscardRoute(onBack, modifier)` and `FolderRoute(bucketId, onBack, modifier)` |
| `:feature:transcode` | `VideoRoute(onBack: () -> Unit, modifier: Modifier = Modifier)` |
| `:feature:archive` | `hu.padlas.feature.archive.ui.AtticRoute(onBack, modifier)` |
| `:feature:sort` | `SortRoute(onBack, modifier, onArchiveRequested: (List<Long>) -> Unit = {})` |
| `:feature:rules` | `RulesRoute(onBack: () -> Unit, modifier: Modifier = Modifier)` |
| `:feature:audit` | `LogRoute(onBack: () -> Unit, modifier: Modifier = Modifier)` |

Additionally, each feature that owns background work exposes a scheduler object the app
module calls, e.g.:

```kotlin
package hu.padlas.feature.index
object IndexScheduler {
    fun enqueueFullScan(context: Context)
    fun enqueueIncremental(context: Context)
    fun cancelAll(context: Context)
    const val WORK_FULL = "padlas.index.full"
}
```

Equivalents: `DedupScheduler` (`:feature:dedup`), `TriageScheduler` (`:feature:triage`),
`TranscodeScheduler` (`:feature:transcode`), `ArchiveScheduler` (`:feature:archive`),
`RulesScheduler` (`:feature:rules`). Each must expose `cancelAll(context)` because the
Vault has a global kill switch (INV-7).

Features may also expose a `@Singleton` repository for the app module's Vault screen when
it needs numbers, but the Vault mostly reads DAOs directly.

## Workers

`@HiltWorker class XWorker @AssistedInject constructor(@Assisted appContext: Context,
@Assisted params: WorkerParameters, ...) : CoroutineWorker(appContext, params)`.

Long-running upload/transcode workers call `setForeground(...)` with a
`ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`. Notification
channel id: `"padlas.work"`, created by the app module — just use the id.

## Audit

Every state change that touches a file writes an `AuditEntry` (INV-5). Use
`AuditDao.insert(...)` with `AuditAction`, the asset id and the byte size.

## Deviations that ended up in the code

- `core:design` does not depend on Coil, so `SelectableTile`/`GhostTile` take a trailing
  `image: @Composable (Modifier) -> Unit` slot; feature modules pass an `AsyncImage`.
- `AtticRoute` lives in `hu.padlas.feature.archive.ui`, not in the module root.
- `SortRoute` takes `onArchiveRequested` so the deck can queue uploads without depending
  on `:feature:archive`; the app module wires it to `ArchiveScheduler.enqueueUploads`.
- A rule with the `TRASH` action stages its matches (`AssetDao.defer`) instead of writing
  `TRASHED`: the system bin can only be entered through a user-confirmed dialog, and a
  staged batch that pretends to be in the bin would make the trash counter lie.
- `Asset.dateModified` is stored in MediaStore's native seconds so the incremental scan can
  feed `AssetDao.latestDateModified()` straight back into `MediaStoreScanner.scan()`.
- Fonts are bundled in `core:design` rather than fetched through the downloadable-fonts
  provider (see `THIRD_PARTY_LICENSES.md`).
