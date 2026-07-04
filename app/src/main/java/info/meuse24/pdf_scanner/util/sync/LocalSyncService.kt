package info.meuse24.pdf_scanner.util.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.util.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LOCAL_SYNC_NOTIFICATION_CHANNEL_ID = "local_sync_channel"
private const val LOCAL_SYNC_NOTIFICATION_ID = 4711
private const val ACTION_STOP = "info.meuse24.pdf_scanner.action.STOP_LOCAL_SYNC"
private const val INACTIVITY_TIMEOUT_MILLIS = 20 * 60 * 1000L
private const val INACTIVITY_CHECK_INTERVAL_MILLIS = 30_000L

/**
 * Foreground service hosting the PC-Sync HTTP server so it survives screen-off.
 * Stops itself when the app is locked (App-Lock is a UI gate that a background
 * server would otherwise bypass, see local-wifi-pc-sync.md) or after [INACTIVITY_TIMEOUT_MILLIS]
 * without a request.
 */
@AndroidEntryPoint
class LocalSyncService : Service() {

    @Inject
    lateinit var localSyncServer: LocalSyncServer

    @Inject
    lateinit var appLockManager: AppLockManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                val session = localSyncServer.start()
                startForeground(
                    LOCAL_SYNC_NOTIFICATION_ID,
                    buildNotification(session.url, session.pin),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                watchForShutdownConditions()
            } catch (_: Exception) {
                // localSyncServer.start() already recorded the failure reason as an Error
                // state; only the Android-side service needs tearing down here, since
                // calling localSyncServer.stop() would overwrite that Error with Stopped
                // and the UI would never see why starting failed.
                stopSelfCompletely(resetServerState = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun watchForShutdownConditions() {
        watchJob?.cancel()
        watchJob = scope.launch {
            appLockManager.isLocked
                .onEach { locked -> if (locked) stopSelfCompletely() }
                .launchIn(this)

            localSyncServer.state
                .onEach { state ->
                    // The server enters Error on its own (e.g. hard-stop after too many
                    // failed PIN attempts) and has already stopped its engine by then;
                    // only the foreground service still needs to be torn down.
                    if (state is LocalSyncState.Error) stopSelfCompletely(resetServerState = false)
                }
                .launchIn(this)

            while (isActive) {
                delay(INACTIVITY_CHECK_INTERVAL_MILLIS)
                val idleMillis = localSyncServer.millisSinceLastActivity() ?: break
                if (idleMillis > INACTIVITY_TIMEOUT_MILLIS) {
                    stopSelfCompletely()
                    break
                }
            }
        }
    }

    private fun stopSelfCompletely(resetServerState: Boolean = true) {
        watchJob?.cancel()
        scope.launch {
            if (resetServerState) {
                localSyncServer.stop()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.launch { localSyncServer.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(url: String, pin: String): Notification {
        val stopIntent = Intent(this, LocalSyncService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, LOCAL_SYNC_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_scan)
            .setContentTitle(getString(R.string.local_sync_notification_title))
            .setContentText(getString(R.string.local_sync_notification_text, url, pin))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(0, getString(R.string.local_sync_notification_stop), stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            LOCAL_SYNC_NOTIFICATION_CHANNEL_ID,
            getString(R.string.local_sync_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        fun startIntent(context: Context): Intent = Intent(context, LocalSyncService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, LocalSyncService::class.java).apply { action = ACTION_STOP }
    }
}
