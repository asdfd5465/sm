package bankwiser.bankpromotion.material.data

import android.content.Context
import android.util.Log
import bankwiser.bankpromotion.material.data.local.DatabaseHelper
import bankwiser.bankpromotion.material.data.local.UserPreferencesHelper
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus // Ensure this is imported
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "AssetPackUpdateManager"

const val RC_ASSET_PACK_DB_FILENAME = "asset_pack_db_filename"
const val RC_LATEST_ASSET_PACK_NAME = "latest_content_database_asset_pack_name"
const val RC_LATEST_DB_VERSION = "latest_content_database_version_number"

sealed class UpdateState {
    object Idle : UpdateState()
    object CheckingForUpdate : UpdateState()
    data class UpdateAvailable(val remoteVersion: Int, val packName: String, val dbFileName: String) : UpdateState()
    object NoUpdateNeeded : UpdateState()
    data class Downloading(val packName: String, val progress: Int) : UpdateState()
    data class DownloadFailed(val packName: String, @AssetPackStatus val errorCode: Int) : UpdateState()
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

    private val assetPackStateUpdateListener: AssetPackStateUpdateListener =
        AssetPackStateUpdateListener { state ->
            handleAssetPackStateUpdate(state)
        }

    private var isListenerRegistered = false

    private fun statusToString(@AssetPackStatus status: Int): String {
        return when (status) {
            AssetPackStatus.UNKNOWN -> "UNKNOWN"
            AssetPackStatus.PENDING -> "PENDING"
            AssetPackStatus.DOWNLOADING -> "DOWNLOADING"
            AssetPackStatus.TRANSFERRING -> "TRANSFERRING"
            AssetPackStatus.COMPLETED -> "COMPLETED"
            AssetPackStatus.FAILED -> "FAILED"
            AssetPackStatus.CANCELED -> "CANCELED"
            AssetPackStatus.WAITING_FOR_WIFI -> "WAITING_FOR_WIFI"
            AssetPackStatus.NOT_INSTALLED -> "NOT_INSTALLED"
            else -> status.toString()
        }
    }

    fun userDismissedPrompt() {
        Log.d(TAG, "User dismissed the update prompt.")
        when (_updateState.value) {
            is UpdateState.UpdateAvailable,
            is UpdateState.DownloadFailed,
            is UpdateState.UpdateFailedInstallation,
            is UpdateState.RemoteConfigFetchFailed -> {
                Log.i(TAG, "Update prompt dismissed. Setting state to Idle and unregistering listener.")
                _updateState.value = UpdateState.Idle
                unregisterListener()
            }
            else -> {
                Log.d(TAG, "User dismissed prompt, but no action taken for state: ${_updateState.value}")
            }
        }
    }

