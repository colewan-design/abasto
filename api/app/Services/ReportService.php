<?php

namespace App\Services;

use App\Enums\AccountType;
use App\Enums\TransactionDirection;
use App\Enums\TransactionType;
use App\Models\Account;
use App\Models\Category;
use App\Models\SavingsContribution;
use App\Models\Transaction;
use App\Models\User;
use App\Support\Money;
use App\Support\Sql;
use Illuminate\Support\Carbon;
use Illuminate\Support\Collection;

/**
 * Read-side aggregations: cash flow, category breakdowns, trends, merchants.
 *
 * Two rules hold everywhere in here. Transfers are excluded, because moving
 * money between your own accounts is not income or expense. Savings
 * allocations are reported on their own line rather than as spending, so
 * putting money aside does not look like consuming it.
 */
class ReportService
{
    public function __construct(
        private readonly CategoryLedger $ledger,
        private readonly BudgetService $budgets,
    ) {}

    /**
     * Everything the home screen needs in one round trip.
     *
     * @return array<string, mixed>
     */
    public function dashboard(User $user, ?Carbon $month = null): array
    {
        $month ??= today();
        $from = $month->copy()->startOfMonth();
        $to = $month->copy()->endOfMonth();

        $cashFlow = $this->cashFlow($user, $from, $to);
        $budget = $this->budgets->forMonth($user, $from);

        return [
            'period' => [
                'from' => $from->toDateString(),
                'to' => $to->toDateString(),
                'label' => $from->format('F Y'),
            ],
            'currency' => $user->currency_code,
            'cash_flow' => $cashFlow,
            'balances' => $this->balances($user),
            'budget' => $budget === null ? null : $this->budgets->progress($user, $budget),
            'upcoming_bills' => $this->upcomingBills($user),
            'savings_goals' => $this->savingsProgress($user),
            'recent_transactions' => $this->recentTransactions($user),
            'top_categories' => array_slice($this->byCategory($user, $from, $to), 0, 5),
        ];
    }

    /**
     * @return array{
     *     income_minor: int,
     *     expense_minor: int,
     *     saved_minor: int,
     *     net_minor: int,
     *     formatted: array<string, string>
     * }
     */
    public function cashFlow(User $user, Carbon $from, Carbon $to): array
    {
        $rows = $user->transactions()
            ->cashFlow()
            ->whereBetween('occurred_on', [$from->toDateString(), $to->toDateString()])
            ->selectRaw('type, savings_goal_id IS NOT NULL AS is_savings, SUM(amount_minor) AS total')
            ->groupBy('type', 'is_savings')
            ->get();

        $income = (int) $rows->where('type', TransactionType::Income)->sum('total');
        $expense = (int) $rows->where('type', TransactionType::Expense)->where('is_savings', false)->sum('total');

        // Money set aside is counted from the contributions themselves, not
        // from the ledger: a contribution can be recorded either as a transfer
        // into a goal's account or as an allocation, and both should show up
        // on the "saved" line while staying out of "expenses".
        $saved = (int) SavingsContribution::query()
            ->whereIn('savings_goal_id', $user->savingsGoals()->select('id'))
            ->whereBetween('contributed_on', [$from->toDateString(), $to->toDateString()])
            ->sum('amount_minor');

        return [
            'income_minor' => $income,
            'expense_minor' => $expense,
            'saved_minor' => $saved,
            'net_minor' => $income - $expense - $saved,
            'formatted' => [
                'income' => Money::ofMinor($income, $user->currency_code)->format(),
                'expense' => Money::ofMinor($expense, $user->currency_code)->format(),
                'saved' => Money::ofMinor($saved, $user->currency_code)->format(),
                'net' => Money::ofMinor($income - $expense - $saved, $user->currency_code)->format(),
            ],
        ];
    }

    /**
     * Account balances, split into money available to spend and money owed.
     *
     * @return array<string, mixed>
     */
    public function balances(User $user): array
    {
        $accounts = $user->accounts()->active()->orderBy('sort_order')->orderBy('name')->get();

        $available = 0;
        $owed = 0;

        foreach ($accounts as $account) {
            if (! $account->include_in_total) {
                continue;
            }

            if ($account->type->isLiability()) {
                $owed += min(0, $account->current_balance_minor);

                continue;
            }

            $available += $account->current_balance_minor;
        }

        return [
            'available_minor' => $available,
            'owed_minor' => abs($owed),
            'net_worth_minor' => $available + $owed,
            'formatted' => [
                'available' => Money::ofMinor($available, $user->currency_code)->format(),
                'owed' => Money::ofMinor(abs($owed), $user->currency_code)->format(),
                'net_worth' => Money::ofMinor($available + $owed, $user->currency_code)->format(),
            ],
            'accounts' => $accounts->map(fn (Account $account): array => [
                'id' => $account->getKey(),
                'name' => $account->name,
                'type' => $account->type->value,
                'balance_minor' => $account->current_balance_minor,
                'formatted' => $account->balance()->format(),
                'is_liability' => $account->type->isLiability(),
            ])->all(),
        ];
    }

