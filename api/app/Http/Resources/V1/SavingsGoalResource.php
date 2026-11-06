<?php

namespace App\Http\Resources\V1;

use App\Models\SavingsGoal;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin SavingsGoal
 */
class SavingsGoalResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'currency_code' => $this->currency_code,
            'target_amount_minor' => $this->target_amount_minor,
            'current_amount_minor' => $this->current_amount_minor,
            'remaining_minor' => $this->remaining()->minor,
            'progress' => $this->progress(),
            'is_achieved' => $this->isAchieved(),
            'achieved_at' => $this->achieved_at?->toIso8601String(),
            'target_date' => $this->target_date?->toDateString(),
            'suggested_monthly_minor' => $this->suggestedMonthlyContribution()?->minor,
            'formatted' => [
                'target' => $this->target()->format(),
                'saved' => $this->savedAmount()->format(),
                'remaining' => $this->remaining()->format(),
                'suggested_monthly' => $this->suggestedMonthlyContribution()?->format(),
            ],
            'color' => $this->color,
            'icon' => $this->icon,
            'note' => $this->note,
            'is_archived' => (bool) $this->is_archived,
            'sort_order' => $this->sort_order,
            'account_id' => $this->account_id,
            'account' => new AccountResource($this->whenLoaded('account')),
            'contributions' => SavingsContributionResource::collection($this->whenLoaded('contributions')),
            'contributions_count' => $this->whenCounted('contributions'),
        ];
    }
}
