package com.budgettracker.core.network

import kotlinx.serialization.Serializable

/** Laravel's error body: a sentence, plus per-field messages on a 422. */
@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val errors: Map<String, List<String>> = emptyMap(),
)

/**
 * Every failure a repository can return, as one type.
 *
 * `status` is the HTTP status, or `0` when the request never reached the server
 * — the distinction matters because an offline failure is worth retrying and a
 * 422 is not.
 */
class ApiException(
    val status: Int,
    override val message: String,
    val errors: Map<String, List<String>> = emptyMap(),
    cause: Throwable? = null,
) : Exception(message, cause) {

    val isValidation: Boolean get() = status == 422
    val isUnauthorized: Boolean get() = status == 401
    val isNotFound: Boolean get() = status == 404
    val isOffline: Boolean get() = status == 0
    val isMalformed: Boolean get() = status == MALFORMED
    val isServerFault: Boolean get() = status >= 500

    /** Worth offering a retry: nothing the person did can fix these. */
    val isRetryable: Boolean get() = isOffline || isMalformed || isServerFault

    /** First message for a field, for rendering under its input. */
    fun field(name: String): String? = errors[name]?.firstOrNull()

    companion object {
        const val OFFLINE_MESSAGE = "No connection. Check your network and try again."
        const val MALFORMED_MESSAGE = "The server's reply was incomplete. Try again."

        /** A response that never arrived, or a connection that was not made. */
        private const val MALFORMED = -2

        fun offline(cause: Throwable? = null) =
            ApiException(status = 0, message = OFFLINE_MESSAGE, cause = cause)

        /** A 2xx whose body could not be read — usually a cut-short response. */
        fun malformed(cause: Throwable? = null) =
            ApiException(status = MALFORMED, message = MALFORMED_MESSAGE, cause = cause)

        fun unknown(cause: Throwable? = null) =
            ApiException(
                status = -1,
                message = cause?.message?.takeIf { it.isNotBlank() } ?: "Something went wrong.",
                cause = cause,
            )
    }
}
