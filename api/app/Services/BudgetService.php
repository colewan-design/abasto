<?php

namespace App\Services;

use App\Models\Budget;
use App\Models\BudgetCategory;
use App\Models\User;
use App\Notifications\BudgetLimitApproaching;
use App\Notifications\BudgetLimitExceeded;
use App\Support\Money;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Monthly budgets: the overall limit, the per-category limits, and the
 * progress and alerts derived from them.
 */
class BudgetService
{
    public function __construct(
        private readonly CategoryLedger $ledger,
    ) {}

    public function forMonth(User $user, Carbon $month): ?Budget
    {
        return $user->budgets()
            ->with(['categories.category'])
            ->whereDate('period_start', $month->copy()->startOfMonth())
            ->first();
    }

    /**
     * Fetch the budget for a month, creating it from the previous month's
     * limits when the user has not set one up yet.
     */
    public function ensureForMonth(User $user, Carbon $month): Budget
    {
        $start = $month->copy()->startOfMonth();

        if ($budget = $this->forMonth($user, $start)) {
            return $budget;
        }

        $previous = $user->budgets()
            ->with('categories')
            ->whereDate('period_start', $start->copy()->subMonth())
            ->first();

        return DB::transaction(function () use ($user, $start, $previous): Budget {
            $budget = $user->budgets()->create([
                'period' => 'monthly',
                'period_start' => $start,
                'period_end' => $start->copy()->endOfMonth(),
                'currency_code' => $user->currency_code,
                'overall_amount_minor' => $previous?->overall_amount_minor,
                'rollover_enabled' => (bool) $previous?->rollover_enabled,
                'alert_threshold' => $previous?->alert_threshold ?? (float) config('budget.alert_threshold'),
            ]);

            foreach ($previous?->categories ?? [] as $line) {
                $budget->categories()->create([
                    'category_id' => $line->category_id,
                    'amount_minor' => $line->amount_minor,
                    'alert_threshold' => $line->alert_threshold,
                    'rollover_minor' => $budget->rollover_enabled
                        ? $this->unusedFromPreviousMonth($user, $previous, $line)
                        : 0,
                ]);
            }

            return $budget->load('categories.category');
        });
    }

    /**
     * Spending progress for a budget period.
     *
     * @return array{
     *     budget: Budget,
     *     overall: array{limit_minor: int|null, spent_minor: int, remaining_minor: int|null, percent_used: float|null, status: string},
     *     categories: array<int, array<string, mixed>>,
     *     uncategorized_minor: int,
     *     unbudgeted_minor: int
     * }
     */
    public function progress(User $user, Budget $budget): array
    {
        $from = $budget->period_start->toDateString();
        $to = $budget->period_end->toDateString();

        $spentByCategory = $this->ledger->spendingByCategory($user->getKey(), $from, $to);
        $totalSpent = array_sum($spentByCategory);

        $categories = [];
        $budgetedSpend = 0;

        foreach ($budget->categories as $line) {
            $limit = $line->limitMinor();
            $spent = (int) ($spentByCategory[$line->category_id] ?? 0);
            $budgetedSpend += $spent;

            $categories[] = [
                'id' => $line->getKey(),
                'category_id' => $line->category_id,
                'category' => $line->category,
                'limit_minor' => $limit,
                'amount_minor' => $line->amount_minor,
                'rollover_minor' => $line->rollover_minor,
                'spent_minor' => $spent,
                'remaining_minor' => $limit - $spent,
                'percent_used' => $limit > 0 ? round($spent / $limit, 4) : null,
                'status' => $this->status($spent, $limit, $line->threshold()),
                'threshold' => $line->threshold(),
                'formatted' => [
                    'limit' => Money::ofMinor($limit, $budget->currency_code)->format(),
                    'spent' => Money::ofMinor($spent, $budget->currency_code)->format(),
                    'remaining' => Money::ofMinor($limit - $spent, $budget->currency_code)->format(),
                ],
            ];
        }

        $overallLimit = $budget->overall_amount_minor;

        return [
            'budget' => $budget,
            'overall' => [
                'limit_minor' => $overallLimit,
                'spent_minor' => $totalSpent,
                'remaining_minor' => $overallLimit === null ? null : $overallLimit - $totalSpent,
                'percent_used' => $overallLimit > 0 ? round($totalSpent / $overallLimit, 4) : null,
                'status' => $overallLimit === null
                    ? 'unset'
                    : $this->status($totalSpent, $overallLimit, (float) $budget->alert_threshold),
            ],
            'categories' => $categories,
            'uncategorized_minor' => (int) ($spentByCategory[''] ?? $spentByCategory[0] ?? 0),
            'unbudgeted_minor' => $totalSpent - $budgetedSpend,
        ];
    }

    /**
     * Replace the category limits on a budget.
     *
     * @param  array<int, array{category_id: int, amount_minor: int, alert_threshold?: float|null}>  $limits
     */
    public function setCategoryLimits(Budget $budget, array $limits): Budget
    {
        return DB::transaction(function () use ($budget, $limits): Budget {
            $keep = [];

            foreach ($limits as $limit) {
                $line = $budget->categories()->updateOrCreate(
                    ['category_id' => $limit['category_id']],
                    [
                        'amount_minor' => abs($limit['amount_minor']),
                        'alert_threshold' => $limit['alert_threshold'] ?? null,
                    ],
                );

                $keep[] = $line->getKey();
            }

            $budget->categories()->whereKeyNot($keep)->delete();

            return $budget->load('categories.category');
        });
    }

    /**
     * Notify the user the first time spending in a category reaches its alert
     * threshold, and again the first time it goes over.
     */
    public function checkThresholds(User $user, Carbon|string $within): void
    {
        $month = $within instanceof Carbon ? $within->copy() : Carbon::parse($within);
        $budget = $this->forMonth($user, $month);

        if ($budget === null) {
            return;
        }

        $progress = $this->progress($user, $budget);

        foreach ($progress['categories'] as $line) {
            /** @var BudgetCategory|null $model */
            $model = $budget->categories->firstWhere('id', $line['id']);

            if ($model === null || $line['limit_minor'] <= 0) {
                continue;
            }

            if ($line['status'] === 'exceeded' && $model->exceeded_alerted_at === null) {
                $user->notify(new BudgetLimitExceeded($budget, $model, $line['spent_minor']));
                $model->forceFill(['exceeded_alerted_at' => now()])->saveQuietly();

                continue;
            }

            if ($line['status'] === 'approaching' && $model->approaching_alerted_at === null) {
                $user->notify(new BudgetLimitApproaching($budget, $model, $line['spent_minor']));
                $model->forceFill(['approaching_alerted_at' => now()])->saveQuietly();
            }
        }
    }

    private function status(int $spent, ?int $limit, float $threshold): string
    {
        if ($limit === null || $limit <= 0) {
            return 'unset';
        }

        if ($spent > $limit) {
            return 'exceeded';
        }

        return $spent >= $limit * $threshold ? 'approaching' : 'ok';
    }

    /**
     * Unspent amount from the previous month, used when rollover is enabled.
     */
    private function unusedFromPreviousMonth(User $user, Budget $previous, BudgetCategory $line): int
    {
        $spent = $this->ledger->spendingByCategory(
            $user->getKey(),
            $previous->period_start->toDateString(),
            $previous->period_end->toDateString(),
        );

        return max(0, $line->limitMinor() - (int) ($spent[$line->category_id] ?? 0));
    }
}
