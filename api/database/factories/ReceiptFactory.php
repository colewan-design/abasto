<?php

namespace Database\Factories;

use App\Enums\ReceiptStatus;
use App\Models\Receipt;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Receipt>
 */
class ReceiptFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $subtotal = fake()->numberBetween(100_00, 300_00);
        $tax = (int) round($subtotal * 0.12);

        return [
            'user_id' => User::factory(),
            'image_path' => 'u1/'.Str::uuid().'.jpg',
            'mime_type' => 'image/jpeg',
            'size_bytes' => fake()->numberBetween(50_000, 900_000),
            'status' => ReceiptStatus::Processed->value,
            'merchant' => fake()->company(),
            'receipt_date' => fake()->dateTimeBetween('-30 days')->format('Y-m-d'),
            'currency_code' => config('budget.default_currency'),
            'subtotal_minor' => $subtotal,
            'tax_minor' => $tax,
            'total_minor' => $subtotal + $tax,
            'processed_at' => now(),
            'is_demo' => false,
        ];
    }

    public function pending(): static
    {
        return $this->state(fn (array $attributes) => [
            'status' => ReceiptStatus::Pending->value,
            'merchant' => null,
            'receipt_date' => null,
            'subtotal_minor' => null,
            'tax_minor' => null,
            'total_minor' => null,
            'processed_at' => null,
        ]);
    }

    public function confirmed(): static
    {
        return $this->state(fn (array $attributes) => [
            'status' => ReceiptStatus::Confirmed->value,
            'confirmed_at' => now(),
        ]);
    }

    public function total(int $minor): static
    {
        return $this->state(fn (array $attributes) => [
            'subtotal_minor' => $minor,
            'tax_minor' => 0,
            'total_minor' => $minor,
        ]);
    }
}
