package bankwiser.bankpromotion.material.data

import android.content.Context
import android.util.Log
import bankwiser.bankpromotion.material.BankWiserApplication
import bankwiser.bankpromotion.material.data.local.DatabaseHelper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.google.android.play.core.assetpacks.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

private const val TAG = "AssetPackUpdateManager"

// Remote Config Keys
const val RC_ASSET_PACK_DB_FILENAME = "asset_pack_db_filename"
const val RC_LATEST_ASSET_PACK_NAME = "latest_content_database_asset_pack_name"
const val RC_LATEST_DB_VERSION = "latest_content_database_version_number"

sealed class UpdateState {
    object Idle : UpdateState()
    object CheckingForUpdate : UpdateState()
    data class UpdateAvailable(val remoteVersion: Int, val packName: String, val dbFileName: String) : UpdateState()
    object NoUpdateNeeded : UpdateState()
    data class Downloading(val packName: String, val progress: Int) : UpdateState()
    data class DownloadFailed(val packName: String, val errorCode: Int) : UpdateState()
    object VerifyingUpdate : UpdateState() // Added for clarity between download and install
    object InstallingUpdate : UpdateState()
    object UpdateComplete(val newVersion: Int) : UpdateState()
    object UpdateFailedInstallation : UpdateState()
    object RemoteConfigFetchFailed: UpdateState()
}

