package com.media.control.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParserTest {

    private val stationJson = """
        {"data": {
          "default_language": "English (US)",
          "design": {"study_content_type": "Start Your Journey", "client_id": "5306"},
          "language": [
            {"code": "spa", "language": "Español", "bible_id": "b-spa", "audio_id": "", "lang_studies_code": "es"},
            {"code": "eng", "language": "English (US)", "bible_id": "b-eng", "audio_id": "a-eng", "lang_studies_code": "en_US"}
          ]
        }}
    """.trimIndent()

    @Test
    fun `station reads languages and study audio format`() {
        val station = CatalogParser.station(stationJson)
        assertEquals("Start Your Journey", station.studyAudioFormat)
        assertEquals(listOf("spa", "eng"), station.languages.map { it.code })
    }

    @Test
    fun `active language prefers the app choice, then the station default`() {
        val station = CatalogParser.station(stationJson)
        assertEquals("spa", CatalogParser.activeLanguage(station, "spa", "999")?.code)
        assertEquals("eng", CatalogParser.activeLanguage(station, null, "999")?.code)
        assertEquals("eng", CatalogParser.activeLanguage(station, "xxx", "999")?.code)
    }

    @Test
    fun `dot Bible in English is forced to the NIV like cmp service`() {
        val station = CatalogParser.station(stationJson)
        val english = CatalogParser.activeLanguage(station, null, "1765")!!
        assertEquals("69d9851c-5c09-4873-b92b-7c96bfb43756", english.bibleId)
        assertEquals("7c674a7c-be75-4090-886d-4bc2a42f4b54", english.audioId)
        val spanish = CatalogParser.activeLanguage(station, "spa", "1765")!!
        assertEquals("b-spa", spanish.bibleId)
    }

    @Test
    fun `no languages means no active language`() {
        val station = CatalogParser.station("""{"data": {"language": []}}""")
        assertNull(CatalogParser.activeLanguage(station, null, "1"))
    }

    @Test
    fun `studies language maps every English variant to en_US`() {
        fun lang(code: String, studies: String) = StationLanguage(code, "", "", "", studies)
        assertEquals("en_US", CatalogParser.studiesLanguage(lang("eng", "en")))
        assertEquals("en_US", CatalogParser.studiesLanguage(lang("eng", "en_us")))
        assertEquals("es", CatalogParser.studiesLanguage(lang("spa", "es")))
        assertEquals("en_US", CatalogParser.studiesLanguage(lang("en", "")))
        assertEquals("amh", CatalogParser.studiesLanguage(lang("amh", "")))
    }

    @Test
    fun `bible books drop deuterocanon and sort by book order`() {
        val books = CatalogParser.bibleBooks(
            """[
              {"id": "MAT", "name": "Matthew", "testament": "N", "bookOrder": 40},
              {"id": "TOB", "name": "Tobit", "testament": "", "bookOrder": 70},
              {"id": "GEN", "name": "Genesis", "testament": "O", "bookOrder": 1, "art_url": "https://x/GEN.png"}
            ]"""
        )
        assertEquals(listOf("GEN", "MAT"), books.map { it.id })
        assertEquals(Testament.OLD, books[0].testament)
    }

    @Test
    fun `chapters sort numerically and skip rows without audio`() {
        val chapters = CatalogParser.chapters(
            """[
              {"id": "LUK.10", "bookId": "LUK", "number": "10", "reference": "Luke 10", "url": "https://cdn/LUK.10.mp3"},
              {"id": "LUK.2", "bookId": "LUK", "number": "2", "reference": "Luke 2", "url": "https://cdn/LUK.2.mp3"},
              {"id": "LUK.3", "bookId": "LUK", "number": "3", "reference": "Luke 3", "url": ""}
            ]"""
        )
        assertEquals(listOf(2, 10), chapters.map { it.number })
    }

    @Test
    fun `study books keep only books with studies, in study order`() {
        val books = CatalogParser.studyBooks(
            """[
              {"id": "LUK", "name_in_language": "Luke", "testament": "New Testament", "study_order": 3, "number_of_studies": 29},
              {"id": "GDL", "name_in_language": "Guidelines", "testament": "", "study_order": 0, "number_of_studies": 10},
              {"id": "OBA", "name_in_language": "Obadiah", "testament": "Old Testament", "study_order": 2, "number_of_studies": 0}
            ]"""
        )
        assertEquals(listOf("GDL", "LUK"), books.map { it.id })
        assertEquals(Testament.OTHER, books[0].testament)
        assertEquals(Testament.NEW, books[1].testament)
    }

    @Test
    fun `studies fall back to the requested book id`() {
        val studies = CatalogParser.studies(
            """[{"uuid": "10e0a899", "name": "Luke 1:1-4", "audio_duration": 1273, "cmp_book_id": ""}, {"uuid": ""}]""",
            "LUK"
        )
        assertEquals(1, studies.size)
        assertEquals("LUK", studies[0].bookId)
        assertEquals(1273L, studies[0].durationSeconds)
    }

    @Test
    fun `settings come from the androidAuto block of capacitor config`() {
        val config = org.json.JSONObject(
            """{"plugins": {"MediaControl": {"androidAuto": {
                "stationId": "1765",
                "stationDataUrl": "https://rb-assets-cdn.ismapps.io/station-data/prod/",
                "cmpApiUrl": "https://cmp.thruthebible.io/v1",
                "cmpApiKey": "k",
                "teachingsCdnUrl": "https://teachings-cdn.thruthebible.io"
            }}}}"""
        )
        val settings = AutoConfig.parseSettings(config)!!
        assertEquals("https://rb-assets-cdn.ismapps.io/station-data/prod", settings.stationDataUrl)
        assertEquals("https://cmp.thruthebible.io/v1/", settings.cmpApiUrl)
        assertEquals("https://teachings-cdn.thruthebible.io/", settings.teachingsCdnUrl)
        assertNull(AutoConfig.parseSettings(org.json.JSONObject("{}")))
    }

    @Test
    fun `English TTB stations use the illustrated book covers`() {
        val station = CatalogParser.station(stationJson)
        val english = station.languages.single { it.code == "eng" }
        val spanish = station.languages.single { it.code == "spa" }
        assertEquals("5306", station.clientId)
        assertTrue(CatalogParser.usesEnglishCovers(station, english))
        assertFalse(CatalogParser.usesEnglishCovers(station, spanish))
        assertFalse(CatalogParser.usesEnglishCovers(station.copy(clientId = "5106"), english))
    }

    @Test
    fun `book covers switch to en_us only for book and Guidelines art`() {
        val cdn = "https://cdn-cmp-audio.thruthebible.io/art"
        assertEquals("$cdn/en_us/LUK.png", CatalogParser.bookCover("$cdn/LUK.png", true))
        assertEquals("$cdn/en_us/GDL.png", CatalogParser.bookCover("$cdn/GDL.png", true))
        assertEquals("$cdn/LUK.png", CatalogParser.bookCover("$cdn/LUK.png", false))
        assertEquals("$cdn/en_us/LUK.png", CatalogParser.bookCover("$cdn/en_us/LUK.png", true))
        assertEquals("$cdn/study-123.png", CatalogParser.bookCover("$cdn/study-123.png", true))
        assertEquals("", CatalogParser.bookCover("", true))
    }
}
