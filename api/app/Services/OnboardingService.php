<?php

namespace App\Services;

use App\Enums\AccountType;
use App\Enums\CategoryType;
use App\Models\Account;
use App\Models\Category;
use App\Models\User;
use App\Support\DefaultCategoryCatalog;
use App\Support\Money;
use Illuminate\Support\Facades\DB;

/**
 * Guided setup: currency, accounts and starting balances, the starter category
 * set, and the first monthly budget.
 */
class OnboardingService
{
    public function __construct(
        private readonly BudgetService $budgets,
        private readonly AccountBalanceService $balances,
    ) {}

    /**
     * Everything the setup wizard needs to render its steps.
     *
     * @return array<string, mixed>
     */
    public function options(): array
    {
        return [
            'currencies' => collect(config('budget.currencies'))
                ->map(fn (array $currency, string $code): array => [
                    'code' => $code,
                    'name' => $currency['name'],
                    'symbol' => $currency['symbol'],
                    'exponent' => $currency['exponent'],
                ])
                ->values()
                ->all(),
            'account_types' => array_map(fn (AccountType $type): array => [
                'value' => $type->value,
                'label' => $type->label(),
                'icon' => $type->defaultIcon(),
                'is_liability' => $type->isLiability(),
            ], AccountType::cases()),
            'catalog' => collect(DefaultCategoryCatalog::all())
                ->map(fn (array $groups, string $type): array => [
                    'type' => $type,
                    'groups' => array_map(fn (array $group): array => [
                        'name' => $group['name'],
                        'icon' => $group['icon'],
                        'color' => $group['color'],
                        'categories' => array_map(fn (array $category): array => [
                            'name' => $category['name'],
                            'icon' => $category['icon'],
                            'recommended' => in_array($category['name'], DefaultCategoryCatalog::recommended(), true),
                        ], $group['categories']),
                    ], $groups),
                ])
                ->values()
                ->all(),
            'recommended_categories' => DefaultCategoryCatalog::recommended(),
        ];
    }

    /**
     * Create the starter groups and categories for a user.
     *
     * Passing `$only` keeps just the named categories (plus their groups),
     * which is how the wizard honours the user's selections. Everything is
     * created with `is_system` so the client can show which entries came from
     * the app, while still allowing renames and recolouring.
     *
     * @param  array<int, string>|null  $only
     */
    public function seedCategories(User $user, ?array $only = null): void
    {
        DB::transaction(function () use ($user, $only): void {
            foreach (DefaultCategoryCatalog::all() as $type => $groups) {
                $categoryType = CategoryType::from($type);
                $groupOrder = 0;

                foreach ($groups as $group) {
                    $wanted = $only === null
                        ? $group['categories']
                        : array_values(array_filter(
                            $group['categories'],
                            fn (array $category): bool => in_array($category['name'], $only, true),
                        ));

                    if ($wanted === []) {
                        continue;
                    }

                    // Written through the relations so the owner is set even
                    // when nobody is authenticated — guest signup and seeding
                    // both create categories for a user who is not signed in.
                    $model = $user->categoryGroups()->firstOrCreate(
                        [
                            'type' => $categoryType->value,
                            'name' => $group['name'],
                        ],
                        [
                            'icon' => $group['icon'],
                            'color' => $group['color'],
                            'sort_order' => $groupOrder++,
                        ],
                    );

                    $model->forceFill(['is_system' => true])->saveQuietly();

                    foreach ($wanted as $order => $category) {
                        $created = $user->categories()->firstOrCreate(
                            [
                                'type' => $categoryType->value,
                                'name' => $category['name'],
                            ],
                            [
                                'category_group_id' => $model->getKey(),
                                'icon' => $category['icon'],
                                'color' => $group['color'],
                                'keywords' => $category['keywords'],
                                'sort_order' => $order,
                            ],
                        );

                        $created->forceFill(['is_system' => true])->saveQuietly();
                    }
                }
            }
        });
    }

