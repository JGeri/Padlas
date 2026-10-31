package hu.padlas.core.media

import android.content.Context

/**
 * Screenshot mode for the debug and profile builds (package `hu.padlas.debug`). With
 * `adb shell setprop debug.padlas.demo Pictures/PadlasDemo/` the gallery and the storage
 * analyzer only see that folder, so the README's pictures show mock photos and never the
 * owner's. The release package never reads the property.
 */
object DemoMode {

    private const val PROPERTY = "debug.padlas.demo"

    @Volatile
    private var resolved: Result<String?>? = null

    /** The relative path everything is confined to, with its trailing slash, or null. */
    fun folder(context: Context): String? {
        resolved?.let { return it.getOrNull() }
        val value = if (context.packageName.endsWith(".debug")) read() else null
        resolved = Result.success(value)
        return value
    }

    private fun read(): String? = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", PROPERTY))
        process.inputStream.bufferedReader().use { it.readLine() }?.trim()
    }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?.let { if (it.endsWith("/")) it else "$it/" }
}
