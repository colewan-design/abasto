<?php

namespace App\Http\Resources\V1;

use App\Models\Bill;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin Bill
 */
class BillResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'payee' => $this->payee,
            'amount_minor' => $this->amount_minor,
            'currency_code' => $this->currency_code,
            'formatted_amount' => $this->amount()->format(),
            'due_date' => $this->due_date->toDateString(),
            'days_until_due' => $this->daysUntilDue(),
            'is_overdue' => $this->isOverdue(),
            'next_due_date' => $this->nextDueDate()?->toDateString(),
            'recurrence' => $this->recurrence->value,
            'recurrence_label' => $this->recurrence->label(),
            'recurrence_interval' => $this->recurrence_interval,
            'reminder_days_before' => $this->reminder_days_before,
            'auto_pay' => (bool) $this->auto_pay,
            'is_active' => (bool) $this->is_active,
            'note' => $this->note,
            'last_paid_on' => $this->last_paid_on?->toDateString(),
            'account_id' => $this->account_id,
            'account' => new AccountResource($this->whenLoaded('account')),
            'category_id' => $this->category_id,
            'category' => new CategoryResource($this->whenLoaded('category')),
            'payments' => BillPaymentResource::collection($this->whenLoaded('payments')),
        ];
    }
}
