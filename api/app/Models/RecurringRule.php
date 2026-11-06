<?php

namespace App\Models;

use App\Enums\RecurrenceFrequency;
use App\Enums\TransactionType;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\RecurringRuleFactory;
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
    'transfer_account_id',
    'category_id',
    'name',
    'type',
    'amount_minor',
    'currency_code',
    'merchant',
    'note',
    'frequency',
    'interval',
    'by_month_day',
    'by_weekday',
    'start_date',
    'end_date',
    'occurrence_limit',
    'auto_post',
    'is_active',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class RecurringRule extends Model
{
    /** @use HasFactory<RecurringRuleFactory> */
    use BelongsToUser, HasFactory;

    /**
     * @var array<string, mixed>
     */
    protected $attributes = [
        'interval' => 1,
        'occurrences_created' => 0,
    ];

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'type' => TransactionType::class,
            'frequency' => RecurrenceFrequency::class,
            'amount_minor' => 'integer',
            'start_date' => 'date',
            'end_date' => 'date',
            'next_run_on' => 'date',
            'last_run_on' => 'date',
            'auto_post' => 'boolean',
            'is_active' => 'boolean',
        ];
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

    public function transactions(): HasMany
    {
        return $this->hasMany(Transaction::class);
    }

    #[Scope]
    protected function due(Builder $query, ?Carbon $on = null): void
    {
        $query->where('is_active', true)
            ->whereNotNull('next_run_on')
            ->whereDate('next_run_on', '<=', $on ?? today());
    }

    public function amount(): Money
    {
        return Money::ofMinor($this->amount_minor, $this->currency_code);
    }

    /**
     * The run date following `$from`, or null once the rule has finished.
     */
    public function dateAfter(Carbon $from): ?Carbon
    {
        $next = $this->frequency->advance($from, $this->interval, $this->by_month_day);

        if ($this->end_date !== null && $next->isAfter($this->end_date)) {
            return null;
        }

        if ($this->occurrence_limit !== null && $this->occurrences_created >= $this->occurrence_limit) {
            return null;
        }

        return $next;
    }

    public function hasFinished(): bool
    {
        if ($this->occurrence_limit !== null && $this->occurrences_created >= $this->occurrence_limit) {
            return true;
        }

        return $this->end_date !== null && $this->end_date->isBefore(today());
    }
}
