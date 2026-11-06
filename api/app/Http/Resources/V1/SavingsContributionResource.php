<?php

namespace App\Http\Resources\V1;

use App\Models\SavingsContribution;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin SavingsContribution
 */
class SavingsContributionResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'savings_goal_id' => $this->savings_goal_id,
            'amount_minor' => $this->amount_minor,
            'formatted_amount' => $this->amount()->format(),
            'contributed_on' => $this->contributed_on->toDateString(),
            'note' => $this->note,
            'transaction_id' => $this->transaction_id,
            'created_at' => $this->created_at?->toIso8601String(),
        ];
    }
}
