package info.meuse24.pdf_scanner.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ResourceProvider {
    fun getString(@StringRes resId: Int): String
    fun getString(@StringRes resId: Int, vararg args: Any): String
    fun getQuantityString(@PluralsRes resId: Int, quantity: Int, vararg args: Any): String
}

class AndroidResourceProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ResourceProvider {
    override fun getString(resId: Int): String = context.getString(resId)

    override fun getString(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    override fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String =
        context.resources.getQuantityString(resId, quantity, *args)
}
