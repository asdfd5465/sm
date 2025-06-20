package bankwiser.bankpromotion.material.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.crypto.FileEncryptionHelper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "DownloadViewModel"

sealed class DownloadUiState {
    object Idle : DownloadUiState()
    data class InProgress(val progress: Int) : DownloadUiState() // Progress 0-100
    object Success : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BankWiserApplication
    private val fileEncryptionHelper: FileEncryptionHelper = app.fileEncryptionHelper
    private val userPreferencesHelper: UserPreferencesHelper = app.userPreferencesHelper

    private val _downloadStates = MutableStateFlow<Map<String, DownloadUiState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadUiState>> = _downloadStates.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    fun getDownloadState(audioId: String): DownloadUiState {
        return _downloadStates.value[audioId] ?: DownloadUiState.Idle
    }

    fun isAudioDownloaded(audioId: String): Boolean {
        return userPreferencesHelper.getDownloadedAudioPath(audioId) != null
    }

    fun downloadAndEncryptAudio(audioId: String, audioUrl: String) {
        if (activeDownloadJobs[audioId]?.isActive == true || isAudioDownloaded(audioId)) {
            Log.d(TAG, "Download for $audioId already in progress or completed.")
            return
        }
        Log.i(TAG, "Starting download and encryption for $audioId from $audioUrl")

        activeDownloadJobs[audioId] = viewModelScope.launch(Dispatchers.IO) {
            updateDownloadState(audioId, DownloadUiState.InProgress(0))
            try {
                val url = URL(audioUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    updateDownloadState(audioId, DownloadUiState.Error("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}"))
                    return@launch
                }

                val fileSize = connection.contentLength
                val inputStream = connection.inputStream

                // Create a temporary file for the encrypted output
                val encryptedFile = File(app.cacheDir, "$audioId.enc.tmp")
                val fileOutputStream = FileOutputStream(encryptedFile)

                // We'll use a separate output stream for encryption that writes to the fileOutputStream
                // This is because encrypt method closes the outputStream it's given.

                val success = fileEncryptionHelper.encrypt(inputStream, fileOutputStream) // This closes fileOutputStream

                if (success) {
                    // Move the temporary encrypted file to permanent app-specific storage
                    val permanentEncryptedFile = File(app.filesDir, "$audioId.enc")
                    encryptedFile.renameTo(permanentEncryptedFile)
                    userPreferencesHelper.setDownloadedAudioPath(audioId, permanentEncryptedFile.absolutePath)
                    updateDownloadState(audioId, DownloadUiState.Success)
                    Log.i(TAG, "Audio $audioId downloaded and encrypted to ${permanentEncryptedFile.absolutePath}")
                } else {
                    updateDownloadState(audioId, DownloadUiState.Error("Encryption failed"))
                    encryptedFile.delete() // Clean up temp file on failure
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/encrypting audio $audioId", e)
                updateDownloadState(audioId, DownloadUiState.Error(e.localizedMessage ?: "Unknown download error"))
            } finally {
                activeDownloadJobs.remove(audioId)
            }
        }
    }
    
    fun deleteDownloadedAudio(audioId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userPreferencesHelper.getDownloadedAudioPath(audioId)?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
                userPreferencesHelper.removeDownloadedAudioPath(audioId)
                updateDownloadState(audioId, DownloadUiState.Idle) // Reset state
                Log.i(TAG, "Deleted downloaded audio: $audioId")
            }
        }
    }


    private fun updateDownloadState(audioId: String, state: DownloadUiState) {
        viewModelScope.launch(Dispatchers.Main) { // Ensure UI updates on main thread
             _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                this[audioId] = state
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDownloadJobs.values.forEach { it.cancel() }
    }
}
