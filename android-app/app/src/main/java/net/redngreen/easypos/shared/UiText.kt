package net.redngreen.easypos.shared

import android.content.Context
import androidx.annotation.StringRes

/** Wraps a string that is — or could be — a localized resource. */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    class StringResource(@StringRes val id: Int, val args: Array<Any> = emptyArray()) : UiText
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.DynamicString -> value
    is UiText.StringResource -> context.getString(id, *args)
}
