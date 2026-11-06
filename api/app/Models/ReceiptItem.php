<?php

namespace App\Models;

use App\Support\Money;
use Database\Factories\ReceiptItemFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

#[Fillable([
    'line_no',
    'description',
    'quantity',
    'unit_price_minor',
    'total_minor',
    'suggested_category_id',
    'category_id',
    'confidence',
])]
class ReceiptItem extends Model
{
    /** @use HasFactory<ReceiptItemFactory> */
    use HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'quantity' => 'float',
            'unit_price_minor' => 'integer',
            'total_minor' => 'integer',
            'confidence' => 'float',
        ];
    }

    public function receipt(): BelongsTo
    {
        return $this->belongsTo(Receipt::class);
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    public function suggestedCategory(): BelongsTo
    {
        return $this->belongsTo(Category::class, 'suggested_category_id');
    }

    public function total(): Money
    {
        return Money::ofMinor($this->total_minor, $this->receipt?->currency_code);
    }

    /**
     * The category the user has chosen, falling back to the suggestion.
     */
    public function effectiveCategoryId(): ?int
    {
        return $this->category_id ?? $this->suggested_category_id;
    }
}
