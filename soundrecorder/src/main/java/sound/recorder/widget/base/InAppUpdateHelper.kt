package sound.recorder.widget.base

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class InAppUpdateHelper(
    private val activity: ComponentActivity,
    private val updateType: Int = AppUpdateType.FLEXIBLE
) {

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)

    private val updateLock = Any()
    private var isUpdateFlowRunning = false
    private var updateJob: Job? = null

    private val updateLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            resetUpdateFlag()
            if (result.resultCode != Activity.RESULT_OK) {
                Log.e("Update", "Update canceled or failed")
                unregisterListenerIfNeeded()
            }
        }

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                activity.lifecycleScope.launch {
                    try {
                        Toast.makeText(activity, "Update downloaded", Toast.LENGTH_SHORT).show()
                        completeFlexibleUpdate()
                    } catch (e: Exception) {
                        Log.e("Update", "Listener error", e)
                    } finally {
                        unregisterListenerIfNeeded()
                    }
                }
            }

            InstallStatus.FAILED,
            InstallStatus.CANCELED -> {
                unregisterListenerIfNeeded()
                resetUpdateFlag()
            }

            else -> Unit
        }
    }

    fun checkUpdate() {
        synchronized(updateLock) {
            if (isUpdateFlowRunning) return
            isUpdateFlowRunning = true
        }

        updateJob = activity.lifecycleScope.launch {
            try {
                val info = withTimeoutOrNull(15_000L) {
                    withContext(Dispatchers.IO) {
                        appUpdateManager.appUpdateInfo.await()
                    }
                } ?: run {
                    resetUpdateFlag()
                    return@launch
                }

                val isUpdateAvailable =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

                val isUpdateAllowed = when (updateType) {
                    AppUpdateType.FLEXIBLE -> info.isFlexibleUpdateAllowed
                    AppUpdateType.IMMEDIATE -> info.isImmediateUpdateAllowed
                    else -> false
                }

                if (isUpdateAvailable && isUpdateAllowed) {
                    startUpdateSafely(info)
                } else {
                    resetUpdateFlag()
                }

            } catch (e: Exception) {
                Log.e("Update", "Check update failed", e)
                resetUpdateFlag()
            }
        }
    }

    private fun startUpdateSafely(info: AppUpdateInfo) {
        if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            resetUpdateFlag()
            return
        }

        activity.lifecycleScope.launch {
            try {
                if (updateType == AppUpdateType.FLEXIBLE) {
                    appUpdateManager.registerListener(installStateUpdatedListener)
                }

                val options = AppUpdateOptions.newBuilder(updateType).build()
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    options
                )

            } catch (e: Exception) {
                Log.e("Update", "Start update error", e)
                unregisterListenerIfNeeded()
                resetUpdateFlag()
            }
        }
    }

    private fun completeFlexibleUpdate() {
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e("Update", "Complete update failed", e)
        } finally {
            resetUpdateFlag()
        }
    }



    fun onResume() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->

            // IMMEDIATE: lanjutkan flow yang tertunda
            if (updateType == AppUpdateType.IMMEDIATE &&
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                startUpdateSafely(info)
            }

            // FLEXIBLE: jika sudah terdownload saat app ditinggal
            else if (updateType == AppUpdateType.FLEXIBLE &&
                info.installStatus() == InstallStatus.DOWNLOADED
            ) {
                try {
                    completeFlexibleUpdate()
                } catch (e: Exception) {
                    Log.e("Update", "Resume complete update failed", e)
                }
            }
        }
    }


    fun onDestroy() {
        try {
            updateJob?.cancel()
            unregisterListenerIfNeeded()
            resetUpdateFlag()
        } catch (e: Exception) {
            Log.e("Update", "Cleanup error", e)
        }
    }

    private fun unregisterListenerIfNeeded() {
        if (updateType == AppUpdateType.FLEXIBLE) {
            try {
                appUpdateManager.unregisterListener(installStateUpdatedListener)
            } catch (_: Exception) { }
        }
    }

    private fun resetUpdateFlag() {
        synchronized(updateLock) {
            isUpdateFlowRunning = false
        }
    }
}
