package info.meuse24.pdf_scanner.domain.common

import java.io.File

fun resolveUniqueFilename(
    dir: File,
    name: String,
    conflictingExtensions: Set<String> = setOf("pdf")
): String {
    if (conflictingExtensions.none { extension -> File(dir, "$name.$extension").exists() }) return name
    var counter = 2
    while (conflictingExtensions.any { extension -> File(dir, "${name}_$counter.$extension").exists() }) counter++
    return "${name}_$counter"
}

fun sanitizeFilename(name: String, fallback: String = "Document"): String {
    val sanitized = name
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .trim()
        .trim('.')
    return sanitized.ifBlank { fallback }
}

fun sanitizeDownloadFolderName(filename: String): String {
    val withoutPdfExtension = filename.replace(Regex("""(?i)\.pdf$"""), "")
    return sanitizeFilename(withoutPdfExtension, fallback = "export")
}

fun resolveSafeChildFile(directory: File, filename: String): File {
    val canonicalDirectory = directory.canonicalFile
    val candidate = File(canonicalDirectory, filename).canonicalFile
    require(candidate.parentFile == canonicalDirectory) {
        "Resolved path escapes target directory"
    }
    return candidate
}
