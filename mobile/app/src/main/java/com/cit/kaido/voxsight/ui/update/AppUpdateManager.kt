package com.cit.kaido.voxsight.ui.update

import android.content.Context
import android.util.Log
import com.cit.kaido.voxsight.BuildConfig
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.AppVersionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    private val _isUpdateRequired = MutableStateFlow(false)
    val isUpdateRequired: StateFlow<Boolean> = _isUpdateRequired.asStateFlow()

    private val _updateInfo = MutableStateFlow<AppVersionResponse?>(null)
    val updateInfo: StateFlow<AppVersionResponse?> = _updateInfo.asStateFlow()

    /**
     * Checks with backend if an update is mandatory.
     * Fails silently if offline so users can still view local cached scores.
     */
    fun checkForUpdates(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = ApiClient.versionApi
                val response = api.getAppVersion()
                val currentVersionCode = BuildConfig.VERSION_CODE
                Log.d(TAG, "Current versionCode=$currentVersionCode, minRequired=${response.minVersionCode}, forceUpdate=${response.forceUpdate}")

                // Only require update if user's version code is strictly lower than minVersionCode AND forceUpdate is enabled
                if (currentVersionCode < response.minVersionCode && response.forceUpdate) {
                    _updateInfo.value = response
                    _isUpdateRequired.value = true
                } else {
                    _isUpdateRequired.value = false
                }
            } catch (e: Exception) {
                // Network failure / offline: allow graceful offline usage
                Log.w(TAG, "Failed to check app version from server: ${e.message}")
            }
        }
    }

    /**
     * Triggered if a 426 Upgrade Required is received during an active API call.
     */
    fun triggerForceUpdate(downloadUrl: String = "", minVersionName: String = "1.1.0") {
        val current = _updateInfo.value ?: AppVersionResponse(
            minVersionName = minVersionName,
            downloadUrl = downloadUrl.ifEmpty { "https://github.com/lVIN15/VoxSight/releases/download/v1.1.0/voxsight-v1.1.0.apk" },
            forceUpdate = true
        )

        // Safety Guard: If client is already on or above the required version, DO NOT trap the user!
        val isAlreadyUpdated = BuildConfig.VERSION_CODE >= current.minVersionCode ||
                BuildConfig.VERSION_NAME.equals(current.minVersionName, ignoreCase = true)

        if (!isAlreadyUpdated) {
            _updateInfo.value = current
            _isUpdateRequired.value = true
        } else {
            Log.d(TAG, "Ignoring triggerForceUpdate: app is already on v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        }
    }
}
