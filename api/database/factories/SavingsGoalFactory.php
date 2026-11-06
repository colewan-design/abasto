<?php

namespace Database\Factories;

use App\Models\SavingsGoal;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<SavingsGoal>
 */
class SavingsGoalFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'name' => fake()->unique()->randomElement([
                'Emergency Fund', 'Trip', 'New Laptop', 'Car Fund', 'House Deposit',
            ]),
            'currency_code' => config('budget.default_currency'),
            'target_amount_minor' => 100_000_00,
            'current_amount_minor' => 0,
            'target_date' => today()->addMonths(6)->toDateString(),
            'is_archived' => false,
        ];
    }

    public function target(int $minor): static
    {
        return $this->state(fn (array $attributes) => [
            'target_amount_minor' => $minor,
        ]);
    }
}