    /**
     * Finish guided setup.
     *
     * @param  array{
     *     currency_code: string,
     *     locale?: string|null,
     *     timezone?: string|null,
     *     accounts: array<int, array{name: string, type: string, opening_balance_minor?: int, credit_limit_minor?: int|null, institution?: string|null, color?: string|null, icon?: string|null}>,
     *     categories?: array<int, string>|null,
     *     budget?: array{overall_amount_minor?: int|null, categories?: array<int, array{category_id?: int|null, category_name?: string|null, amount_minor: int}>}|null
     * }  $data
     * @return array<string, mixed>
     */
    public function complete(User $user, array $data): array
    {
        return DB::transaction(function () use ($user, $data): array {
            $currency = strtoupper($data['currency_code']);

            $user->forceFill([
                'currency_code' => $currency,
                'locale' => $data['locale'] ?? $user->locale,
                'timezone' => $data['timezone'] ?? $user->timezone,
            ])->save();

            $this->seedCategories($user, $data['categories'] ?? DefaultCategoryCatalog::recommended());

            $accounts = $this->createAccounts($user, $currency, $data['accounts']);
            $budget = $this->createFirstBudget($user, $data['budget'] ?? null);

            $user->forceFill(['onboarding_completed_at' => now()])->save();

            return [
                'user' => $user->refresh(),
                'accounts' => $accounts,
                'budget' => $budget,
            ];
        });
    }

    /**
     * @param  array<int, array<string, mixed>>  $accounts
     * @return array<int, Account>
     */
    private function createAccounts(User $user, string $currency, array $accounts): array
    {
        $created = [];

        foreach ($accounts as $order => $input) {
            $type = AccountType::from($input['type']);
            $opening = (int) ($input['opening_balance_minor'] ?? 0);

            // A credit card's starting balance is money owed, so it is held as
            // a negative number regardless of how the user typed it in.
            if ($type->isLiability() && $opening > 0) {
                $opening = -$opening;
            }

            $account = $user->accounts()->create([
                'name' => $input['name'],
                'type' => $type->value,
                'currency_code' => $input['currency_code'] ?? $currency,
                'opening_balance_minor' => $opening,
                'credit_limit_minor' => $input['credit_limit_minor'] ?? null,
                'institution' => $input['institution'] ?? null,
                'color' => $input['color'] ?? null,
                'icon' => $input['icon'] ?? $type->defaultIcon(),
                'sort_order' => $order,
            ]);

            $this->balances->recalculate($account);

            $created[] = $account->refresh();
        }

        return $created;
    }

    /**
     * @param  array<string, mixed>|null  $input
     */
    private function createFirstBudget(User $user, ?array $input): mixed
    {
        if ($input === null) {
            return null;
        }

        $budget = $this->budgets->ensureForMonth($user, today());

        $budget->forceFill([
            'currency_code' => $user->currency_code,
            'overall_amount_minor' => $input['overall_amount_minor'] ?? null,
        ])->save();

        $limits = [];

        foreach ($input['categories'] ?? [] as $line) {
            $categoryId = $line['category_id'] ?? $this->categoryIdByName($user, $line['category_name'] ?? null);

            if ($categoryId === null) {
                continue;
            }

            $limits[] = [
                'category_id' => $categoryId,
                'amount_minor' => abs((int) $line['amount_minor']),
            ];
        }

        if ($limits !== []) {
            $this->budgets->setCategoryLimits($budget, $limits);
        }

        return $budget->refresh()->load('categories.category');
    }

    private function categoryIdByName(User $user, ?string $name): ?int
    {
        if ($name === null) {
            return null;
        }

        return $user->categories()->where('name', $name)->value('id');
    }

    /**
     * A sensible starting budget: the user's own last three months of spending
     * per category, rounded up to a tidy number.
     *
     * @return array<int, array{category_id: int, category: string, suggested_minor: int, formatted: string}>
     */
    public function suggestBudget(User $user, CategoryLedger $ledger): array
    {
        $from = today()->copy()->subMonths(3)->startOfMonth();
        $spending = $ledger->spendingByCategory($user->getKey(), $from->toDateString(), today()->toDateString());

        $categories = $user->categories()->ofType(CategoryType::Expense)->whereKey(array_keys($spending))->get();

        return $categories
            ->map(function (Category $category) use ($spending, $user): array {
                $average = (int) round(($spending[$category->getKey()] ?? 0) / 3);
                $rounded = (int) (ceil($average / 10000) * 10000);

                return [
                    'category_id' => $category->getKey(),
                    'category' => $category->name,
                    'suggested_minor' => max($rounded, $average),
                    'formatted' => Money::ofMinor(max($rounded, $average), $user->currency_code)->format(),
                ];
            })
            ->sortByDesc('suggested_minor')
            ->values()
            ->all();
    }
}
