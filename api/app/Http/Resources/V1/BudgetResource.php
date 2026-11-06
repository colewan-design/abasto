<?php

namespace App\Http\Resources\V1;

use App\Models\Budget;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin Budget
 */
class BudgetResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'period' => $this->period,
            'period_start' => $this->period_start->toDateString(),
            'period_end' => $this->period_end->toDateString(),
            'label' => $this->period_start->format('F Y'),
            'currency_code' => $this->currency_code,
            'overall_amount_minor' => $this->overall_amount_minor,
            'rollover_enabled' => (bool) $this->rollover_enabled,
            'alert_threshold' => $this->alert_threshold,
            'is_current' => $this->isCurrent(),
            'categories' => $this->whenLoaded('categories', fn () => $this->categories->map(fn ($line): array => [
                'id' => $line->id,
                'category_id' => $line->category_id,
                'category' => new CategoryResource($line->category),
                'amount_minor' => $line->amount_minor,
                'rollover_minor' => $line->rollover_minor,
                'limit_minor' => $line->limitMinor(),
                'alert_threshold' => $line->threshold(),
            ])),
        ];
    }
}
