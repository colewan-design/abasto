<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class OnboardingTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_offers_the_choices_the_wizard_needs(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $response = $this->actingAs($user)->getJson('/api/v1/onboarding/options');

        $response->assertOk()
            ->assertJsonPath('data.completed', false)
            ->assertJsonStructure([
                'data' => [
                    'currencies' => [['code', 'name', 'symbol', 'exponent']],
                    'account_types' => [['value', 'label', 'icon', 'is_liability']],
                    'catalog' => [['type', 'groups']],
                    'recommended_categories',
                ],
            ]);
    }

    public function test_it_sets_up_the_account_from_the_wizard(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $response = $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'timezone' => 'Asia/Manila',
            'accounts' => [
                ['name' => 'Cash', 'type' => 'cash', 'opening_balance' => '3500'],
                ['name' => 'Bank', 'type' => 'bank', 'opening_balance' => '42800.50'],
                ['name' => 'Card', 'type' => 'credit_card', 'opening_balance' => '8400', 'credit_limit' => '50000'],
            ],
            'categories' => ['Groceries', 'Utilities', 'Salary'],
            'budget' => [
                'overall_amount' => '40000',
                'categories' => [['category_name' => 'Groceries', 'amount' => '8000']],
            ],
        ]);

        $response->assertCreated()
            ->assertJsonPath('user.onboarding_completed', true)
            ->assertJsonPath('user.currency.code', 'PHP')
            ->assertJsonCount(3, 'accounts');

        $user->refresh();

        $this->assertNotNull($user->onboarding_completed_at);
        $this->assertSame('Asia/Manila', $user->timezone);
        $this->assertSame(3, $user->accounts()->count());
    }

    public function test_opening_balances_become_the_starting_account_balances(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'accounts' => [['name' => 'Bank', 'type' => 'bank', 'opening_balance' => '42800.50']],
        ])->assertCreated();

        $account = $user->accounts()->sole();

        $this->assertSame(42_800_50, $account->opening_balance_minor);
        $this->assertSame(42_800_50, $account->current_balance_minor);
    }

    /**
     * A card's starting balance is money owed, so it is stored negative no
     * matter how the user typed it in.
     */
    public function test_a_credit_card_opening_balance_is_stored_as_money_owed(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'accounts' => [['name' => 'Card', 'type' => 'credit_card', 'opening_balance' => '8400', 'credit_limit' => '50000']],
        ])->assertCreated();

        $account = $user->accounts()->sole();

        $this->assertSame(-8_400_00, $account->current_balance_minor);
        $this->assertSame(50_000_00, $account->credit_limit_minor);

        $this->actingAs($user)
            ->getJson('/api/v1/accounts')
            ->assertOk()
            ->assertJsonPath('data.0.available_credit_minor', 41_600_00)
            ->assertJsonPath('meta.owed_minor', 8_400_00)
            ->assertJsonPath('meta.available_minor', 0);
    }

    public function test_it_seeds_only_the_selected_categories_with_their_groups(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'accounts' => [['name' => 'Cash', 'type' => 'cash']],
            'categories' => ['Groceries', 'Salary'],
        ])->assertCreated();

        $this->assertSame(
            ['Groceries', 'Salary'],
            $user->categories()->orderBy('name')->pluck('name')->all(),
        );

        // Groceries belongs to an expense group and Salary to an income one.
        $this->assertSame(2, $user->categoryGroups()->count());
        $this->assertTrue($user->categories()->where('name', 'Groceries')->sole()->is_system);
    }

    public function test_seeded_categories_carry_the_keywords_that_drive_suggestions(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'accounts' => [['name' => 'Cash', 'type' => 'cash']],
            'categories' => ['Fuel'],
        ])->assertCreated();

        $this->actingAs($user)
            ->postJson('/api/v1/categories/suggest', ['merchant' => 'Shell Station EDSA'])
            ->assertOk()
            ->assertJsonPath('category.name', 'Fuel')
            ->assertJsonPath('source', 'keywords');
    }

    public function test_it_creates_the_first_budget_with_its_category_limit(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'accounts' => [['name' => 'Cash', 'type' => 'cash']],
            'categories' => ['Groceries'],
            'budget' => [
                'overall_amount' => '40000',
                'categories' => [['category_name' => 'Groceries', 'amount' => '8000']],
            ],
        ])->assertCreated()->assertJsonPath('budget.overall_amount_minor', 40_000_00);

        $this->actingAs($user)
            ->getJson('/api/v1/budget')
            ->assertOk()
            ->assertJsonPath('meta.categories.0.limit_minor', 8_000_00);
    }

    public function test_it_requires_a_currency_and_at_least_one_account(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)
            ->postJson('/api/v1/onboarding/complete', [])
            ->assertInvalid([
                'currency_code',
                'accounts' => 'Add at least one account so balances can be tracked.',
            ]);
    }

    public function test_it_rejects_an_unsupported_currency(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'XYZ',
            'accounts' => [['name' => 'Cash', 'type' => 'cash']],
        ])->assertInvalid(['currency_code']);
    }

    public function test_it_rejects_two_accounts_with_the_same_name(): void
    {
        $user = User::factory()->needsOnboarding()->create();

        $this->actingAs($user)->postJson('/api/v1/onboarding/complete', [
            'currency_code' => 'PHP',
            'accounts' => [
                ['name' => 'Cash', 'type' => 'cash'],
                ['name' => 'Cash', 'type' => 'bank'],
            ],
        ])->assertInvalid(['accounts.0.name' => 'Each account needs a different name.']);
    }
}
