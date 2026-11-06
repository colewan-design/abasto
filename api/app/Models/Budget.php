<?php

namespace App\Models;

use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\BudgetFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Carbon;

#[Fillable([
    'period',
    'period_start',
    'period_end',
    'currency_code',
    'overall_amount_minor',
    'rollover_enabled',
    'alert_threshold',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Budget extends Model
{
    /** @use HasFactory<BudgetFactory> */
    use BelongsToUser, HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'period_start' => 'date',
            'period_end' => 'date',
            'overall_amount_minor' => 'integer',
            'rollover_enabled' => 'boolean',
            'alert_threshold' => 'float',
        ];
    }

    public function categories(): HasMany
    {
        return $this->hasMany(BudgetCategory::class);
    }

    #[Scope]
    protected function forMonth(Builder $query, Carbon $month): void
    {
        $query->whereDate('period_start', $month->copy()->startOfMonth());
    }

    public function overallAmount(): ?Money
    {
        return $this->overall_amount_minor === null
            ? null
            : Money::ofMinor($this->overall_amount_minor, $this->currency_code);
    }

    public function isCurrent(): bool
    {
        return now()->betweenIncluded($this->period_start, $this->period_end);
    }
}
