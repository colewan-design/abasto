<?php

namespace App\Models;

use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\SavingsGoalFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable([
    'account_id',
    'name',
    'currency_code',
    'target_amount_minor',
    'target_date',
    'color',
    'icon',
    'note',
    'is_archived',
    'sort_order',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class SavingsGoal extends Model
{
    /** @use HasFactory<SavingsGoalFactory> */
    use BelongsToUser, HasFactory;

    /**
     * Mirrors the column defaults so a freshly created goal reports a saved
     * amount of zero instead of null.
     *
     * @var array<string, mixed>
     */
    protected $attributes = [
        'current_amount_minor' => 0,
    ];

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'target_amount_minor' => 'integer',
            'current_amount_minor' => 'integer',
            'target_date' => 'date',
            'achieved_at' => 'datetime',
            'is_archived' => 'boolean',
        ];
    }

    public function account(): BelongsTo
    {
        return $this->belongsTo(Account::class);
    }

    public function contributions(): HasMany
    {
        return $this->hasMany(SavingsContribution::class);
    }

    public function transactions(): HasMany
    {
        return $this->hasMany(Transaction::class);
    }

    #[Scope]
    protected function active(Builder $query): void
    {
        $query->where('is_archived', false);
    }

    public function target(): Money
    {
        return Money::ofMinor($this->target_amount_minor, $this->currency_code);
    }

    public function savedAmount(): Money
    {
        return Money::ofMinor($this->current_amount_minor, $this->currency_code);
    }

    public function remaining(): Money
    {
        return Money::ofMinor(
            max(0, $this->target_amount_minor - $this->current_amount_minor),
            $this->currency_code,
        );
    }

    public function progress(): float
    {
        if ($this->target_amount_minor <= 0) {
            return 0.0;
        }

        return round(min(1, $this->current_amount_minor / $this->target_amount_minor), 4);
    }

    public function isAchieved(): bool
    {
        return $this->current_amount_minor >= $this->target_amount_minor;
    }

    /**
     * How much needs to be set aside each month to hit the target date.
     */
    public function suggestedMonthlyContribution(): ?Money
    {
        if ($this->target_date === null || $this->isAchieved()) {
            return null;
        }

        $months = max(1, (int) ceil(today()->diffInMonths($this->target_date, false)));

        return Money::ofMinor((int) ceil($this->remaining()->minor / $months), $this->currency_code);
    }
}
