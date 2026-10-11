package net.redngreen.easypos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import net.redngreen.easypos.core.result.DataError
import net.redngreen.easypos.core.result.Result

/**
 * Typed [Result]-returning helpers over Ktor, per the data-layer skill.
 * Callers pass full URLs (the base URL is device-local state, resolved at
 * call time — see core.sync.SyncConfig).
 */

suspend inline fun <reified Response : Any> HttpClient.get(
    url: String,
    queryParameters: Map<String, Any?> = mapOf(),
): Result<Response, DataError.Network> = safeCall {
    get(url) {
        queryParameters.forEach { (key, value) ->
            parameter(key, value)
        }
    }
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    url: String,
    body: Request,
): Result<Response, DataError.Network> = safeCall {
    post(url) {
        setBody(body)
    }
}

suspend inline fun <reified Response : Any> HttpClient.delete(
    url: String,
): Result<Response, DataError.Network> = safeCall {
    delete(url)
}

suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse,
): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        return Result.Error(DataError.Network.NO_INTERNET)
    } catch (e: SerializationException) {
        return Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: IOException) {
        return Result.Error(DataError.Network.UNKNOWN)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        return Result.Error(DataError.Network.UNKNOWN)
    }

    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(
    response: HttpResponse,
): Result<T, DataError.Network> = when (response.status.value) {
    in 200..299 -> Result.Success(response.body<T>())
    401 -> Result.Error(DataError.Network.UNAUTHORIZED)
    408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
    409 -> Result.Error(DataError.Network.CONFLICT)
    413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
    429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
    in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
    else -> Result.Error(DataError.Network.UNKNOWN)
}
