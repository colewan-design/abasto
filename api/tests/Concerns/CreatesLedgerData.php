<?php

namespace Tests\Concerns;

use App\Models\Account;
use App\Models\Category;
use App\Models\User;

/**
 * Helpers for arranging a user who has finished setup, so each test only has
 * to create the records its own behaviour depends on.
 */
trait CreatesLedgerData
{
    protected function userWithAccount(int $openingMinor = 10_000_00): array
    {
        $user = User::factory()->create();

        $account = Account::factory()
            ->for($user)
            ->cash()
            ->withOpeningBalance($openingMinor)
            ->create();

        return [$user, $account];
    }

    protected function category(User $user, string $name, array $keywords = []): Category
    {
        return Category::factory()->for($user)->named($name, $keywords)->create();
    }

    protected function incomeCategory(User $user, string $name = 'Salary'): Category
    {
        return Category::factory()->for($user)->income()->named($name)->create();
    }

    /**
     * The balance currently stored on an account, read fresh from the database.
     */
    protected function balanceOf(Account $account): int
    {
        return (int) Account::query()
            ->withoutGlobalScopes()
            ->whereKey($account->getKey())
            ->value('current_balance_minor');
    }
}
