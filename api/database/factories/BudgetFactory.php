<?php

namespace Database\Factories;

use App\Models\Budget;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Carbon;

/**
 * @extends Factory<Budget>
 */
class BudgetFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $start = today()->startOfMonth();

        return [
            'user_id' => User::factory(),
            'period' => 'monthly',
            'period_start' => $start->toDateString(),
            'period_end' => $start->copy()->endOfMonth()->toDateString(),
            'currency_code' => config('budget.default_currency'),
            'overall_amount_minor' => 30_000_00,
            'rollover_enabled' => false,
            'alert_threshold' => 0.8,
        ];
    }

    public function forMonth(string $yearMonth): static
    {
        $start = Carbon::createFromFormat('Y-m', $yearMonth)->startOfMonth();

        return $this->state(fn (array $attributes) => [
            'period_start' => $start->toDateString(),
            'period_end' => $start->copy()->endOfMonth()->toDateString(),
        ]);
    }
}
