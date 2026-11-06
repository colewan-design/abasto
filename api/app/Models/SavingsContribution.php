<?php

namespace App\Models;

use App\Support\Money;
use Database\Factories\SavingsContributionFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['transaction_id', 'amount_minor', 'contributed_on', 'note'])]
class SavingsContribution extends Model
{
    /** @use HasFactory<SavingsContributionFactory> */
    use HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'amount_minor' => 'integer',
            'contributed_on' => 'date',
        ];
    }

    public function goal(): BelongsTo
    {
        return $this->belongsTo(SavingsGoal::class, 'savings_goal_id');
    }

    public function transaction(): BelongsTo
    {
        return $this->belongsTo(Transaction::class);
    }

    public function amount(): Money
    {
        return Money::ofMinor($this->amount_minor, $this->goal?->currency_code);
    }
}
