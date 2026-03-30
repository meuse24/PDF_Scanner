package info.meuse24.pdf_scanner.util

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> Task<T>.awaitTask(): T {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { error -> cont.resumeWithException(error) }
    }
}
