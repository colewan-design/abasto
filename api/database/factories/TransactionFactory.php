<?php

namespace Database\Factories;

use App\Enums\CreatedVia;
use App\Enums\TransactionDirection;
use App\Enums\TransactionType;
use App\Models\Account;
use App\Models\Category;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Transaction>
 */
class TransactionFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'account_id' => Account::factory(),
            'category_id' => null,
            'type' => TransactionType::Expense->value,
            'direction' => TransactionDirection::Outflow->value,
            'amount_minor' => fake()->numberBetween(100, 500_00),
            'currency_code' => config('budget.default_currency'),
            'occurred_on' => fake()->dateTimeBetween('-60 days')->format('Y-m-d'),
            'merchant' => fake()->company(),
            'created_via' => CreatedVia::Manual->value,
            'is_demo' => false,
        ];
    }

    public function income(): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => TransactionType::Income->value,
            'direction' => TransactionDirection::Inflow->value,
        ]);
    }

    public function expense(): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => TransactionType::Expense->value,
            'direction' => TransactionDirection::Outflow->value,
        ]);
    }

    public function of(User $user, ?Account $account = null, ?Category $category = null): static
    {
        return $this->state(fn (array $attributes) => [
            'user_id' => $user->getKey(),
            'account_id' => $account?->getKey() ?? $attributes['account_id'],
            'category_id' => $category?->getKey(),
            'currency_code' => $account?->currency_code ?? $user->currency_code,
        ]);
    }

    public function amount(int $minor): static
    {
        return $this->state(fn (array $attributes) => [
            'amount_minor' => abs($minor),
        ]);
    }

    public function on(string $date): static
    {
        return $this->state(fn (array $attributes) => [
            'occurred_on' => $date,
        ]);
    }
}
