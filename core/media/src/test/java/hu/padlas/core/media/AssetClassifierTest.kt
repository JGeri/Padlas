package hu.padlas.core.media

import com.google.common.truth.Truth.assertThat
import hu.padlas.core.database.entity.ClassifiedBy
import hu.padlas.core.database.entity.ValueClass
import org.junit.Test

class AssetClassifierTest {

    @Test
    fun `camera folder is irreplaceable`() {
        val result = classify(path = "DCIM/Camera/", name = "20240101_120000.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.IRREPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    @Test
    fun `screenshot folder is ephemeral`() {
        val result = classify(path = "Pictures/Screenshots/", name = "shot.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    @Test
    fun `bare screenshots folder is ephemeral`() {
        val result = classify(path = "Screenshots/", name = "shot.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
    }

    @Test
    fun `screenshot file name survives a move to a neutral folder`() {
        val result = classify(path = "Pictures/Random/", name = "Screenshot_20240101_101010.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    @Test
    fun `display resolution wins over a neutral folder`() {
        val result = classify(path = "Pictures/Random/", name = "renamed.jpg", width = 1440, height = 3088)

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.RESOLUTION)
    }

    @Test
    fun `display resolution is orientation independent`() {
        val result = classify(path = "Pictures/Random/", name = "renamed.jpg", width = 2316, height = 1080)

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.RESOLUTION)
    }

    @Test
    fun `sixteen by nine video is not mistaken for a screen capture`() {
        val result = classify(
            path = "Movies/",
            name = "clip.mp4",
            mime = "video/mp4",
            width = 1920,
            height = 1080,
            isVideo = true,
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.REPLACEABLE)
    }

    @Test
    fun `received whatsapp media is replaceable`() {
        val result = classify(
            path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/",
            name = "IMG-20240101-WA0001.jpg",
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.REPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    @Test
    fun `whatsapp sent folder is irreplaceable`() {
        val result = classify(
            path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/",
            name = "IMG-20240101-WA0002.jpg",
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.IRREPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    @Test
    fun `a screenshot forwarded from the sent folder stays ephemeral`() {
        val result = classify(
            path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/",
            name = "IMG-20240101-WA0003.jpg",
            width = 1080,
            height = 2316,
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.RESOLUTION)
    }

    @Test
    fun `received telegram media is replaceable`() {
        val result = classify(path = "Pictures/Telegram/", name = "photo_1.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.REPLACEABLE)
    }

    @Test
    fun `downloads are replaceable`() {
        val result = classify(path = "Download/", name = "invoice.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.REPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    @Test
    fun `camera exif beats every path rule`() {
        val result = classify(path = "Download/", name = "sent-to-me.jpg", hasCameraExif = true)

        assertThat(result.valueClass).isEqualTo(ValueClass.IRREPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.EXIF)
    }

    @Test
    fun `favorite is always irreplaceable`() {
        val result = classify(
            path = "Pictures/Screenshots/",
            name = "shot.png",
            mime = "image/png",
            width = 1440,
            height = 3088,
            isFavorite = true,
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.IRREPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.MANUAL)
    }

    @Test
    fun `png without exif at screen aspect is generated`() {
        val result = classify(
            path = "Pictures/Random/",
            name = "image.png",
            mime = "image/png",
            width = 1000,
            height = 2200,
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.EPHEMERAL)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.MIME)
    }

    @Test
    fun `png at photo aspect is not generated`() {
        val result = classify(
            path = "Pictures/Random/",
            name = "image.png",
            mime = "image/png",
            width = 4000,
            height = 3000,
        )

        assertThat(result.valueClass).isEqualTo(ValueClass.REPLACEABLE)
    }

    @Test
    fun `unknown folder falls back to replaceable`() {
        val result = classify(path = "Pictures/Random/", name = "image.jpg")

        assertThat(result.valueClass).isEqualTo(ValueClass.REPLACEABLE)
        assertThat(result.classifiedBy).isEqualTo(ClassifiedBy.PATH)
    }

    private fun classify(
        path: String,
        name: String,
        mime: String = "image/jpeg",
        width: Int = 3024,
        height: Int = 4032,
        hasCameraExif: Boolean = false,
        isFavorite: Boolean = false,
        isVideo: Boolean = false,
    ): Classification = AssetClassifier.classify(
        ClassificationInput(
            relativePath = path,
            displayName = name,
            mimeType = mime,
            width = width,
            height = height,
            hasCameraExif = hasCameraExif,
            isFavorite = isFavorite,
            isVideo = isVideo,
        ),
    )
}
