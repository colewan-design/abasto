<?php

namespace App\Http\Resources\V1;

use App\Models\Receipt;
use App\Support\Money;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;
use Illuminate\Support\Facades\URL;

/**
 * @mixin Receipt
 */
class ReceiptResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        $currency = $this->currency_code ?? $request->user()?->currency_code;
        $money = fn (?int $minor): ?string => $minor === null ? null : Money::ofMinor($minor, $currency)->format();

        return [
            'id' => $this->id,
            'status' => $this->status->value,
            'is_reviewable' => $this->status->isReviewable(),
            'merchant' => $this->merchant,
            'receipt_date' => $this->receipt_date?->toDateString(),
            'currency_code' => $currency,
            'subtotal_minor' => $this->subtotal_minor,
            'tax_minor' => $this->tax_minor,
            'tip_minor' => $this->tip_minor,
            'discount_minor' => $this->discount_minor,
            'total_minor' => $this->total_minor,
            'payment_method' => $this->payment_method,
            'formatted' => [
                'subtotal' => $money($this->subtotal_minor),
                'tax' => $money($this->tax_minor),
                'total' => $money($this->total_minor),
            ],
            'items' => ReceiptItemResource::collection($this->whenLoaded('items')),
            'items_count' => $this->whenCounted('items'),
            'extraction' => [
                'driver' => $this->ocr_driver,
                'confidence' => $this->ocr_confidence,
                'field_confidence' => $this->field_confidence ?? [],
                'needs_review' => $this->lowConfidenceFields(),
                'failure_reason' => $this->failure_reason,
            ],
            'duplicate' => [
                'is_duplicate' => $this->isDuplicate(),
                'score' => $this->duplicate_score,
                'reason' => $this->extracted['duplicate']['reason'] ?? null,
                'receipt_id' => $this->duplicate_of_receipt_id,
                'transaction_id' => $this->extracted['duplicate']['transaction_id'] ?? null,
            ],
            'suggestion' => $this->when(isset($this->suggestion), fn () => $this->suggestion),
            'transaction_id' => $this->whenLoaded('transactions', fn () => $this->transactions->first()?->getKey()),
            'image_url' => $this->signedImageUrl(),
            'thumbnail_url' => $this->thumbnail_path === null ? null : $this->signedImageUrl(thumbnail: true),
            'processed_at' => $this->processed_at?->toIso8601String(),
            'confirmed_at' => $this->confirmed_at?->toIso8601String(),
            'created_at' => $this->created_at?->toIso8601String(),
        ];
    }

    /**
     * Receipt images are private; the client is handed a short-lived signed URL
     * rather than a path on a public disk.
     */
    private function signedImageUrl(bool $thumbnail = false): string
    {
        return URL::temporarySignedRoute(
            'api.v1.receipts.image',
            now()->addHours(6),
            ['receipt' => $this->id, 'variant' => $thumbnail ? 'thumbnail' : 'original'],
        );
    }
}
