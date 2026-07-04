package info.meuse24.pdf_scanner.util.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.domain.common.findLocalIPv4Address
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.model.LocalSyncError
import info.meuse24.pdf_scanner.domain.model.LocalSyncSession
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val LOCAL_SYNC_PORT = 8080

@Singleton
class KtorLocalSyncServer @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val importFileUseCase: ImportFileUseCase,
    private val storageProvider: StorageProvider,
    private val resourceProvider: ResourceProvider,
    @param:ApplicationContext private val context: Context
) : LocalSyncServer {

    private val _state = MutableStateFlow<LocalSyncState>(LocalSyncState.Stopped)
    override val state: StateFlow<LocalSyncState> = _state.asStateFlow()

    // Own scope so engine shutdown triggered from within a request handler (hard-stop,
    // network loss) doesn't try to cancel the coroutine it is currently running on.
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: EmbeddedServer<*, *>? = null

    // Recreated on every start() so a hard-stop lockout or stale sessions from a previous
    // run never carry over into the next one.
    private var sessionStore = LocalSyncSessionStore()
    private var rateLimiter = LoginRateLimiter()
    private val lastActivityMillis = AtomicLong(0L)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundIp: String? = null

    override suspend fun start(): LocalSyncSession {
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
        lastActivityMillis.set(session.startedAt)
        sessionStore = LocalSyncSessionStore()
        rateLimiter = LoginRateLimiter()

        val server = try {
            embeddedServer(CIO, port = LOCAL_SYNC_PORT, host = ip) {
                intercept(ApplicationCallPipeline.Call) {
                    lastActivityMillis.set(System.currentTimeMillis())
                    proceed()
                }
                routing {
                    localSyncRouting(
                        pin = pin,
                        documentRepository = documentRepository,
                        importFileUseCase = importFileUseCase,
                        storageProvider = storageProvider,
                        resourceProvider = resourceProvider,
                        sessionStore = sessionStore,
                        rateLimiter = rateLimiter,
                        onHardStop = ::triggerHardStop
                    )
                }
            }.also { it.start(wait = false) }
        } catch (throwable: Throwable) {
            _state.value = LocalSyncState.Error(LocalSyncError.PORT_UNAVAILABLE)
            throw throwable
        }

        engine = server
        boundIp = ip
        registerNetworkWatch(ip)
        _state.value = LocalSyncState.Running(session)
        return session
    }

    override suspend fun stop() {
        stopInternal()
        _state.value = LocalSyncState.Stopped
    }

    override fun millisSinceLastActivity(): Long? {
        if (_state.value !is LocalSyncState.Running) return null
        return System.currentTimeMillis() - lastActivityMillis.get()
    }

    /** Invoked from the login route once the total-failure hard-stop threshold is hit. */
    private fun triggerHardStop() {
        serverScope.launch {
            stopInternal()
            _state.value = LocalSyncState.Error(LocalSyncError.HARD_STOPPED)
        }
    }

    /**
     * The server is only meant to be reachable on the Wi-Fi/LAN interface whose address it
     * bound to at start(). If that address disappears (Wi-Fi disconnects, network switches),
     * [findLocalIPv4Address] will no longer return it, so the server is stopped instead of
     * silently continuing to listen on an interface nobody expects it to be reachable from.
     */
    private fun registerNetworkWatch(ip: String) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return

        fun checkStillBound() {
            if (boundIp != null && findLocalIPv4Address() != boundIp) {
                serverScope.launch { stop() }
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = checkStillBound()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
                checkStillBound()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                checkStillBound()
        }
        runCatching { connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun stopInternal() {
        networkCallback?.let { callback ->
            runCatching {
                context.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            }
        }
        networkCallback = null
        boundIp = null
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        engine = null
        sessionStore.invalidateAll()
    }
}
