<?php

namespace App\Models;

use App\Support\Money;
use Database\Factories\TransactionSplitFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['category_id', 'amount_minor', 'note', 'sort_order'])]
class TransactionSplit extends Model
{
    /** @use HasFactory<TransactionSplitFactory> */
    use HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'amount_minor' => 'integer',
        ];
    }

    public function transaction(): BelongsTo
    {
        return $this->belongsTo(Transaction::class);
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function amount(): Money
    {
        return Money::ofMinor($this->amount_minor, $this->transaction?->currency_code);
    }
}
