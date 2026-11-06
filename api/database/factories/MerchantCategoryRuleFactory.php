<?php

namespace Database\Factories;

use App\Models\Category;
use App\Models\MerchantCategoryRule;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<MerchantCategoryRule>
 */
class MerchantCategoryRuleFactory extends Factory
{
    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'category_id' => Category::factory(),
            'merchant_pattern' => MerchantCategoryRule::normalize(fake()->company()),
            'match_type' => 'contains',
            'source' => 'user',
            'hit_count' => 1,
            'last_used_at' => now(),
        ];
    }

    public function forMerchant(string $merchant): static
    {
        return $this->state(fn (array $attributes) => [
            'merchant_pattern' => MerchantCategoryRule::normalize($merchant),
        ]);
    }
}
