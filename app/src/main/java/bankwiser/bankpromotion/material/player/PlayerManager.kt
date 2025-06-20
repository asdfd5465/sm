package bankwiser.bankpromotion.material.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.crypto.FileEncryptionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "PlayerManager"

data class PlayerState(
    val currentPlayingUrlOrPath: String? = null, // Can be URL or local file path
    val isActuallyPlaying: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false
)

class PlayerManager(private val context: Context) {
    private var exoPlayer: ExoPlayer? = ExoPlayer.Builder(context).build()
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val application = context.applicationContext as BankWiserApplication
    private val fileEncryptionHelper: FileEncryptionHelper = application.fileEncryptionHelper
    private val playerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentDecryptionJob: Job? = null

    init {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(
                    isActuallyPlaying = isPlaying,
                    isLoading = if (isPlaying) false else _playerState.value.isLoading // Stop loading if playback starts
                )
                if (!isPlaying && (exoPlayer?.playbackState == Player.STATE_ENDED || exoPlayer?.playbackState == Player.STATE_IDLE)) {
                    clearCurrentPlayingState()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer Error: ${error.localizedMessage}", error)
                _playerState.value = _playerState.value.copy(
                    error = error.localizedMessage ?: "Unknown player error",
                    isActuallyPlaying = false,
                    isLoading = false
                )
                clearCurrentPlayingState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> _playerState.value = _playerState.value.copy(isLoading = true, error = null)
                    Player.STATE_READY -> _playerState.value = _playerState.value.copy(isLoading = false, error = null)
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        if (!_playerState.value.isActuallyPlaying) { // Already handled by onIsPlayingChanged
                            clearCurrentPlayingState()
                        }
                         _playerState.value = _playerState.value.copy(isLoading = false)
                    }
                }
            }
        })
    }
    
    private fun clearCurrentPlayingState() {
        _playerState.value = PlayerState() // Reset to idle, no URL/path
    }


    fun isCurrentlyPlaying(urlOrPath: String): Boolean {
        return _playerState.value.currentPlayingUrlOrPath == urlOrPath && _playerState.value.isActuallyPlaying
    }

    fun play(contentPathOrUrl: String, isLocalEncrypted: Boolean = false) {
        currentDecryptionJob?.cancel() // Cancel any ongoing decryption/playback setup
        if (exoPlayer == null) {
            Log.e(TAG, "ExoPlayer is null, cannot play.")
            _playerState.value = _playerState.value.copy(error = "Player not initialized")
            return
        }
        _playerState.value = PlayerState(isLoading = true, currentPlayingUrlOrPath = contentPathOrUrl) // Reset error, set loading

        if (isLocalEncrypted) {
            currentDecryptionJob = playerScope.launch {
                playEncryptedLocalFile(contentPathOrUrl)
            }
        } else {
            // Stream from URL
            try {
                Log.i(TAG, "Streaming from URL: $contentPathOrUrl")
                val mediaItem = MediaItem.fromUri(contentPathOrUrl)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
                 // isActuallyPlaying will be set by listener
            } catch (e: Exception) {
                Log.e(TAG, "Error setting media item for URL: $contentPathOrUrl", e)
                _playerState.value = _playerState.value.copy(error = "Invalid audio URL", isLoading = false)
            }
        }
    }

    private suspend fun playEncryptedLocalFile(encryptedFilePath: String) {
        Log.i(TAG, "Attempting to play encrypted local file: $encryptedFilePath")
        var tempDecryptedFile: File? = null
        try {
            val encryptedFile = File(encryptedFilePath)
            if (!encryptedFile.exists()) {
                _playerState.value = _playerState.value.copy(error = "Encrypted file not found", isLoading = false)
                return
            }

            // Create a temporary file in cache for the decrypted content
            tempDecryptedFile = File.createTempFile("decrypted_audio_", ".mp3", context.cacheDir)
            val success = withContext(Dispatchers.IO) {
                fileEncryptionHelper.decrypt(FileInputStream(encryptedFile), FileOutputStream(tempDecryptedFile))
            }

            if (success && tempDecryptedFile.exists() && tempDecryptedFile.length() > 0) {
                Log.i(TAG, "Decryption successful. Playing from temp file: ${tempDecryptedFile.absolutePath}")
                val mediaItem = MediaItem.fromUri(Uri.fromFile(tempDecryptedFile))
                withContext(Dispatchers.Main) { // ExoPlayer calls must be on main thread
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.playWhenReady = true
                }
                 // isActuallyPlaying will be set by listener
            } else {
                Log.e(TAG, "Decryption failed or temp file is empty for $encryptedFilePath")
                _playerState.value = _playerState.value.copy(error = "Decryption failed", isLoading = false)
                tempDecryptedFile?.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing encrypted local file $encryptedFilePath", e)
            _playerState.value = _playerState.value.copy(error = e.localizedMessage ?: "Failed to play local file", isLoading = false)
            tempDecryptedFile?.delete()
        }
        // Note: The tempDecryptedFile should be deleted when playback stops or errors out.
        // ExoPlayer's onPlaybackStateChanged (STATE_IDLE, STATE_ENDED) or onPlayerError can be used.
        // For simplicity here, it's deleted on error or if decryption fails.
        // A more robust cleanup involves tracking the temp file and deleting it in a finally block or player listener.
    }


    fun pause() {
        exoPlayer?.pause()
    }

    fun releasePlayer() {
        playerScope.cancel() // Cancel any ongoing coroutines in this scope
        exoPlayer?.release()
        exoPlayer = null
        _playerState.value = PlayerState() // Reset state
        Log.i(TAG, "PlayerManager released.")
    }
}