class AssetPackUpdateManager(
    private val context: Context,
    private val databaseHelper: DatabaseHelper,
    private val userPreferencesHelper: UserPreferencesHelper,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val assetPackManager: AssetPackManager = AssetPackManagerFactory.getInstance(context)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var currentRemoteConfigVersion = -1
    private var currentRemotePackName = ""
    private var currentRemoteDbFilename = ""


    fun initializeAndCheckRemoteConfig() {
        _updateState.value = UpdateState.CheckingForUpdate
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // Cache for 1 hour
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        // Set default values (optional, but good practice)
        val defaults = mapOf(
            RC_LATEST_DB_VERSION to userPreferencesHelper.getCurrentDatabaseVersion().toLong(), // Default to current local
            RC_LATEST_ASSET_PACK_NAME to "contentpack",
            RC_ASSET_PACK_DB_FILENAME to "content_v${userPreferencesHelper.getCurrentDatabaseVersion()}_encrypted.db"
        )
        remoteConfig.setDefaultsAsync(defaults)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Remote Config params updated: $updated")
                    currentRemoteConfigVersion = remoteConfig.getLong(RC_LATEST_DB_VERSION).toInt()
                    currentRemotePackName = remoteConfig.getString(RC_LATEST_ASSET_PACK_NAME)
                    currentRemoteDbFilename = remoteConfig.getString(RC_ASSET_PACK_DB_FILENAME)
                    Log.i(TAG, "Remote Config: Version=$currentRemoteConfigVersion, Pack=$currentRemotePackName, File=$currentRemoteDbFilename")
                    checkForUpdate()
                } else {
                    Log.e(TAG, "Remote Config fetch failed")
                    _updateState.value = UpdateState.RemoteConfigFetchFailed
                }
            }
    }

    private fun checkForUpdate() {
        val localDbVersion = userPreferencesHelper.getCurrentDatabaseVersion()
        Log.d(TAG, "Checking for update: Remote version=$currentRemoteConfigVersion, Local version=$localDbVersion")

        if (currentRemoteConfigVersion > localDbVersion && currentRemotePackName.isNotBlank() && currentRemoteDbFilename.isNotBlank()) {
            _updateState.value = UpdateState.UpdateAvailable(currentRemoteConfigVersion, currentRemotePackName, currentRemoteDbFilename)
            // Optionally: automatically start download or wait for user confirmation
            // downloadAssetPack(currentRemotePackName) // Example: auto-download
        } else {
            _updateState.value = UpdateState.NoUpdateNeeded
            Log.i(TAG, "No update needed or remote config invalid.")
        }
    }

    fun startUpdateProcess() {
        val state = _updateState.value
        if (state is UpdateState.UpdateAvailable) {
            downloadAssetPack(state.packName)
        } else {
            Log.w(TAG, "Update process started but no update available or not in correct state.")
            // Optionally re-check remote config if idle
            if (_updateState.value == UpdateState.Idle || _updateState.value == UpdateState.NoUpdateNeeded) {
                initializeAndCheckRemoteConfig()
            }
        }
    }


    private fun downloadAssetPack(packName: String) {
        Log.i(TAG, "Requesting download for asset pack: $packName")
        _updateState.value = UpdateState.Downloading(packName, 0)

        assetPackManager.registerListener(assetPackStateUpdateListener)

        // Fetching the pack states to ensure we have the latest info, then request the pack.
        assetPackManager.getPackStates(listOf(packName))
            .addOnSuccessListener { assetPackStates ->
                val packState = assetPackStates.packStates()[packName]
                if (packState == null || packState.status() == AssetPackStatus.UNKNOWN) {
                     Log.e(TAG, "Asset pack $packName not found by PAD.")
                    _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.UNKNOWN)
                    return@addOnSuccessListener
                }

                if (packState.status() == AssetPackStatus.NOT_INSTALLED || packState.status() == AssetPackStatus.DOWNLOAD_FAILED) {
                    assetPackManager.fetch(listOf(packName))
                        .addOnSuccessListener {
                            Log.d(TAG, "Asset pack fetch initiated for $packName. Waiting for download...")
                            // Listener will handle progress and completion
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to initiate fetch for asset pack $packName", e)
                            _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.FAILED) // Generic failure
                        }
                } else if (packState.status() == AssetPackStatus.DOWNLOADED) {
                    Log.i(TAG, "Asset pack $packName already downloaded. Proceeding to install.")
                    _updateState.value = UpdateState.VerifyingUpdate
                    installDownloadedPack(packName, currentRemoteDbFilename, currentRemoteConfigVersion)
                } else {
                     Log.d(TAG, "Asset pack $packName status: ${packState.status()}. Monitoring via listener.")
                     // If it's PENDING, DOWNLOADING, etc., the listener will handle it.
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get pack states for $packName", e)
                _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.FAILED)
            }
    }

    private val assetPackStateUpdateListener = AssetPackStateUpdateListener { state ->
        when (state.status()) {
            AssetPackStatus.PENDING -> {
                Log.d(TAG, "Asset pack ${state.name()} status: PENDING")
                _updateState.value = UpdateState.Downloading(state.name(), 0)
            }
            AssetPackStatus.DOWNLOADING -> {
                val progress = (state.transferProgressPercentage()).toInt()
                Log.d(TAG, "Asset pack ${state.name()} status: DOWNLOADING $progress%")
                _updateState.value = UpdateState.Downloading(state.name(), progress)
            }
            AssetPackStatus.TRANSFERRING -> {
                 Log.d(TAG, "Asset pack ${state.name()} status: TRANSFERRING")
                // Can show a different state if needed, or keep as Downloading
            }
            AssetPackStatus.COMPLETED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: COMPLETED. Ready to install.")
                _updateState.value = UpdateState.VerifyingUpdate
                installDownloadedPack(state.name(), currentRemoteDbFilename, currentRemoteConfigVersion)
                assetPackManager.unregisterListener(assetPackStateUpdateListener) // Unregister after completion
            }
            AssetPackStatus.FAILED -> {
                Log.e(TAG, "Asset pack ${state.name()} status: FAILED. Error code: ${state.errorCode()}")
                _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode())
                assetPackManager.unregisterListener(assetPackStateUpdateListener)
            }
            AssetPackStatus.CANCELED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: CANCELED")
                 _updateState.value = UpdateState.DownloadFailed(state.name(), AssetPackStatus.CANCELED)
                assetPackManager.unregisterListener(assetPackStateUpdateListener)
            }
            AssetPackStatus.WAITING_FOR_WIFI -> {
                Log.i(TAG, "Asset pack ${state.name()} status: WAITING_FOR_WIFI")
                // You might want to show a specific UI for this
            }
            AssetPackStatus.NOT_INSTALLED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: NOT_INSTALLED")
                // This state might occur if fetch wasn't called yet or if removed.
            }
            AssetPackStatus.UNKNOWN -> {
                 Log.e(TAG, "Asset pack ${state.name()} status: UNKNOWN")
                _updateState.value = UpdateState.DownloadFailed(state.name(), AssetPackStatus.UNKNOWN)
                assetPackManager.unregisterListener(assetPackStateUpdateListener)
            }
            else -> Log.d(TAG, "Asset pack ${state.name()} status: ${state.status()}")
        }
    }

    private fun installDownloadedPack(packName: String, dbFileNameInPack: String, newVersion: Int) {
        coroutineScope.launch {
            _updateState.value = UpdateState.InstallingUpdate
            Log.i(TAG, "Installing downloaded pack: $packName, DB file: $dbFileNameInPack")

            val assetPackPathFile = assetPackManager.getPackLocation(packName)
            if (assetPackPathFile == null) {
                Log.e(TAG, "Failed to get asset pack location for $packName.")
                _updateState.value = UpdateState.UpdateFailedInstallation
                return@launch
            }

            // The path to the actual assets within the pack
            val assetsFolderPath = assetPackPathFile.assetsPath()
            val dbFileFromPack = File(assetsFolderPath, "database/$dbFileNameInPack") // Assumes "database" subfolder in asset pack

            if (!dbFileFromPack.exists()) {
                Log.e(TAG, "Database file $dbFileNameInPack not found in pack $packName at ${dbFileFromPack.absolutePath}")
                _updateState.value = UpdateState.UpdateFailedInstallation
                return@launch
            }

            Log.d(TAG, "DB file from pack found at: ${dbFileFromPack.absolutePath}")

            val success = databaseHelper.replaceDatabase(dbFileFromPack)

            if (success) {
                userPreferencesHelper.setCurrentDatabaseVersion(newVersion)
                _updateState.value = UpdateState.UpdateComplete(newVersion)
                Log.i(TAG, "Database update to version $newVersion successful.")
                // Important: After successful update, you might want to clear the downloaded pack
                // to free up space, especially for on-demand packs.
                // assetPackManager.removePack(packName) // Use with caution
            } else {
                _updateState.value = UpdateState.UpdateFailedInstallation
                Log.e(TAG, "Failed to replace internal database with new version.")
            }
        }
    }

    fun unregisterListener() {
        try {
            assetPackManager.unregisterListener(assetPackStateUpdateListener)
        } catch (e: Exception) {
            Log.w(TAG, "Listener might have already been unregistered: ${e.message}")
        }
    }
}
