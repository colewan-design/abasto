<?php

namespace App\Http\Resources\V1;

use App\Models\Account;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * The few account fields a nested reference needs — enough to label and colour
 * a row without loading balances.
 *
 * Transaction lists select a narrow set of account columns, so anything that
 * depends on a balance belongs in {@see AccountResource} instead.
 *
 * @mixin Account
 */
class AccountSummaryResource extends JsonResource
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
            'color' => $this->color,
            'icon' => $this->icon,
        ];
    }
}
