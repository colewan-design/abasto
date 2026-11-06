<?php

namespace App\Http\Resources\V1;

use App\Models\RecurringRule;
use App\Services\RecurringRuleRunner;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin RecurringRule
 */
class RecurringRuleResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'type' => $this->type->value,
            'amount_minor' => $this->amount_minor,
            'currency_code' => $this->currency_code,
            'formatted_amount' => $this->amount()->format(),
            'merchant' => $this->merchant,
            'note' => $this->note,
            'frequency' => $this->frequency->value,
            'frequency_label' => $this->frequency->label(),
            'interval' => $this->interval,
            'by_month_day' => $this->by_month_day,
            'by_weekday' => $this->by_weekday,
            'start_date' => $this->start_date->toDateString(),
            'end_date' => $this->end_date?->toDateString(),
            'occurrence_limit' => $this->occurrence_limit,
            'occurrences_created' => $this->occurrences_created,
            'next_run_on' => $this->next_run_on?->toDateString(),
            'last_run_on' => $this->last_run_on?->toDateString(),
            'upcoming_dates' => app(RecurringRuleRunner::class)->preview($this->resource),
            'auto_post' => (bool) $this->auto_post,
            'is_active' => (bool) $this->is_active,
            'account_id' => $this->account_id,
            'account' => new AccountResource($this->whenLoaded('account')),
            'transfer_account_id' => $this->transfer_account_id,
            'category_id' => $this->category_id,
            'category' => new CategoryResource($this->whenLoaded('category')),
        ];
    }
}
