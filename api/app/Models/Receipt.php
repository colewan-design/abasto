<?php

namespace App\Models;

use App\Enums\ReceiptStatus;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use App\Support\Money;
use Database\Factories\ReceiptFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\SoftDeletes;

#[Fillable([
    'image_path',
    'thumbnail_path',
    'original_filename',
    'mime_type',
    'size_bytes',
    'status',
    'merchant',
    'receipt_date',
    'currency_code',
    'subtotal_minor',
    'tax_minor',
    'tip_minor',
    'discount_minor',
    'total_minor',
    'payment_method',
    'raw_text',
    'is_demo',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Receipt extends Model
{
    /** @use HasFactory<ReceiptFactory> */
    use BelongsToUser, HasFactory, SoftDeletes;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'status' => ReceiptStatus::class,
            'receipt_date' => 'date',
            'subtotal_minor' => 'integer',
            'tax_minor' => 'integer',
            'tip_minor' => 'integer',
            'discount_minor' => 'integer',
            'total_minor' => 'integer',
            'ocr_confidence' => 'float',
            'duplicate_score' => 'float',
            'extracted' => 'array',
            'field_confidence' => 'array',
            'is_demo' => 'boolean',
            'processed_at' => 'datetime',
            'confirmed_at' => 'datetime',
        ];
    }

    protected static function booted(): void
    {
        static::saving(function (self $receipt): void {
            $receipt->merchant_normalized = $receipt->merchant === null
                ? null
                : MerchantCategoryRule::normalize($receipt->merchant);
        });
    }

    public function items(): HasMany
    {
        return $this->hasMany(ReceiptItem::class)->orderBy('line_no');
    }

    public function transactions(): HasMany
    {
        return $this->hasMany(Transaction::class);
    }

    public function duplicateOf(): BelongsTo
    {
        return $this->belongsTo(self::class, 'duplicate_of_receipt_id');
    }

    #[Scope]
    protected function awaitingReview(Builder $query): void
    {
        $query->whereIn('status', [ReceiptStatus::Processed->value, ReceiptStatus::Failed->value]);
    }

    public function total(): ?Money
    {
        return $this->total_minor === null
            ? null
            : Money::ofMinor($this->total_minor, $this->currency_code);
    }

    /**
     * Fields the review screen should highlight because the extractor was not
     * confident about them.
     *
     * @return array<int, string>
     */
    public function lowConfidenceFields(): array
    {
        $threshold = (float) config('budget.receipts.low_confidence_threshold');

        return collect($this->field_confidence ?? [])
            ->filter(fn ($score) => is_numeric($score) && (float) $score < $threshold)
            ->keys()
            ->all();
    }

    public function isDuplicate(): bool
    {
        return $this->duplicate_of_receipt_id !== null;
    }
}
