<?php

namespace App\Services;

use App\Enums\TransactionType;
use Illuminate\Database\Query\Builder;
use Illuminate\Support\Facades\DB;

/**
 * Flattens the ledger into one row per category amount.
 *
 * A receipt split across groceries and household supplies is a single
 * transaction with two `transaction_splits` rows. Budgets and category reports
 * need to see those as two amounts, while an unsplit transaction contributes
 * its own `category_id`. This service produces a query that unions both shapes
 * so every consumer attributes money the same way.
 */
class CategoryLedger
{
    /**
     * @param  array{
     *     from?: string|null,
     *     to?: string|null,
     *     type?: TransactionType|null,
     *     account_id?: int|null,
     *     exclude_savings?: bool
     * }  $filters
     */
    public function query(int $userId, array $filters = []): Builder
    {
        $unsplit = $this->base($userId, $filters)
            ->where('t.has_splits', false)
            ->select([
                't.id as transaction_id',
                't.category_id',
                't.amount_minor',
                't.type',
                't.occurred_on',
                't.account_id',
                't.merchant',
                't.merchant_normalized',
                't.savings_goal_id',
            ]);

        $split = $this->base($userId, $filters)
            ->where('t.has_splits', true)
            ->join('transaction_splits as s', 's.transaction_id', '=', 't.id')
            ->select([
                't.id as transaction_id',
                's.category_id',
                's.amount_minor',
                't.type',
                't.occurred_on',
                't.account_id',
                't.merchant',
                't.merchant_normalized',
                't.savings_goal_id',
            ]);

        return DB::query()->fromSub($unsplit->unionAll($split), 'ledger');
    }

    /**
     * Total spending per category for a period, keyed by category id.
     *
     * @return array<int|string, int>
     */
    public function spendingByCategory(int $userId, string $from, string $to): array
    {
        return $this->query($userId, [
            'from' => $from,
            'to' => $to,
            'type' => TransactionType::Expense,
            'exclude_savings' => true,
        ])
            ->selectRaw('category_id, SUM(amount_minor) AS total')
            ->groupBy('category_id')
            ->pluck('total', 'category_id')
            ->map(fn ($total): int => (int) $total)
            ->all();
    }

    /**
     * @param  array<string, mixed>  $filters
     */
    private function base(int $userId, array $filters): Builder
    {
        $query = DB::table('transactions as t')
            ->where('t.user_id', $userId)
            ->whereNull('t.deleted_at')
            ->whereIn('t.type', [TransactionType::Income->value, TransactionType::Expense->value]);

        if (($filters['type'] ?? null) instanceof TransactionType) {
            $query->where('t.type', $filters['type']->value);
        }

        if (($filters['from'] ?? null) !== null) {
            $query->where('t.occurred_on', '>=', $filters['from']);
        }

        if (($filters['to'] ?? null) !== null) {
            $query->where('t.occurred_on', '<=', $filters['to']);
        }

        if (($filters['account_id'] ?? null) !== null) {
            $query->where('t.account_id', $filters['account_id']);
        }

        if ($filters['exclude_savings'] ?? false) {
            $query->whereNull('t.savings_goal_id');
        }

        return $query;
    }
}
