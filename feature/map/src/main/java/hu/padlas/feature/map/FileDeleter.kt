package hu.padlas.feature.map

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal data class DeleteReport(
    val freedBytes: Long = 0L,
    val deletedFiles: Int = 0,
    val failures: Int = 0,
    /** Media files that are gone, so the gallery can be told to forget them. */
    val mediaPaths: List<String> = emptyList(),
) {
    operator fun plus(other: DeleteReport) = DeleteReport(
        freedBytes = freedBytes + other.freedBytes,
        deletedFiles = deletedFiles + other.deletedFiles,
        failures = failures + other.failures,
        mediaPaths = (mediaPaths + other.mediaPaths).take(MEDIA_PATH_CAP),
    )

    companion object {
        /** Past this the scanner is told about the parent folders instead of every file. */
        const val MEDIA_PATH_CAP = 500
    }
}

/**
 * Deletes bottom up without following links: a link inside a doomed folder is removed as a
 * link, never as the thing it points to.
 */
internal object FileDeleter {

    fun delete(target: Path): DeleteReport {
        val attrs = try {
            Files.readAttributes(target, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: IOException) {
            return DeleteReport(failures = 1)
        }
        if (!attrs.isDirectory) return deleteFile(target, attrs)

        var freed = 0L
        var deleted = 0
        var failures = 0
        val media = ArrayList<String>()
        try {
            Files.walkFileTree(
                target,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val report = deleteFile(file, attrs)
                        freed += report.freedBytes
                        deleted += report.deletedFiles
                        failures += report.failures
                        if (media.size < DeleteReport.MEDIA_PATH_CAP) media += report.mediaPaths
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        failures++
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        // A folder that kept a file it could not lose is already counted above.
                        try {
                            Files.delete(dir)
                        } catch (e: IOException) {
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (e: IOException) {
            failures++
        } catch (e: SecurityException) {
            failures++
        }
        return DeleteReport(freed, deleted, failures, media)
    }

    /**
     * For folders that were empty at scan time: only directories are removed, so a file
     * that appeared since then survives and keeps its folder alive.
     */
    fun deleteEmptyTree(target: Path): DeleteReport {
        var failures = 0
        try {
            Files.walkFileTree(
                target,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        failures++
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        failures++
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        try {
                            Files.delete(dir)
                        } catch (e: IOException) {
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (e: IOException) {
            failures++
        } catch (e: SecurityException) {
            failures++
        }
        return DeleteReport(failures = failures)
    }

    private fun deleteFile(file: Path, attrs: BasicFileAttributes): DeleteReport = try {
        Files.delete(file)
        if (attrs.isSymbolicLink) {
            DeleteReport()
        } else {
            val name = file.fileName?.toString().orEmpty()
            val isMedia = FileTypes.categoryOf(name).isMedia
            DeleteReport(
                freedBytes = attrs.size(),
                deletedFiles = 1,
                mediaPaths = if (isMedia) listOf(file.toString()) else emptyList(),
            )
        }
    } catch (e: IOException) {
        DeleteReport(failures = 1)
    } catch (e: SecurityException) {
        DeleteReport(failures = 1)
    }
}