    /**
     * Spending (or income) per category for a period.
     *
     * @return array<int, array<string, mixed>>
     */
    public function byCategory(User $user, Carbon $from, Carbon $to, TransactionType $type = TransactionType::Expense): array
    {
        $rows = $this->ledger->query($user->getKey(), [
            'from' => $from->toDateString(),
            'to' => $to->toDateString(),
            'type' => $type,
            'exclude_savings' => $type === TransactionType::Expense,
        ])
            ->selectRaw('category_id, SUM(amount_minor) AS total, COUNT(DISTINCT transaction_id) AS transactions')
            ->groupBy('category_id')
            ->orderByDesc('total')
            ->get();

        $total = (int) $rows->sum('total');
        $categories = $this->categoryLookup($user, $rows->pluck('category_id')->filter()->all());

        return $rows->map(function (object $row) use ($categories, $total, $user): array {
            $category = $row->category_id === null ? null : $categories->get((int) $row->category_id);

            return [
                'category_id' => $row->category_id === null ? null : (int) $row->category_id,
                'name' => $category?->name ?? 'Uncategorized',
                'color' => $category?->color,
                'icon' => $category?->icon,
                'group' => $category?->group?->name,
                'amount_minor' => (int) $row->total,
                'formatted' => Money::ofMinor((int) $row->total, $user->currency_code)->format(),
                'transactions' => (int) $row->transactions,
                'share' => $total > 0 ? round($row->total / $total, 4) : 0.0,
            ];
        })->all();
    }

    /**
     * Month-by-month income and expense totals.
     *
     * @return array<int, array<string, mixed>>
     */
    public function monthlyTrends(User $user, int $months = 6): array
    {
        $start = today()->copy()->startOfMonth()->subMonths(max(0, $months - 1));

        $rows = $user->transactions()
            ->cashFlow()
            ->where('occurred_on', '>=', $start->toDateString())
            ->selectRaw(Sql::monthKey('occurred_on').' AS period, type, savings_goal_id IS NOT NULL AS is_savings, SUM(amount_minor) AS total')
            ->groupBy('period', 'type', 'is_savings')
            ->get()
            ->groupBy('period');

        // Saved comes from the contributions, matching `cashFlow()`.
        $contributions = SavingsContribution::query()
            ->whereIn('savings_goal_id', $user->savingsGoals()->select('id'))
            ->where('contributed_on', '>=', $start->toDateString())
            ->selectRaw(Sql::monthKey('contributed_on').' AS period, SUM(amount_minor) AS total')
            ->groupBy('period')
            ->pluck('total', 'period');

        return collect(range(0, max(0, $months - 1)))
            ->map(function (int $offset) use ($start, $rows, $contributions, $user): array {
                $month = $start->copy()->addMonths($offset);
                $group = $rows->get($month->format('Y-m')) ?? collect();

                $income = (int) $group->where('type', TransactionType::Income)->sum('total');
                $saved = (int) ($contributions[$month->format('Y-m')] ?? 0);
                $expense = (int) $group->where('type', TransactionType::Expense)->where('is_savings', false)->sum('total');

                return [
                    'period' => $month->format('Y-m'),
                    'label' => $month->format('M Y'),
                    'income_minor' => $income,
                    'expense_minor' => $expense,
                    'saved_minor' => $saved,
                    'net_minor' => $income - $expense - $saved,
                    'currency' => $user->currency_code,
                ];
            })
            ->all();
    }

