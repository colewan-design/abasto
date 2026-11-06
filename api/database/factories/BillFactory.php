<?php

namespace Database\Factories;

use App\Enums\BillRecurrence;
use App\Models\Bill;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Bill>
 */
class BillFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'name' => fake()->randomElement(['Electricity', 'Internet', 'Water', 'Phone', 'Rent']),
            'payee' => fake()->company(),
            'amount_minor' => fake()->numberBetween(500_00, 5_000_00),
            'currency_code' => config('budget.default_currency'),
            'due_date' => today()->addDays(fake()->numberBetween(1, 20))->toDateString(),
            'recurrence' => BillRecurrence::Monthly->value,
            'recurrence_interval' => 1,
            'reminder_days_before' => 3,
            'auto_pay' => false,
            'is_active' => true,
        ];
    }

    public function dueIn(int $days): static
    {
        return $this->state(fn (array $attributes) => [
            'due_date' => today()->addDays($days)->toDateString(),
        ]);
    }

    public function overdue(int $days = 3): static
    {
        return $this->state(fn (array $attributes) => [
            'due_date' => today()->subDays($days)->toDateString(),
        ]);
    }

    public function oneTime(): static
    {
        return $this->state(fn (array $attributes) => [
            'recurrence' => BillRecurrence::None->value,
        ]);
    }
}
