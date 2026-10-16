package com.media.control.auto

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Small HTTP client with a disk cache. A fresh cache entry is served without
 * touching the network; a stale one is the fallback when the request fails, so
 * the car keeps a usable library through tunnels and dead zones. With no
 * network at all it goes straight to the cache instead of waiting on timeouts.
 */
class CatalogClient(
    private val cacheDir: File,
    private val ttlMillis: Long = 24 * 60 * 60 * 1000L,
    private val isOnline: () -> Boolean = { true },
) {

    class RequestFailed(message: String) : Exception(message)

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        cached("GET $url") { request("GET", url, headers, null) }

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        cached("POST $url $body") { request("POST", url, headers, body) }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun cached(key: String, fetch: () -> String): String {
        val file = File(cacheDir, sha1(key))
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMillis) {
            return file.readText()
        }
        if (!isOnline()) {
            if (file.exists()) return file.readText()
            throw RequestFailed("offline, nothing cached for $key")
        }
        return try {
            val body = fetch()
            cacheDir.mkdirs()
            file.writeText(body)
            body
        } catch (e: Exception) {
            if (file.exists()) file.readText() else throw e
        }
    }

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw RequestFailed("$method $url -> $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TIMEOUT_MS = 10_000
    }
}
