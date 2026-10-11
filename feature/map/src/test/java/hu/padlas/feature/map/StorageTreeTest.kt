package hu.padlas.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StorageTreeTest {

    private fun sample() = tree {
        file("notes.txt", 100)
        dir("DCIM") {
            dir("Camera") {
                file("IMG_1.jpg", 4_000)
                file("IMG_2.jpg", 6_000)
                file("VID_1.mp4", 50_000)
            }
            file("cover.png", 500)
        }
        dir("Download") {
            file("setup.apk", 20_000)
            dir("empty")
        }
    }

    @Test
    fun `totals are recursive`() {
        val root = sample()

        assertThat(root.bytes).isEqualTo(80_600)
        assertThat(root.fileCount).isEqualTo(6)
        assertThat(root.categoryBytes[FileCategory.IMAGES.ordinal]).isEqualTo(10_500)
        assertThat(root.categoryBytes[FileCategory.VIDEOS.ordinal]).isEqualTo(50_000)
        root.assertConsistent()
    }

    @Test
    fun `a folder is coloured by what fills it most`() {
        val root = sample()

        assertThat(root.find("$ROOT/DCIM")?.dominantCategory()).isEqualTo(FileCategory.VIDEOS)
        assertThat(root.find("$ROOT/Download")?.dominantCategory()).isEqualTo(FileCategory.INSTALLERS)
        assertThat(root.find("$ROOT/Download/empty")?.dominantCategory()).isEqualTo(FileCategory.OTHER)
    }

    @Test
    fun `paths resolve down the tree`() {
        val root = sample()

        assertThat(root.find(ROOT)).isSameInstanceAs(root)
        assertThat(root.find("$ROOT/DCIM/Camera")?.fileCount).isEqualTo(3)
        assertThat(root.find("$ROOT/DCIM/Nope")).isNull()
        assertThat(root.find("/somewhere/else")).isNull()
    }

    @Test
    fun `a vanished folder falls back to its deepest surviving ancestor`() {
        val root = sample()

        assertThat(root.deepestExisting("$ROOT/DCIM/Camera/Gone/Deeper").path).isEqualTo("$ROOT/DCIM/Camera")
        assertThat(root.deepestExisting("/elsewhere")).isSameInstanceAs(root)
        assertThat(root.deepestExisting(ROOT + "x")).isSameInstanceAs(root)
    }

    @Test
    fun `removing a file takes its size off every ancestor`() {
        val root = sample()
        val camera = root.find("$ROOT/DCIM/Camera") ?: error("missing")

        assertThat(camera.removeFile("VID_1.mp4")).isTrue()

        assertThat(camera.files.names.toList()).containsExactly("IMG_1.jpg", "IMG_2.jpg")
        assertThat(camera.bytes).isEqualTo(10_000)
        assertThat(root.find("$ROOT/DCIM")?.bytes).isEqualTo(10_500)
        assertThat(root.bytes).isEqualTo(30_600)
        assertThat(root.fileCount).isEqualTo(5)
        assertThat(root.categoryBytes[FileCategory.VIDEOS.ordinal]).isEqualTo(0)
        assertThat(root.find("$ROOT/DCIM")?.dominantCategory()).isEqualTo(FileCategory.IMAGES)
        root.assertConsistent()
    }

    @Test
    fun `removing a missing file changes nothing`() {
        val root = sample()

        assertThat(root.removeFile("nothing.bin")).isFalse()
        assertThat(root.bytes).isEqualTo(80_600)
    }

    @Test
    fun `removing a folder detaches its whole subtree`() {
        val root = sample()
        val dcim = root.find("$ROOT/DCIM") ?: error("missing")

        assertThat(root.removeDir(dcim)).isTrue()

        assertThat(root.find("$ROOT/DCIM")).isNull()
        assertThat(root.bytes).isEqualTo(20_100)
        assertThat(root.fileCount).isEqualTo(2)
        assertThat(root.categoryBytes[FileCategory.IMAGES.ordinal]).isEqualTo(0)
        root.assertConsistent()
    }

    @Test
    fun `a nested folder removal reaches the root`() {
        val root = sample()
        val dcim = root.find("$ROOT/DCIM") ?: error("missing")
        val camera = dcim.child("Camera") ?: error("missing")

        dcim.removeDir(camera)

        assertThat(dcim.bytes).isEqualTo(500)
        assertThat(root.bytes).isEqualTo(20_600)
        root.assertConsistent()
    }

    @Test
    fun `replacing a subtree moves only the difference up`() {
        val root = sample()
        val download = root.find("$ROOT/Download") ?: error("missing")
        val fresh = DirNode("Download", root).apply {
            val leaves = FileLeavesBuilder()
            leaves.add("left.pdf", 1_000, 1L, FileCategory.DOCUMENTS)
            files = leaves.build()
            recomputeTotals()
        }

        assertThat(root.replaceDir(download, fresh)).isTrue()

        assertThat(root.bytes).isEqualTo(80_600 - 20_000 + 1_000)
        assertThat(root.categoryBytes[FileCategory.INSTALLERS.ordinal]).isEqualTo(0)
        assertThat(root.categoryBytes[FileCategory.DOCUMENTS.ordinal]).isEqualTo(100 + 1_000)
        assertThat(root.child("Download")).isSameInstanceAs(fresh)
        root.assertConsistent()
    }

    @Test
    fun `file leaves drop one entry and keep the rest aligned`() {
        val leaves = FileLeavesBuilder().apply {
            add("a.jpg", 1, 10, FileCategory.IMAGES)
            add("b.mp4", 2, 20, FileCategory.VIDEOS)
            add("c.pdf", 3, 30, FileCategory.DOCUMENTS)
        }.build()

        val trimmed = leaves.without(1)

        assertThat(trimmed.names.toList()).containsExactly("a.jpg", "c.pdf").inOrder()
        assertThat(trimmed.sizes.toList()).containsExactly(1L, 3L).inOrder()
        assertThat(trimmed.modified.toList()).containsExactly(10L, 30L).inOrder()
        assertThat(trimmed.category(1)).isEqualTo(FileCategory.DOCUMENTS)
        assertThat(leaves.without(7)).isSameInstanceAs(leaves)
    }

    @Test
    fun `the builder grows past its first capacity`() {
        val builder = FileLeavesBuilder()
        repeat(100) { builder.add("f$it.bin", it.toLong(), 0L, FileCategory.OTHER) }

        val leaves = builder.build()

        assertThat(leaves.count).isEqualTo(100)
        assertThat(builder.bytes).isEqualTo((0 until 100).sum().toLong())
        assertThat(leaves.names.last()).isEqualTo("f99.bin")
    }
}
