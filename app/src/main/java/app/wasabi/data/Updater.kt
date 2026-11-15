package app.wasabi.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. */
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val notes: String,
)

/**
 * In-app updates from the project's GitHub releases: check the latest
 * release, download its APK into the app's cache, and hand it to Android's
 * installer. Android asks the user to confirm every install.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Gaalpos/wasabi/releases/latest"

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** The latest release if it's newer than [current], otherwise null. */
    suspend fun check(current: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL(LATEST).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Wasabi-Android")
        try {
            if (conn.responseCode != 200) throw ApiException("Couldn't reach GitHub (${conn.responseCode}).")
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val version = j.optString("tag_name").removePrefix("v")
            if (compare(version, current) <= 0) return@withContext null
            val assets = j.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return@withContext null
            UpdateInfo(
                version = version,
                apkUrl = apk.optString("browser_download_url"),
                apkName = apk.optString("name"),
                sizeBytes = apk.optLong("size"),
                notes = j.optString("body").trim(),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // drop older downloads
            val out = File(dir, info.apkName.ifBlank { "wasabi-${info.version}.apk" })
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Wasabi-Android")
            try {
                if (conn.responseCode != 200) throw ApiException("Download failed (${conn.responseCode}).")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            // Only hand over an APK that really is a newer Wasabi.
            val archive = context.packageManager.getPackageArchiveInfo(out.path, 0)
            if (archive?.packageName != context.packageName) {
                out.delete()
                throw ApiException("The downloaded file isn't a Wasabi update.")
            }
            out
        }

    /**
     * Opens Android's installer for [apk]. Returns false when Wasabi first
     * needs permission to install apps; the matching settings page is opened
     * instead, and the caller should try again when the user comes back.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    /** Compares dotted versions numerically: 1.10 > 1.9. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
