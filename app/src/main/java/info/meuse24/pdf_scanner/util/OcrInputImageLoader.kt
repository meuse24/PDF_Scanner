package info.meuse24.pdf_scanner.util

import android.content.Context
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

interface OcrInputImageLoader {
    fun loadFromFile(file: File): InputImage
}

class AndroidOcrInputImageLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) : OcrInputImageLoader {
    override fun loadFromFile(file: File): InputImage {
        return InputImage.fromFilePath(context, android.net.Uri.fromFile(file))
    }
}
