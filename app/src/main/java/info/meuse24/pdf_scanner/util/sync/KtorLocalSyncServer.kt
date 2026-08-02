package info.meuse24.pdf_scanner.util.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.common.findLocalIPv4Address
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.model.LocalSyncActivity
import info.meuse24.pdf_scanner.domain.model.LocalSyncError
import info.meuse24.pdf_scanner.domain.model.LocalSyncSession
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCAL_SYNC_PORT = 8080

/** One server run's bound address, tagged with the generation it belongs to. */
internal data class BoundRun(val generation: Long, val ipAddress: String)

/**
 * True when [checkedRun] no longer describes the active run, so its address verdict must be
 * discarded. Guards against a slow interface scan outliving the run it was started for and
 * stopping a replacement session (see docs/pcsync.md).
 */
internal fun isStaleAddressCheck(checkedRun: BoundRun, currentRun: BoundRun?): Boolean =
    currentRun == null || currentRun.generation != checkedRun.generation

/**
 * Closes half-dead keep-alive sockets (PC asleep, Wi-Fi dozing) instead of letting them
 * linger in the selector set, which is the suspected cause of the CPU spin documented in
 * docs/pcsync.md.
 */
private const val CONNECTION_IDLE_TIMEOUT_SECONDS = 60

/** Minimum spacing between two interface scans triggered by network callbacks. */
private const val NETWORK_CHECK_DEBOUNCE_MILLIS = 2_000L

