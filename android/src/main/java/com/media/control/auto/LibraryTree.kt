package com.media.control.auto

/**
 * Browse IDs for Android Auto. Playable items resolve to audio URLs before they
 * reach the player, because the app identifies the current track by its URL.
 */
object MediaIds {
    const val ROOT = "root"
    const val BIBLE = "bible"
    const val BIBLE_OLD = "bible/ot"
    const val BIBLE_NEW = "bible/nt"
    const val STUDY = "study"
    const val STUDY_GUIDELINES = "study/gdl"
    const val STUDY_OLD = "study/ot"
    const val STUDY_NEW = "study/nt"
    const val MESSAGE = "message"

    /**
     * The downloads branch mirrors the catalog under this prefix:
     * "dl/bible/book/LUK", "dl/bible/ch/LUK.3", "dl/study/item/LUK/<uuid>".
     */
    const val DOWNLOADS = "dl"
    private const val DOWNLOADS_PREFIX = "dl/"

    private const val BIBLE_BOOK = "bible/book/"
    private const val CHAPTER = "bible/ch/"
    private const val STUDY_BOOK = "study/book/"
    private const val STUDY_ITEM = "study/item/"

    const val GUIDELINES_BOOK_ID = "GDL"

    private val NODES = setOf(ROOT, BIBLE, BIBLE_OLD, BIBLE_NEW, STUDY, STUDY_GUIDELINES, STUDY_OLD, STUDY_NEW)

    fun bibleBook(bookId: String) = BIBLE_BOOK + bookId
    fun chapter(chapterId: String) = CHAPTER + chapterId
    fun studyBook(bookId: String) = STUDY_BOOK + bookId
    fun studyItem(bookId: String, uuid: String) = "$STUDY_ITEM$bookId/$uuid"

    fun downloads(catalogId: String) = DOWNLOADS_PREFIX + catalogId
    fun isDownloadsId(id: String) = id == DOWNLOADS || id.startsWith(DOWNLOADS_PREFIX)
    /** Catalog IDs pass through unchanged. */
    fun catalogId(id: String) = id.removePrefix(DOWNLOADS_PREFIX)

    fun isBrowseId(id: String) = id == ROOT || id.startsWith(BIBLE) || id.startsWith(STUDY) || isDownloadsId(id)

    sealed class Parsed {
        data class Node(val id: String) : Parsed()
        data class BibleBookId(val bookId: String) : Parsed()
        data class ChapterId(val chapterId: String, val bookId: String) : Parsed()
        data class StudyBookId(val bookId: String) : Parsed()
        data class StudyItemId(val bookId: String, val uuid: String) : Parsed()
        object Unknown : Parsed()
    }

    fun parse(id: String): Parsed = when {
        id.startsWith(BIBLE_BOOK) -> Parsed.BibleBookId(id.removePrefix(BIBLE_BOOK))
        id.startsWith(CHAPTER) -> {
            val chapterId = id.removePrefix(CHAPTER)
            Parsed.ChapterId(chapterId, chapterId.substringBefore('.'))
        }
        id.startsWith(STUDY_BOOK) -> Parsed.StudyBookId(id.removePrefix(STUDY_BOOK))
        id.startsWith(STUDY_ITEM) -> {
            val rest = id.removePrefix(STUDY_ITEM)
            Parsed.StudyItemId(rest.substringBefore('/'), rest.substringAfter('/'))
        }
        id in NODES -> Parsed.Node(id)
        else -> Parsed.Unknown
    }
}

data class Labels(
    val bible: String = "Bible",
    val study: String = "Study",
    val oldTestament: String = "Old Testament",
    val newTestament: String = "New Testament",
    val guidelines: String = "Guidelines",
    val downloads: String = "Downloads",
    val offline: String = "No connection. Try again when you have signal.",
    val unavailable: String = "Content not available in this language.",
)

data class LibraryNode(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artUrl: String = "",
    val playable: Boolean = false,
    val browsable: Boolean = !playable,
    val audioUrl: String = "",
    val durationMs: Long = 0,
    val downloaded: Boolean = false,
    /** The player reads this instead of [audioUrl] when set. */
    val localUri: String = "",
)

/** Builds the nodes Android Auto shows. Pure functions, covered by unit tests. */
object LibraryTree {

    fun root(labels: Labels, hasBible: Boolean, hasStudies: Boolean, hasDownloads: Boolean = false): List<LibraryNode> {
        val tabs = buildList {
            if (hasBible) add(LibraryNode(MediaIds.BIBLE, labels.bible))
            if (hasStudies) add(LibraryNode(MediaIds.STUDY, labels.study))
            if (hasDownloads) add(LibraryNode(MediaIds.DOWNLOADS, labels.downloads))
        }
        return tabs.ifEmpty { listOf(message(labels.unavailable)) }
    }

    /** Root when the catalog cannot load: downloads stay reachable. */
    fun offlineRoot(labels: Labels, hasDownloads: Boolean): List<LibraryNode> = buildList {
        if (hasDownloads) add(LibraryNode(MediaIds.DOWNLOADS, labels.downloads))
        add(message(labels.offline))
    }

