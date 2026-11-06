<?php

namespace Database\Factories;

use App\Enums\CategoryType;
use App\Models\CategoryGroup;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<CategoryGroup>
 */
class CategoryGroupFactory extends Factory
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
            'icon' => 'folder',
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
}
