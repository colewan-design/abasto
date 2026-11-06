package com.budgettracker.feature.receipts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Receipt
import com.budgettracker.data.model.ReceiptStatus
import com.budgettracker.ui.components.CentredBox
import com.budgettracker.ui.components.ChoiceRow
import com.budgettracker.ui.components.DetailScaffold
import com.budgettracker.ui.components.EmptyState
import com.budgettracker.ui.components.ErrorState
import com.budgettracker.ui.components.FullScreenLoader
import com.budgettracker.ui.components.IconBadge
import com.budgettracker.ui.components.Pill
import com.budgettracker.ui.components.SectionCard
import com.budgettracker.ui.components.SheetTitle
import com.budgettracker.ui.theme.money

/**
 * Scanned receipts: the ones still waiting to be checked, then the rest.
 *
 * A scan is never recorded automatically — the draft waits here until someone
 * confirms it, which is what keeps a misread total out of the ledger.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    onBack: () -> Unit,
    onOpenReview: (Long) -> Unit,
    viewModel: ReceiptsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var choosingSource by remember { mutableStateOf(false) }

    val capture = rememberReceiptCapture { uri ->
        choosingSource = false
        viewModel.scan(context, uri)
    }

    // An upload that finished has a draft worth looking at straight away.
    LaunchedEffect(state.readyToReview) {
        state.readyToReview?.let { id ->
            viewModel.reviewOpened()
            onOpenReview(id)
        }
    }

    DetailScaffold(
        title = "Receipts",
        onBack = onBack,
        message = state.message,
        onMessageShown = viewModel::messageShown,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { choosingSource = true },
                icon = { Icon(Icons.Outlined.AddAPhoto, contentDescription = null) },
                text = { Text("Scan a receipt") },
            )
        },
    ) { padding ->
        when {
            state.uploading -> CentredBox(padding = padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()

                    Text(
                        text = state.uploadStage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp),
                    )

                    Text(
                        text = "The text is read on this device, so only the photo and " +
                            "what it says are sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
                    )
                }
            }

            state.loading -> CentredBox(padding = padding) { FullScreenLoader() }

            state.receipts.isEmpty() && state.error != null -> CentredBox(padding = padding) {
                ErrorState(error = state.error!!, onRetry = { viewModel.load() })
            }

            state.receipts.isEmpty() -> CentredBox(padding = padding) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No receipts yet",
                    message = "Photograph a receipt and the merchant, date, total and line " +
                        "items are filled in for you to check.",
                    actionLabel = "Scan one now",
                    onAction = { choosingSource = true },
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.awaitingReview.isNotEmpty()) {
                    item {
                        Text(
                            text = "Waiting for you",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }

                    items(state.awaitingReview, key = { "review-${it.id}" }) { receipt ->
                        ReceiptCard(
                            receipt = receipt,
                            onClick = { onOpenReview(receipt.id) },
                        )
                    }
                }

                if (state.settled.isNotEmpty()) {
                    item {
                        Text(
                            text = "Earlier scans",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    items(state.settled, key = { "settled-${it.id}" }) { receipt ->
                        ReceiptCard(
                            receipt = receipt,
                            onClick = { onOpenReview(receipt.id) },
                        )
                    }
                }

                if (state.hasMore) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Load more")
                        }
                    }
                }
            }
        }
    }

    if (choosingSource) {
        ModalBottomSheet(
            onDismissRequest = { choosingSource = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                SheetTitle(title = "Add a receipt")

                if (capture.cameraAvailable) {
                    ChoiceRow(
                        label = "Take a photo",
                        secondary = "Point at the receipt — flat and well lit reads best",
                        selected = false,
                        onClick = capture::takePhoto,
                        leading = {
                            IconBadge(
                                icon = Icons.Outlined.PhotoCamera,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 36.dp,
                            )
                        },
                    )
                }

                ChoiceRow(
                    label = "Choose an existing photo",
                    secondary = "Pick one you already took",
                    selected = false,
                    onClick = capture::pickImage,
                    leading = {
                        IconBadge(
                            icon = Icons.Outlined.PhotoLibrary,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 36.dp,
                        )
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ReceiptCard(receipt: Receipt, onClick: () -> Unit) {
    SectionCard(padding = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(56.dp),
                ) {}

                AsyncImage(
                    model = receipt.thumbnailUrl ?: receipt.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(text = receipt.title, style = MaterialTheme.typography.titleSmall)

                Text(
                    text = listOfNotNull(
                        receipt.receiptDate?.let { Dates.relativeOrRaw(it) },
                        receipt.itemsCount?.takeIf { it > 0 }?.let { "$it items" },
                    ).joinToString(" · ").ifBlank { Dates.relativeOrRaw(receipt.createdAt) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                StatusPill(receipt)
            }

            Text(
                text = receipt.formatted.total ?: "—",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun StatusPill(receipt: Receipt) {
    val money = MaterialTheme.money

    when {
        receipt.duplicate.isDuplicate -> Pill(
            text = "Possible duplicate",
            container = money.warningContainer,
            content = money.warning,
        )

        receipt.status == ReceiptStatus.Confirmed -> Pill(
            text = "Recorded",
            container = money.positiveContainer,
            content = money.positive,
        )

        receipt.status == ReceiptStatus.Failed -> Pill(
            text = "Could not be read",
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )

        receipt.status.isWorking -> Pill(text = "Reading…")

        receipt.extraction.needsReview.isNotEmpty() -> Pill(
            text = "${receipt.extraction.needsReview.size} to check",
            container = money.warningContainer,
            content = money.warning,
        )

        else -> Pill(text = "Ready to review")
    }
}
