package com.media.control.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTreeTest {

    private val labels = Labels(bible = "Biblia", study = "Estudio", oldTestament = "Antiguo Testamento", newTestament = "Nuevo Testamento")

    private val bibleBooks = listOf(
        BibleBook("GEN", "Génesis", Testament.OLD, 1, ""),
        BibleBook("LUK", "Lucas", Testament.NEW, 42, "https://x/LUK.png"),
    )

    private val studyBooks = listOf(
        StudyBook("GDL", "Guías", Testament.OTHER, 0, "https://x/GDL.png", 10),
        StudyBook("GEN", "Génesis", Testament.OLD, 1, "https://x/GEN.png", 55),
        StudyBook("MAT", "Mateo", Testament.NEW, 2, "https://x/MAT.png", 39),
    )

    @Test
    fun `root shows one tab per available content type, then Downloads`() {
        assertEquals(
            listOf(MediaIds.BIBLE, MediaIds.STUDY, MediaIds.DOWNLOADS),
            LibraryTree.root(labels, true, true).map { it.id }
        )
        assertEquals(listOf(MediaIds.STUDY, MediaIds.DOWNLOADS), LibraryTree.root(labels, false, true).map { it.id })
        assertEquals("Biblia", LibraryTree.root(labels, true, false).first().title)
    }

    @Test
    fun `root with nothing available shows a message row above Downloads`() {
        val nodes = LibraryTree.root(labels, false, false)
        assertEquals(listOf(MediaIds.MESSAGE, MediaIds.DOWNLOADS), nodes.map { it.id })
        assertFalse(nodes[0].browsable)
        assertFalse(nodes[0].playable)
    }

    @Test
    fun `bible splits by testament and hides empty ones`() {
        assertEquals(
            listOf("Antiguo Testamento", "Nuevo Testamento"),
            LibraryTree.bibleTestaments(labels, bibleBooks).map { it.title }
        )
        assertEquals(
            listOf(MediaIds.BIBLE_NEW),
            LibraryTree.bibleTestaments(labels, bibleBooks.filter { it.testament == Testament.NEW }).map { it.id }
        )
        assertEquals(listOf("bible/book/LUK"), LibraryTree.bibleBooks(bibleBooks, Testament.NEW).map { it.id })
    }

    @Test
    fun `study is one list of books in study order, guidelines included`() {
        val nodes = LibraryTree.studyBooks(studyBooks)
        assertEquals(listOf("study/book/GDL", "study/book/GEN", "study/book/MAT"), nodes.map { it.id })
        assertEquals("Guías", nodes[0].title)
        assertTrue(nodes.all { it.browsable })
    }

    @Test
    fun `chapter rows show the number and play with reference and book name`() {
        val chapter = Chapter("LUK.3", "LUK", 3, "Luke 3", "https://cdn/LUK.3.mp3", "")
        val row = LibraryTree.chapterRows(listOf(chapter), "Luke", "https://x/LUK.png").single()
        assertEquals("3", row.title)
        assertTrue(row.playable)
        val track = LibraryTree.chapterTrack(chapter, "Luke", "https://x/LUK.png")
        assertEquals("Luke 3", track.title)
        assertEquals("Luke", track.subtitle)
        assertEquals("https://x/LUK.png", track.artUrl)
        assertEquals("https://cdn/LUK.3.mp3", track.audioUrl)
    }

    @Test
    fun `study tracks point at the teachings CDN`() {
        val study = Study("10e0a899", "LUK", "Luke 1:1-4", 1273, "https://x/LUK.png")
        val row = LibraryTree.studyRows(listOf(study), "Luke", "https://teachings-cdn.thruthebible.io/").single()
        assertEquals("21 min", row.subtitle)
        assertEquals("https://teachings-cdn.thruthebible.io/10e0a899", row.audioUrl)
        assertEquals("study/item/LUK/10e0a899", row.id)
    }

    @Test
    fun `media ids round trip`() {
        assertEquals(MediaIds.Parsed.ChapterId("LUK.3", "LUK"), MediaIds.parse(MediaIds.chapter("LUK.3")))
        assertEquals(MediaIds.Parsed.StudyItemId("LUK", "abc"), MediaIds.parse(MediaIds.studyItem("LUK", "abc")))
        assertEquals(MediaIds.Parsed.BibleBookId("GEN"), MediaIds.parse(MediaIds.bibleBook("GEN")))
        assertEquals(MediaIds.Parsed.Node(MediaIds.STUDY), MediaIds.parse(MediaIds.STUDY))
        assertEquals(MediaIds.Parsed.Unknown, MediaIds.parse("https://cdn/LUK.3.mp3"))
        assertFalse(MediaIds.isBrowseId("https://cdn/LUK.3.mp3"))
        assertFalse(MediaIds.isBrowseId("/data/user/0/app/files/study.mp3"))
        assertTrue(MediaIds.isBrowseId("bible/ch/LUK.3"))
    }
}
