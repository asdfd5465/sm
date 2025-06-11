package bankwiser.bankpromotion.material.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerManager(context: Context) {
    private var exoPlayer: ExoPlayer? = ExoPlayer.Builder(context).build()
    private val _currentPlayingUrl = MutableStateFlow<String?>(null)

    init {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && exoPlayer?.playbackState == Player.STATE_ENDED) {
                    _currentPlayingUrl.value = null // Clear when playback ends
                } else if (!isPlaying && exoPlayer?.playbackState == Player.STATE_IDLE){
                     _currentPlayingUrl.value = null // Clear if stopped/reset
                }
            }
        })
    }

    fun isPlaying(url: String): StateFlow<Boolean> {
        val flow = MutableStateFlow(exoPlayer?.isPlaying == true && _currentPlayingUrl.value == url)
        // This isn't perfect as it doesn't dynamically update, but it's a start for UI state
        // A more robust solution would involve a listener in the composable or a more complex state management.
        // For now, this is simpler and relies on recomposition when _currentPlayingUrl changes.
        // We'll improve this if needed. This is a basic example for Phase 4.
        return flow.asStateFlow() // In a real app, this flow would be updated by player events
    }


    fun play(url: String) {
        if (exoPlayer == null) return // Should not happen if initialized

        if (_currentPlayingUrl.value == url && exoPlayer?.isPlaying == true) {
            // Already playing this URL, do nothing or maybe restart? For now, do nothing.
            return
        }

        if (_currentPlayingUrl.value != url) { // New URL or different URL
            exoPlayer?.stop()
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
        }
        exoPlayer?.playWhenReady = true
        exoPlayer?.play()
        _currentPlayingUrl.value = url
    }

    fun pause() {
        exoPlayer?.pause()
        // Do not clear _currentPlayingUrl on pause, so we can resume
    }

    fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        _currentPlayingUrl.value = null
    }
}
