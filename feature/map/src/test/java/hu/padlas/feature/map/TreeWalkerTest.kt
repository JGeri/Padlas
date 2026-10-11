package hu.padlas.feature.map

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TreeWalkerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun write(relative: String, bytes: Int): File {
        val file = File(temp.root, relative)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
        return file
    }

    @Test
    fun `the walk measures every folder recursively`() {
        write("DCIM/Camera/IMG_1.jpg", 1_000)
        write("DCIM/Camera/VID_1.mp4", 5_000)
        write("Download/setup.apk", 2_000)
        write("notes.txt", 10)
        File(temp.root, "Empty").mkdirs()
        val counters = ScanCounters()

        val root = runBlocking { TreeWalker(Dispatchers.IO, counters).walk(temp.root.path) }

        assertThat(root.bytes).isEqualTo(8_010)
        assertThat(root.fileCount).isEqualTo(4)
        assertThat(root.find(root.path + "/DCIM/Camera")?.bytes).isEqualTo(6_000)
        assertThat(root.find(root.path + "/Empty")?.fileCount).isEqualTo(0)
        assertThat(root.categoryBytes[FileCategory.VIDEOS.ordinal]).isEqualTo(5_000)
        assertThat(counters.files.get()).isEqualTo(4)
        assertThat(counters.bytes.get()).isEqualTo(8_010)
        root.assertConsistent()
    }

    @Test
    fun `skipped folders are left out entirely`() {
        write("Android/data/com.app/cache.bin", 9_000)
        write("Android/media/com.chat/voice.opus", 300)

        val root = runBlocking {
            TreeWalker(Dispatchers.IO, skippedPaths = listOf(temp.root.path + "/Android/data")).walk(temp.root.path)
        }

        val android = root.child("Android") ?: error("missing")
        assertThat(android.child("data")).isNull()
        assertThat(android.bytes).isEqualTo(300)
    }

    @Test
    fun `a remeasure replaces a folder with what is on disk now`() {
        write("Pictures/a.png", 100)
        val gone = write("Pictures/b.png", 200)
        val root = runBlocking { TreeWalker(Dispatchers.IO).walk(temp.root.path) }
        val pictures = root.child("Pictures") ?: error("missing")
        gone.delete()

        val fresh = runBlocking { TreeWalker(Dispatchers.IO).remeasure(root, "Pictures") }
        root.replaceDir(pictures, fresh)

        assertThat(root.bytes).isEqualTo(100)
        root.assertConsistent()
    }

    @Test
    fun `deleting a folder removes everything under it and reports the bytes`() {
        write("Old/a.jpg", 400)
        write("Old/deeper/b.mp4", 600)
        write("Old/deeper/c.txt", 50)
        val target = File(temp.root, "Old").toPath()

        val report = FileDeleter.delete(target)

        assertThat(Files.exists(target)).isFalse()
        assertThat(report.freedBytes).isEqualTo(1_050)
        assertThat(report.deletedFiles).isEqualTo(3)
        assertThat(report.failures).isEqualTo(0)
        assertThat(report.mediaPaths.map { File(it).name }).containsExactly("a.jpg", "b.mp4")
    }

    @Test
    fun `deleting a single file reports its size`() {
        val file = write("one.pdf", 123)

        val report = FileDeleter.delete(file.toPath())

        assertThat(file.exists()).isFalse()
        assertThat(report.freedBytes).isEqualTo(123)
        assertThat(report.mediaPaths).isEmpty()
    }

    @Test
    fun `an empty folder goes with its empty children`() {
        File(temp.root, "Stale/Inner/Deeper").mkdirs()
        val target = File(temp.root, "Stale").toPath()

        val report = FileDeleter.deleteEmptyTree(target)

        assertThat(Files.exists(target)).isFalse()
        assertThat(report.failures).isEqualTo(0)
    }

    @Test
    fun `a file that appeared in an empty folder since the scan survives`() {
        File(temp.root, "Stale/Inner").mkdirs()
        val newcomer = write("Stale/Inner/new.jpg", 10)

        val report = FileDeleter.deleteEmptyTree(File(temp.root, "Stale").toPath())

        assertThat(newcomer.exists()).isTrue()
        assertThat(report.failures).isEqualTo(1)
        assertThat(report.freedBytes).isEqualTo(0)
    }

    @Test
    fun `deleting something that is not there is a failure, not a crash`() {
        val report = FileDeleter.delete(File(temp.root, "missing.bin").toPath())

        assertThat(report.failures).isEqualTo(1)
        assertThat(report.freedBytes).isEqualTo(0)
    }
}
