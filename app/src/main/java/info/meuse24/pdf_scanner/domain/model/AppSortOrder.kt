package info.meuse24.pdf_scanner.domain.model

enum class AppSortOrder(val storageValue: String) {
    BY_DATE("date"),
    BY_NAME("name"),
    BY_SIZE("size");

    companion object {
        fun fromStorageValue(value: String?): AppSortOrder =
            entries.firstOrNull { it.storageValue == value } ?: BY_DATE
    }
}
