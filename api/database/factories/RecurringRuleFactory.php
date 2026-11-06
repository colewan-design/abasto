<?php

namespace Database\Factories;

use App\Enums\RecurrenceFrequency;
use App\Enums\TransactionType;
use App\Models\Account;
use App\Models\RecurringRule;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<RecurringRule>
 */
class RecurringRuleFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'account_id' => Account::factory(),
            'name' => 'Monthly subscription',
            'type' => TransactionType::Expense->value,
            'amount_minor' => 500_00,
            'currency_code' => config('budget.default_currency'),
            'merchant' => fake()->company(),
            'frequency' => RecurrenceFrequency::Monthly->value,
            'interval' => 1,
            'start_date' => today()->startOfMonth()->toDateString(),
            'next_run_on' => today()->toDateString(),
            'auto_post' => true,
            'is_active' => true,
        ];
    }

    public function income(): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => TransactionType::Income->value,
            'name' => 'Payroll',
        ]);
    }

    public function due(string $date): static
    {
        return $this->state(fn (array $attributes) => [
            'next_run_on' => $date,
        ]);
    }
}
