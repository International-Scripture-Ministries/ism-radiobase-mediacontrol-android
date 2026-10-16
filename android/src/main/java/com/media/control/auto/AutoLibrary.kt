package com.media.control.auto

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * Loads the station's catalog from CMP and turns it into Android Auto nodes.
 * Every method blocks on the network, so callers run it off the main thread.
 * Downloaded audio needs no network: it has its own branch and replaces the
 * stream wherever the same track is queued.
 */
class AutoLibrary(private val context: Context) {

    class NotConfigured : Exception("plugins.MediaControl.androidAuto is missing from capacitor.config.json")

    data class Queue(val items: List<LibraryNode>, val startIndex: Int)

    private data class Session(
        val settings: AutoSettings,
        val station: Station,
        val language: StationLanguage,
        val labels: Labels,
    ) {
        private val englishCovers = CatalogParser.usesEnglishCovers(station, language)
        fun cover(artUrl: String) = CatalogParser.bookCover(artUrl, englishCovers)
    }

    private val client = CatalogClient(File(context.cacheDir, "auto-catalog"), isOnline = ::hasInternet)

    fun children(parentId: String): List<LibraryNode> {
        val downloads = AutoDownloads.index(context)
        val session = runCatching { session() }.getOrNull()
        val labels = session?.labels ?: labels(AutoConfig.overrides(context).labels, languageCode = null)
        if (MediaIds.isDownloadsId(parentId)) return downloadChildren(labels, downloads, parentId)
        val nodes = session?.let { runCatching { childrenOf(it, parentId, downloads) }.getOrNull() }
            ?: return offlineChildren(labels, downloads, parentId)
        return LibraryTree.withDownloads(nodes, downloads)
    }

    /**
     * The tapped track's book as a queue, plus the next book so playback keeps
     * going. When the catalog cannot load, a downloaded track still plays from
     * the downloads queue.
     */
    fun queueFor(mediaId: String, withNextBook: Boolean = true): Queue? {
        val downloads = AutoDownloads.index(context)
        if (MediaIds.isDownloadsId(mediaId)) return downloadQueue(downloads, mediaId)
        val queue = runCatching { catalogQueue(mediaId, withNextBook) }.getOrNull()
            ?: return downloadQueue(downloads, MediaIds.downloads(mediaId))
        return queue.copy(items = LibraryTree.withDownloads(queue.items, downloads))
    }

    fun item(mediaId: String): LibraryNode? =
        queueFor(mediaId, withNextBook = false)?.let { it.items[it.startIndex] }

    /** Drops cached responses, e.g. after the app switches language. */
    fun invalidate() {
        client.clear()
    }

    private fun downloadChildren(labels: Labels, downloads: DownloadIndex, parentId: String): List<LibraryNode> {
        if (parentId == MediaIds.DOWNLOADS) return LibraryTree.downloadsRoot(labels, downloads)
        return when (val parsed = MediaIds.parse(MediaIds.catalogId(parentId))) {
            is MediaIds.Parsed.Node -> when (parsed.id) {
                MediaIds.BIBLE -> LibraryTree.downloadBooks(downloads, DownloadKind.BIBLE)
                MediaIds.STUDY -> LibraryTree.downloadBooks(downloads, DownloadKind.STUDY)
                else -> emptyList()
            }
            else -> downloadedRows(downloads, parsed)
        }
    }

    /** No catalog: the root keeps the downloads branch, a book shows what was downloaded from it. */
    private fun offlineChildren(labels: Labels, downloads: DownloadIndex, parentId: String): List<LibraryNode> {
        if (parentId == MediaIds.ROOT) return LibraryTree.offlineRoot(labels, !downloads.isEmpty())
        return downloadedRows(downloads, MediaIds.parse(parentId)).ifEmpty { listOf(LibraryTree.message(labels.offline)) }
    }

