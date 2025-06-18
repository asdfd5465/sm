package bankwiser.bankpromotion.material.data

import android.content.Context
import android.util.Log
// import bankwiser.bankpromotion.material.BankWiserApplication // Not strictly needed here
import bankwiser.bankpromotion.material.data.local.DatabaseHelper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus // <<< CORRECT IMPORT
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.channels.awaitClose // Not used in this version
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// import kotlinx.coroutines.flow.callbackFlow // Not used in this version
import kotlinx.coroutines.launch
// import kotlinx.coroutines.tasks.await // Not strictly needed for Remote Config listener
import java.io.File

private const val TAG = "AssetPackUpdateManager"

// Remote Config Keys
const val RC_ASSET_PACK_DB_FILENAME = "asset_pack_db_filename"
const val RC_LATEST_ASSET_PACK_NAME = "latest_content_database_asset_pack_name"
const val RC_LATEST_DB_VERSION = "latest_content_database_version_number"

sealed class UpdateState {
    object Idle : UpdateState() // Correct: Objects don't have constructors
    object CheckingForUpdate : UpdateState()
    data class UpdateAvailable(val remoteVersion: Int, val packName: String, val dbFileName: String) : UpdateState()
    object NoUpdateNeeded : UpdateState()
    data class Downloading(val packName: String, val progress: Int) : UpdateState()
    data class DownloadFailed(val packName: String, @AssetPackStatus val errorCode: Int) : UpdateState() // Use annotation
    object VerifyingUpdate : UpdateState()
    object InstallingUpdate : UpdateState()
    data class UpdateComplete(val newVersion: Int) : UpdateState()
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
            minimumFetchIntervalInSeconds = 300 // Cache for 5 minutes during testing, increase for prod
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        val defaults = mapOf(
            RC_LATEST_DB_VERSION to userPreferencesHelper.getCurrentDatabaseVersion().toLong(),
            RC_LATEST_ASSET_PACK_NAME to "contentpack",
            RC_ASSET_PACK_DB_FILENAME to "content_v${userPreferencesHelper.getCurrentDatabaseVersion()}.db" // Reflecting simple name
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
                    Log.e(TAG, "Remote Config fetch failed", task.exception)
                    _updateState.value = UpdateState.RemoteConfigFetchFailed
                }
            }
    }

    private fun checkForUpdate() {
        val localDbVersion = userPreferencesHelper.getCurrentDatabaseVersion()
        Log.d(TAG, "Checking for update: Remote version=$currentRemoteConfigVersion, Local version=$localDbVersion")

        if (currentRemoteConfigVersion > localDbVersion && currentRemotePackName.isNotBlank() && currentRemoteDbFilename.isNotBlank()) {
            _updateState.value = UpdateState.UpdateAvailable(currentRemoteConfigVersion, currentRemotePackName, currentRemoteDbFilename)
        } else {
            _updateState.value = UpdateState.NoUpdateNeeded
            Log.i(TAG, "No update needed or remote config invalid. Local: $localDbVersion, Remote: $currentRemoteConfigVersion")
        }
    }

    fun startUpdateProcess() {
        val state = _updateState.value
        if (state is UpdateState.UpdateAvailable) {
            downloadAssetPack(state.packName)
        } else {
            Log.w(TAG, "Update process started but no update available or not in correct state: $state")
            if (_updateState.value == UpdateState.Idle || _updateState.value == UpdateState.NoUpdateNeeded || _updateState.value == UpdateState.UpdateFailedInstallation || _updateState.value == UpdateState.RemoteConfigFetchFailed) {
                initializeAndCheckRemoteConfig() // Re-check if in a final or failed state
            }
        }
    }

    private val assetPackStateUpdateListener = AssetPackStateUpdateListener { state ->
        Log.d(TAG, "AssetPack State Update: ${state.name()} is ${state.status()}, progress: ${state.transferProgressPercentage()}%")
        when (state.status()) {
            AssetPackStatus.PENDING -> {
                _updateState.value = UpdateState.Downloading(state.name(), 0)
            }
            AssetPackStatus.DOWNLOADING -> {
                val progress = (state.transferProgressPercentage()).toInt()
                _updateState.value = UpdateState.Downloading(state.name(), progress)
            }
            AssetPackStatus.TRANSFERRING -> {
                // Often a very short state, can keep UI as Downloading
                 _updateState.value = UpdateState.Downloading(state.name(), state.transferProgressPercentage().toInt())
            }
            AssetPackStatus.COMPLETED -> {
                Log.i(TAG, "Asset pack ${state.name()} download COMPLETED. Verifying and installing.")
                _updateState.value = UpdateState.VerifyingUpdate
                // Ensure currentRemote... values are set from the latest successful Remote Config fetch
                // that triggered this download.
                if (currentRemotePackName == state.name() && currentRemoteDbFilename.isNotBlank() && currentRemoteConfigVersion != -1) {
                    installDownloadedPack(state.name(), currentRemoteDbFilename, currentRemoteConfigVersion)
                } else {
                    Log.e(TAG, "Mismatch or invalid state for installing pack: ${state.name()}. RC Pack: $currentRemotePackName, File: $currentRemoteDbFilename, Ver: $currentRemoteConfigVersion")
                    _updateState.value = UpdateState.UpdateFailedInstallation // Or a more specific error
                }
                // Consider unregistering only after final states (COMPLETED, FAILED, CANCELED)
                // assetPackManager.unregisterListener(this@AssetPackUpdateManager.assetPackStateUpdateListener)
            }
            AssetPackStatus.FAILED -> {
                Log.e(TAG, "Asset pack ${state.name()} status: FAILED. Error code: ${state.errorCode()}")
                _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode())
                assetPackManager.unregisterListener(this@AssetPackUpdateManager.assetPackStateUpdateListener)
            }
            AssetPackStatus.CANCELED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: CANCELED")
                 _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode()) // Or a specific CANCELED state
                assetPackManager.unregisterListener(this@AssetPackUpdateManager.assetPackStateUpdateListener)
            }
            AssetPackStatus.WAITING_FOR_WIFI -> {
                Log.i(TAG, "Asset pack ${state.name()} status: WAITING_FOR_WIFI")
                // You might want to show a specific UI for this or trigger a notification
                // For now, treat as a form of Downloading (stalled)
                 _updateState.value = UpdateState.Downloading(state.name(), state.transferProgressPercentage().toInt()) // Show current progress
            }
            AssetPackStatus.NOT_INSTALLED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: NOT_INSTALLED. Attempting to fetch.")
                // This can happen if the listener is registered before fetch is called or if pack was removed.
                // Let's try to fetch it if we are in a state expecting a download.
                 if (_updateState.value is UpdateState.Downloading && (_updateState.value as UpdateState.Downloading).packName == state.name()) {
                     assetPackManager.fetch(listOf(state.name())) // Re-trigger fetch
                 }
            }
            AssetPackStatus.UNKNOWN -> {
                 Log.e(TAG, "Asset pack ${state.name()} status: UNKNOWN")
                _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode())
                assetPackManager.unregisterListener(this@AssetPackUpdateManager.assetPackStateUpdateListener)
            }
            else -> Log.d(TAG, "Asset pack ${state.name()} status: ${state.status()} (unhandled by when)")
        }
    }


    private fun downloadAssetPack(packName: String) {
        Log.i(TAG, "Requesting asset pack info and download for: $packName")
        _updateState.value = UpdateState.Downloading(packName, 0)

        assetPackManager.registerListener(assetPackStateUpdateListener)

        assetPackManager.getPackStates(listOf(packName))
            .addOnSuccessListener { assetPackStatesResult ->
                val packState = assetPackStatesResult.packStates()[packName]
                if (packState == null) {
                    Log.e(TAG, "Asset pack $packName info not found by PAD getPackStates.")
                    _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.UNKNOWN) // Custom error or specific
                    unregisterListener()
                    return@addOnSuccessListener
                }

                Log.d(TAG, "Initial state for $packName: ${packState.status()}")
                when (packState.status()) {
                    AssetPackStatus.NOT_INSTALLED, AssetPackStatus.DOWNLOAD_FAILED, AssetPackStatus.CANCELED -> {
                        assetPackManager.fetch(listOf(packName)) // This initiates or resumes download
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to initiate fetch for asset pack $packName", e)
                                _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.FAILED) // Generic failure
                                unregisterListener()
                            }
                    }
                    AssetPackStatus.DOWNLOADED -> { // Already downloaded from a previous attempt
                        Log.i(TAG, "Asset pack $packName already downloaded. Proceeding to install.")
                        _updateState.value = UpdateState.VerifyingUpdate
                        installDownloadedPack(packName, currentRemoteDbFilename, currentRemoteConfigVersion)
                    }
                    AssetPackStatus.PENDING, AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING, AssetPackStatus.WAITING_FOR_WIFI -> {
                        Log.d(TAG, "Asset pack $packName is already in progress (Status: ${packState.status()}). Listener will handle.")
                        // Listener is already registered and will pick up progress.
                    }
                    else -> {
                        Log.d(TAG, "Asset pack $packName in unhandled initial state: ${packState.status()}")
                        // Could treat as error or let listener try to resolve
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get pack states for $packName", e)
                _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.FAILED) // Generic
                unregisterListener()
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
                unregisterListener()
                return@launch
            }

            val assetsFolderPath = assetPackPathFile.assetsPath()
            // Ensure the "database" subfolder is part of the path if your asset pack structure includes it
            val dbFileFromPack = File(assetsFolderPath, "database/$dbFileNameInPack")

            if (!dbFileFromPack.exists()) {
                Log.e(TAG, "Database file $dbFileNameInPack not found in pack $packName at ${dbFileFromPack.absolutePath}")
                _updateState.value = UpdateState.UpdateFailedInstallation
                unregisterListener()
                return@launch
            }

            Log.d(TAG, "DB file from pack found at: ${dbFileFromPack.absolutePath}")

            val success = databaseHelper.replaceDatabase(dbFileFromPack)

            if (success) {
                userPreferencesHelper.setCurrentDatabaseVersion(newVersion)
                _updateState.value = UpdateState.UpdateComplete(newVersion)
                Log.i(TAG, "Database update to version $newVersion successful.")
                // Consider removing the pack if it's large and on-demand to free space
                // assetPackManager.removePack(packName)
            } else {
                _updateState.value = UpdateState.UpdateFailedInstallation
                Log.e(TAG, "Failed to replace internal database with new version.")
            }
            unregisterListener() // Always unregister after attempting install
        }
    }

    fun unregisterListener() {
        // It's safe to call unregister even if not registered or already unregistered.
        assetPackManager.unregisterListener(assetPackStateUpdateListener)
        Log.d(TAG, "AssetPackStateUpdateListener unregistered.")
    }
}
