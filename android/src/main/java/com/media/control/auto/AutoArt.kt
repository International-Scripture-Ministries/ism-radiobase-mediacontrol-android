package com.media.control.auto

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Keeps a local copy of the covers of downloaded tracks so the car still shows
 * them offline. Android Auto reads artwork from another process, so the copies
 * are served through [AutoArtProvider] as content:// URIs.
 */
object AutoArt {
    private const val DIR = "auto_art"
    private const val MAX_BYTES = 8L * 1024 * 1024
    private const val TIMEOUT_MS = 15_000

    private val executor = Executors.newSingleThreadExecutor()

    /** Local copy of [artUrl] when there is one, else the URL itself. */
    fun uri(context: Context, artUrl: String): Uri? {
        if (artUrl.isEmpty()) return null
        val name = fileName(artUrl)
        if (!File(dir(context), name).isFile) return Uri.parse(artUrl)
        return Uri.Builder().scheme("content").authority(AutoArtProvider.authority(context)).appendPath(name).build()
    }

    /** Downloads the missing covers of [artUrls] and deletes the ones no longer needed. */
    fun sync(context: Context, artUrls: Set<String>) {
        val appContext = context.applicationContext
        executor.execute {
            val dir = dir(appContext).apply { mkdirs() }
            val wanted = artUrls.filter { it.startsWith("http") }.associateBy(::fileName)
            dir.listFiles()?.filter { it.name !in wanted }?.forEach { it.delete() }
            wanted.forEach { (name, url) ->
                val file = File(dir, name)
                if (!file.isFile) runCatching { download(url, file) }
            }
        }
    }

    internal fun dir(context: Context) = File(context.filesDir, DIR)

    internal fun fileName(artUrl: String): String =
        MessageDigest.getInstance("SHA-256").digest(artUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun download(url: String, target: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode !in 200..299) return
            if (connection.contentType?.startsWith("image/") == false) return
            if (connection.contentLengthLong > MAX_BYTES) return
            val partial = File(target.parentFile, "${target.name}.part")
            partial.outputStream().use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            partial.delete()
                            return
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (!partial.renameTo(target)) partial.delete()
        } finally {
            connection.disconnect()
        }
    }
}
