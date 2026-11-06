<?php

namespace App\Http\Resources\V1;

use App\Enums\TransactionType;
use App\Models\Transaction;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin Transaction
 */
class TransactionResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'type' => $this->type->value,
            'direction' => $this->direction->value,
            'is_transfer' => $this->type === TransactionType::Transfer,
            'is_savings_allocation' => $this->savings_goal_id !== null,
            'amount_minor' => $this->amount_minor,
            'signed_amount_minor' => $this->signedMinor(),
            'currency_code' => $this->currency_code,
            'formatted_amount' => $this->amount()->format(),
            'occurred_on' => $this->occurred_on->toDateString(),
            'occurred_at' => $this->occurred_at?->toIso8601String(),
            'merchant' => $this->merchant,
            'note' => $this->note,
            'account_id' => $this->account_id,
            'account' => new AccountSummaryResource($this->whenLoaded('account')),
            'transfer_account_id' => $this->transfer_account_id,
            'transfer_account' => new AccountSummaryResource($this->whenLoaded('transferAccount')),
            'transfer_group' => $this->transfer_group,
            'category_id' => $this->category_id,
            'category' => new CategoryResource($this->whenLoaded('category')),
            'tags' => TagResource::collection($this->whenLoaded('tags')),
            'has_splits' => (bool) $this->has_splits,
            'splits' => TransactionSplitResource::collection($this->whenLoaded('splits')),
            'receipt_id' => $this->receipt_id,
            'receipt' => new ReceiptResource($this->whenLoaded('receipt')),
            'bill_id' => $this->bill_id,
            'savings_goal_id' => $this->savings_goal_id,
            'recurring_rule_id' => $this->recurring_rule_id,
            'created_via' => $this->created_via->value,
            'created_at' => $this->created_at?->toIso8601String(),
            'updated_at' => $this->updated_at?->toIso8601String(),
        ];
    }
}
