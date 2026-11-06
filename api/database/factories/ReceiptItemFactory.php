<?php

namespace Database\Factories;

use App\Models\Receipt;
use App\Models\ReceiptItem;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<ReceiptItem>
 */
class ReceiptItemFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $total = fake()->numberBetween(50, 50_00);

        return [
            'receipt_id' => Receipt::factory(),
            'line_no' => 0,
            'description' => fake()->words(2, true),
            'quantity' => 1,
            'unit_price_minor' => $total,
            'total_minor' => $total,
            'confidence' => 0.7,
        ];
    }

    public function describing(string $description, int $totalMinor): static
    {
        return $this->state(fn (array $attributes) => [
            'description' => $description,
            'unit_price_minor' => $totalMinor,
            'total_minor' => $totalMinor,
        ]);
    }
}
