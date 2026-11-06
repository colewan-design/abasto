package com.budgettracker.feature.receipts

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.data.model.Receipt
import com.budgettracker.data.repository.ReceiptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptsUiState(
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val uploadStage: String? = null,
    val error: ApiException? = null,
    val message: String? = null,
    val receipts: List<Receipt> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    /** Set when an upload finishes, so the screen can open the review. */
    val readyToReview: Long? = null,
) {
    val awaitingReview: List<Receipt> get() = receipts.filter { it.isReviewable }
    val settled: List<Receipt> get() = receipts.filterNot { it.isReviewable }
}

/**
 * Scanning and the list of scans.
 *
 * A capture is three steps — prepare the image, read it on-device, upload it —
 * and each is reported so a scan on a slow connection does not look stuck.
 */
@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    private val receipts: ReceiptRepository,
    private val preparer: ReceiptImagePreparer,
    private val recognizer: ReceiptTextRecognizer,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptsUiState())
    val state: StateFlow<ReceiptsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(reset: Boolean = true) {
        viewModelScope.launch {
            val page = if (reset) 1 else _state.value.page + 1

            _state.update { it.copy(loading = reset && it.receipts.isEmpty(), error = null) }

            when (val outcome = receipts.list(page = page)) {
                is Outcome.Ok -> _state.update { current ->
                    current.copy(
                        loading = false,
                        receipts = if (reset) {
                            outcome.value.data
                        } else {
                            current.receipts + outcome.value.data
                        },
                        page = outcome.value.meta.currentPage,
                        hasMore = outcome.value.meta.hasMore,
                    )
                }

                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    fun loadMore() {
        if (_state.value.hasMore && !_state.value.loading) load(reset = false)
    }

    /**
     * The whole capture path, from a photo on disk to a draft worth reviewing.
     */
    fun scan(context: Context, image: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, uploadStage = "Preparing the photo…") }

            val prepared = preparer.prepare(context, image)

            if (prepared == null) {
                _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = null,
                        message = "That image could not be read. Try taking the photo again.",
                    )
                }

                return@launch
            }

            _state.update { it.copy(uploadStage = "Reading the receipt…") }

            val text = recognizer.recognize(context, prepared)

            _state.update { it.copy(uploadStage = "Uploading…") }

            when (val outcome = receipts.upload(prepared, text)) {
                is Outcome.Ok -> {
                    val receipt = outcome.value

                    // Extraction normally runs inline, but if the server queued
                    // it instead, wait briefly rather than opening a blank review.
                    val settled = if (receipt.status.isWorking) {
                        receipts.awaitExtraction(receipt.id).let { polled ->
                            (polled as? Outcome.Ok)?.value ?: receipt
                        }
                    } else {
                        receipt
                    }

                    _state.update {
                        it.copy(
                            uploading = false,
                            uploadStage = null,
                            readyToReview = settled.id,
                        )
                    }

                    preparer.clearOldFiles(context)
                    load()
                }

                is Outcome.Err -> _state.update {
                    it.copy(
                        uploading = false,
                        uploadStage = null,
                        message = outcome.error.message,
                    )
                }
            }
        }
    }

    fun reviewOpened() = _state.update { it.copy(readyToReview = null) }

    fun delete(receipt: Receipt) {
        viewModelScope.launch {
            when (val outcome = receipts.delete(receipt.id)) {
                is Outcome.Ok -> _state.update { current ->
                    current.copy(
                        receipts = current.receipts.filterNot { it.id == receipt.id },
                        message = "Receipt deleted.",
                    )
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
