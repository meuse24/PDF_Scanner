package info.meuse24.pdf_scanner.util

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TextRecognizerRunner {
    suspend fun recognize(recognizer: TextRecognizer, image: InputImage): String
}

class MlKitTextRecognizerRunner @Inject constructor() : TextRecognizerRunner {
    override suspend fun recognize(recognizer: TextRecognizer, image: InputImage): String {
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
            cont.invokeOnCancellation { recognizer.close() }
        }
    }
}
