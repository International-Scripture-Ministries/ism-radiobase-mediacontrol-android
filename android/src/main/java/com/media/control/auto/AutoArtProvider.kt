package com.media.control.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Read-only access to the covers [AutoArt] stored, for Android Auto and the media notification. */
class AutoArtProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        val name = uri.lastPathSegment?.takeIf { FILE_NAME.matches(it) } ?: throw FileNotFoundException(uri.toString())
        val file = File(AutoArt.dir(context!!), name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/*"

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0

    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0

    companion object {
        private val FILE_NAME = Regex("[0-9a-f]{64}")

        fun authority(context: Context) = "${context.packageName}.mediacontrol.art"
    }
}