    private fun handleAssetPackStateUpdate(state: AssetPackState) {
        Log.d(TAG, "AssetPack State Update: ${state.name()} is ${statusToString(state.status())}, progress: ${state.transferProgressPercentage()}%")
        when (state.status()) {
            AssetPackStatus.PENDING -> _updateState.value = UpdateState.Downloading(state.name(), 0)
            AssetPackStatus.DOWNLOADING -> {
                val progress = state.transferProgressPercentage().toInt()
                _updateState.value = UpdateState.Downloading(state.name(), progress)
            }
            AssetPackStatus.TRANSFERRING -> _updateState.value = UpdateState.Downloading(state.name(), state.transferProgressPercentage().toInt())
            AssetPackStatus.COMPLETED -> {
                Log.i(TAG, "Asset pack ${state.name()} download COMPLETED. Verifying and installing.")
                _updateState.value = UpdateState.VerifyingUpdate
                if (currentRemotePackName == state.name() && currentRemoteDbFilename.isNotBlank() && currentRemoteConfigVersion != -1) {
                    installDownloadedPack(state.name(), currentRemoteDbFilename, currentRemoteConfigVersion)
                } else {
                    Log.e(TAG, "Mismatch or invalid state for installing pack: ${state.name()}. RC Pack: $currentRemotePackName, File: $currentRemoteDbFilename, Ver: $currentRemoteConfigVersion")
                    _updateState.value = UpdateState.UpdateFailedInstallation
                    unregisterListener()
                }
            }
            AssetPackStatus.FAILED -> {
                Log.e(TAG, "Asset pack ${state.name()} status: FAILED. Error code: ${state.errorCode()}")
                _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode())
                unregisterListener()
            }
            AssetPackStatus.CANCELED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: CANCELED")
                _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode())
                unregisterListener()
            }
            AssetPackStatus.WAITING_FOR_WIFI -> {
                Log.i(TAG, "Asset pack ${state.name()} status: WAITING_FOR_WIFI")
                _updateState.value = UpdateState.Downloading(state.name(), state.transferProgressPercentage().toInt())
            }
            AssetPackStatus.NOT_INSTALLED -> {
                Log.i(TAG, "Asset pack ${state.name()} status: NOT_INSTALLED.")
                val currentUpdateState = _updateState.value
                if (currentUpdateState is UpdateState.Downloading && currentUpdateState.packName == state.name()) {
                    Log.d(TAG, "Re-triggering fetch for ${state.name()}")
                    assetPackManager.fetch(listOf(state.name()))
                }
            }
            AssetPackStatus.UNKNOWN -> {
                Log.e(TAG, "Asset pack ${state.name()} status: UNKNOWN")
                _updateState.value = UpdateState.DownloadFailed(state.name(), state.errorCode())
                unregisterListener()
            }
            else -> Log.d(TAG, "Asset pack ${state.name()} status: ${state.status()} (unhandled by when)")
        }
    }

    fun initializeAndCheckRemoteConfig() {
        _updateState.value = UpdateState.CheckingForUpdate
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 300
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        val defaults = mapOf(
            RC_LATEST_DB_VERSION to userPreferencesHelper.getCurrentDatabaseVersion().toLong(),
            RC_LATEST_ASSET_PACK_NAME to "contentpack",
            RC_ASSET_PACK_DB_FILENAME to "content_v${userPreferencesHelper.getCurrentDatabaseVersion()}.db"
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
            if (_updateState.value == UpdateState.Idle || _updateState.value == UpdateState.NoUpdateNeeded || _updateState.value is UpdateState.DownloadFailed || _updateState.value == UpdateState.UpdateFailedInstallation || _updateState.value == UpdateState.RemoteConfigFetchFailed) {
                initializeAndCheckRemoteConfig()
            }
        }
    }

    private fun downloadAssetPack(packName: String) {
        Log.i(TAG, "Requesting asset pack info and download for: $packName")
        _updateState.value = UpdateState.Downloading(packName, 0)

        if (!isListenerRegistered) {
            assetPackManager.registerListener(assetPackStateUpdateListener)
            isListenerRegistered = true
            Log.d(TAG, "AssetPackStateUpdateListener registered.")
        }

        assetPackManager.getPackStates(listOf(packName))
            .addOnSuccessListener { assetPackStatesResult ->
                val packState = assetPackStatesResult.packStates()[packName]
                if (packState == null) {
                    Log.e(TAG, "Asset pack $packName info not found by PAD getPackStates.")
                    _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.UNKNOWN)
                    unregisterListener()
                    return@addOnSuccessListener
                }

                Log.d(TAG, "Initial state for $packName: ${statusToString(packState.status())}")
                when (packState.status()) {
                    AssetPackStatus.NOT_INSTALLED, AssetPackStatus.FAILED, AssetPackStatus.CANCELED -> {
                        Log.d(TAG, "Fetching pack $packName as it's ${statusToString(packState.status())}.")
                        assetPackManager.fetch(listOf(packName))
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to initiate fetch for asset pack $packName", e)
                                _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.FAILED)
                                unregisterListener()
                            }
                    }
                    // AssetPackStatus.DOWNLOADED is a legacy status.
                    // COMPLETED means it's fully on device and ready.
                    AssetPackStatus.COMPLETED -> {
                        Log.i(TAG, "Asset pack $packName ALREADY DOWNLOADED (status COMPLETED). Proceeding to install.")
                        _updateState.value = UpdateState.VerifyingUpdate
                        installDownloadedPack(packName, currentRemoteDbFilename, currentRemoteConfigVersion)
                    }
                    AssetPackStatus.PENDING, AssetPackStatus.DOWNLOADING, AssetPackStatus.TRANSFERRING, AssetPackStatus.WAITING_FOR_WIFI -> {
                        Log.d(TAG, "Asset pack $packName is already in progress (Status: ${statusToString(packState.status())}). Listener will handle.")
                    }
                    else -> {
                        Log.d(TAG, "Asset pack $packName in unhandled initial state: ${statusToString(packState.status())}")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get pack states for $packName", e)
                _updateState.value = UpdateState.DownloadFailed(packName, AssetPackStatus.FAILED)
                unregisterListener()
            }
    }

    private fun installDownloadedPack(packName: String, dbFileNameInPack: String, newVersion: Int) {
        coroutineScope.launch {
            _updateState.value = UpdateState.InstallingUpdate
            Log.i(TAG, "Installing downloaded pack: $packName, DB file: $dbFileNameInPack")

            val assetPackLocation = assetPackManager.getPackLocation(packName)
            if (assetPackLocation == null) {
                Log.e(TAG, "Failed to get asset pack location for $packName.")
                _updateState.value = UpdateState.UpdateFailedInstallation
                unregisterListener()
                return@launch
            }

            val assetsFolderPath = assetPackLocation.assetsPath()
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
            } else {
                _updateState.value = UpdateState.UpdateFailedInstallation
                Log.e(TAG, "Failed to replace internal database with new version.")
            }
            unregisterListener()
        }
    }

    fun unregisterListener() {
        if (isListenerRegistered) {
            try { // Add try-catch as unregister can sometimes throw if already unregistered.
                assetPackManager.unregisterListener(assetPackStateUpdateListener)
                isListenerRegistered = false
                Log.d(TAG, "AssetPackStateUpdateListener unregistered.")
            } catch (e: RuntimeException) {
                Log.w(TAG, "Error unregistering listener (might be already unregistered): ${e.message}")
                isListenerRegistered = false // Ensure flag is reset
            }
        }
    }
}
