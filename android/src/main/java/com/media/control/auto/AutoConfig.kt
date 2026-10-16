package com.media.control.auto

import android.content.Context
import org.json.JSONObject

/**
 * `plugins.MediaControl.androidAuto` from `capacitor.config.json`. Android Auto
 * can start the service without the WebView, so this cannot come from JavaScript.
 */
data class AutoSettings(
    val stationId: String,
    val stationDataUrl: String,
    val cmpApiUrl: String,
    val cmpApiKey: String,
    val teachingsCdnUrl: String,
)

/** Values the app pushes at runtime through `MediaControl.setAutoConfig()`. */
data class AutoOverrides(
    val languageCode: String?,
    val labels: Map<String, String>,
)

object AutoConfig {
    private const val CONFIG_ASSET = "capacitor.config.json"
    private const val PREFS = "media_control_auto"
    private const val KEY_LANGUAGE = "languageCode"
    private const val KEY_LABELS = "labels"

    @Volatile
    private var cached: AutoSettings? = null

    fun settings(context: Context): AutoSettings? {
        cached?.let { return it }
        val parsed = try {
            val text = context.assets.open(CONFIG_ASSET).bufferedReader().use { it.readText() }
            parseSettings(JSONObject(text))
        } catch (_: Exception) {
            null
        }
        cached = parsed
        return parsed
    }

    fun parseSettings(config: JSONObject): AutoSettings? {
        val auto = config.optJSONObject("plugins")
            ?.optJSONObject("MediaControl")
            ?.optJSONObject("androidAuto") ?: return null
        val stationId = auto.optString("stationId")
        val cmpApiUrl = auto.optString("cmpApiUrl")
        if (stationId.isEmpty() || cmpApiUrl.isEmpty()) return null
        return AutoSettings(
            stationId = stationId,
            stationDataUrl = auto.optString("stationDataUrl").trimEnd('/'),
            cmpApiUrl = cmpApiUrl.trimEnd('/') + "/",
            cmpApiKey = auto.optString("cmpApiKey"),
            teachingsCdnUrl = auto.optString("teachingsCdnUrl").trimEnd('/') + "/",
        )
    }

    fun overrides(context: Context): AutoOverrides {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val labels = mutableMapOf<String, String>()
        prefs.getString(KEY_LABELS, null)?.let { raw ->
            try {
                val json = JSONObject(raw)
                json.keys().forEach { key -> labels[key] = json.optString(key) }
            } catch (_: Exception) {
            }
        }
        return AutoOverrides(prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotEmpty() }, labels)
    }

    fun saveOverrides(context: Context, languageCode: String?, labels: JSONObject?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (languageCode != null) putString(KEY_LANGUAGE, languageCode)
            if (labels != null) putString(KEY_LABELS, labels.toString())
        }.apply()
    }
}
