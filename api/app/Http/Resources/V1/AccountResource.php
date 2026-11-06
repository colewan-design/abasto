<?php

namespace App\Http\Resources\V1;

use App\Models\Account;
use App\Support\Money;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin Account
 */
class AccountResource extends JsonResource
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
            'type_label' => $this->type->label(),
            'is_liability' => $this->type->isLiability(),
            'currency_code' => $this->currency_code,
            'opening_balance_minor' => $this->opening_balance_minor,
            'current_balance_minor' => $this->current_balance_minor,
            'credit_limit_minor' => $this->credit_limit_minor,
            'available_credit_minor' => $this->availableCredit()?->minor,
            'institution' => $this->institution,
            'number_last4' => $this->number_last4,
            'color' => $this->color,
            'icon' => $this->icon,
            'include_in_total' => (bool) $this->include_in_total,
            'is_archived' => (bool) $this->is_archived,
            'sort_order' => $this->sort_order,
            'transactions_count' => $this->whenCounted('transactions'),
            'formatted' => [
                'balance' => $this->balance()->format(),
                'opening_balance' => Money::ofMinor($this->opening_balance_minor, $this->currency_code)->format(),
                'available_credit' => $this->availableCredit()?->format(),
            ],
            'updated_at' => $this->updated_at?->toIso8601String(),
        ];
    }
}
