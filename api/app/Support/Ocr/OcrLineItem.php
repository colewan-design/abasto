<?php

namespace App\Support\Ocr;

/**
 * One line read off a receipt.
 */
final class OcrLineItem
{
    public function __construct(
        public readonly string $description,
        public readonly int $totalMinor,
        public readonly float $quantity = 1.0,
        public readonly ?int $unitPriceMinor = null,
        public readonly float $confidence = 0.5,
    ) {}

    /**
     * @return array{description: string, total_minor: int, quantity: float, unit_price_minor: int|null, confidence: float}
     */
    public function toArray(): array
    {
        return [
            'description' => $this->description,
            'total_minor' => $this->totalMinor,
            'quantity' => $this->quantity,
            'unit_price_minor' => $this->unitPriceMinor,
            'confidence' => $this->confidence,
        ];
    }
}
