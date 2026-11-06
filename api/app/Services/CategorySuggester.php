<?php

namespace App\Services;

use App\Enums\CategoryType;
use App\Models\Category;
use App\Models\MerchantCategoryRule;
use App\Models\User;
use App\Support\DefaultCategoryCatalog;
use Illuminate\Support\Collection;
use Illuminate\Support\Str;

/**
 * Suggests a category for a merchant or a receipt line item, and learns from
 * the corrections a user makes on the review screen.
 *
 * Resolution order, highest confidence first:
 *   1. a rule this user has confirmed before for the same merchant
 *   2. keyword overlap with the user's categories — this is where the
 *      knowledge the app ships with lives, seeded from the starter catalog
 *   3. nothing — the caller asks the user to pick, and then we learn it
 */
class CategorySuggester
{
    /** @var array<int, Collection<int, Category>> */
    private array $categoryCache = [];

    /**
     * @return array{category_id: int|null, confidence: float, source: string}
     */
    public function suggestForMerchant(User $user, ?string $merchant, CategoryType $type = CategoryType::Expense): array
    {
        if ($merchant === null || trim($merchant) === '') {
            return $this->miss();
        }

        $normalized = MerchantCategoryRule::normalize($merchant);

        if ($normalized === '') {
            return $this->miss();
        }

        if ($rule = $this->matchRule($user, $normalized)) {
            $rule->recordHit();

            return ['category_id' => $rule->category_id, 'confidence' => 0.98, 'source' => 'learned'];
        }

        if ($category = $this->matchKeywords($user, $normalized, $type)) {
            return ['category_id' => $category->getKey(), 'confidence' => 0.75, 'source' => 'keywords'];
        }

        return $this->miss();
    }

    /**
     * @return array{category_id: int|null, confidence: float, source: string}
     */
    public function suggestForItem(User $user, string $description, CategoryType $type = CategoryType::Expense): array
    {
        $normalized = MerchantCategoryRule::normalize($description);

        if ($normalized === '') {
            return $this->miss();
        }

        if ($category = $this->matchKeywords($user, $normalized, $type)) {
            return ['category_id' => $category->getKey(), 'confidence' => 0.6, 'source' => 'item_keywords'];
        }

        return $this->miss();
    }

    /**
     * Suggest a category for each line on a receipt, falling back to whatever
     * the merchant itself suggests for lines nothing matched.
     *
     * @param  array<int, string>  $descriptions
     * @return array<int, array{category_id: int|null, confidence: float, source: string}>
     */
    public function suggestForItems(User $user, array $descriptions, ?string $merchant = null): array
    {
        $fallback = $this->suggestForMerchant($user, $merchant);

        return array_map(function (string $description) use ($user, $fallback): array {
            $suggestion = $this->suggestForItem($user, $description);

            return $suggestion['category_id'] !== null ? $suggestion : $fallback;
        }, $descriptions);
    }

    /**
     * Record the category a user actually chose for a merchant.
     *
     * When the choice overrides an earlier suggestion, the stale rule loses a
     * hit so a one-off mistake stops being suggested.
     */
    public function learn(User $user, string $merchant, int $categoryId, ?int $correctedFrom = null): void
    {
        $normalized = MerchantCategoryRule::normalize($merchant);

        if ($normalized === '' || Str::length($normalized) < 3) {
            return;
        }

        if (! $this->categories($user)->contains(fn (Category $category): bool => $category->getKey() === $categoryId)) {
            return;
        }

        $rule = MerchantCategoryRule::query()->firstOrNew([
            'user_id' => $user->getKey(),
            'merchant_pattern' => $normalized,
            'category_id' => $categoryId,
        ]);

        $rule->fill([
            'match_type' => 'contains',
            'source' => 'user',
            'hit_count' => ($rule->exists ? $rule->hit_count : 0) + 1,
        ]);

        $rule->user_id = $user->getKey();
        $rule->last_used_at = now();
        $rule->save();

        if ($correctedFrom !== null && $correctedFrom !== $categoryId) {
            $this->weaken($user, $normalized, $correctedFrom);
        }
    }

