package com.media.control.service

import android.os.Handler
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player the media session exposes. While the app swaps tracks, between
 * `load()` and `playLoaded()`, the real player sits paused; reporting it as
 * buffering makes Android Auto show a spinner instead of a play button that
 * looks frozen.
 */
@UnstableApi
class TrackSwapPlayer(player: Player) : ForwardingPlayer(player) {

    private val listeners = mutableSetOf<Player.Listener>()
    private val handler = Handler(player.applicationLooper)
    private val giveUp = Runnable { awaitingPlay = false }

    var awaitingPlay = false
        set(value) {
            if (field == value) return
            field = value
            handler.removeCallbacks(giveUp)
            if (value) handler.postDelayed(giveUp, MAX_WAIT_MS)
            listeners.toList().forEach {
                it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                it.onPlaybackStateChanged(playbackState)
            }
        }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) awaitingPlay = false
            }

            override fun onPlayerError(error: PlaybackException) {
                awaitingPlay = false
            }
        })
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getPlaybackState(): Int =
        if (awaitingPlay) Player.STATE_BUFFERING else super.getPlaybackState()

    override fun getPlayWhenReady(): Boolean = awaitingPlay || super.getPlayWhenReady()

    private companion object {
        /** The app normally calls playLoaded() well within this; a failed load never does. */
        const val MAX_WAIT_MS = 10_000L
    }
}
