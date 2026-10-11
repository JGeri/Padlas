package hu.padlas.feature.map

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class FileTypesTest {

    @Test
    fun `common extensions land in their category`() {
        val expected = mapOf(
            "IMG_2041.jpg" to FileCategory.IMAGES,
            "photo.HEIC" to FileCategory.IMAGES,
            "raw.dng" to FileCategory.IMAGES,
            "Screenshot.png" to FileCategory.IMAGES,
            "VID_0001.mp4" to FileCategory.VIDEOS,
            "clip.MKV" to FileCategory.VIDEOS,
            "voice.opus" to FileCategory.AUDIO,
            "song.flac" to FileCategory.AUDIO,
            "call.amr" to FileCategory.AUDIO,
            "invoice.pdf" to FileCategory.DOCUMENTS,
            "budget.xlsx" to FileCategory.DOCUMENTS,
            "book.epub" to FileCategory.DOCUMENTS,
            "backup.zip" to FileCategory.ARCHIVES,
            "logs.7z" to FileCategory.ARCHIVES,
            "game.apk" to FileCategory.INSTALLERS,
            "bundle.xapk" to FileCategory.INSTALLERS,
            "database.db" to FileCategory.OTHER,
        )
        expected.forEach { (name, category) ->
            assertWithMessage(name).that(FileTypes.categoryOf(name)).isEqualTo(category)
        }
    }

    @Test
    fun `the last extension decides for compound names`() {
        assertThat(FileTypes.extensionOf("site.tar.gz")).isEqualTo("gz")
        assertThat(FileTypes.categoryOf("site.tar.gz")).isEqualTo(FileCategory.ARCHIVES)
    }

    @Test
    fun `extensions are lower cased`() {
        assertThat(FileTypes.extensionOf("IMG_1.JPEG")).isEqualTo("jpeg")
    }

    @Test
    fun `hidden files and dotless names have no extension`() {
        assertThat(FileTypes.extensionOf(".nomedia")).isEmpty()
        assertThat(FileTypes.extensionOf("README")).isEmpty()
        assertThat(FileTypes.extensionOf("trailing.")).isEmpty()
        assertThat(FileTypes.categoryOf(".nomedia")).isEqualTo(FileCategory.OTHER)
    }

    @Test
    fun `a long tail after the last dot is not an extension`() {
        assertThat(FileTypes.extensionOf("com.whatsapp.backup-settings")).isEmpty()
    }

    @Test
    fun `media categories are the ones the gallery indexes`() {
        assertThat(FileCategory.all.filter { it.isMedia })
            .containsExactly(FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO)
    }

    @Test
    fun `the category count constant matches the enum`() {
        assertThat(CATEGORY_COUNT).isEqualTo(FileCategory.all.size)
    }
}