    private fun downloadedRows(downloads: DownloadIndex, parsed: MediaIds.Parsed): List<LibraryNode> = when (parsed) {
        is MediaIds.Parsed.BibleBookId -> LibraryTree.downloadRows(downloads, DownloadKind.BIBLE, parsed.bookId)
        is MediaIds.Parsed.StudyBookId -> LibraryTree.downloadRows(downloads, DownloadKind.STUDY, parsed.bookId)
        else -> emptyList()
    }

    /** Every downloaded track of the same kind, starting at the tapped one. */
    private fun downloadQueue(downloads: DownloadIndex, mediaId: String): Queue? {
        val target = downloads.forCatalogId(MediaIds.catalogId(mediaId)) ?: return null
        val tracks = downloads.tracks.filter { it.kind == target.kind }
        return Queue(tracks.map(LibraryTree::downloadTrack), tracks.indexOf(target))
    }

    private fun catalogQueue(mediaId: String, withNextBook: Boolean): Queue? {
        val session = session()
        return when (val parsed = MediaIds.parse(mediaId)) {
            is MediaIds.Parsed.ChapterId ->
                bookQueue(mediaId, bibleBooks(session), parsed.bookId, withNextBook) { chapterTracks(session, it) }
            is MediaIds.Parsed.StudyItemId ->
                bookQueue(mediaId, studyBooks(session), parsed.bookId, withNextBook) { studyTracks(session, it) }
            else -> null
        }
    }

    private fun <B : Book> bookQueue(
        mediaId: String,
        books: List<B>,
        bookId: String,
        withNextBook: Boolean,
        tracksOf: (B) -> List<LibraryNode>,
    ): Queue? {
        val index = books.indexOfFirst { it.id == bookId }
        if (index < 0) return null
        val tracks = tracksOf(books[index]).toMutableList()
        val start = tracks.indexOfFirst { it.id == mediaId }
        if (start < 0) return null
        if (withNextBook) {
            books.getOrNull(index + 1)?.let { next -> runCatching { tracks += tracksOf(next) } }
        }
        return Queue(tracks, start)
    }

    private fun childrenOf(session: Session, parentId: String, downloads: DownloadIndex): List<LibraryNode> {
        val labels = session.labels
        return when (val parsed = MediaIds.parse(parentId)) {
            is MediaIds.Parsed.Node -> when (parsed.id) {
                MediaIds.ROOT -> LibraryTree.root(
                    labels,
                    hasBible = session.language.audioId.isNotEmpty(),
                    hasStudies = runCatching { studyBooks(session).isNotEmpty() }.getOrDefault(false),
                    hasDownloads = !downloads.isEmpty(),
                )
                MediaIds.BIBLE -> LibraryTree.bibleTestaments(labels, bibleBooks(session))
                MediaIds.BIBLE_OLD -> LibraryTree.bibleBooks(bibleBooks(session), Testament.OLD)
                MediaIds.BIBLE_NEW -> LibraryTree.bibleBooks(bibleBooks(session), Testament.NEW)
                MediaIds.STUDY -> LibraryTree.studyTestaments(labels, studyBooks(session))
                MediaIds.STUDY_OLD -> LibraryTree.studyBooks(studyBooks(session), Testament.OLD)
                MediaIds.STUDY_NEW -> LibraryTree.studyBooks(studyBooks(session), Testament.NEW)
                MediaIds.STUDY_GUIDELINES -> studyRows(session, MediaIds.GUIDELINES_BOOK_ID)
                else -> emptyList()
            }
            is MediaIds.Parsed.BibleBookId -> {
                val book = bibleBooks(session).firstOrNull { it.id == parsed.bookId } ?: return emptyList()
                LibraryTree.chapterRows(chapters(session, book), book.name, book.artUrl)
            }
            is MediaIds.Parsed.StudyBookId -> studyRows(session, parsed.bookId)
            else -> emptyList()
        }
    }

    private fun studyRows(session: Session, bookId: String): List<LibraryNode> {
        val book = studyBooks(session).firstOrNull { it.id == bookId } ?: return emptyList()
        return LibraryTree.studyRows(studies(session, book.id), book.name, session.settings.teachingsCdnUrl)
    }

