<?php

namespace App\Http\Resources\V1;

use App\Models\BillPayment;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin BillPayment
 */
class BillPaymentResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'bill_id' => $this->bill_id,
            'period_due_date' => $this->period_due_date->toDateString(),
            'amount_minor' => $this->amount_minor,
            'formatted_amount' => $this->amount()->format(),
            'paid_on' => $this->paid_on?->toDateString(),
            'status' => $this->status->value,
            'transaction_id' => $this->transaction_id,
            'transaction' => new TransactionResource($this->whenLoaded('transaction')),
        ];
    }
}
