package com.media.control.service

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.media.control.MainActivity
import com.media.control.auto.AutoArt
import com.media.control.auto.AutoDownloads
import com.media.control.auto.AutoLibrary
import com.media.control.auto.LibraryNode
import com.media.control.auto.MediaIds
import com.media.control.constants.Const
import com.media.control.constants.Enums
import com.media.control.model.ResponseData
import java.util.concurrent.Executors

/**
 * Owns the app's single ExoPlayer. It is a [MediaLibraryService] so Android Auto
 * can browse the station's Bible and studies and play them, with or without the
 * WebView running. The app keeps driving the same player through
 * [com.media.control.MediaControlPlugin] exactly as before.
 */
class PlaybackService : MediaLibraryService(), MediaLibrarySession.Callback {

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var library: AutoLibrary
    private val libraryExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))
    private var currentSongPosition: Long = 0L
    private var isPauseFromLoss = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var connectivity: ConnectivityManager? = null
    /** Set while the current item is swapped for its downloaded copy. */
    private var switchingToLocalCopy = false

    /**
     * A stream that failed for lack of network resumes where it stopped: right
     * away when Android reports a new network, and every few seconds otherwise,
     * because a dead zone often keeps the network up with no data flowing.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post(retryStream)
        }
    }
    private val retryStream: Runnable = object : Runnable {
        override fun run() {
            mainHandler.removeCallbacks(this)
            val current = playerInstance ?: return
            if (current.playerError?.isNetworkError() != true || !current.playWhenReady) return
            current.prepare()
            mainHandler.postDelayed(this, STREAM_RETRY_MS)
        }
    }

    // for audio focus request
    private var audioManager: AudioManager? = null
    private var audioFocusState: Int = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    private var focusRequest: AudioFocusRequest? = null
    private val attributes = AudioAttributes.Builder().apply {
        setUsage(AudioAttributes.USAGE_MEDIA)
        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    }.build()

    private val notificationPlayerCustomCommandButtons =
        Enums.Companion.NotificationPlayerCustomCommandButton.entries.map { command -> command.commandButton }

    override fun onCreate() {
        super.onCreate()
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        library = AutoLibrary(applicationContext)
        initializeSessionAndPlayer()
        connectivity = getSystemService(ConnectivityManager::class.java)
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun releaseAudioFocus() {
        focusRequest?.let {
            if (Build.VERSION.SDK_INT >= 26)
                audioManager?.abandonAudioFocusRequest(it)
            else
                audioManager?.abandonAudioFocus(focusChangeListener)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setupAndRequestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest?.let {
                audioFocusState =
                    audioManager?.requestAudioFocus(it) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            }
        } else {
            audioFocusState = audioManager?.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }

    private val playerListener = @UnstableApi object : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            // Same track at the same position, now read from disk: keep the volume.
            if (switchingToLocalCopy) return
            // Items started from Android Auto have no plugin loop to restore the
            // volume (see MediaControlPlugin.playerPosition), so never mute them.
            if (mediaItem.autoBrowseId() != null) {
                playerInstance?.volume = 1F
                saveResumptionPoint()
                return
            }
            playerInstance?.let {
                if(!it.isPlaying) {
                    it.volume = 0F
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    it.volume = 0F
                }
            }
        }
        override fun onPlayerError(error: PlaybackException) {
            if (!error.isNetworkError()) return
            if (!switchToLocalCopy()) mainHandler.postDelayed(retryStream, STREAM_RETRY_MS)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (playWhenReady)
                setupAndRequestAudioFocus()
            else
                saveResumptionPoint()
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { state ->
        audioFocusState = state
        when (state) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isPauseFromLoss) {
                    player.value!!.play()
                    isPauseFromLoss = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (player.value!!.isPlaying) {
                    player.value!!.pause()
                    isPauseFromLoss = true
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                if (player.value!!.isPlaying) {
                    player.value!!.pause()
                }
            }
        }
    }
    private lateinit var launcherIntent: PendingIntent
    private lateinit var mClass: Class<*>

    private fun initializeIntent(): PendingIntent {
    val mContext = this
    try {
        val mainActivityClass = Class.forName(mContext.packageName + ".MainActivity")
        mClass = mainActivityClass
    } catch (e: ClassNotFoundException) {
        mClass = javaClass
    }

    val resultIntent = Intent(this, mClass).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    // Remove setPendingIntentBackgroundActivityStartMode completely
    return PendingIntent.getActivity(
        this,
        0,
        resultIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}


    /**
     * When a stream fails because the network dropped and the app downloaded
     * the same track, keep playing it from disk at the same position. The media
     * ID stays the streaming URL, so neither the app nor Android Auto notice.
     */
    private fun switchToLocalCopy(): Boolean {
        val current = playerInstance ?: return false
        val item = current.currentMediaItem ?: return false
        if (item.localConfiguration?.uri?.scheme == "file") return false
        val local = AutoDownloads.localUri(applicationContext, item.mediaId) ?: return false
        val index = current.currentMediaItemIndex
        val position = current.currentPosition
        val volume = current.volume
        switchingToLocalCopy = true
        try {
            current.replaceMediaItem(index, item.buildUpon().setUri(local).build())
            current.seekTo(index, position)
            current.prepare()
        } finally {
            switchingToLocalCopy = false
            current.volume = volume
        }
        return true
    }

    private fun initializeSessionAndPlayer() {
        if (playerInstance == null) {
            // Ten minutes ahead, so a tunnel or a dead zone shorter than that
            // goes unnoticed even for audio that was never downloaded.
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    BUFFER_AHEAD_MS,
                    BUFFER_AHEAD_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .build()
            playerInstance = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build()
                .also { it.addListener(playerListener) }
        }
        player.value = playerInstance!!
        launcherIntent = initializeIntent()

        if (mediaSessionInstance == null) {
            mediaSessionInstance =
                MediaLibrarySession.Builder(this, TrackSwapPlayer(player.value!!).also { sessionPlayer = it }, this)
                    .setSessionActivity(launcherIntent)
                    .build()
        }
        mediaSession = mediaSessionInstance

        mediaSession?.setCustomLayout(notificationPlayerCustomCommandButtons)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        if (mediaItems.any { MediaIds.isBrowseId(it.mediaId) }) {
            return libraryExecutor.submit<MutableList<MediaItem>> {
                mediaItems.flatMap { item ->
                    if (MediaIds.isBrowseId(item.mediaId)) {
                        listOfNotNull(library.item(item.mediaId)?.toPlayerItem())
                    } else {
                        listOf(item.withPlayableUri())
                    }
                }.toMutableList()
            }
        }
        return Futures.immediateFuture(mediaItems.map { it.withPlayableUri() }.toMutableList())
    }

    /** The app's items carry their URL as media ID; a downloaded copy wins over the stream. */
    private fun MediaItem.withPlayableUri(): MediaItem =
        buildUpon().setUri(AutoDownloads.localUri(applicationContext, mediaId) ?: Uri.parse(mediaId)).build()

    /**
     * Android Auto sends the browse ID of the row the driver tapped. It becomes
     * the whole book as a queue, and the next book joins it once the track
     * started, so playback continues on its own like it does in the app.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val tapped = mediaItems.singleOrNull()?.mediaId
        if (tapped != null && MediaIds.isBrowseId(tapped)) {
            return libraryExecutor.submit<MediaItemsWithStartPosition> {
                val queue = library.queueFor(tapped)
                    ?: throw IllegalArgumentException("No queue for $tapped")
                appendNextBook(queue.items.last().id)
                MediaItemsWithStartPosition(
                    queue.items.map { it.toPlayerItem() },
                    queue.startIndex,
                    C.TIME_UNSET
                )
            }
        }
        return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        val root = MediaItem.Builder()
            .setMediaId(MediaIds.ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(extras)
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(root, LibraryParams.Builder().setExtras(extras).build()))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        libraryExecutor.submit<LibraryResult<ImmutableList<MediaItem>>> {
            val nodes = library.children(parentId)
            // pageSize can be Int.MAX_VALUE, so Int math would overflow past page 0.
            val from = (page.toLong() * pageSize).coerceAtMost(nodes.size.toLong()).toInt()
            val to = (from.toLong() + pageSize).coerceAtMost(nodes.size.toLong()).toInt()
            LibraryResult.ofItemList(nodes.subList(from, to).map { it.toBrowseItem() }, params)
        }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> =
        libraryExecutor.submit<LibraryResult<MediaItem>> {
            val node = library.item(mediaId)
            if (node != null) LibraryResult.ofItem(node.toBrowseItem(), null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

    /** Lets Android Auto (and Android 13+) offer to resume the last car item. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val prefs = getSharedPreferences(RESUMPTION_PREFS, Context.MODE_PRIVATE)
        val browseId = prefs.getString(KEY_RESUME_ID, null)
            ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
        val position = prefs.getLong(KEY_RESUME_POSITION, 0L)
        return libraryExecutor.submit<MediaItemsWithStartPosition> {
            val queue = library.queueFor(browseId)
                ?: throw IllegalStateException("Cannot rebuild queue for $browseId")
            appendNextBook(queue.items.last().id)
            MediaItemsWithStartPosition(queue.items.map { it.toPlayerItem() }, queue.startIndex, position)
        }
    }

    /**
     * Fetching the next book used to hold back the first track by one or two
     * requests, which on a weak car connection meant several seconds of
     * silence. It now loads in the background and joins the end of the queue.
     * Media3 hands the queue to the player only after this call returns, so
     * the append waits until the queue still ends with [lastBrowseId]; if the
     * driver picked something else meanwhile, it gives up.
     */
    private fun appendNextBook(lastBrowseId: String) {
        libraryExecutor.execute {
            val tracks = runCatching { library.nextBookTracks(lastBrowseId) }.getOrNull()
            if (tracks.isNullOrEmpty()) return@execute
            val items = tracks.map { it.toPlayerItem() }
            var attempts = 0
            mainHandler.post(object : Runnable {
                override fun run() {
                    val current = playerInstance ?: return
                    val count = current.mediaItemCount
                    if (count > 0 && current.getMediaItemAt(count - 1).autoBrowseId() == lastBrowseId) {
                        current.addMediaItems(items)
                    } else if (++attempts < APPEND_ATTEMPTS) {
                        mainHandler.postDelayed(this, APPEND_RETRY_MS)
                    }
                }
            })
        }
    }

    private fun saveResumptionPoint() {
        val current = playerInstance ?: return
        val browseId = current.currentMediaItem.autoBrowseId() ?: return
        getSharedPreferences(RESUMPTION_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RESUME_ID, browseId)
            .putLong(KEY_RESUME_POSITION, current.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    private fun LibraryNode.toBrowseItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle.ifEmpty { null })
                    // The legacy browser protocol Android Auto speaks reads the row subtitle from the artist.
                    .setArtist(subtitle.ifEmpty { null })
                    .setArtworkUri(artUri())
                    .setIsBrowsable(browsable)
                    .setIsPlayable(playable)
                    .setMediaType(
                        if (browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        else MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE
                    )
                    .setExtras(downloadedExtras().takeIf { downloaded })
                    .build()
            )
            .build()

    /**
     * What actually reaches the player. The media ID stays the audio URL, the
     * same contract the app relies on in `playerUpdates`.
     */
    private fun LibraryNode.toPlayerItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(audioUrl)
            .setUri(localUri.ifEmpty { audioUrl })
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setAlbumTitle(subtitle)
                    .setArtworkUri(artUri())
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setExtras(Bundle().apply {
                        putString(EXTRA_AUTO_BROWSE_ID, id)
                        putString(EXTRA_ART_URL, artUrl)
                    })
                    .build()
            )
            .build()

    private fun LibraryNode.artUri(): Uri? = AutoArt.uri(applicationContext, artUrl)

    private fun downloadedExtras() = Bundle().apply {
        putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED)
    }

    private fun PlaybackException.isNetworkError() =
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    private fun MediaItem?.autoBrowseId(): String? =
        this?.mediaMetadata?.extras?.getString(EXTRA_AUTO_BROWSE_ID)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
            // Media3 advertises search to Android Auto whenever these are
            // available. The library has no search, so the button only led to
            // a keyboard and an empty result.
            .remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
            .remove(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)

        /* Registering custom player command buttons for player notification. */
        notificationPlayerCustomCommandButtons.forEach { commandButton ->
            commandButton.sessionCommand?.let(availableSessionCommands::add)
        }

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        super.onPostConnect(session, controller)
        if (notificationPlayerCustomCommandButtons.isNotEmpty()) {
            /* Setting custom player command buttons to mediaLibrarySession for player notification. */
            mediaSession?.setCustomLayout(notificationPlayerCustomCommandButtons)
            if (player.value!!.playWhenReady)
                setupAndRequestAudioFocus()
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        /* Handling custom command buttons from player notification. */

        currentSongPosition = session.player.currentPosition

        if (player.value!!.playWhenReady)
            setupAndRequestAudioFocus()

        if (customCommand.customAction == Enums.Companion.NotificationPlayerCustomCommandButton.REWIND.customAction) {
            if (currentSongPosition - Const.SEEK_SPEED >= 0) {
                session.player.seekTo(currentSongPosition - Const.SEEK_SPEED)
            } else {
                session.player.seekTo(0)
            }
        }
        if (customCommand.customAction == Enums.Companion.NotificationPlayerCustomCommandButton.FORWARD.customAction) {
            if (currentSongPosition + Const.SEEK_SPEED <= session.player.duration) {
                session.player.seekTo(currentSongPosition + Const.SEEK_SPEED)
            } else {
                session.player.seekTo(player.value!!.duration)
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.run {
            release()
            player.release()
            mediaSession = null
            playerInstance = null
            mediaSessionInstance = null
            sessionPlayer = null
        }
        releaseAudioFocus()
        libraryExecutor.shutdown()
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        mainHandler.removeCallbacks(retryStream)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.let { player ->
            if (player.playWhenReady) {
                player.pause()
            }
            stopSelf()
        }
        releaseAudioFocus()
    }

    companion object {
        private const val BUFFER_AHEAD_MS = 10 * 60 * 1000
        private const val STREAM_RETRY_MS = 5_000L
        private const val APPEND_RETRY_MS = 250L
        private const val APPEND_ATTEMPTS = 20
        /** Present on player items started from Android Auto; holds their browse ID. */
        const val EXTRA_AUTO_BROWSE_ID = "com.media.control.AUTO_BROWSE_ID"
        /** The cover's web URL; the artwork URI may point at the local copy instead. */
        const val EXTRA_ART_URL = "com.media.control.ART_URL"
        private const val RESUMPTION_PREFS = "media_control_auto_resume"
        private const val KEY_RESUME_ID = "browseId"
        private const val KEY_RESUME_POSITION = "positionMs"

        private var playerInstance: Player? = null
        private var sessionPlayer: TrackSwapPlayer? = null

        /** See [TrackSwapPlayer]. */
        var awaitingPlay: Boolean
            get() = sessionPlayer?.awaitingPlay == true
            set(value) {
                sessionPlayer?.awaitingPlay = value
            }
        private var mediaSessionInstance: MediaLibrarySession? = null

        /** Asks connected browsers (Android Auto) to reload the tree. */
        fun notifyLibraryChanged() {
            mediaSessionInstance?.let { session ->
                listOf(
                    MediaIds.ROOT, MediaIds.BIBLE, MediaIds.STUDY, MediaIds.DOWNLOADS,
                    MediaIds.downloads(MediaIds.BIBLE), MediaIds.downloads(MediaIds.STUDY)
                ).forEach { id ->
                    session.notifyChildrenChanged(id, Int.MAX_VALUE, null)
                }
            }
        }
        var player = MutableLiveData<Player>()
        var mPlayerState = MutableLiveData<String>()
        var mCurrentAudio = MutableLiveData<ResponseData>()
        var mAppBackground = MutableLiveData<Boolean>()
    }
}
