package info.meuse24.pdf_scanner.util.sync

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.common.LocalSyncShutdownReason
import info.meuse24.pdf_scanner.domain.common.evaluateLocalSyncShutdown
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.domain.model.LocalSyncTimeout
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
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
import java.security.SecureRandom
import javax.inject.Inject

private const val LOCAL_SYNC_NOTIFICATION_CHANNEL_ID = "local_sync_channel"
private const val LOCAL_SYNC_NOTIFICATION_ID = 4711
private const val ACTION_STOP = "info.meuse24.pdf_scanner.action.STOP_LOCAL_SYNC"
private const val EXTRA_RUN_ID = "info.meuse24.pdf_scanner.extra.LOCAL_SYNC_RUN_ID"
private const val ALARM_REQUEST_CODE = 4712
private const val INACTIVITY_CHECK_INTERVAL_MILLIS = 30_000L

/** Reserved id for a user-triggered stop, which applies to whatever run is active. */
internal const val USER_STOP_RUN_ID = 0L

/**
 * A pending runtime-ceiling alarm survives the run it was scheduled for. Without this check
 * a stale alarm could tear down a freshly started connection minutes after the user opened it.
 */
internal fun shouldApplyStopRequest(requestedRunId: Long, currentRunId: Long): Boolean =
    requestedRunId == USER_STOP_RUN_ID || requestedRunId == currentRunId

/**
 * Foreground service hosting the PC-Sync HTTP server so it survives screen-off.
 *
 * Every shutdown condition — App-Lock, inactivity, the hard runtime ceiling, the server
 * having stopped itself — is decided by the single domain function
 * [evaluateLocalSyncShutdown]; this class only executes the verdict. A previous version
 * spread that logic across the watchdog loop and two flow collectors and could leave the
 * service running indefinitely when the server stopped on its own (see docs/pcsync.md, D1).
 */
@AndroidEntryPoint
class LocalSyncService : Service() {

    @Inject
    lateinit var localSyncServer: LocalSyncServer

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    /**
     * Identifies one server run. The runtime-ceiling alarm carries the id it was scheduled
     * for, so a stale alarm can never tear down a later run.
     */
    private var runId: Long = 0L
    private var shuttingDown = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (shouldApplyStopRequest(intent.getLongExtra(EXTRA_RUN_ID, USER_STOP_RUN_ID), runId)) {
                stopSelfCompletely()
            }
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                val session = localSyncServer.start()
                runId = nextRunId()
                shuttingDown = false
                val timeoutMinutes = appSettingsRepository.settings.value.localSyncIdleTimeoutMinutes
                startForeground(
                    LOCAL_SYNC_NOTIFICATION_ID,
                    buildNotification(session.url, session.pin, timeoutMinutes),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                scheduleRuntimeCeilingAlarm()
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
            // Both flows re-run the same policy so App-Lock and a self-stopped server take
            // effect immediately rather than at the next tick.
            appLockManager.isLocked
                .onEach { evaluateAndMaybeShutdown() }
                .launchIn(this)

            localSyncServer.state
                .onEach { evaluateAndMaybeShutdown() }
                .launchIn(this)

            while (isActive) {
                delay(INACTIVITY_CHECK_INTERVAL_MILLIS)
                evaluateAndMaybeShutdown()
            }
        }
    }

    /**
     * The single decision point. Reading the timeout per evaluation means a change in
     * settings applies to the running session without restarting it.
     */
    private fun evaluateAndMaybeShutdown() {
        if (shuttingDown) return
        val reason = evaluateLocalSyncShutdown(
            appLocked = appLockManager.isLocked.value,
            activity = localSyncServer.activitySnapshot(),
            idleTimeoutMillis = LocalSyncTimeout.minutesToMillis(
                appSettingsRepository.settings.value.localSyncIdleTimeoutMinutes
            ),
            maxRuntimeMillis = LocalSyncTimeout.minutesToMillis(
                LocalSyncTimeout.MAX_SESSION_RUNTIME_MINUTES
            )
        ) ?: return
        shutdown(reason)
    }

    private fun shutdown(reason: LocalSyncShutdownReason) {
        // ServerNotRunning means the server already stopped itself (network loss) or failed;
        // calling stop() again would overwrite an Error state the UI still needs to show.
        stopSelfCompletely(resetServerState = reason != LocalSyncShutdownReason.ServerNotRunning)
    }

    private fun stopSelfCompletely(resetServerState: Boolean = true) {
        if (shuttingDown) return
        shuttingDown = true
        watchJob?.cancel()
        cancelRuntimeCeilingAlarm()
        scope.launch {
            if (resetServerState) {
                localSyncServer.stop()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Android 15+ reports the shared six-hour `dataSync` budget per 24 h as exhausted here
     * and expects the service to stop within seconds — otherwise it raises a
     * RemoteServiceException. Not the app's own two-hour policy, hence its own reason.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        shutdown(LocalSyncShutdownReason.SystemForegroundServiceTimeout)
    }

    override fun onDestroy() {
        watchJob?.cancel()
        cancelRuntimeCeilingAlarm()
        scope.launch { localSyncServer.stop() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Redundant safety net for the hard runtime ceiling: if the watchdog coroutine ever dies
     * — exactly the D1 failure mode — nothing living inside this process would notice.
     *
     * Best effort by design: an exact wake-up in Doze would need the SCHEDULE_EXACT_ALARM
     * special permission, which is denied by default since Android 12 and is not worth
     * requesting for a document app. The binding two-hour limit therefore remains the
     * watchdog policy; this alarm only shortens the window when the process is unhealthy.
     */
    private fun scheduleRuntimeCeilingAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() +
            LocalSyncTimeout.minutesToMillis(LocalSyncTimeout.MAX_SESSION_RUNTIME_MINUTES)
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                runtimeCeilingPendingIntent()
            )
        }
    }

    private fun cancelRuntimeCeilingAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(runtimeCeilingPendingIntent()) }
    }

    private fun runtimeCeilingPendingIntent(): PendingIntent {
        val intent = Intent(this, LocalSyncService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_RUN_ID, runId)
        }
        return PendingIntent.getService(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun nextRunId(): Long {
        val random = SecureRandom()
        var candidate = random.nextLong()
        while (candidate == USER_STOP_RUN_ID) candidate = random.nextLong()
        return candidate
    }

    private fun buildNotification(url: String, pin: String, timeoutMinutes: Int): Notification {
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
            .setContentText(
                getString(R.string.local_sync_notification_text_timeout, url, pin, timeoutMinutes)
            )
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