    private fun chapterTracks(session: Session, book: BibleBook): List<LibraryNode> =
        chapters(session, book).map { LibraryTree.chapterTrack(it, book.name, book.artUrl) }

    private fun studyTracks(session: Session, book: StudyBook): List<LibraryNode> =
        studies(session, book.id).map { LibraryTree.studyTrack(it, book.name, session.settings.teachingsCdnUrl) }

    private fun bibleBooks(session: Session): List<BibleBook> =
        CatalogParser.bibleBooks(get(session, "bibles/${session.language.bibleId}/books"))
            .map { it.copy(artUrl = session.cover(it.artUrl)) }

    private fun chapters(session: Session, book: BibleBook): List<Chapter> =
        CatalogParser.chapters(get(session, "audio-bibles/${session.language.audioId}/book/${book.id}"))
            .map { it.copy(artUrl = session.cover(it.artUrl)) }

    private fun studyBooks(session: Session): List<StudyBook> {
        val language = encode(CatalogParser.studiesLanguage(session.language))
        val format = session.station.studyAudioFormat
        val query = if (format.isNotEmpty()) "&audio_format=${encode(format)}" else ""
        return CatalogParser.studyBooks(get(session, "bibles/study-order?language=$language$query"))
            .map { it.copy(artUrl = session.cover(it.artUrl)) }
    }

    private fun studies(session: Session, bookId: String): List<Study> {
        val body = JSONObject()
            .put("language", CatalogParser.studiesLanguage(session.language))
            .put("bible_book", bookId)
        if (session.station.studyAudioFormat.isNotEmpty()) body.put("audio_format", session.station.studyAudioFormat)
        val payload = client.post(session.settings.cmpApiUrl + "teachings/search", body.toString(), headers(session.settings))
        return CatalogParser.studies(payload, bookId).map { it.copy(artUrl = session.cover(it.artUrl)) }
    }

    private fun get(session: Session, path: String): String =
        client.get(session.settings.cmpApiUrl + path, headers(session.settings))

    private fun headers(settings: AutoSettings): Map<String, String> =
        if (settings.cmpApiKey.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer ${settings.cmpApiKey}")

    private fun session(): Session {
        val settings = AutoConfig.settings(context) ?: throw NotConfigured()
        val payload = try {
            client.get("${settings.stationDataUrl}/${settings.stationId}.json")
        } catch (e: Exception) {
            bundledAsset("public/data/station-data/${settings.stationId}.json") ?: throw e
        }
        val station = CatalogParser.station(payload)
        val overrides = AutoConfig.overrides(context)
        val language = CatalogParser.activeLanguage(station, overrides.languageCode, settings.stationId)
            ?: throw IllegalStateException("Station ${settings.stationId} has no languages")
        return Session(settings, station, language, labels(overrides.labels, language.code))
    }

    /**
     * Labels pushed by the app win; then the translation file bundled with the
     * web build for [languageCode]; then English.
     */
    private fun labels(pushed: Map<String, String>, languageCode: String?): Labels {
        val bundled = languageCode
            ?.let { bundledAsset("public/assets/i18n/cdn/${it.lowercase()}.json") }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        fun pick(key: String, fallback: String, bundledSection: String? = null): String =
            pushed[key]?.takeIf { it.isNotEmpty() }
                ?: bundledSection?.let { bundled?.optJSONObject(it)?.optString(key) }?.takeIf { it.isNotEmpty() }
                ?: fallback
        val defaults = Labels()
        return Labels(
            bible = pick("bible", defaults.bible, "BIBLE"),
            study = pick("study", defaults.study, "SIDEBAR"),
            oldTestament = pick("old_testament", defaults.oldTestament, "BIBLE"),
            newTestament = pick("new_testament", defaults.newTestament, "BIBLE"),
            guidelines = pick("guidelines", defaults.guidelines),
            downloads = pick("downloads", defaults.downloads),
            offline = pick("offline", defaults.offline),
            unavailable = pick("unavailable", defaults.unavailable),
        )
    }

    private fun hasInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun bundledAsset(path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
