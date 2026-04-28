package info.meuse24.pdf_scanner.domain.common

import java.io.File

fun resolveUniqueFilename(dir: File, name: String): String {
    if (!File(dir, "$name.pdf").exists()) return name
    var counter = 2
    while (File(dir, "${name}_$counter.pdf").exists()) counter++
    return "${name}_$counter"
}
