package sound.recorder.widget.base

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import sound.recorder.widget.R

class InAppUpdateHelper(
    private val activity: ComponentActivity,
    private val updateType: Int = AppUpdateType.FLEXIBLE
) {

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(activity)
    }

    private companion object {
        /** Jeda cek update setelah app dibuka, agar tidak bersaing dengan startup. */
        const val INITIAL_CHECK_DELAY_MS = 8_000L
    }

    private val updateLock = Any()
    private var isUpdateFlowRunning = false
    private var isListenerRegistered = false
    private var lastResumeCheckTime = 0L
    private var updateJob: Job? = null

    private var installDialog: AlertDialog? = null
    /** Unduhan selesai saat app di background: dialog ditampilkan saat app kembali. */
    private var installPromptPending = false
    /** User memilih Nanti: tidak ditanya lagi sampai app dibuka ulang. */
    private var installPostponed = false

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
                        requestInstall()
                    } catch (e: Exception) {
                        Log.e("Update", "Listener error", e)
                    } finally {
                        unregisterListenerIfNeeded()
                        resetUpdateFlag()
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
                // Jangan cek saat app baru dibuka: hasil bind service Play (onServiceConnected)
                // selalu dikirim ke main thread dan menumpuk dengan inisialisasi awal di HP
                // lambat → ANR "Input dispatching timed out" (Oppo A18, Android 15).
                delay(INITIAL_CHECK_DELAY_MS)
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    resetUpdateFlag()
                    return@launch
                }
                // Gunakan IO dan Timeout panjang untuk initial check
                val info = withTimeoutOrNull(15_000L) {
                    withContext(Dispatchers.IO) {
                        appUpdateManager.appUpdateInfo.await()
                    }
                } ?: run {
                    resetUpdateFlag()
                    return@launch
                }

                // Sudah diunduh tapi belum dipasang (user memilih Nanti di sesi sebelumnya)
                if (updateType == AppUpdateType.FLEXIBLE &&
                    info.installStatus() == InstallStatus.DOWNLOADED
                ) {
                    requestInstall()
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
                    registerListenerIfNeeded()
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

    /**
     * Memasang update me-restart app, jadi user yang menentukan waktunya:
     * bisa saja dia sedang bermain, merekam, atau export.
     */
    private fun requestInstall() {
        if (installPostponed || installDialog?.isShowing == true) return
        if (activity.isFinishing || activity.isDestroyed) return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            installPromptPending = true
            return
        }
        installPromptPending = false
        installDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_ready_title)
            .setMessage(R.string.update_ready_message)
            .setPositiveButton(R.string.update_install) { _, _ -> completeFlexibleUpdate() }
            .setNegativeButton(R.string.update_later) { _, _ -> installPostponed = true }
            .setOnCancelListener { installPostponed = true }
            .show()
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
        if (installPromptPending) requestInstall()
        val now = System.currentTimeMillis()
        
        synchronized(updateLock) {
            // onResume pertama sesi ini: cek awal sudah dijadwalkan checkUpdate (tertunda),
            // jangan bind service Play kedua kalinya saat app baru dibuka.
            if (lastResumeCheckTime == 0L) {
                lastResumeCheckTime = now
                return
            }
            // Throttling: Jangan cek di onResume jika baru saja dicek (30 detik)
            if (isUpdateFlowRunning || (now - lastResumeCheckTime < 30_000L)) return
            lastResumeCheckTime = now
        }

        activity.lifecycleScope.launch {
            try {
                val info = withTimeoutOrNull(5_000L) {
                    withContext(Dispatchers.IO) {
                        appUpdateManager.appUpdateInfo.await()
                    }
                } ?: return@launch

                if (updateType == AppUpdateType.IMMEDIATE &&
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    startUpdateSafely(info)
                } else if (updateType == AppUpdateType.FLEXIBLE &&
                    info.installStatus() == InstallStatus.DOWNLOADED
                ) {
                    requestInstall()
                }
            } catch (e: Exception) {
                Log.e("Update", "Resume check failed", e)
            }
        }
    }

    fun onDestroy() {
        try {
            updateJob?.cancel()
            installDialog?.dismiss()
            installDialog = null
            unregisterListenerIfNeeded()
            resetUpdateFlag()
        } catch (e: Exception) {
            Log.e("Update", "Cleanup error", e)
        }
    }

    private fun registerListenerIfNeeded() {
        if (updateType == AppUpdateType.FLEXIBLE && !isListenerRegistered) {
            try {
                appUpdateManager.registerListener(installStateUpdatedListener)
                isListenerRegistered = true
            } catch (_: Exception) { }
        }
    }

    private fun unregisterListenerIfNeeded() {
        if (isListenerRegistered) {
            try {
                appUpdateManager.unregisterListener(installStateUpdatedListener)
                isListenerRegistered = false
            } catch (_: Exception) { }
        }
    }

    private fun resetUpdateFlag() {
        synchronized(updateLock) {
            isUpdateFlowRunning = false
        }
    }
}
