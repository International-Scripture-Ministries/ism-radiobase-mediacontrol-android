package com.media.control.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsTest {

    private val labels = Labels(bible = "Biblia", study = "Estudio", downloads = "Descargas")

    private val payload = """
        [
          {"kind":"study","id":"u-2","bookId":"LUK","bookName":"Lucas","bookOrder":3,"order":2002,
           "title":"Lucas 2","remoteUrl":"https://cdn/u-2","localPath":"file:///data/files/u-2.mp3","durationSeconds":1500},
          {"kind":"bible","id":"LUK.3","bookId":"LUK","bookName":"Lucas","bookOrder":41,"order":3,
           "title":"Lucas 3","remoteUrl":"https://bible/LUK3.mp3","localPath":"/data/files/LUK_3.mp3"},
          {"kind":"bible","id":"GEN.1","bookId":"GEN","bookName":"Génesis","bookOrder":0,"order":1,
           "title":"Génesis 1","remoteUrl":"https://bible/GEN1.mp3","localPath":"/data/files/GEN_1.mp3"},
          {"kind":"study","id":"u-1","bookId":"GDL","bookName":"Guías","bookOrder":0,"order":1,
           "title":"Guía 1","remoteUrl":"https://cdn/u-1","localPath":"/data/files/u-1.mp3","durationSeconds":40},
          {"kind":"study","id":"","bookId":"LUK","remoteUrl":"https://cdn/x","localPath":"/x.mp3"},
          {"kind":"video","id":"v","bookId":"LUK","remoteUrl":"https://cdn/v","localPath":"/v.mp4"}
        ]
    """.trimIndent()

    private val index = DownloadIndex.parse(payload)

    @Test
    fun `parse keeps complete entries in listening order`() {
        assertEquals(listOf("GEN.1", "LUK.3", "u-1", "u-2"), index.tracks.map { it.id })
    }

    @Test
    fun `parse survives garbage`() {
        assertTrue(DownloadIndex.parse("not json").isEmpty())
    }

    @Test
    fun `file URIs and plain paths both resolve to a path`() {
        assertEquals("/data/files/u-2.mp3", index.forUrl("https://cdn/u-2")!!.filePath)
        assertEquals("/data/files/LUK_3.mp3", index.forUrl("https://bible/LUK3.mp3")!!.filePath)
    }

    @Test
    fun `lookups match the catalog IDs`() {
        assertEquals("LUK.3", index.forCatalogId(MediaIds.chapter("LUK.3"))!!.id)
        assertEquals("u-2", index.forCatalogId(MediaIds.studyItem("LUK", "u-2"))!!.id)
        assertNull(index.forCatalogId(MediaIds.chapter("LUK.4")))
    }

    @Test
    fun `downloads IDs mirror the catalog`() {
        val id = MediaIds.downloads(MediaIds.chapter("LUK.3"))
        assertEquals("dl/bible/ch/LUK.3", id)
        assertTrue(MediaIds.isDownloadsId(id))
        assertTrue(MediaIds.isBrowseId(id))
        assertEquals(MediaIds.Parsed.ChapterId("LUK.3", "LUK"), MediaIds.parse(MediaIds.catalogId(id)))
        assertFalse(MediaIds.isDownloadsId(MediaIds.chapter("LUK.3")))
    }

    @Test
    fun `offline root keeps Downloads above the message`() {
        val nodes = LibraryTree.offlineRoot(labels)
        assertEquals(listOf(MediaIds.DOWNLOADS, MediaIds.MESSAGE), nodes.map { it.id })
    }

    @Test
    fun `downloads with nothing downloaded says so`() {
        val only = LibraryTree.downloadsRoot(labels.copy(noDownloads = "Sin descargas"), DownloadIndex.EMPTY).single()
        assertEquals(MediaIds.MESSAGE, only.id)
        assertEquals("Sin descargas", only.title)
    }

    @Test
    fun `downloads root splits by kind, or skips that level with one kind`() {
        assertEquals(listOf("Biblia", "Estudio"), LibraryTree.downloadsRoot(labels, index).map { it.title })
        val bibleOnly = index.filter { it.kind == DownloadKind.BIBLE }
        assertEquals(
            listOf("dl/bible/book/GEN", "dl/bible/book/LUK"),
            LibraryTree.downloadsRoot(labels, bibleOnly).map { it.id }
        )
    }

    @Test
    fun `download rows look like catalog rows and point at the file`() {
        val chapter = LibraryTree.downloadRows(index, DownloadKind.BIBLE, "LUK").single()
        assertEquals("3", chapter.title)
        assertTrue(chapter.playable)
        assertTrue(chapter.downloaded)
        assertEquals("file:///data/files/LUK_3.mp3", chapter.localUri)
        assertEquals("https://bible/LUK3.mp3", chapter.audioUrl)

        val study = LibraryTree.downloadRows(index, DownloadKind.STUDY, "LUK").single()
        assertEquals("Lucas 2", study.title)
        assertEquals("25 min", study.subtitle)
    }

    @Test
    fun `catalog tracks pick up their downloaded copy`() {
        val nodes = listOf(
            LibraryNode(MediaIds.chapter("LUK.3"), "3", playable = true, audioUrl = "https://bible/LUK3.mp3"),
            LibraryNode(MediaIds.chapter("LUK.4"), "4", playable = true, audioUrl = "https://bible/LUK4.mp3"),
        )
        val marked = LibraryTree.withDownloads(nodes, index)
        assertTrue(marked[0].downloaded)
        assertEquals("file:///data/files/LUK_3.mp3", marked[0].localUri)
        assertFalse(marked[1].downloaded)
        assertEquals("", marked[1].localUri)
    }
}
