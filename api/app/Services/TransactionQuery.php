<?php

namespace App\Services;

use App\Enums\TransactionDirection;
use App\Enums\TransactionType;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Relations\Relation;

/**
 * Builds the filtered transaction query behind search, the transactions list
 * and the CSV export, so every surface filters identically.
 *
 * Transfers are stored as two rows. When no account filter is applied the
 * query collapses them to the outflow leg, because showing both legs in one
 * list reads as if the money moved twice. Filtering by account shows that
 * account's own leg, which is what a statement view needs.
 */
class TransactionQuery
{
    /**
     * @param  array{
     *     q?: string|null,
     *     type?: string|null,
     *     account_id?: int|array<int, int>|null,
     *     category_id?: int|array<int, int>|null,
     *     tag_id?: int|array<int, int>|null,
     *     from?: string|null,
     *     to?: string|null,
     *     min_amount_minor?: int|null,
     *     max_amount_minor?: int|null,
     *     has_receipt?: bool|null,
     *     uncategorized?: bool|null,
     *     savings_goal_id?: int|null,
     *     bill_id?: int|null,
     *     recurring_rule_id?: int|null,
     *     collapse_transfers?: bool,
     *     sort?: string|null,
     *     direction?: string|null
     * }  $filters
     * @return Builder<Transaction>
     */
    public function build(User $user, array $filters = []): Builder
    {
        $query = $user->transactions()->getQuery();

        $this->applySearch($query, $filters['q'] ?? null);
        $this->applyType($query, $filters['type'] ?? null);
        $this->applyIn($query, 'account_id', $filters['account_id'] ?? null);
        $this->applyIn($query, 'category_id', $filters['category_id'] ?? null);
        $this->applyDates($query, $filters['from'] ?? null, $filters['to'] ?? null);
        $this->applyAmounts($query, $filters['min_amount_minor'] ?? null, $filters['max_amount_minor'] ?? null);
        $this->applyTags($query, $filters['tag_id'] ?? null);
        $this->applyFlags($query, $filters);
        $this->applyTransferCollapse($query, $filters);
        $this->applySort($query, $filters['sort'] ?? null, $filters['direction'] ?? null);

        return $query;
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applySearch(Builder $query, ?string $term): void
    {
        $term = $term === null ? null : trim($term);

        if ($term === null || $term === '') {
            return;
        }

        $like = '%'.str_replace(['%', '_'], ['\%', '\_'], $term).'%';

        $query->where(function (Builder $query) use ($like, $term): void {
            $query->where('merchant', 'like', $like)
                ->orWhere('note', 'like', $like)
                ->orWhereHas('category', fn (Builder $category) => $category->where('name', 'like', $like))
                ->orWhereHas('account', fn (Builder $account) => $account->where('name', 'like', $like))
                ->orWhereHas('splits', fn (Builder $split) => $split->where('note', 'like', $like));

            if (is_numeric($term)) {
                $query->orWhere('amount_minor', (int) round((float) $term * 100));
            }
        });
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applyType(Builder $query, ?string $type): void
    {
        if ($type === null || $type === 'all') {
            return;
        }

        if ($type === 'cash_flow') {
            $query->cashFlow();

            return;
        }

        if (TransactionType::tryFrom($type) !== null) {
            $query->where('type', $type);
        }
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applyIn(Builder $query, string $column, int|array|null $value): void
    {
        if ($value === null || $value === []) {
            return;
        }

        is_array($value)
            ? $query->whereIn($column, $value)
            : $query->where($column, $value);
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applyDates(Builder $query, ?string $from, ?string $to): void
    {
        if ($from !== null) {
            $query->where('occurred_on', '>=', $from);
        }

        if ($to !== null) {
            $query->where('occurred_on', '<=', $to);
        }
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applyAmounts(Builder $query, ?int $min, ?int $max): void
    {
        if ($min !== null) {
            $query->where('amount_minor', '>=', abs($min));
        }

        if ($max !== null) {
            $query->where('amount_minor', '<=', abs($max));
        }
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applyTags(Builder $query, int|array|null $tagIds): void
    {
        if ($tagIds === null || $tagIds === []) {
            return;
        }

        $tagIds = is_array($tagIds) ? $tagIds : [$tagIds];

        $query->whereHas('tags', fn (Builder $tags) => $tags->whereIn('tags.id', $tagIds));
    }

    /**
     * @param  Builder<Transaction>  $query
     * @param  array<string, mixed>  $filters
     */
    private function applyFlags(Builder $query, array $filters): void
    {
        if (($filters['has_receipt'] ?? null) !== null) {
            $filters['has_receipt']
                ? $query->whereNotNull('receipt_id')
                : $query->whereNull('receipt_id');
        }

        if ($filters['uncategorized'] ?? false) {
            $query->whereNull('category_id')->where('type', '!=', TransactionType::Transfer->value);
        }

        foreach (['savings_goal_id', 'bill_id', 'recurring_rule_id'] as $column) {
            if (($filters[$column] ?? null) !== null) {
                $query->where($column, $filters[$column]);
            }
        }
    }

    /**
     * @param  Builder<Transaction>  $query
     * @param  array<string, mixed>  $filters
     */
    private function applyTransferCollapse(Builder $query, array $filters): void
    {
        $collapse = $filters['collapse_transfers'] ?? ($filters['account_id'] ?? null) === null;

        if (! $collapse) {
            return;
        }

        $query->where(fn (Builder $query) => $query
            ->where('type', '!=', TransactionType::Transfer->value)
            ->orWhere('direction', TransactionDirection::Outflow->value));
    }

    /**
     * @param  Builder<Transaction>  $query
     */
    private function applySort(Builder $query, ?string $sort, ?string $direction): void
    {
        $direction = strtolower((string) $direction) === 'asc' ? 'asc' : 'desc';

        match ($sort) {
            'amount' => $query->orderBy('amount_minor', $direction),
            'merchant' => $query->orderBy('merchant', $direction),
            'created_at' => $query->orderBy('created_at', $direction),
            default => $query->orderBy('occurred_on', $direction),
        };

        $query->orderBy('id', $direction);
    }

    /**
     * Totals for the current filter set, shown above the list so the numbers
     * always match what is on screen.
     *
     * @param  array<string, mixed>  $filters
     * @return array{count: int, income_minor: int, expense_minor: int, net_minor: int}
     */
    public function totals(User $user, array $filters = []): array
    {
        $rows = $this->build($user, $filters)
            ->toBase()
            ->selectRaw('type, direction, COUNT(*) AS count, SUM(amount_minor) AS total')
            ->groupBy('type', 'direction')
            ->get();

        $income = (int) $rows->where('type', TransactionType::Income->value)->sum('total');
        $expense = (int) $rows->where('type', TransactionType::Expense->value)->sum('total');

        return [
            'count' => (int) $rows->sum('count'),
            'income_minor' => $income,
            'expense_minor' => $expense,
            'net_minor' => $income - $expense,
        ];
    }

    /**
     * Relations every transaction list needs.
     *
     * Whole rows are loaded rather than hand-picked columns: these tables are
     * narrow, and a column list that drifts out of step with the API resources
     * produces null enums and null money instead of an obvious error.
     *
     * @param  Relation<Transaction>|Builder<Transaction>  $query
     */
    public static function withListRelations(Relation|Builder $query): Relation|Builder
    {
        return $query->with([
            'account',
            'transferAccount',
            'category.group',
            'tags',
            'splits.category',
        ]);
    }
}
