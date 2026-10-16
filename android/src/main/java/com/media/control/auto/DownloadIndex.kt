package com.media.control.auto

import org.json.JSONArray
import java.net.URI

enum class DownloadKind { BIBLE, STUDY }

/** An audio file the app finished downloading, pushed through `MediaControl.setAutoDownloads()`. */
data class DownloadedTrack(
    val kind: DownloadKind,
    /** Chapter ID ("LUK.3") or CMP teaching UUID. */
    val id: String,
    val bookId: String,
    val bookName: String,
    val bookOrder: Int,
    /** Chapter number, or the study's position inside its book. */
    val order: Int,
    val title: String,
    val remoteUrl: String,
    val localPath: String,
    val artUrl: String,
    val durationSeconds: Long,
) {
    val catalogId: String
        get() = when (kind) {
            DownloadKind.BIBLE -> MediaIds.chapter(id)
            DownloadKind.STUDY -> MediaIds.studyItem(bookId, id)
        }

    /** The app may store a plain path or a file:// URI. */
    val filePath: String
        get() = if (localPath.startsWith("file:")) runCatching { URI(localPath).path }.getOrNull() ?: localPath
        else localPath

    val fileUri: String
        get() = "file://$filePath"
}

/** Downloaded tracks in listening order: kind, then book, then chapter or study. */
class DownloadIndex(tracks: List<DownloadedTrack>) {

    val tracks: List<DownloadedTrack> = tracks.sortedWith(
        compareBy<DownloadedTrack>({ it.kind }, { it.bookOrder }, { it.bookId }, { it.order }, { it.title })
    )
    private val byCatalogId = this.tracks.associateBy { it.catalogId }
    private val byRemoteUrl = this.tracks.associateBy { it.remoteUrl }

    fun isEmpty() = tracks.isEmpty()

    fun forCatalogId(catalogId: String): DownloadedTrack? = byCatalogId[catalogId]

    fun forUrl(url: String): DownloadedTrack? = byRemoteUrl[url]

    fun kinds(): List<DownloadKind> = tracks.map { it.kind }.distinct()

    /** First track of each book, which carries the book's name and order. */
    fun books(kind: DownloadKind): List<DownloadedTrack> =
        tracks.filter { it.kind == kind }.distinctBy { it.bookId }

    fun tracksOf(kind: DownloadKind, bookId: String): List<DownloadedTrack> =
        tracks.filter { it.kind == kind && it.bookId == bookId }

    fun filter(predicate: (DownloadedTrack) -> Boolean) = DownloadIndex(tracks.filter(predicate))

    companion object {
        val EMPTY = DownloadIndex(emptyList())

        fun parse(payload: String): DownloadIndex {
            val array = runCatching { JSONArray(payload) }.getOrNull() ?: return EMPTY
            val tracks = (0 until array.length()).mapNotNull { i ->
                val it = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = when (it.optString("kind")) {
                    "bible" -> DownloadKind.BIBLE
                    "study" -> DownloadKind.STUDY
                    else -> return@mapNotNull null
                }
                val track = DownloadedTrack(
                    kind = kind,
                    id = it.optString("id"),
                    bookId = it.optString("bookId"),
                    bookName = it.optString("bookName"),
                    bookOrder = it.optInt("bookOrder", Int.MAX_VALUE),
                    order = it.optInt("order"),
                    title = it.optString("title"),
                    remoteUrl = it.optString("remoteUrl"),
                    localPath = it.optString("localPath"),
                    artUrl = it.optString("artUrl"),
                    durationSeconds = it.optLong("durationSeconds"),
                )
                val complete = track.id.isNotEmpty() && track.bookId.isNotEmpty() &&
                    track.remoteUrl.isNotEmpty() && track.localPath.isNotEmpty()
                track.takeIf { complete }
            }
            return DownloadIndex(tracks.distinctBy { it.catalogId })
        }
    }
}
