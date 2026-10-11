package hu.padlas.feature.map

import java.util.Locale

/** The ordinal is stored per file as a byte, so new categories may only be appended. */
enum class FileCategory(val label: String) {
    IMAGES("Képek"),
    VIDEOS("Videók"),
    AUDIO("Hang"),
    DOCUMENTS("Dokumentumok"),
    ARCHIVES("Archívumok"),
    INSTALLERS("Telepítők"),
    OTHER("Egyéb"),
    ;

    val isMedia: Boolean get() = this == IMAGES || this == VIDEOS || this == AUDIO

    companion object {
        val all: List<FileCategory> = entries.toList()

        fun fromOrdinal(ordinal: Int): FileCategory = all.getOrElse(ordinal) { OTHER }
    }
}

internal const val CATEGORY_COUNT = 7

internal object FileTypes {

    /** Longer than this is a name that happens to contain a dot, not an extension. */
    private const val MAX_EXTENSION = 8

    private val byExtension: Map<String, FileCategory> = buildMap {
        fun put(category: FileCategory, vararg extensions: String) = extensions.forEach { put(it, category) }
        put(
            FileCategory.IMAGES,
            "jpg", "jpeg", "jpe", "jfif", "png", "heic", "heif", "webp", "gif", "dng", "raw", "bmp",
            "tif", "tiff", "avif", "jxl", "svg", "ico", "cr2", "cr3", "nef", "arw", "orf", "rw2", "raf",
            "srw", "pef", "psd",
        )
        put(
            FileCategory.VIDEOS,
            "mp4", "m4v", "mkv", "mov", "3gp", "3g2", "webm", "avi", "wmv", "flv", "ts", "mts", "m2ts",
            "mpg", "mpeg", "vob", "ogv", "insv", "lrv",
        )
        put(
            FileCategory.AUDIO,
            "mp3", "m4a", "m4b", "aac", "flac", "wav", "ogg", "oga", "opus", "amr", "awb", "3ga", "wma",
            "mid", "midi", "aif", "aiff", "ape", "alac", "mka", "dsf",
        )
        put(
            FileCategory.DOCUMENTS,
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "pps", "ppsx", "txt", "rtf", "odt", "ods",
            "odp", "epub", "mobi", "azw", "azw3", "fb2", "djvu", "csv", "md", "pages", "numbers", "key",
        )
        put(
            FileCategory.ARCHIVES,
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "zst", "lz", "lz4", "lzma",
            "cab", "iso",
        )
        put(FileCategory.INSTALLERS, "apk", "apks", "xapk", "apkm")
    }

    /** Lower case, without the dot, empty when there is none. A leading dot marks a hidden file. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        if (name.length - dot - 1 > MAX_EXTENSION) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun categoryOf(name: String): FileCategory = categoryOfExtension(extensionOf(name))

    fun categoryOfExtension(extension: String): FileCategory =
        byExtension[extension.lowercase(Locale.ROOT)] ?: FileCategory.OTHER
}
