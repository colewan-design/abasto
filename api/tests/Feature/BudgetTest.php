<?php

namespace Tests\Feature;

use App\Models\Budget;
use App\Models\BudgetCategory;
use App\Models\Transaction;
use App\Models\User;
use App\Notifications\BudgetLimitApproaching;
use App\Notifications\BudgetLimitExceeded;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Notification;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

class BudgetTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    public function test_a_month_with_no_budget_returns_null_with_suggestions(): void
    {
        [$user] = $this->userWithAccount();

        $this->actingAs($user)
            ->getJson('/api/v1/budget')
            ->assertOk()
            ->assertJsonPath('data', null)
            ->assertJsonPath('meta.month', today()->format('Y-m'));
    }

    public function test_it_creates_the_budget_and_category_limits(): void
    {
        [$user] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');

        $this->actingAs($user)->putJson('/api/v1/budget', [
            'overall_amount' => '40000',
            'categories' => [
                ['category_id' => $groceries->id, 'amount' => '8000'],
            ],
        ])->assertOk()
            ->assertJsonPath('data.overall_amount_minor', 4_000_000)
            ->assertJsonPath('meta.categories.0.limit_minor', 800_000);

        // Asserted through the model so the comparison does not depend on how
        // the driver renders a date column.
        $budget = Budget::query()->withoutGlobalScopes()->sole();

        $this->assertSame($user->id, $budget->user_id);
        $this->assertSame(today()->startOfMonth()->toDateString(), $budget->period_start->toDateString());
        $this->assertSame(4_000_000, $budget->overall_amount_minor);
    }

    public function test_it_reports_spending_against_each_category_limit(): void
    {
        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');

        $budget = Budget::factory()->for($user)->create();
        BudgetCategory::factory()->for($budget)->for($groceries)->limit(800_00)->create();

        Transaction::factory()->of($user, $account, $groceries)
            ->expense()
            ->amount(300_00)
            ->on(today()->toDateString())
            ->create();

        $this->actingAs($user)
            ->getJson('/api/v1/budget')
            ->assertOk()
            ->assertJsonPath('meta.categories.0.spent_minor', 300_00)
            ->assertJsonPath('meta.categories.0.remaining_minor', 500_00)
            ->assertJsonPath('meta.categories.0.percent_used', 0.375)
            ->assertJsonPath('meta.categories.0.status', 'ok');
    }

    /**
     * Spending attributed through a split has to count against the budget, or a
     * mixed receipt would quietly escape the limits it belongs to.
     */
    public function test_spending_recorded_as_a_split_counts_against_the_category_limit(): void
    {
        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');
        $household = $this->category($user, 'Household Supplies');

        $budget = Budget::factory()->for($user)->create();
        BudgetCategory::factory()->for($budget)->for($household)->limit(1_000_00)->create();

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'amount' => '1000.00',
            'occurred_on' => today()->toDateString(),
            'splits' => [
                ['category_id' => $groceries->id, 'amount' => '700.00'],
                ['category_id' => $household->id, 'amount' => '300.00'],
            ],
        ])->assertCreated();

        $this->actingAs($user)
            ->getJson('/api/v1/budget')
            ->assertOk()
            ->assertJsonPath('meta.categories.0.spent_minor', 300_00);
    }

    public function test_it_notifies_once_when_spending_approaches_a_limit(): void
    {
        Notification::fake();

        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');

        $budget = Budget::factory()->for($user)->create();
        BudgetCategory::factory()->for($budget)->for($groceries)->limit(1_000_00)->create();

        // 85% of the limit, past the 80% default threshold.
        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'category_id' => $groceries->id,
            'amount' => '850.00',
            'occurred_on' => today()->toDateString(),
        ])->assertCreated();

        Notification::assertSentTimes(BudgetLimitApproaching::class, 1);

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'category_id' => $groceries->id,
            'amount' => '10.00',
            'occurred_on' => today()->toDateString(),
        ])->assertCreated();

        Notification::assertSentTimes(BudgetLimitApproaching::class, 1);
    }

    public function test_it_notifies_when_a_limit_is_exceeded(): void
    {
        Notification::fake();

        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');

        $budget = Budget::factory()->for($user)->create();
        BudgetCategory::factory()->for($budget)->for($groceries)->limit(1_000_00)->create();

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'category_id' => $groceries->id,
            'amount' => '1200.00',
            'occurred_on' => today()->toDateString(),
        ])->assertCreated();

        Notification::assertSentTo($user, BudgetLimitExceeded::class);
        Notification::assertNotSentTo($user, BudgetLimitApproaching::class);
    }

    public function test_the_notification_names_the_category_and_the_amounts(): void
    {
        [$user] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');

        $budget = Budget::factory()->for($user)->create();
        $line = BudgetCategory::factory()->for($budget)->for($groceries)->limit(1_000_00)->create();

        $payload = (new BudgetLimitExceeded($budget, $line->load('category'), 1_200_00))->toArray($user);

        $this->assertSame('budget.exceeded', $payload['type']);
        $this->assertSame('Over your Groceries budget', $payload['title']);
        $this->assertSame('₱1,200.00 spent of ₱1,000.00 — ₱200.00 over.', $payload['body']);
    }

    public function test_it_rejects_a_category_limit_for_another_users_category(): void
    {
        [$user] = $this->userWithAccount();
        $stranger = $this->category(User::factory()->create(), 'Groceries');

        $this->actingAs($user)->putJson('/api/v1/budget', [
            'categories' => [['category_id' => $stranger->id, 'amount' => '500']],
        ])->assertInvalid(['categories.0.category_id']);
    }

    public function test_it_rejects_an_income_category_as_a_spending_limit(): void
    {
        [$user] = $this->userWithAccount();
        $salary = $this->incomeCategory($user);

        $this->actingAs($user)->putJson('/api/v1/budget', [
            'categories' => [['category_id' => $salary->id, 'amount' => '500']],
        ])->assertInvalid(['categories.0.category_id']);
    }
}
