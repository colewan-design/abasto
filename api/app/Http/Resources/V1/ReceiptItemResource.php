<?php

namespace App\Http\Resources\V1;

use App\Models\ReceiptItem;
use App\Support\Money;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin ReceiptItem
 */
class ReceiptItemResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        $currency = $this->receipt?->currency_code ?? $request->user()?->currency_code;

        return [
            'id' => $this->id,
            'line_no' => $this->line_no,
            'description' => $this->description,
            'quantity' => $this->quantity,
            'unit_price_minor' => $this->unit_price_minor,
            'total_minor' => $this->total_minor,
            'formatted' => Money::ofMinor($this->total_minor, $currency)->format(),
            'category_id' => $this->category_id,
            'suggested_category_id' => $this->suggested_category_id,
            'effective_category_id' => $this->effectiveCategoryId(),
            'category' => new CategoryResource($this->whenLoaded('category')),
            'suggested_category' => new CategoryResource($this->whenLoaded('suggestedCategory')),
            'confidence' => $this->confidence,
            'needs_review' => $this->effectiveCategoryId() === null
                || ($this->confidence ?? 0) < (float) config('budget.receipts.low_confidence_threshold'),
        ];
    }
}
