package com.media.control.auto

import org.json.JSONArray
import org.json.JSONObject

enum class Testament { OLD, NEW, OTHER }

data class StationLanguage(
    val code: String,
    val name: String,
    val bibleId: String,
    val audioId: String,
    val studiesCode: String,
)

data class Station(
    val defaultLanguage: String,
    val studyAudioFormat: String,
    val languages: List<StationLanguage>,
    val clientId: String = "",
)

sealed interface Book {
    val id: String
    val name: String
}

data class BibleBook(
    override val id: String,
    override val name: String,
    val testament: Testament,
    val order: Int,
    val artUrl: String,
) : Book

data class Chapter(
    val id: String,
    val bookId: String,
    val number: Int,
    val reference: String,
    val audioUrl: String,
    val artUrl: String,
)

data class StudyBook(
    override val id: String,
    override val name: String,
    val testament: Testament,
    val order: Int,
    val artUrl: String,
    val studyCount: Int,
) : Book

data class Study(
    val uuid: String,
    val bookId: String,
    val name: String,
    val durationSeconds: Long,
    val artUrl: String,
)

/** JSON → models. Kept free of Android types so it runs in plain JVM tests. */
object CatalogParser {

    private const val DOT_BIBLE_STATION_ID = "1765"
    private const val ISM_BIBLE_STATION_ID = "1550"
    private const val ENG_NIV_BIBLE_ID = "69d9851c-5c09-4873-b92b-7c96bfb43756"
    private const val ENG_NIV_AUDIO_ID = "7c674a7c-be75-4090-886d-4bc2a42f4b54"
    private const val CMP_ENG_US_LANG_ID = "en_US"
    private const val TTB_CLIENT_ID = "5306"
    private val ART_BOOK_CODE = Regex("""/art/([A-Z0-9]+)\.png$""")
    private val BOOK_CODES = setOf(
        "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI",
        "1CH", "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER",
        "LAM", "EZK", "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP",
        "HAG", "ZEC", "MAL", "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL",
        "EPH", "PHP", "COL", "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE",
        "2PE", "1JN", "2JN", "3JN", "JUD", "REV", "GDL",
    )

    fun station(payload: String): Station {
        val root = JSONObject(payload)
        val data = root.optJSONObject("data") ?: root
        val languages = data.optJSONArray("language").objects().map {
            StationLanguage(
                code = it.optString("code"),
                name = it.optString("language"),
                bibleId = it.optString("bible_id"),
                audioId = it.optString("audio_id"),
                studiesCode = it.optString("lang_studies_code"),
            )
        }
        val design = data.optJSONObject("design")
        return Station(
            defaultLanguage = data.optString("default_language"),
            studyAudioFormat = design?.optString("study_content_type").orEmpty(),
            languages = languages,
            clientId = design?.optString("client_id").orEmpty(),
        )
    }

    /**
     * Mirrors _transformArtUrl() in cmp.service.ts: English TTB apps use the
     * illustrated book covers under /art/en_us/; the color and icon covers
     * at /art/ are for every other language and client.
     */
    fun usesEnglishCovers(station: Station, language: StationLanguage): Boolean =
        station.clientId == TTB_CLIENT_ID && isEnglish(language)

    fun bookCover(artUrl: String, englishCovers: Boolean): String {
        if (!englishCovers) return artUrl
        val code = ART_BOOK_CODE.find(artUrl)?.groupValues?.get(1) ?: return artUrl
        return if (code in BOOK_CODES) artUrl.replace("/art/", "/art/en_us/") else artUrl
    }

    /** Language chosen in the app, else the station default, else the first one. */
    fun activeLanguage(station: Station, preferredCode: String?, stationId: String): StationLanguage? {
        val language = station.languages.firstOrNull { preferredCode != null && it.code == preferredCode }
            ?: station.languages.firstOrNull { it.name == station.defaultLanguage }
            ?: station.languages.firstOrNull()
            ?: return null
        // Mirrors cmp.service.ts: these two stations read the NIV in English.
        val forcesNiv = stationId == DOT_BIBLE_STATION_ID || stationId == ISM_BIBLE_STATION_ID
        return if (forcesNiv && isEnglish(language)) {
            language.copy(bibleId = ENG_NIV_BIBLE_ID, audioId = ENG_NIV_AUDIO_ID)
        } else {
            language
        }
    }

    /** Mirrors getLanguageForStudies() in constants.ts: CMP wants "en_US" for any English. */
    fun studiesLanguage(language: StationLanguage): String {
        if (language.studiesCode.isNotEmpty()) {
            val english = language.studiesCode.lowercase() in setOf("en", "en_us")
            return if (english) CMP_ENG_US_LANG_ID else language.studiesCode
        }
        if (language.code.isEmpty() || language.code == "en") return CMP_ENG_US_LANG_ID
        return language.code
    }

    /** The app's check (`code.includes("en")`), loose as it is: "ben" passes too. */
    private fun isEnglish(language: StationLanguage) = language.code.contains("en")

    fun bibleBooks(payload: String): List<BibleBook> =
        JSONArray(payload).objects()
            .map {
                BibleBook(
                    id = it.optString("id"),
                    name = it.optString("name").ifEmpty { it.optString("book_name_eng") },
                    testament = when (it.optString("testament")) {
                        "O" -> Testament.OLD
                        "N" -> Testament.NEW
                        else -> Testament.OTHER
                    },
                    order = it.optInt("bookOrder"),
                    artUrl = it.optString("art_url"),
                )
            }
            .filter { it.testament != Testament.OTHER }
            .sortedBy { it.order }

    fun chapters(payload: String): List<Chapter> =
        JSONArray(payload).objects()
            .mapNotNull {
                val url = it.optString("url")
                if (url.isEmpty()) return@mapNotNull null
                Chapter(
                    id = it.optString("id"),
                    bookId = it.optString("bookId"),
                    number = it.optString("number").toIntOrNull() ?: 0,
                    reference = it.optString("reference"),
                    audioUrl = url,
                    artUrl = it.optString("art_url"),
                )
            }
            .sortedBy { it.number }

    fun studyBooks(payload: String): List<StudyBook> =
        JSONArray(payload).objects()
            .map {
                StudyBook(
                    id = it.optString("id"),
                    name = it.optString("name_in_language").ifEmpty { it.optString("name_eng") },
                    testament = when (it.optString("testament")) {
                        "Old Testament" -> Testament.OLD
                        "New Testament" -> Testament.NEW
                        else -> Testament.OTHER
                    },
                    order = it.optInt("study_order"),
                    artUrl = it.optString("art_url"),
                    studyCount = it.optInt("number_of_studies"),
                )
            }
            .filter { it.studyCount > 0 }
            .sortedBy { it.order }

    fun studies(payload: String, bookId: String): List<Study> =
        JSONArray(payload).objects()
            .filter { it.optString("uuid").isNotEmpty() }
            .map {
                Study(
                    uuid = it.optString("uuid"),
                    bookId = it.optString("cmp_book_id").ifEmpty { bookId },
                    name = it.optString("name"),
                    durationSeconds = it.optLong("audio_duration"),
                    artUrl = it.optString("art_url"),
                )
            }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
