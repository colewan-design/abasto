<?php

namespace App\Services\Receipts;

use App\Enums\ReceiptStatus;
use App\Models\Receipt;
use App\Services\CategorySuggester;
use App\Services\Ocr\OcrManager;
use App\Support\Ocr\OcrLineItem;
use App\Support\Ocr\OcrResult;
use Illuminate\Support\Facades\DB;
use Throwable;

/**
 * Runs a stored receipt image through OCR, category suggestion and duplicate
 * detection, leaving it ready for the review screen.
 */
class ReceiptProcessor
{
    public function __construct(
        private readonly ReceiptStorage $storage,
        private readonly OcrManager $ocr,
        private readonly CategorySuggester $suggester,
        private readonly DuplicateDetector $duplicates,
    ) {}

    public function process(Receipt $receipt): Receipt
    {
        if (! $this->storage->exists($receipt)) {
            return $this->fail($receipt, 'The receipt image is no longer available.');
        }

        $receipt->forceFill(['status' => ReceiptStatus::Processing->value])->saveQuietly();

        try {
            $result = $this->ocr->extract(
                $this->storage->contents($receipt),
                $receipt->mime_type ?? 'image/jpeg',
                $receipt->raw_text,
            );
        } catch (Throwable $exception) {
            return $this->fail($receipt, $exception->getMessage());
        }

        return DB::transaction(function () use ($receipt, $result): Receipt {
            $this->writeFields($receipt, $result);
            $this->writeItems($receipt, $result);

            $receipt->forceFill([
                'status' => ReceiptStatus::Processed->value,
                'processed_at' => now(),
                'failure_reason' => null,
            ])->saveQuietly();

            return $this->duplicates->apply($receipt->refresh())->load('items.suggestedCategory');
        });
    }

    private function writeFields(Receipt $receipt, OcrResult $result): void
    {
        $currency = $result->currency ?? $receipt->currency_code ?? $receipt->user->currency_code;

        $receipt->fill([
            'merchant' => $result->merchant ?? $receipt->merchant,
            'receipt_date' => $result->receiptDate ?? $receipt->receipt_date,
            'currency_code' => $currency,
            'subtotal_minor' => $result->subtotalMinor,
            'tax_minor' => $result->taxMinor,
            'tip_minor' => $result->tipMinor,
            'discount_minor' => $result->discountMinor,
            'total_minor' => $result->totalMinor,
            'payment_method' => $result->paymentMethod,
            'raw_text' => $result->rawText ?? $receipt->raw_text,
        ]);

        $receipt->forceFill([
            'ocr_driver' => $result->driver,
            'ocr_confidence' => $result->overallConfidence(),
            'field_confidence' => $result->confidence,
            'extracted' => $result->toArray(),
        ])->save();
    }

    /**
     * Store the line items with a suggested category each, so a mixed purchase
     * arrives at the review screen already split along sensible lines.
     */
    private function writeItems(Receipt $receipt, OcrResult $result): void
    {
        $receipt->items()->delete();

        if ($result->items === []) {
            return;
        }

        $suggestions = $this->suggester->suggestForItems(
            $receipt->user,
            array_map(fn (OcrLineItem $item): string => $item->description, $result->items),
            $result->merchant,
        );

        foreach ($result->items as $index => $item) {
            $receipt->items()->create([
                'line_no' => $index,
                'description' => $item->description,
                'quantity' => $item->quantity,
                'unit_price_minor' => $item->unitPriceMinor,
                'total_minor' => $item->totalMinor,
                'suggested_category_id' => $suggestions[$index]['category_id'] ?? null,
                'confidence' => max($item->confidence, $suggestions[$index]['confidence'] ?? 0),
            ]);
        }
    }

    private function fail(Receipt $receipt, string $reason): Receipt
    {
        $receipt->forceFill([
            'status' => ReceiptStatus::Failed->value,
            'failure_reason' => $reason,
            'processed_at' => now(),
        ])->saveQuietly();

        return $receipt;
    }
}
