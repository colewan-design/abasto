package com.budgettracker.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * How every API call comes back. Repositories never throw: a screen gets
 * either a value or an [ApiException] it can render, which keeps `try`/`catch`
 * out of the view models.
 */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: ApiException) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Ok) action(value)
}

inline fun <T> Outcome<T>.onFailure(action: (ApiException) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Err) action(error)
}

inline fun <T, R> Outcome<T>.fold(onOk: (T) -> R, onErr: (ApiException) -> R): R = when (this) {
    is Outcome.Ok -> onOk(value)
    is Outcome.Err -> onErr(error)
}

/** For calls whose body says nothing beyond "it worked". */
fun <T> Outcome<T>.discard(): Outcome<Unit> = when (this) {
    is Outcome.Ok -> Outcome.Ok(Unit)
    is Outcome.Err -> this
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Ok)?.value

fun <T> Outcome<T>.errorOrNull(): ApiException? = (this as? Outcome.Err)?.error

/** The JSON contract, shared by Retrofit, stored state, and error parsing. */
object ApiJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }
}

/**
 * Runs an API call and turns anything it throws into an [ApiException].
 *
 * A non-2xx response carries Laravel's error body, so the message people see
 * is the one the server wrote — including the per-field messages behind a 422.
 */
suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (failure: HttpException) {
        Outcome.Err(failure.toApiException())
    } catch (failure: IOException) {
        Outcome.Err(ApiException.offline(failure))
    } catch (failure: SerializationException) {
        // A 200 whose body does not parse: a truncated response, or a field
        // that changed shape. The parser's own message names a byte offset,
        // which means nothing to anyone reading it on a phone — it goes to the
        // cause for a bug report instead.
        Outcome.Err(ApiException.malformed(failure))
    } catch (failure: Exception) {
        Outcome.Err(ApiException.unknown(failure))
    }

private fun HttpException.toApiException(): ApiException {
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    val parsed = body
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { ApiJson.instance.decodeFromString<ApiErrorBody>(it) }.getOrNull() }

    return ApiException(
        status = code(),
        message = parsed?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(code()),
        errors = parsed?.errors ?: emptyMap(),
        cause = this,
    )
}

private fun defaultMessage(status: Int): String = when (status) {
    401 -> "Your session has expired. Sign in again."
    403 -> "You do not have access to that."
    404 -> "That is no longer there."
    413 -> "That image is too large to upload."
    419, 429 -> "Too many attempts. Give it a moment."
    in 500..599 -> "The server had a problem. Try again shortly."
    else -> "Something went wrong."
}
