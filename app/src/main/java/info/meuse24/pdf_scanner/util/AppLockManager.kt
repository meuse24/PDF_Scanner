package info.meuse24.pdf_scanner.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class AppLockAvailability {
    AVAILABLE,
    NONE_ENROLLED,
    UNAVAILABLE
}

sealed interface AppLockAuthResult {
    data object Success : AppLockAuthResult
    data object Cancelled : AppLockAuthResult
    data object Unavailable : AppLockAuthResult
}

@Singleton
class AppLockManager @Inject constructor(
    private val settingsRepository: AppSettingsRepository,
    @param:ApplicationContext private val context: Context
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _isLocked = MutableStateFlow(settingsRepository.settings.value.appLockEnabled)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastBackgroundedAtMillis: Long? = null

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                if (!settings.appLockEnabled) {
                    _isLocked.value = false
                    lastBackgroundedAtMillis = null
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val settings = settingsRepository.settings.value
        if (!settings.appLockEnabled) {
            _isLocked.value = false
            return
        }

        val timeoutMillis = settings.appLockTimeoutSeconds.coerceAtLeast(0) * 1000L
        val backgroundedAt = lastBackgroundedAtMillis
        _isLocked.value = when {
            backgroundedAt == null -> true
            timeoutMillis == 0L -> true
            else -> System.currentTimeMillis() - backgroundedAt > timeoutMillis
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (settingsRepository.settings.value.appLockEnabled) {
            lastBackgroundedAtMillis = System.currentTimeMillis()
        }
    }

    fun getAvailability(): AppLockAvailability {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> AppLockAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AppLockAvailability.NONE_ENROLLED
            else -> AppLockAvailability.UNAVAILABLE
        }
    }

    fun unlock() {
        _isLocked.value = false
    }

    suspend fun authenticate(activity: FragmentActivity): AppLockAuthResult {
        if (getAvailability() != AppLockAvailability.AVAILABLE) {
            return AppLockAuthResult.Unavailable
        }

        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlock()
                        if (continuation.isActive) {
                            continuation.resume(AppLockAuthResult.Success)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) {
                            continuation.resume(AppLockAuthResult.Cancelled)
                        }
                    }
                }
            )

            continuation.invokeOnCancellation {
                prompt.cancelAuthentication()
            }

            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.app_lock_prompt_title))
                    .setSubtitle(context.getString(R.string.app_lock_prompt_subtitle))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
            )
        }
    }
}
