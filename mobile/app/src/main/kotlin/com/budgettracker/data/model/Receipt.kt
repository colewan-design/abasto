package com.budgettracker.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReceiptStatus {
    @SerialName("pending") Pending,
    @SerialName("processing") Processing,
    @SerialName("processed") Processed,
    @SerialName("failed") Failed,
    @SerialName("confirmed") Confirmed,
    ;

    val isWorking: Boolean get() = this == Pending || this == Processing
}

@Serializable
data class ReceiptItem(
    val id: Long,
    @SerialName("line_no") val lineNo: Int = 0,
    val description: String = "",
    val quantity: Double = 1.0,
    @SerialName("unit_price_minor") val unitPriceMinor: Long? = null,
    @SerialName("total_minor") val totalMinor: Long = 0,
    val formatted: String = "",
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("suggested_category_id") val suggestedCategoryId: Long? = null,
    @SerialName("effective_category_id") val effectiveCategoryId: Long? = null,
    val category: Category? = null,
    @SerialName("suggested_category") val suggestedCategory: Category? = null,
    val confidence: Double? = null,
    @SerialName("needs_review") val needsReview: Boolean = false,
)

@Serializable
data class ReceiptFormatted(
    val subtotal: String? = null,
    val tax: String? = null,
    val total: String? = null,
)

/**
 * Why a field is flagged. `needs_review` lists the field names the parser was
 * not sure about, which is what drives the highlighting on the review screen —
 * the app never silently accepts a low-confidence total.
 */
@Serializable
data class ReceiptExtraction(
    val driver: String? = null,
    val confidence: Double? = null,
    @SerialName("field_confidence") val fieldConfidence: Map<String, Double> = emptyMap(),
    @SerialName("needs_review") val needsReview: List<String> = emptyList(),
    @SerialName("failure_reason") val failureReason: String? = null,
)

@Serializable
data class ReceiptDuplicate(
    @SerialName("is_duplicate") val isDuplicate: Boolean = false,
    val score: Double? = null,
    val reason: String? = null,
    @SerialName("receipt_id") val receiptId: Long? = null,
    @SerialName("transaction_id") val transactionId: Long? = null,
)

@Serializable
data class Receipt(
    val id: Long,
    val status: ReceiptStatus = ReceiptStatus.Pending,
    @SerialName("is_reviewable") val isReviewable: Boolean = false,
    val merchant: String? = null,
    @SerialName("receipt_date") val receiptDate: String? = null,
    @SerialName("currency_code") val currencyCode: String = "PHP",
    @SerialName("subtotal_minor") val subtotalMinor: Long? = null,
    @SerialName("tax_minor") val taxMinor: Long? = null,
    @SerialName("tip_minor") val tipMinor: Long? = null,
    @SerialName("discount_minor") val discountMinor: Long? = null,
    @SerialName("total_minor") val totalMinor: Long? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val formatted: ReceiptFormatted = ReceiptFormatted(),
    val items: List<ReceiptItem> = emptyList(),
    @SerialName("items_count") val itemsCount: Int? = null,
    val extraction: ReceiptExtraction = ReceiptExtraction(),
    val duplicate: ReceiptDuplicate = ReceiptDuplicate(),
    @SerialName("transaction_id") val transactionId: Long? = null,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("confirmed_at") val confirmedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val title: String get() = merchant?.takeIf { it.isNotBlank() } ?: "Receipt"

    fun flagged(field: String): Boolean = field in extraction.needsReview
}

@Serializable
data class ReceiptPage(
    val data: List<Receipt> = emptyList(),
    val meta: PageMeta = PageMeta(),
)

/**
 * How the receipt's own lines group into category splits, computed by the
 * server from the per-item categories. The review screen offers this as the
 * split to save rather than asking the user to add up lines by hand.
 */
@Serializable
data class SuggestedSplit(
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val items: Int = 0,
)

@Serializable
data class ReceiptMeta(
    @SerialName("suggested_category") val suggestedCategory: CategorySuggestion? = null,
    @SerialName("suggested_split") val suggestedSplit: List<SuggestedSplit> = emptyList(),
)

@Serializable
data class ReceiptDetail(
    val data: Receipt,
    val meta: ReceiptMeta = ReceiptMeta(),
)

/** The confirm endpoint returns the transaction it created and the receipt. */
@Serializable
data class ReceiptConfirmation(
    val message: String? = null,
    val transaction: Transaction,
    val receipt: Receipt? = null,
)
