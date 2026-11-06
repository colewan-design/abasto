<?php

namespace App\Models;

use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use Database\Factories\MerchantCategoryRuleFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Str;

/**
 * A learned "this merchant means this category" mapping.
 *
 * Rules are written when a user confirms or corrects a suggestion, which is
 * what makes suggestions improve over time. The knowledge the app ships with
 * lives in `categories.keywords` rather than here.
 */
#[Fillable(['category_id', 'merchant_pattern', 'match_type', 'source', 'hit_count'])]
#[ScopedBy(OwnedByCurrentUser::class)]
class MerchantCategoryRule extends Model
{
    /** @use HasFactory<MerchantCategoryRuleFactory> */
    use BelongsToUser, HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'hit_count' => 'integer',
            'last_used_at' => 'datetime',
        ];
    }

    public function category(): BelongsTo
    {
        return $this->belongsTo(Category::class);
    }

    /**
     * Reduce a merchant name to a stable key: lowercase, no punctuation, no
     * store numbers, collapsed whitespace. "SM Supermarket #231 - BGC" and
     * "sm supermarket bgc" both normalize to "sm supermarket bgc".
     */
    public static function normalize(string $merchant): string
    {
        $value = Str::lower(Str::ascii($merchant));
        $value = preg_replace('/#\s*\d+/', ' ', $value) ?? $value;
        $value = preg_replace('/\b(inc|corp|corporation|co|ltd|llc|branch|store|outlet)\b/', ' ', $value) ?? $value;
        $value = preg_replace('/[^a-z0-9 ]+/', ' ', $value) ?? $value;

        return trim(preg_replace('/\s+/', ' ', $value) ?? $value);
    }

    public function recordHit(): void
    {
        $this->forceFill([
            'hit_count' => $this->hit_count + 1,
            'last_used_at' => now(),
        ])->saveQuietly();
    }
}