    /**
     * Daily totals, for the sparkline on the cash-flow screen.
     *
     * @return array<int, array<string, mixed>>
     */
    public function dailyFlow(User $user, Carbon $from, Carbon $to): array
    {
        $rows = $user->transactions()
            ->cashFlow()
            ->whereBetween('occurred_on', [$from->toDateString(), $to->toDateString()])
            ->selectRaw('occurred_on, type, SUM(amount_minor) AS total')
            ->groupBy('occurred_on', 'type')
            ->get()
            ->groupBy(fn (object $row): string => Carbon::parse($row->occurred_on)->toDateString());

        $days = [];

        for ($date = $from->copy(); $date->lte($to); $date->addDay()) {
            $group = $rows->get($date->toDateString()) ?? collect();

            $days[] = [
                'date' => $date->toDateString(),
                'income_minor' => (int) $group->where('type', TransactionType::Income)->sum('total'),
                'expense_minor' => (int) $group->where('type', TransactionType::Expense)->sum('total'),
            ];
        }

        return $days;
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    public function topMerchants(User $user, Carbon $from, Carbon $to, int $limit = 10): array
    {
        return $user->transactions()
            ->ofType(TransactionType::Expense)
            ->whereNull('savings_goal_id')
            ->whereNotNull('merchant')
            ->whereBetween('occurred_on', [$from->toDateString(), $to->toDateString()])
            ->selectRaw('merchant_normalized, MAX(merchant) AS merchant, SUM(amount_minor) AS total, COUNT(*) AS visits')
            ->groupBy('merchant_normalized')
            ->orderByDesc('total')
            ->limit($limit)
            ->get()
            ->map(fn (object $row): array => [
                'merchant' => $row->merchant,
                'amount_minor' => (int) $row->total,
                'formatted' => Money::ofMinor((int) $row->total, $user->currency_code)->format(),
                'visits' => (int) $row->visits,
                'average_minor' => (int) round($row->total / max(1, $row->visits)),
            ])
            ->all();
    }

    /**
     * Balance history for one account, oldest first.
     *
     * @return array<int, array<string, mixed>>
     */
    public function accountHistory(User $user, Account $account, int $days = 30): array
    {
        $from = today()->copy()->subDays($days);

        $deltas = Transaction::query()
            ->where('user_id', $user->getKey())
            ->where('account_id', $account->getKey())
            ->where('occurred_on', '>=', $from->toDateString())
            ->selectRaw('occurred_on, SUM(CASE WHEN direction = ? THEN amount_minor ELSE -amount_minor END) AS delta', [TransactionDirection::Inflow->value])
            ->groupBy('occurred_on')
            ->pluck('delta', 'occurred_on');

        $future = (int) Transaction::query()
            ->where('user_id', $user->getKey())
            ->where('account_id', $account->getKey())
            ->where('occurred_on', '>', today()->toDateString())
            ->selectRaw('COALESCE(SUM(CASE WHEN direction = ? THEN amount_minor ELSE -amount_minor END), 0) AS total', [TransactionDirection::Inflow->value])
            ->value('total');

        $balance = $account->current_balance_minor - $future;
        $series = [];

        for ($date = today()->copy(); $date->gte($from); $date->subDay()) {
            $series[] = ['date' => $date->toDateString(), 'balance_minor' => $balance];
            $balance -= (int) ($deltas[$date->toDateString()] ?? 0);
        }

        return array_reverse($series);
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function upcomingBills(User $user, int $withinDays = 30): array
    {
        return $user->bills()
            ->active()
            ->whereDate('due_date', '<=', today()->addDays($withinDays))
            ->orderBy('due_date')
            ->with('category.group')
            ->limit(10)
            ->get()
            ->map(fn ($bill): array => [
                'id' => $bill->getKey(),
                'name' => $bill->name,
                'amount_minor' => $bill->amount_minor,
                'formatted' => $bill->amount()->format(),
                'due_date' => $bill->due_date->toDateString(),
                'days_until_due' => $bill->daysUntilDue(),
                'is_overdue' => $bill->isOverdue(),
                'category' => $bill->category?->name,
                // The colour lives on the group and only the icon is per
                // category, so both are resolved here rather than leaving a
                // client to walk the relation it was not sent.
                'icon' => $bill->category?->icon ?? $bill->category?->group?->icon,
                'color' => $bill->category?->color ?? $bill->category?->group?->color,
            ])
            ->all();
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function savingsProgress(User $user): array
    {
        return $user->savingsGoals()
            ->active()
            ->orderBy('sort_order')
            ->get()
            ->map(fn ($goal): array => [
                'id' => $goal->getKey(),
                'name' => $goal->name,
                'target_minor' => $goal->target_amount_minor,
                'saved_minor' => $goal->current_amount_minor,
                'remaining_minor' => $goal->remaining()->minor,
                'progress' => $goal->progress(),
                'target_date' => $goal->target_date?->toDateString(),
                'formatted' => [
                    'target' => $goal->target()->format(),
                    'saved' => $goal->savedAmount()->format(),
                ],
            ])
            ->all();
    }

    /**
     * @return Collection<int, Transaction>
     */
    private function recentTransactions(User $user, int $limit = 10): Collection
    {
        return $user->transactions()
            ->with(['account', 'category', 'transferAccount'])
            ->where(fn ($query) => $query
                ->where('type', '!=', TransactionType::Transfer->value)
                ->orWhere('direction', TransactionDirection::Outflow->value))
            ->orderByDesc('occurred_on')
            ->orderByDesc('id')
            ->limit($limit)
            ->get();
    }

    /**
     * @param  array<int, mixed>  $ids
     * @return Collection<int, Category>
     */
    private function categoryLookup(User $user, array $ids): Collection
    {
        if ($ids === []) {
            return collect();
        }

        return $user->categories()->with('group')->whereKey($ids)->get()->keyBy('id');
    }

    /**
     * Account types that hold spendable money, used by the onboarding summary.
     *
     * @return array<int, string>
     */
    public static function spendableTypes(): array
    {
        return array_map(
            fn (AccountType $type): string => $type->value,
            array_filter(AccountType::cases(), fn (AccountType $type): bool => ! $type->isLiability()),
        );
    }
}
