package info.meuse24.pdf_scanner.domain.gateway

interface ResourceProvider {
    fun getString(resource: StringResource): String
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg args: Any): String
    fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String
}
