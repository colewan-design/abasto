<?php

namespace App\Models;

use App\Enums\BillPaymentStatus;
use App\Support\Money;
use Database\Factories\BillPaymentFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['transaction_id', 'period_due_date', 'amount_minor', 'paid_on', 'status'])]
class BillPayment extends Model
{
    /** @use HasFactory<BillPaymentFactory> */
    use HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'status' => BillPaymentStatus::class,
            'amount_minor' => 'integer',
            'period_due_date' => 'date',
            'paid_on' => 'date',
        ];
    }

    public function bill(): BelongsTo
    {
        return $this->belongsTo(Bill::class);
    }

    /**
     * The recorded expense this payment is linked to, which is what keeps a
     * paid bill from being entered twice.
     */
    public function transaction(): BelongsTo
    {
        return $this->belongsTo(Transaction::class);
    }

    public function amount(): Money
    {
        return Money::ofMinor($this->amount_minor, $this->bill?->currency_code);
    }
}