    /**
     * Propose a brand new category for a merchant that nothing matched, so the
     * app can offer it for approval instead of silently using "Miscellaneous".
     *
     * @return array{name: string, group: string, icon: string, color: string}|null
     */
    public function proposeCategory(?string $merchant, CategoryType $type = CategoryType::Expense): ?array
    {
        if ($merchant === null || trim($merchant) === '') {
            return null;
        }

        $normalized = MerchantCategoryRule::normalize($merchant);

        foreach (DefaultCategoryCatalog::all()[$type->value] ?? [] as $group) {
            foreach ($group['categories'] as $category) {
                foreach ($category['keywords'] as $keyword) {
                    if ($keyword !== '' && str_contains($normalized, $keyword)) {
                        return [
                            'name' => $category['name'],
                            'group' => $group['name'],
                            'icon' => $category['icon'],
                            'color' => $group['color'],
                        ];
                    }
                }
            }
        }

        return [
            'name' => Str::title($merchant),
            'group' => $type === CategoryType::Income ? 'Other Income' : 'Other',
            'icon' => 'circle-ellipsis',
            'color' => '#94a3b8',
        ];
    }

    /**
     * The most-used rule whose pattern overlaps this merchant name.
     */
    private function matchRule(User $user, string $normalized): ?MerchantCategoryRule
    {
        $rules = MerchantCategoryRule::query()
            ->ownedBy($user)
            ->orderByDesc('hit_count')
            ->get();

        $match = $rules->first(function (MerchantCategoryRule $rule) use ($normalized): bool {
            if ($rule->match_type === 'exact') {
                return $rule->merchant_pattern === $normalized;
            }

            return $rule->merchant_pattern !== ''
                && (str_contains($normalized, $rule->merchant_pattern) || str_contains($rule->merchant_pattern, $normalized));
        });

        if ($match === null) {
            return null;
        }

        // A rule outlives the category it points at being archived or deleted,
        // so never suggest one the user can no longer use.
        return $this->categories($user)->contains(fn (Category $category): bool => $category->getKey() === $match->category_id)
            ? $match
            : null;
    }

    /**
     * Score the user's categories by keyword overlap and return the best hit.
     * Longer keywords win, so "coffee bean" beats "coffee".
     */
    private function matchKeywords(User $user, string $normalized, CategoryType $type): ?Category
    {
        $best = null;
        $bestScore = 0;

        foreach ($this->categories($user) as $category) {
            if ($category->type !== $type) {
                continue;
            }

            foreach ($category->keywords ?? [] as $keyword) {
                $keyword = MerchantCategoryRule::normalize((string) $keyword);

                if ($keyword === '' || ! str_contains($normalized, $keyword)) {
                    continue;
                }

                $score = Str::length($keyword);

                if ($score > $bestScore) {
                    $best = $category;
                    $bestScore = $score;
                }
            }
        }

        return $best;
    }

    private function weaken(User $user, string $normalized, int $categoryId): void
    {
        $stale = MerchantCategoryRule::query()
            ->ownedBy($user)
            ->where('merchant_pattern', $normalized)
            ->where('category_id', $categoryId)
            ->first();

        if ($stale === null) {
            return;
        }

        if ($stale->hit_count <= 1) {
            $stale->delete();

            return;
        }

        $stale->forceFill(['hit_count' => $stale->hit_count - 1])->saveQuietly();
    }

    /**
     * @return Collection<int, Category>
     */
    private function categories(User $user): Collection
    {
        return $this->categoryCache[$user->getKey()] ??= Category::query()
            ->ownedBy($user)
            ->whereNull('archived_at')
            ->get(['id', 'name', 'type', 'keywords', 'category_group_id']);
    }

    /**
     * @return array{category_id: null, confidence: float, source: string}
     */
    private function miss(): array
    {
        return ['category_id' => null, 'confidence' => 0.0, 'source' => 'none'];
    }
}
