package com.media.control

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.Gson
import com.media.control.constants.Const
import com.media.control.helpers.DBHelper
import com.media.control.model.AudioData
import com.media.control.model.ResponseData
import com.media.control.service.PlaybackService
import com.media.control.service.PlaybackService.Companion.mAppBackground
import com.media.control.service.PlaybackService.Companion.mCurrentAudio
import com.media.control.service.PlaybackService.Companion.mPlayerState
import com.media.control.service.PlaybackService.Companion.player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@CapacitorPlugin(name = "MediaControl")
class MediaControlPlugin : Plugin() {

    private var seekUpdates: CoroutineScope? = null

    private val handler: Handler = Handler(Looper.getMainLooper())

    private lateinit var mAudioData: AudioData

    private lateinit var title: String
    private lateinit var cover: String
    private lateinit var audio: String
    private var playbackPos: String = Const.DEFAUT_POS
    private var playbackSpeed: String = Const.DEFAUT_SPEED

    private var speedSet = true
    private var seekSet = true

    private lateinit var call: PluginCall

    private var isPaused = false
    private var isLoaded = false
    private var isEnded = false

    private lateinit var mDBHelper: DBHelper

    private lateinit var controller: MediaController
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    override fun handleOnStart() {
        super.handleOnStart()

        mDBHelper = DBHelper(context)
        mAppBackground.value = false
        if (mCurrentAudio.value != null) {
            if (mCurrentAudio.value!!.url != "") {
                if (!mDBHelper.audioExist(mCurrentAudio.value!!.url)) {
                    mDBHelper.insertAudio(mCurrentAudio.value)
                }
            }
        } else if (mCurrentAudio.value == null) {
            mCurrentAudio.value = ResponseData("", "", "", "", "", "")
        }
    }

    override fun handleOnStop() {
        super.handleOnStop()
        mAppBackground.value = true
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        handler.removeCallbacks(playerPosition)
        seekUpdates?.cancel() // Clean up coroutine scope when plugin is destroyed
        seekUpdates = null
    }

    @PluginMethod
    fun play(call: PluginCall) {
        if (call != null) {
            try {
                /*audio = if (call.data.getString("audio") != null) {
                    if (call.data.getString("audio") as String != "NaN") call.data.getString("audio") as String else ""
                } else ""*/
                audio = call.data.getString("audio")?.takeIf { it != "NaN" } ?: ""
                cover = call.data.getString("cover")?.takeIf { it != "NaN" } ?: ""
                title = call.data.getString("title")?.takeIf { it != "NaN" } ?: ""
                playbackPos = call.data.getString("playbackPosition")?.takeIf { it != "NaN" }
                    ?: Const.DEFAUT_POS
                playbackSpeed = call.data.getString("playbackSpeed")?.takeIf { it != "NaN" }
                    ?: Const.DEFAUT_SPEED

                if (verifyAudio(audio)) {
                    mAudioData = AudioData(audio, cover, title, playbackPos, playbackSpeed)
                    Thread {
                        Handler(Looper.getMainLooper()).post {
                            playOrLoadMediaItem(false)
                        }
                    }.start()
                    call.resolve()
                } else {
                    val ret = JSObject()
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.post {
                        isLoaded = false
                        ret.put("message", "Audio Error")
                        ret.put("url", audio)
                        call.reject(Const.ERROR, ret)
                    }
                    return
                }
            } catch (e: Exception) {
                val ret = JSObject()
                ret.put("message", e.toString())
                call.reject(Const.ERROR, ret)
            }
        }
    }

    @PluginMethod
    fun load(call: PluginCall) {
        if (call != null) {
            try {
                val mAudio = call.data.getString("audio")?.takeIf { it != "NaN" } ?: ""
                if (verifyAudio(mAudio)) {
                    Thread {
                        Handler(Looper.getMainLooper()).post {
                            if (isLoaded) {
                                stopMedia()
                            }

                            this.call = call

                            val mTitle = call.data.getString("title")?.takeIf { it != "NaN" } ?: ""
                            val mCover = call.data.getString("cover")?.takeIf { it != "NaN" } ?: ""

                            playbackPos =
                                call.data.getString("playbackPosition")?.takeIf { it != "NaN" }
                                    ?: Const.DEFAUT_POS
                            playbackSpeed =
                                call.data.getString("playbackSpeed")?.takeIf { it != "NaN" }
                                    ?: Const.DEFAUT_SPEED

                            mAudioData =
                                AudioData(mAudio, mCover, mTitle, playbackPos, playbackSpeed)
                            playOrLoadMediaItem(true)
                        }
                    }.start()
                } else {
                    val ret = JSObject()
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.post {
                        isLoaded = false
                        ret.put("message", "Audio Error")
                        ret.put("url", mAudio)
                        call.reject(Const.ERROR, ret)
                    }
                    return
                }
            } catch (e: Exception) {
                val ret = JSObject()
                ret.put("message", e.toString())
                call.reject(Const.ERROR, ret)
            }
        }
    }

