<?php

namespace App\Support\Ocr;

/**
 * What an OCR driver managed to read from a receipt image.
 *
 * `confidence` is per field, keyed by the same names the API exposes
 * (`merchant`, `receipt_date`, `total_minor`, …). The review screen highlights
 * anything below `budget.receipts.low_confidence_threshold` so the user checks
 * the fields the extractor was unsure about instead of re-reading all of them.
 */
final class OcrResult
{
    /**
     * @param  array<int, OcrLineItem>  $items
     * @param  array<string, float>  $confidence
     */
    public function __construct(
        public readonly string $driver,
        public readonly ?string $merchant = null,
        public readonly ?string $receiptDate = null,
        public readonly ?string $currency = null,
        public readonly ?int $subtotalMinor = null,
        public readonly ?int $taxMinor = null,
        public readonly ?int $tipMinor = null,
        public readonly ?int $discountMinor = null,
        public readonly ?int $totalMinor = null,
        public readonly ?string $paymentMethod = null,
        public readonly array $items = [],
        public readonly ?string $rawText = null,
        public readonly array $confidence = [],
    ) {}

    public static function empty(string $driver, ?string $rawText = null): self
    {
        return new self(driver: $driver, rawText: $rawText);
    }

    public function overallConfidence(): float
    {
        if ($this->confidence === []) {
            return 0.0;
        }

        return round(array_sum($this->confidence) / count($this->confidence), 3);
    }

    /**
     * True when the result is too thin to be worth showing as a draft.
     */
    public function isEmpty(): bool
    {
        return $this->merchant === null && $this->totalMinor === null && $this->items === [];
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        return [
            'driver' => $this->driver,
            'merchant' => $this->merchant,
            'receipt_date' => $this->receiptDate,
            'currency' => $this->currency,
            'subtotal_minor' => $this->subtotalMinor,
            'tax_minor' => $this->taxMinor,
            'tip_minor' => $this->tipMinor,
            'discount_minor' => $this->discountMinor,
            'total_minor' => $this->totalMinor,
            'payment_method' => $this->paymentMethod,
            'items' => array_map(fn (OcrLineItem $item): array => $item->toArray(), $this->items),
            'confidence' => $this->confidence,
        ];
    }
}