@Singleton
class KtorLocalSyncServer @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val importFileUseCase: ImportFileUseCase,
    private val storageProvider: StorageProvider,
    private val resourceProvider: ResourceProvider,
    @param:ApplicationContext private val context: Context
) : LocalSyncServer {

    /**
     * Immutable activity state, swapped atomically. Kept as one value rather than three
     * separate atomics so [activitySnapshot] always describes a single instant.
     * Null means "not running".
     */
    private data class ActivityState(
        val startedElapsedRealtime: Long,
        val lastActivityElapsedRealtime: Long,
        val activeRequests: Int
    )

    private val _state = MutableStateFlow<LocalSyncState>(LocalSyncState.Stopped)
    override val state: StateFlow<LocalSyncState> = _state.asStateFlow()

    // Own scope so engine shutdown triggered from within a request handler (hard-stop,
    // network loss) doesn't try to cancel the coroutine it is currently running on.
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Serialises start/stop so overlapping network callbacks cannot tear down an engine
    // another coroutine is still bringing up.
    private val lifecycleMutex = Mutex()

    private var engine: EmbeddedServer<*, *>? = null

    // Recreated on every start() so a hard-stop lockout or stale sessions from a previous
    // run never carry over into the next one.
    private var sessionStore = LocalSyncSessionStore()
    private var rateLimiter = LoginRateLimiter()

    // elapsedRealtime, not wall clock: an NTP or timezone jump must not defeat or
    // prematurely trip the inactivity timeout.
    private val activityState = AtomicReference<ActivityState?>(null)

    private val activityTracker = object : LocalSyncActivityTracker {
        override fun recordActivity() {
            activityState.updateIfPresent { it.copy(lastActivityElapsedRealtime = SystemClock.elapsedRealtime()) }
        }

        override fun beginTransfer() {
            activityState.updateIfPresent {
                it.copy(
                    lastActivityElapsedRealtime = SystemClock.elapsedRealtime(),
                    activeRequests = it.activeRequests + 1
                )
            }
        }

        override fun endTransfer() {
            activityState.updateIfPresent {
                it.copy(
                    lastActivityElapsedRealtime = SystemClock.elapsedRealtime(),
                    activeRequests = (it.activeRequests - 1).coerceAtLeast(0)
                )
            }
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkCallbackThread: HandlerThread? = null
    private val lastNetworkCheckElapsed = AtomicLong(0L)

    // Address plus the generation it belongs to, swapped as one value. The generation lets a
    // slow address check tell whether the run it was started for is still the current one.
    private val boundRun = AtomicReference<BoundRun?>(null)
    private val runGenerationCounter = AtomicLong(0L)

    override suspend fun start(): LocalSyncSession = lifecycleMutex.withLock {
        stopInternal()
        _state.value = LocalSyncState.Starting

        val ip = findLocalIPv4Address()
        if (ip == null) {
            _state.value = LocalSyncState.Error(LocalSyncError.NO_LOCAL_NETWORK)
            throw IllegalStateException("Kein lokales Netzwerk verfuegbar")
        }

        val pin = generateLocalSyncPin()
        val session = LocalSyncSession(
            ipAddress = ip,
            port = LOCAL_SYNC_PORT,
            pin = pin,
            startedAt = System.currentTimeMillis()
        )
        val now = SystemClock.elapsedRealtime()
        activityState.set(
            ActivityState(
                startedElapsedRealtime = now,
                lastActivityElapsedRealtime = now,
                activeRequests = 0
            )
        )
        sessionStore = LocalSyncSessionStore()
        rateLimiter = LoginRateLimiter()

        val server = try {
            // The port/host overload of embeddedServer offers no engine `configure` block,
            // so the connector is declared inside the configuration instead.
            embeddedServer(
                CIO,
                environment = applicationEnvironment { },
                configure = {
                    connectionIdleTimeoutSeconds = CONNECTION_IDLE_TIMEOUT_SECONDS
                    reuseAddress = true
                    connector {
                        port = LOCAL_SYNC_PORT
                        host = ip
                    }
                }
            ) {
                routing {
                    localSyncRouting(
                        pin = pin,
                        documentRepository = documentRepository,
                        importFileUseCase = importFileUseCase,
                        storageProvider = storageProvider,
                        resourceProvider = resourceProvider,
                        sessionStore = sessionStore,
                        rateLimiter = rateLimiter,
                        onHardStop = ::triggerHardStop,
                        activityTracker = activityTracker
                    )
                }
            }.also { it.start(wait = false) }
        } catch (throwable: Throwable) {
            activityState.set(null)
            _state.value = LocalSyncState.Error(LocalSyncError.PORT_UNAVAILABLE)
            throw throwable
        }

        engine = server
        boundRun.set(BoundRun(generation = runGenerationCounter.incrementAndGet(), ipAddress = ip))
        registerNetworkWatch()
        _state.value = LocalSyncState.Running(session)
        return session
    }

    override suspend fun stop() {
        lifecycleMutex.withLock {
            stopInternal()
        }
        _state.value = LocalSyncState.Stopped
    }

    override fun activitySnapshot(): LocalSyncActivity? {
        if (_state.value !is LocalSyncState.Running) return null
        val current = activityState.get() ?: return null
        val now = SystemClock.elapsedRealtime()
        return LocalSyncActivity(
            idleMillis = now - current.lastActivityElapsedRealtime,
            runtimeMillis = now - current.startedElapsedRealtime,
            activeRequests = current.activeRequests
        )
    }

    /** Invoked from the login route once the total-failure hard-stop threshold is hit. */
    private fun triggerHardStop() {
        serverScope.launch {
            lifecycleMutex.withLock { stopInternal() }
            _state.value = LocalSyncState.Error(LocalSyncError.HARD_STOPPED)
        }
    }

    /**
     * The server is only meant to be reachable on the Wi-Fi/LAN interface whose address it
     * bound to at start(). If that address disappears (Wi-Fi disconnects, network switches),
     * [findLocalIPv4Address] will no longer return it, so the server is stopped instead of
     * silently continuing to listen on an interface nobody expects it to be reachable from.
     *
     * `onCapabilitiesChanged` is deliberately NOT observed: it fires on every RSSI/link-speed
     * update — sometimes once per second — and each callback used to run a full
     * [findLocalIPv4Address] interface scan (one ioctl per interface) on a binder thread.
     * A signal-strength change cannot alter the bound address, so it carries no information here.
     */
    private fun registerNetworkWatch() {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return

        val callbackThread = HandlerThread("local-sync-network").apply { start() }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = scheduleBoundAddressCheck()
            override fun onAvailable(network: Network) = scheduleBoundAddressCheck()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                scheduleBoundAddressCheck()
        }

        val request = NetworkRequest.Builder()
            // No NET_CAPABILITY_INTERNET: hotspot / Wi-Fi Direct setups have none but are
            // exactly the LAN cases this feature must keep supporting.
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        runCatching {
            connectivityManager.registerNetworkCallback(request, callback, Handler(callbackThread.looper))
        }.onSuccess {
            networkCallback = callback
            networkCallbackThread = callbackThread
        }.onFailure {
            callbackThread.quitSafely()
        }
    }

    /** Debounced and moved off the binder thread; the interface scan is comparatively expensive. */
    private fun scheduleBoundAddressCheck() {
        val now = SystemClock.elapsedRealtime()
        val previous = lastNetworkCheckElapsed.get()
        if (now - previous < NETWORK_CHECK_DEBOUNCE_MILLIS) return
        if (!lastNetworkCheckElapsed.compareAndSet(previous, now)) return

        serverScope.launch {
            val checkedRun = boundRun.get() ?: return@launch
            val currentAddress = withContext(Dispatchers.IO) { findLocalIPv4Address() }
            if (currentAddress == checkedRun.ipAddress) return@launch

            // The interface scan is slow enough that the user can stop and restart PC Sync
            // while it runs. Without the generation check, the verdict about the *old* run
            // would tear down the newly started one.
            lifecycleMutex.withLock {
                if (isStaleAddressCheck(checkedRun, boundRun.get())) return@withLock
                stopInternal()
                _state.value = LocalSyncState.Stopped
            }
        }
    }

    /** Must only be called while [lifecycleMutex] is held. Idempotent. */
    private suspend fun stopInternal() {
        networkCallback?.let { callback ->
            runCatching {
                context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            }
        }
        networkCallback = null
        networkCallbackThread?.quitSafely()
        networkCallbackThread = null
        boundRun.set(null)
        activityState.set(null)

        val current = engine
        engine = null
        if (current != null) {
            // EmbeddedServer.stop() blocks; the caller may well be on the main thread.
            withContext(Dispatchers.IO) {
                current.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
            }
        }
        sessionStore.invalidateAll()
    }

    /** Applies [transform] only while a run is active, leaving a stopped server untouched. */
    private inline fun AtomicReference<ActivityState?>.updateIfPresent(
        crossinline transform: (ActivityState) -> ActivityState
    ) {
        updateAndGet { current -> current?.let(transform) }
    }
}
