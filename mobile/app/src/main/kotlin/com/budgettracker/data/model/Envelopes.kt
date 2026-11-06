package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The API wraps a single resource in `data` and a collection in `data` plus
 * `meta`. The envelopes are modelled rather than unwrapped by an interceptor
 * so the `meta` block — which carries the running totals and balances the
 * screens show above a list — is not thrown away on the way through.
 */
@Serializable
data class DataEnvelope<T>(
    val data: T,
)

@Serializable
data class ListEnvelope<T>(
    val data: List<T> = emptyList(),
)

@Serializable
data class MessageResponse(
    val message: String? = null,
)

/** Laravel's pagination block, trimmed to the fields the lists use. */
@Serializable
data class PageMeta(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
    val total: Int = 0,
) {
    val hasMore: Boolean get() = currentPage < lastPage
}