    /** Bible and Study folders, or straight to the books when only one kind was downloaded. */
    fun downloadsRoot(labels: Labels, index: DownloadIndex): List<LibraryNode> {
        val kinds = index.kinds()
        if (kinds.size == 1) return downloadBooks(index, kinds.single())
        return kinds.map {
            when (it) {
                DownloadKind.BIBLE -> LibraryNode(MediaIds.downloads(MediaIds.BIBLE), labels.bible)
                DownloadKind.STUDY -> LibraryNode(MediaIds.downloads(MediaIds.STUDY), labels.study)
            }
        }
    }

    fun downloadBooks(index: DownloadIndex, kind: DownloadKind): List<LibraryNode> =
        index.books(kind).map {
            val bookId = when (kind) {
                DownloadKind.BIBLE -> MediaIds.bibleBook(it.bookId)
                DownloadKind.STUDY -> MediaIds.studyBook(it.bookId)
            }
            LibraryNode(MediaIds.downloads(bookId), it.bookName.ifEmpty { it.bookId }, artUrl = it.artUrl)
        }

    /** Same row style as the catalog: chapter numbers, or study titles with their length. */
    fun downloadRows(index: DownloadIndex, kind: DownloadKind, bookId: String): List<LibraryNode> =
        index.tracksOf(kind, bookId).map { track ->
            val node = downloadTrack(track)
            when (kind) {
                DownloadKind.BIBLE -> node.copy(title = if (track.order > 0) track.order.toString() else track.title, subtitle = "")
                DownloadKind.STUDY -> node.copy(subtitle = durationLabel(track.durationSeconds))
            }
        }

    fun downloadTrack(track: DownloadedTrack) = LibraryNode(
        id = MediaIds.downloads(track.catalogId),
        title = track.title,
        subtitle = track.bookName,
        artUrl = track.artUrl,
        playable = true,
        audioUrl = track.remoteUrl,
        durationMs = track.durationSeconds * 1000,
        downloaded = true,
        localUri = track.fileUri,
    )

    /** Points catalog tracks at their downloaded copies. */
    fun withDownloads(nodes: List<LibraryNode>, index: DownloadIndex): List<LibraryNode> =
        nodes.map { node ->
            val track = index.forCatalogId(MediaIds.catalogId(node.id)) ?: return@map node
            node.copy(downloaded = true, localUri = track.fileUri)
        }

    fun bibleTestaments(labels: Labels, books: List<BibleBook>): List<LibraryNode> = buildList {
        if (books.any { it.testament == Testament.OLD }) add(LibraryNode(MediaIds.BIBLE_OLD, labels.oldTestament))
        if (books.any { it.testament == Testament.NEW }) add(LibraryNode(MediaIds.BIBLE_NEW, labels.newTestament))
    }

    fun studyTestaments(labels: Labels, books: List<StudyBook>): List<LibraryNode> = buildList {
        books.firstOrNull { it.id == MediaIds.GUIDELINES_BOOK_ID }?.let {
            add(LibraryNode(MediaIds.STUDY_GUIDELINES, it.name.ifEmpty { labels.guidelines }, artUrl = it.artUrl))
        }
        if (books.any { it.testament == Testament.OLD }) add(LibraryNode(MediaIds.STUDY_OLD, labels.oldTestament))
        if (books.any { it.testament == Testament.NEW }) add(LibraryNode(MediaIds.STUDY_NEW, labels.newTestament))
    }

    fun bibleBooks(books: List<BibleBook>, testament: Testament): List<LibraryNode> =
        books.filter { it.testament == testament }
            .map { LibraryNode(MediaIds.bibleBook(it.id), it.name, artUrl = it.artUrl) }

    fun studyBooks(books: List<StudyBook>, testament: Testament): List<LibraryNode> =
        books.filter { it.testament == testament && it.id != MediaIds.GUIDELINES_BOOK_ID }
            .map { LibraryNode(MediaIds.studyBook(it.id), it.name, artUrl = it.artUrl) }

    /** Rows in the chapter list show only the number, as in CarPlay. */
    fun chapterRows(chapters: List<Chapter>, bookName: String, bookArt: String): List<LibraryNode> =
        chapters.map { chapterTrack(it, bookName, bookArt).copy(title = it.number.toString(), subtitle = "") }

    fun chapterTrack(chapter: Chapter, bookName: String, bookArt: String) = LibraryNode(
        id = MediaIds.chapter(chapter.id),
        title = chapter.reference.ifEmpty { "$bookName ${chapter.number}" },
        subtitle = bookName,
        artUrl = chapter.artUrl.ifEmpty { bookArt },
        playable = true,
        audioUrl = chapter.audioUrl,
    )

    fun studyRows(studies: List<Study>, bookName: String, teachingsCdnUrl: String): List<LibraryNode> =
        studies.map { studyTrack(it, bookName, teachingsCdnUrl).copy(subtitle = durationLabel(it.durationSeconds)) }

    fun studyTrack(study: Study, bookName: String, teachingsCdnUrl: String) = LibraryNode(
        id = MediaIds.studyItem(study.bookId, study.uuid),
        title = study.name,
        subtitle = bookName,
        artUrl = study.artUrl,
        playable = true,
        audioUrl = teachingsCdnUrl + study.uuid,
        durationMs = study.durationSeconds * 1000,
    )

    fun message(text: String) = LibraryNode(MediaIds.MESSAGE, text, browsable = false)

    private fun durationLabel(seconds: Long): String {
        val minutes = seconds / 60
        return if (minutes > 0) "$minutes min" else ""
    }
}
