<?php

namespace App\Models;

use App\Enums\AccountType;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\AccountFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable([
    'name',
    'type',
    'currency_code',
    'opening_balance_minor',
    'credit_limit_minor',
    'institution',
    'number_last4',
    'color',
    'icon',
    'include_in_total',
    'is_archived',
    'sort_order',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Account extends Model
{
    /** @use HasFactory<AccountFactory> */
    use BelongsToUser, HasFactory;

    /**
     * Mirrors the column defaults so a freshly created account reports a
     * balance of zero instead of null.
     *
     * @var array<string, mixed>
     */
    protected $attributes = [
        'opening_balance_minor' => 0,
        'current_balance_minor' => 0,
    ];

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'type' => AccountType::class,
            'opening_balance_minor' => 'integer',
            'current_balance_minor' => 'integer',
            'credit_limit_minor' => 'integer',
            'include_in_total' => 'boolean',
            'is_archived' => 'boolean',
        ];
    }

    public function transactions(): HasMany
    {
        return $this->hasMany(Transaction::class);
    }

    public function incomingTransfers(): HasMany
    {
        return $this->hasMany(Transaction::class, 'transfer_account_id');
    }

    public function savingsGoals(): HasMany
    {
        return $this->hasMany(SavingsGoal::class);
    }

    #[Scope]
    protected function active(Builder $query): void
    {
        $query->where('is_archived', false);
    }

    public function balance(): Money
    {
        return Money::ofMinor($this->current_balance_minor, $this->currency_code);
    }

    /**
     * Credit limit minus the outstanding balance, for credit card accounts.
     */
    public function availableCredit(): ?Money
    {
        if (! $this->type->isLiability() || $this->credit_limit_minor === null) {
            return null;
        }

        return Money::ofMinor($this->credit_limit_minor + $this->current_balance_minor, $this->currency_code);
    }
}
