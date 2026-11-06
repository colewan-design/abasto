<?php

namespace App\Http\Resources\V1;

use App\Models\TransactionSplit;
use App\Support\Money;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin TransactionSplit
 */
class TransactionSplitResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        $currency = $this->transaction?->currency_code ?? $request->user()?->currency_code;

        return [
            'id' => $this->id,
            'category_id' => $this->category_id,
            'category' => new CategoryResource($this->whenLoaded('category')),
            'amount_minor' => $this->amount_minor,
            'formatted' => Money::ofMinor($this->amount_minor, $currency)->format(),
            'note' => $this->note,
            'sort_order' => $this->sort_order,
        ];
    }
}
