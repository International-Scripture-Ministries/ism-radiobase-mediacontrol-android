package com.media.control.auto

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File

/**
 * Stores the download index the app pushes, so Android Auto can list and play
 * downloaded audio while the WebView is not running and the phone is offline.
 * Every read checks the file on disk, so a download the app deleted since the
 * last push never reaches the car or the player.
 */
object AutoDownloads {
    private const val INDEX_FILE = "media_control_auto_downloads.json"

    @Volatile
    private var cached: DownloadIndex? = null

    fun save(context: Context, items: JSONArray) {
        val payload = items.toString()
        File(context.filesDir, INDEX_FILE).writeText(payload)
        val index = DownloadIndex.parse(payload)
        cached = index
        AutoArt.sync(context, index.filter(::isOnDisk).tracks.map { it.artUrl }.toSet())
    }

    /** Downloads whose file is still on disk. */
    fun index(context: Context): DownloadIndex = stored(context).filter(::isOnDisk)

    /** Local copy of a streaming URL, or null when it was not downloaded. */
    fun localUri(context: Context, remoteUrl: String): Uri? =
        stored(context).forUrl(remoteUrl)?.takeIf(::isOnDisk)?.let { Uri.fromFile(File(it.filePath)) }

    private fun stored(context: Context): DownloadIndex {
        cached?.let { return it }
        val file = File(context.filesDir, INDEX_FILE)
        val index = if (file.exists()) DownloadIndex.parse(file.readText()) else DownloadIndex.EMPTY
        cached = index
        return index
    }

    private fun isOnDisk(track: DownloadedTrack): Boolean {
        val file = File(track.filePath)
        return file.isFile && file.length() > 0
    }
}
