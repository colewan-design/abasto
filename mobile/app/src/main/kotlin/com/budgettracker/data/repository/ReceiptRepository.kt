package com.budgettracker.data.repository

import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.core.network.discard
import com.budgettracker.data.model.Receipt
import com.budgettracker.data.model.ReceiptConfirmation
import com.budgettracker.data.model.ReceiptDetail
import com.budgettracker.data.model.ReceiptPage
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.ConfirmReceiptRequest
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val api: BudgetApi,
    private val events: LedgerEvents,
) {

    suspend fun list(awaitingReview: Boolean = false, page: Int = 1): Outcome<ReceiptPage> =
        apiCall {
            api.receipts(
                awaitingReview = awaitingReview.takeIf { it },
                perPage = PAGE_SIZE,
                page = page,
            )
        }

    suspend fun detail(id: Long): Outcome<ReceiptDetail> =
        apiCall { api.receipt(id) }

    /**
     * Uploads the photo together with the text ML Kit read off it on-device.
     *
     * Extraction runs inline so the review screen has a draft to show the
     * moment the upload finishes, rather than polling a queued job — and with
     * the recognised text already attached, the server has no OCR call to make.
     */
    suspend fun upload(image: File, recognizedText: String?): Outcome<Receipt> = apiCall {
        val mime = when (image.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            else -> "image/jpeg"
        }

        api.uploadReceipt(
            image = MultipartBody.Part.createFormData(
                name = "image",
                filename = image.name,
                body = image.asRequestBody(mime.toMediaTypeOrNull()),
            ),
            recognizedText = recognizedText
                ?.takeIf { it.isNotBlank() }
                ?.take(MAX_TEXT_CHARS)
                ?.toPlainPart(),
            capturedAt = Instant.now().toString().toPlainPart(),
            process = "1".toPlainPart(),
        ).data
    }

    /**
     * Waits for a receipt that is still being read. Used when extraction was
     * queued rather than run inline; gives up rather than spinning forever so
     * the screen can offer a manual entry instead.
     */
    suspend fun awaitExtraction(id: Long, attempts: Int = 5): Outcome<Receipt> {
        repeat(attempts) { attempt ->
            when (val outcome = detail(id)) {
                is Outcome.Err -> return outcome
                is Outcome.Ok -> {
                    val receipt = outcome.value.data

                    if (!receipt.status.isWorking) return Outcome.Ok(receipt)
                }
            }

            delay(POLL_DELAY_MS * (attempt + 1))
        }

        return detail(id).let { outcome ->
            when (outcome) {
                is Outcome.Ok -> Outcome.Ok(outcome.value.data)
                is Outcome.Err -> outcome
            }
        }
    }

    suspend fun reprocess(id: Long): Outcome<Receipt> =
        apiCall { api.reprocessReceipt(id).data }

    /** Turns a reviewed draft into a real expense, splits and all. */
    suspend fun confirm(id: Long, request: ConfirmReceiptRequest): Outcome<ReceiptConfirmation> =
        apiCall { api.confirmReceipt(id, request) }
            .also { if (it is Outcome.Ok) events.signal() }

    suspend fun delete(id: Long): Outcome<Unit> =
        apiCall { api.deleteReceipt(id) }.discard()

    private fun String.toPlainPart(): RequestBody =
        toRequestBody("text/plain".toMediaTypeOrNull())

    companion object {
        const val PAGE_SIZE = 20
        private const val MAX_TEXT_CHARS = 20_000
        private const val POLL_DELAY_MS = 700L
    }
}
