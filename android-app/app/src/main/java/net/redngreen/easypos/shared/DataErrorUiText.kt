package net.redngreen.easypos.shared

import net.redngreen.easypos.R
import net.redngreen.easypos.core.result.DataError

fun DataError.toUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResource(R.string.error_no_internet)
    DataError.Network.REQUEST_TIMEOUT -> UiText.StringResource(R.string.error_request_timeout)
    DataError.Network.UNAUTHORIZED -> UiText.StringResource(R.string.error_unauthorized)
    DataError.Network.SERVER_ERROR -> UiText.StringResource(R.string.error_server)
    DataError.Network.SERVICE_UNAVAILABLE -> UiText.StringResource(R.string.error_server)
    DataError.Network.SERIALIZATION -> UiText.StringResource(R.string.error_serialization)
    DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
    DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
    else -> UiText.StringResource(R.string.error_unknown)
}
