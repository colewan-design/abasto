<?php

namespace Database\Factories;

use App\Enums\BillPaymentStatus;
use App\Models\Bill;
use App\Models\BillPayment;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<BillPayment>
 */
class BillPaymentFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'bill_id' => Bill::factory(),
            'period_due_date' => today()->toDateString(),
            'amount_minor' => 1_000_00,
            'status' => BillPaymentStatus::Unpaid->value,
        ];
    }

    public function paid(): static
    {
        return $this->state(fn (array $attributes) => [
            'status' => BillPaymentStatus::Paid->value,
            'paid_on' => today()->toDateString(),
        ]);
    }
}
