package bankwiser.bankpromotion.material.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val currentPlayingUrl: String? = null,
    val isActuallyPlaying: Boolean = false,
    val error: String? = null
)

class PlayerManager(context: Context) {
    private var exoPlayer: ExoPlayer? = ExoPlayer.Builder(context).build()
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    init {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isActuallyPlaying = isPlaying)
                if (!isPlaying && exoPlayer?.playbackState == Player.STATE_ENDED) {
                    _playerState.value = _playerState.value.copy(currentPlayingUrl = null, isActuallyPlaying = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                _playerState.value = _playerState.value.copy(error = error.localizedMessage ?: "Unknown player error", isActuallyPlaying = false)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                     if (!_playerState.value.isActuallyPlaying) { // Double check if it wasn't already set by onIsPlayingChanged
                        _playerState.value = _playerState.value.copy(currentPlayingUrl = null, isActuallyPlaying = false)
                     }
                }
            }
        })
    }

    // Convenience function for UI to check if a specific URL is the one playing
    fun isCurrentlyPlayingUrl(url: String): Boolean {
        return _playerState.value.currentPlayingUrl == url && _playerState.value.isActuallyPlaying
    }

    fun play(url: String) {
        if (exoPlayer == null) {
            _playerState.value = _playerState.value.copy(error = "Player not initialized")
            return
        }
        _playerState.value = _playerState.value.copy(error = null) // Clear previous errors

        if (_playerState.value.currentPlayingUrl == url && _playerState.value.isActuallyPlaying) {
            return // Already playing this URL
        }

        try {
            if (_playerState.value.currentPlayingUrl != url) { // New URL or different URL
                exoPlayer?.stop()
                val mediaItem = MediaItem.fromUri(url)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
            }
            exoPlayer?.playWhenReady = true // Ensure player starts when ready
            exoPlayer?.play()
            _playerState.value = _playerState.value.copy(currentPlayingUrl = url, isActuallyPlaying = true)
        } catch (e: Exception) {
            // Catch potential errors from setting media item with bad URL format, etc.
             _playerState.value = _playerState.value.copy(error = e.localizedMessage ?: "Failed to play URL", currentPlayingUrl = url, isActuallyPlaying = false)
        }
    }

    fun pause() {
        exoPlayer?.pause()
        // isActuallyPlaying will be updated by the listener
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        _playerState.value = PlayerState() // Reset state
    }
}
