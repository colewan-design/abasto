<?php

namespace Database\Factories;

use App\Enums\CategoryType;
use App\Models\Category;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Category>
 */
class CategoryFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'name' => fake()->unique()->words(2, true),
            'type' => CategoryType::Expense->value,
            'color' => fake()->hexColor(),
            'icon' => 'tag',
            'keywords' => [],
            'is_system' => false,
            'sort_order' => 0,
        ];
    }

    public function income(): static
    {
        return $this->state(fn (array $attributes) => [
            'type' => CategoryType::Income->value,
        ]);
    }

    /**
     * @param  array<int, string>  $keywords
     */
    public function named(string $name, array $keywords = []): static
    {
        return $this->state(fn (array $attributes) => [
            'name' => $name,
            'keywords' => $keywords,
        ]);
    }

    public function archived(): static
    {
        return $this->state(fn (array $attributes) => [
            'archived_at' => now(),
        ]);
    }
}
