<?php

namespace Database\Factories;

use App\Enums\AccountType;
use App\Models\Account;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Account>
 */
class AccountFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $type = fake()->randomElement([AccountType::Cash, AccountType::Bank, AccountType::Ewallet]);

        return [
            'user_id' => User::factory(),
            'name' => fake()->unique()->words(2, true),
            'type' => $type->value,
            'currency_code' => config('budget.default_currency'),
            'opening_balance_minor' => fake()->numberBetween(0, 5_000_00),
            'current_balance_minor' => 0,
            'color' => fake()->hexColor(),
            'icon' => $type->defaultIcon(),
            'include_in_total' => true,
            'is_archived' => false,
        ];
    }

    public function cash(): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => AccountType::Cash->value,
            'name' => 'Cash',
        ]);
    }

    /**
     * A credit card, whose balance is money owed and so is held negative.
     */
    public function creditCard(int $owedMinor = 500_00): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => AccountType::CreditCard->value,
            'name' => 'Credit Card',
            'opening_balance_minor' => -abs($owedMinor),
            'credit_limit_minor' => 50_000_00,
        ]);
    }

    public function withOpeningBalance(int $minor): static
    {
        return $this->state(fn (array $attributes) => [
            'opening_balance_minor' => $minor,
            'current_balance_minor' => $minor,
        ]);
    }
}
