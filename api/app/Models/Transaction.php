<?php

namespace App\Models;

use App\Enums\CreatedVia;
use App\Enums\TransactionDirection;
use App\Enums\TransactionType;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\TransactionFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\SoftDeletes;

#[Fillable([
    'account_id',
    'transfer_account_id',
    'category_id',
    'type',
    'direction',
    'amount_minor',
    'currency_code',
    'occurred_on',
    'occurred_at',
    'merchant',
    'note',
    'transfer_group',
    'receipt_id',
    'bill_id',
    'savings_goal_id',
    'recurring_rule_id',
    'has_splits',
    'is_demo',
    'created_via',
    'external_ref',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Transaction extends Model
{
    /** @use HasFactory<TransactionFactory> */
    use BelongsToUser, HasFactory, SoftDeletes;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'type' => TransactionType::class,
            'direction' => TransactionDirection::class,
            'created_via' => CreatedVia::class,
            'amount_minor' => 'integer',
            'occurred_on' => 'date',
            'occurred_at' => 'datetime',
            'has_splits' => 'boolean',
            'is_demo' => 'boolean',
        ];
    }

    protected static function booted(): void
    {
        static::saving(function (self $transaction): void {
            $transaction->merchant_normalized = $transaction->merchant === null
                ? null
                : MerchantCategoryRule::normalize($transaction->merchant);
        });
    }

    public function account(): BelongsTo
    {
        return $this->belongsTo(Account::class);
    }

    public function transferAccount(): BelongsTo
    {
        return $this->belongsTo(Account::class, 'transfer_account_id');
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function splits(): HasMany
    {
        return $this->hasMany(TransactionSplit::class);
    }

    public function tags(): BelongsToMany
    {
        return $this->belongsToMany(Tag::class);
    }

    public function receipt(): BelongsTo
    {
        return $this->belongsTo(Receipt::class);
    }

    public function bill(): BelongsTo
    {
        return $this->belongsTo(Bill::class);
    }

    public function savingsGoal(): BelongsTo
    {
        return $this->belongsTo(SavingsGoal::class);
    }

    public function recurringRule(): BelongsTo
    {
        return $this->belongsTo(RecurringRule::class);
    }

    /**
     * The paired row of a transfer, which carries the opposite direction and
     * the same `transfer_group`.
     */
    public function transferCounterpart(): ?self
    {
        if ($this->transfer_group === null) {
            return null;
        }

        return static::query()
            ->where('transfer_group', $this->transfer_group)
            ->whereKeyNot($this->getKey())
            ->first();
    }

    #[Scope]
    protected function ofType(Builder $query, TransactionType|string $type): void
    {
        $query->where('type', $type instanceof TransactionType ? $type->value : $type);
    }

    /**
     * Income and expenses only. Transfers move money between the user's own
     * accounts and must never be counted as either.
     */
    #[Scope]
    protected function cashFlow(Builder $query): void
    {
        $query->whereIn('type', [TransactionType::Income->value, TransactionType::Expense->value]);
    }

    /**
     * Spending that counts against budgets and expense reports: expenses that
     * are not savings allocations.
     */
    #[Scope]
    protected function spending(Builder $query): void
    {
        $query->where('type', TransactionType::Expense->value)->whereNull('savings_goal_id');
    }

    #[Scope]
    protected function between(Builder $query, string $from, string $to): void
    {
        $query->whereBetween('occurred_on', [$from, $to]);
    }

    public function amount(): Money
    {
        return Money::ofMinor($this->amount_minor, $this->currency_code);
    }

    public function signedMinor(): int
    {
        return $this->amount_minor * $this->direction->sign();
    }
}