    @PluginMethod
    fun playLoaded(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (::controller.isInitialized) {
                        if (!controller.isPlaying && !isPaused && isLoaded) {
                            playMedia() // PlayLoaded callback
                        }
                    }
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun clearPlaylist(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    clearPlaylist()
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun fetchPlaylist(call: PluginCall) {
        try {
            val ret = JSObject()
            Thread {
                Handler(Looper.getMainLooper()).post {
                    mDBHelper = DBHelper(context)

                    val gson = Gson()
                    ret.put("result", gson.toJson(mDBHelper.allAudios))
                    clearPlaylist()
                    mDBHelper.deleteAllAudio()
                    call.resolve(ret)
                }
            }.start()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun add(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (call.data.getJSONArray("audioArray").length() > 1) {
                        for (i in 0 until call.data.getJSONArray("audioArray").length()) {
                            val mData: JSONObject =
                                call.data.getJSONArray("audioArray").get(i) as JSONObject
                            if (mCurrentAudio.value != null) {
                                if (!mCurrentAudio.value!!.url.contains(mData.getString("audio"))) {
                                    if (::controller.isInitialized) {
                                        controller.addMediaItem(buildMediaItem(mData))
                                    }
                                }
                            } else {
                                if (::controller.isInitialized) {
                                    controller.addMediaItem(buildMediaItem(mData))
                                }
                            }
                        }
                    }
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun isLoaded(call: PluginCall) {
        try {
            val ret = JSObject()
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (mCurrentAudio.value?.url.isNullOrEmpty()) {
                        ret.put("value", false)
                        ret.put("url", "")
                    } else {
                        ret.put("value", isLoaded)
                        ret.put("url", mCurrentAudio.value!!.url)
                    }
                    call.resolve(ret)
                }
            }.start()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun pause(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (::controller.isInitialized) {
                        controller.pause()
                    }
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun resume(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (::controller.isInitialized) {
                        controller.playWhenReady = true
                    }
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun seek(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    try {
                        if (call.data.has("seekTo") && call.data.getString("seekTo") != "" && call.data.getString(
                                "seekTo"
                            ) != "NaN" && call.data.getString("seekTo") != null
                        ) {
                            val seekTo = call.data.getString("seekTo")!!.toLong()
                            if (::controller.isInitialized) {
                                controller.seekTo(seekTo)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun speed(call: PluginCall) {
        try {
            if (call.data.has("value") && call.data.getString("value") != "" && call.data.getString(
                    "value"
                ) != "NaN" && call.data.getString("value") != null
            ) {
                val mVal = call.data.getString("value")
                Thread {
                    Handler(Looper.getMainLooper()).post {
                        if (::controller.isInitialized) {
                            controller.setPlaybackSpeed(mVal!!.toFloat())
                        }
                    }
                }.start()
            }
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        try {
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (::controller.isInitialized) {
                        if (controller.isPlaying && mPlayerState.value != Const.END) {
                            mPlayerState.value = Const.END
                            notifyListeners() // Notify when stop() and was playing
                            stopMedia()
                        } else if (controller.playbackState == Player.STATE_READY) {
                            mPlayerState.value = Const.END
                            notifyListeners() // Notify when stop() and was load
                            stopMedia()
                        }
                    }
                }
            }.start()
            call.resolve()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun isPlaying(call: PluginCall) {
        try {
            val ret = JSObject()
            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (::controller.isInitialized) {
						ret.put("value", controller.isPlaying)
					} else {
						ret.put("value", false)
					}
                    call.resolve(ret)
                }
            }.start()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun getUpdate(call: PluginCall) {
        try {
            val ret = JSObject()
            Thread {
                Handler(Looper.getMainLooper()).post {
                    ret.put("state", mPlayerState.value.toString())
                    ret.put("position", mCurrentAudio.value!!.position)
                    ret.put("duration", mCurrentAudio.value!!.duration)
                    ret.put("url", mCurrentAudio.value!!.url)
                    call.resolve(ret)

                }
            }.start()
        } catch (e: Exception) {
            val ret = JSObject()
            ret.put("message", e.toString())
            call.reject(Const.ERROR, ret)
        }
    }

    @PluginMethod
    fun getDuration(call: PluginCall) {
        if (call != null) {
            try {
                val mAudio = call.data.getString("audio")?.takeIf { it != "NaN" } ?: ""
                val ret = JSObject()
                Thread {
                    Handler(Looper.getMainLooper()).post {
                        getAudioDuration(context, mAudio) { duration ->
                            ret.put("url", mAudio)
                            ret.put("duration", duration.toString())
                            call.resolve(ret)
                        }
                    }
                }.start()
            } catch (e: Exception) {
                val ret = JSObject()
                ret.put("message", e.toString())
                call.reject(Const.ERROR, ret)
            }
        }
    }

    private val playerPosition: Runnable = object : Runnable {
        override fun run() {
            try {
                Handler().postDelayed({
                    val tsLong = System.currentTimeMillis() / 1000
                    mCurrentAudio.value = ResponseData(
                        mAudioData.url.split("/")[mAudioData.url.split("/").lastIndex],
                        mAudioData.url,
                        Const.INCOMPLETE,
                        controller.duration.toString(),
                        controller.currentPosition.toString(),
                        tsLong.toString()
                    )
                }, Const.UPDATE_SPEED)
            } catch (e: Exception) {
//                Log.d(TAG, e.toString())
            }

            handler.postDelayed(this, Const.UPDATE_SPEED)

            Thread {
                Handler(Looper.getMainLooper()).post {
                    if (speedSet) {
                        controller.setPlaybackSpeed(playbackSpeed.toFloat())
                        speedSet = false
                    }
                    if (seekSet) {
                        if (controller.currentPosition > 0L) {
                            controller.seekTo(playbackPos.toLong())
                            seekSet = false
                        }
                    }
                }
            }.start()
        }
    }

    private fun notifyListeners() {
        Thread {
            Handler(Looper.getMainLooper()).post {
                val ret = JSObject()
                ret.put("state", mPlayerState.value.toString())
                ret.put("position", controller.currentPosition.toString())
                ret.put("duration", controller.duration.toString())
                ret.put("url", mCurrentAudio.value!!.url)
                if (!isEnded) {
                  notifyListeners("playerUpdates", ret)
                }
            }
        }.start()
    }

    private fun verifyAudio(audio: String): Boolean {
      return if (audio.contains("http")) {
        try {
          val url = URL(audio)
          val connection = url.openConnection() as HttpURLConnection
          connection.requestMethod = "GET"
          connection.connect()
          val code = connection.responseCode
          code == 200
        } catch (e: Exception) {
          e.printStackTrace()
          false
        }
      } else {
        val file = File(audio)
        file.exists() && file.isFile
      }
    }

    private fun mediaBuilder(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mAudioData.url)
            .setMediaMetadata(
                MediaMetadata.Builder().setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                    .setArtworkUri(Uri.parse(mAudioData.image)).setTitle(mAudioData.title)
                    .setAlbumTitle(mAudioData.title).build()
            ).build()
    }

    private fun buildMediaItem(mJson: JSONObject): MediaItem {
        val source: String = mJson.getString("audio") as String
        val requestMetadata = MediaItem.RequestMetadata.Builder().apply {
            setMediaUri(source.toUri())
        }.build()

        val mediaMetadata = MediaMetadata.Builder().apply {
            setAlbumTitle(mJson.getString("title") as String)
            setTitle(mJson.getString("title") as String)
            setArtworkUri(Uri.parse(mJson.getString("cover") as String))
        }.build()

        return MediaItem.Builder().apply {
            setMediaId(mJson.getString("audio") as String)
            setRequestMetadata(requestMetadata)
            setMediaMetadata(mediaMetadata)
            setUri(source.toUri())
        }.build()
    }

    private fun playOrLoadMediaItem(loadItem: Boolean) {
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.apply {
            addListener(Runnable {
                controller = get()
                mediaControllerUpdates(controller)
                // Ensure media is played appropriately based on state
//                Log.d(TAG, "INITIAL STATE = ${controller.playbackState}")
                if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                    if (loadItem) {
                        loadMedia()
                    } else {
                        controller.setMediaItem(mediaBuilder())
                        playMedia() // Play if IDLE or ENDED
                    }
                } else if (controller.playbackState == Player.STATE_READY || controller.playbackState == Player.STATE_BUFFERING) {
                    if (controller.playWhenReady) {
                        if (controller.isPlaying) {
                            stopMedia()
                            if (loadItem) {
                                loadMedia()
                            } else {
                                controller.setMediaItem(mediaBuilder())
                                playMedia() // Play if READY OR BUFFERING
                            }
                        }
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun playMedia() {
        controller.prepare()
        controller.play()
        startNotifyingPlayerUpdates()
    }

    private fun loadMedia() {
        controller.setMediaItem(mediaBuilder())
        isLoaded = true

        val ret = JSObject()
        ret.put("value", isLoaded)
        ret.put("url", mAudioData.url)
        call.resolve(ret)
    }

    private fun stopMedia() {
        mPlayerState.value = null
        seekUpdates?.cancel()
        seekUpdates = null
        handler.removeCallbacks(playerPosition)

        speedSet = true
        seekSet = true

        isPaused = false
        isLoaded = false
        isEnded = false

        mCurrentAudio.value!!.url = ""

        playbackPos = Const.DEFAUT_POS

        controller.stop()
        controller.clearMediaItems()
    }

    private fun audioCompleted() {
        if (mAppBackground.value!!) {
            if (!mDBHelper.audioExist(mCurrentAudio.value!!.url)) {
                mCurrentAudio.value!!.state = Const.COMPLETE
                mDBHelper.insertAudio(mCurrentAudio.value)
            }
        }
    }

    private fun clearPlaylist() {
        if (::controller.isInitialized) {
            if (controller.hasNextMediaItem()) {
                if (controller.currentMediaItemIndex == 0) {
                    controller.removeMediaItems(1, controller.mediaItemCount)
                } else {
                    controller.replaceMediaItem(0, controller.currentMediaItem!!)
                    controller.seekTo(0, controller.currentPosition)
                    controller.removeMediaItems(1, controller.mediaItemCount)
                }
            }
        }
    }

    private fun getAudioDuration(context: Context, audioUri: String, callback: (Long) -> Unit) {
        val exoPlayer = ExoPlayer.Builder(context).build()
        val mediaItem = MediaItem.fromUri(audioUri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                val duration = exoPlayer.duration
                if (duration > 0) {
                    callback(duration)
                    exoPlayer.release()
                }
            }
        })
    }

    private fun mediaControllerUpdates(controller: MediaController) {
        controller.addListener(object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (mAppBackground.value!!) {
                    val tempPos = controller.currentPosition.toInt() / 1000
                    if (tempPos == 0) {
                        audioCompleted()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
//                        Log.d(TAG, "STATE_BUFFERING")
                    }

                    Player.STATE_READY -> {
//                        Log.d(TAG, "STATE_READY")
                        controller.playWhenReady
                    }

                    Player.STATE_ENDED -> {
//                        Log.d(TAG, "STATE_ENDED")
                        controller.stop()
                    }

                    Player.STATE_IDLE -> {
//                        Log.d(TAG, "STATE_IDLE")
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    mPlayerState.value = Const.PLAYING
                    isPaused = false
                } else {
                    if (player.value!!.playbackState == Player.STATE_ENDED) {
                        mPlayerState.value = Const.END
                        Thread {
                            Handler(Looper.getMainLooper()).post {
                                val ret = JSObject()
                                ret.put("state", "END")
                                ret.put("position", controller.currentPosition.toString())
                                ret.put("duration", controller.duration.toString())
                                ret.put("url", mCurrentAudio.value!!.url)
                                if (!isEnded) {
                                  notifyListeners("playerUpdates", ret)  // Notify when media ended
                                  isEnded = true
                                }
                            }
                        }.start()
                        stopMedia()
                    }
                }

                if (!isPlaying && mPlayerState.value != Const.PAUSE) {
                    if (controller.playbackState != Player.STATE_BUFFERING && player.value!!.playbackState != Player.STATE_IDLE && player.value!!.playbackState != Player.STATE_ENDED) {
                        mPlayerState.value = Const.PAUSE
                        notifyListeners() // Notify when media got paused
                        isPaused = true
                    }
                }

//                Log.d(TAG, mPlayerState.value.toString())
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                if (controller.playbackState != Player.STATE_IDLE) {
                    if (controller.currentMediaItem!!.mediaId.isNotEmpty()) {
                        mAudioData = AudioData(
                            controller.currentMediaItem!!.mediaId,
                            controller.mediaMetadata.artworkUri.toString(),
                            controller.mediaMetadata.title.toString(),
                            playbackPos,
                            playbackSpeed
                        )
                        isEnded = false
                    }
                }

            }
        })

        handler.post(playerPosition)
    }

    private fun startNotifyingPlayerUpdates() {
        if (seekUpdates?.isActive == true) {
            return
        }
        seekUpdates = CoroutineScope(Dispatchers.Default + Job())
        seekUpdates?.launch {
            while (true) {
                if (!isPaused) {
                    notifyListeners() // Loop started to notify Ionic callback
                }
                delay(Const.NOTIFY_SPEED)
            }
        }
    }

    companion object {
        protected const val TAG = "MediaControl"
    }
}
