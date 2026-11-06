<?php

namespace App\Models;

use App\Support\Money;
use Database\Factories\BudgetCategoryFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable(['category_id', 'amount_minor', 'rollover_minor', 'alert_threshold'])]
class BudgetCategory extends Model
{
    /** @use HasFactory<BudgetCategoryFactory> */
    use HasFactory;

    /**
     * @var array<string, mixed>
     */
    protected $attributes = [
        'rollover_minor' => 0,
    ];

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'amount_minor' => 'integer',
            'rollover_minor' => 'integer',
            'alert_threshold' => 'float',
            'approaching_alerted_at' => 'datetime',
            'exceeded_alerted_at' => 'datetime',
        ];
    }

    public function budget(): BelongsTo
    {
        return $this->belongsTo(Budget::class);
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    /**
     * The limit for the period, including any amount rolled over from the
     * previous month.
     */
    public function limitMinor(): int
    {
        return $this->amount_minor + $this->rollover_minor;
    }

    public function limit(): Money
    {
        return Money::ofMinor($this->limitMinor(), $this->budget?->currency_code);
    }

    public function threshold(): float
    {
        return $this->alert_threshold
            ?? $this->budget?->alert_threshold
            ?? (float) config('budget.alert_threshold');
    }
}
