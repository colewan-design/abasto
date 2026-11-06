<?php

namespace App\Models;

use App\Enums\BillRecurrence;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\BillFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Carbon;

#[Fillable([
    'account_id',
    'category_id',
    'name',
    'payee',
    'amount_minor',
    'currency_code',
    'due_date',
    'recurrence',
    'recurrence_interval',
    'reminder_days_before',
    'auto_pay',
    'is_active',
    'note',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Bill extends Model
{
    /** @use HasFactory<BillFactory> */
    use BelongsToUser, HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'recurrence' => BillRecurrence::class,
            'amount_minor' => 'integer',
            'due_date' => 'date',
            'last_paid_on' => 'date',
            'auto_pay' => 'boolean',
            'is_active' => 'boolean',
            'reminded_at' => 'datetime',
        ];
    }

    public function account(): BelongsTo
    {
        return $this->belongsTo(Account::class);
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function payments(): HasMany
    {
        return $this->hasMany(BillPayment::class);
    }

    public function transactions(): HasMany
    {
        return $this->hasMany(Transaction::class);
    }

    #[Scope]
    protected function active(Builder $query): void
    {
        $query->where('is_active', true);
    }

    #[Scope]
    protected function dueBefore(Builder $query, Carbon $date): void
    {
        $query->whereDate('due_date', '<=', $date);
    }

    public function amount(): Money
    {
        return Money::ofMinor($this->amount_minor, $this->currency_code);
    }

    public function isOverdue(): bool
    {
        return $this->is_active && $this->due_date->isBefore(today());
    }

    public function daysUntilDue(): int
    {
        return today()->diffInDays($this->due_date, false);
    }

    public function nextDueDate(): ?Carbon
    {
        return $this->recurrence->nextDueDate($this->due_date, $this->recurrence_interval);
    }
}
