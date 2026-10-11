package hu.padlas.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StorageIndexTest {

    private val now = 1_000L * 24 * 60 * 60 * 1000
    private val old = now - StorageIndex.OLD_DOWNLOAD_MS - 1
    private val recent = now - 1_000

    private fun sample() = tree {
        dir("DCIM") {
            dir("Camera") {
                file("IMG_1.jpg", 4_000)
                file("IMG_2.jpg", 6_000)
                file("VID_1.mp4", 50_000)
            }
            dir(".thumbnails") {
                file("1.jpg", 300)
                file("2.jpg", 200)
            }
        }
        dir("Download") {
            file("old.apk", 20_000, modified = old)
            file("fresh.pdf", 2_000, modified = recent)
            file("ancient.zip", 9_000, modified = old)
            dir("nested") { file("deep.mp3", 700, modified = old) }
        }
        dir("Music")
        dir("Stale") {
            dir("Inner")
        }
        dir("Android") {
            dir("media") {
                dir("com.chat") { file("voice.opus", 1_500) }
                dir("com.empty")
            }
        }
    }

    @Test
    fun `category totals add up to the tree`() {
        val index = StorageIndex.build(sample(), now)

        val byCategory = index.categories.associate { it.category to it.bytes }
        assertThat(byCategory[FileCategory.IMAGES]).isEqualTo(10_500)
        assertThat(byCategory[FileCategory.VIDEOS]).isEqualTo(50_000)
        assertThat(byCategory[FileCategory.INSTALLERS]).isEqualTo(20_000)
        assertThat(byCategory[FileCategory.AUDIO]).isEqualTo(2_200)
        assertThat(index.categories.sumOf { it.bytes }).isEqualTo(index.totalBytes)
        assertThat(index.categories.map { it.bytes }).isInOrder(Comparator.reverseOrder<Long>())
        assertThat(index.totalFiles).isEqualTo(10)
    }

    @Test
    fun `extensions are summed and ranked`() {
        val index = StorageIndex.build(sample(), now)

        val jpg = index.extensions.first { it.extension == "jpg" }
        assertThat(jpg.count).isEqualTo(4)
        assertThat(jpg.bytes).isEqualTo(10_500)
        assertThat(jpg.category).isEqualTo(FileCategory.IMAGES)
        assertThat(index.extensions.first().extension).isEqualTo("mp4")
        assertThat(index.extensions.map { it.bytes }).isInOrder(Comparator.reverseOrder<Long>())
    }

    @Test
    fun `the extension list is capped`() {
        val root = tree { repeat(50) { file("file.e$it", (it + 1).toLong()) } }

        val index = StorageIndex.build(root, now, topExtensions = 10)

        assertThat(index.extensions).hasSize(10)
        assertThat(index.extensions.first().extension).isEqualTo("e49")
    }

    @Test
    fun `the largest files come back biggest first and capped`() {
        val root = tree {
            dir("a") { repeat(40) { file("a$it.bin", (it * 10).toLong()) } }
            dir("b") { repeat(40) { file("b$it.bin", (it * 10 + 5).toLong()) } }
        }

        val index = StorageIndex.build(root, now, topFiles = 5)

        assertThat(index.largest.map { it.name }).containsExactly("b39.bin", "a39.bin", "b38.bin", "a38.bin", "b37.bin").inOrder()
        assertThat(index.largest.first().path).isEqualTo("$ROOT/b/b39.bin")
        assertThat(index.largest.first().parentPath).isEqualTo("$ROOT/b")
    }

    @Test
    fun `every category keeps its own top list`() {
        val index = StorageIndex.build(sample(), now)

        assertThat(index.largestByCategory[FileCategory.IMAGES]?.map { it.name })
            .containsExactly("IMG_2.jpg", "IMG_1.jpg", "1.jpg", "2.jpg").inOrder()
        assertThat(index.largestByCategory[FileCategory.DOCUMENTS]?.map { it.name }).containsExactly("fresh.pdf")
        assertThat(index.largestByCategory[FileCategory.ARCHIVES]?.single()?.name).isEqualTo("ancient.zip")
    }

    @Test
    fun `installers, old downloads and thumbnails are found`() {
        val index = StorageIndex.build(sample(), now)

        assertThat(index.installers.map { it.name }).containsExactly("old.apk")
        assertThat(index.oldDownloads.map { it.name }).containsExactly("old.apk", "ancient.zip", "deep.mp3").inOrder()
        assertThat(index.thumbnailDirs.map { it.path }).containsExactly("$ROOT/DCIM/.thumbnails")
        assertThat(index.thumbnailDirs.single().bytes).isEqualTo(500)
    }

    @Test
    fun `only the outermost empty folder is offered, never a standard one`() {
        val index = StorageIndex.build(sample(), now)

        assertThat(index.emptyDirs.map { it.path })
            .containsExactly("$ROOT/Android/media/com.empty", "$ROOT/Stale")
            .inOrder()
    }

    @Test
    fun `app media is measured for the summary`() {
        val index = StorageIndex.build(sample(), now)

        assertThat(index.appMediaBytes).isEqualTo(1_500)
    }

    @Test
    fun `system folders are protected from deletion`() {
        assertThat(StoragePaths.isProtected(ROOT, ROOT)).isTrue()
        assertThat(StoragePaths.isProtected("$ROOT/Android", ROOT)).isTrue()
        assertThat(StoragePaths.isProtected("$ROOT/Android/media", ROOT)).isTrue()
        assertThat(StoragePaths.isProtected("$ROOT/android/DATA", ROOT)).isTrue()
        assertThat(StoragePaths.isProtected("/data/data/app", ROOT)).isTrue()
        assertThat(StoragePaths.isProtected("$ROOT/Android/media/com.chat", ROOT)).isFalse()
        assertThat(StoragePaths.isProtected("$ROOT/DCIM", ROOT)).isFalse()
        assertThat(StoragePaths.isProtected("$ROOT/Download/old.apk", ROOT)).isFalse()
    }

    @Test
    fun `the index follows a delete`() {
        val root = sample()
        root.find("$ROOT/DCIM/Camera")?.removeFile("VID_1.mp4")

        val index = StorageIndex.build(root, now)

        assertThat(index.categories.none { it.category == FileCategory.VIDEOS }).isTrue()
        assertThat(index.largest.none { it.name == "VID_1.mp4" }).isTrue()
        assertThat(index.totalBytes).isEqualTo(root.bytes)
    }
}
