<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\SavingsGoal;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

class SavingsGoalTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    public function test_it_creates_a_goal_and_reports_progress(): void
    {
        [$user] = $this->userWithAccount();

        $this->actingAs($user)->postJson('/api/v1/savings-goals', [
            'name' => 'Emergency Fund',
            'target_amount' => '100000',
            'target_date' => today()->addMonths(6)->toDateString(),
        ])->assertCreated()
            ->assertJsonPath('data.target_amount_minor', 10_000_000)
            ->assertJsonPath('data.current_amount_minor', 0)
            ->assertJsonPath('data.progress', 0)
            ->assertJsonPath('data.is_achieved', false);
    }

    public function test_a_contribution_updates_progress_and_moves_money_out_of_the_account(): void
    {
        $user = User::factory()->create();
        $wallet = Account::factory()->for($user)->cash()->withOpeningBalance(50_000_00)->create();
        $savings = Account::factory()->for($user)->withOpeningBalance(0)->create(['name' => 'Savings']);
        $goal = SavingsGoal::factory()->for($user)->target(100_000_00)->create([
            'account_id' => $savings->id,
            'name' => 'Emergency Fund',
        ]);

        $this->actingAs($user)->postJson("/api/v1/savings-goals/{$goal->id}/contributions", [
            'amount' => '5000',
            'contributed_on' => today()->toDateString(),
            'from_account_id' => $wallet->id,
        ])->assertCreated()
            ->assertJsonPath('goal.current_amount_minor', 5_000_00)
            ->assertJsonPath('goal.progress', 0.05)
            ->assertJsonPath('goal.remaining_minor', 95_000_00);

        $this->assertSame(45_000_00, $this->balanceOf($wallet));
        $this->assertSame(5_000_00, $this->balanceOf($savings));
    }

    /**
     * Setting money aside is not consumption; it has to stay out of the
     * expense total or every saving month would look like overspending.
     */
    public function test_a_contribution_is_reported_as_saved_rather_than_spent(): void
    {
        $user = User::factory()->create();
        $wallet = Account::factory()->for($user)->cash()->withOpeningBalance(50_000_00)->create();
        $savings = Account::factory()->for($user)->withOpeningBalance(0)->create(['name' => 'Savings']);
        $goal = SavingsGoal::factory()->for($user)->create(['account_id' => $savings->id]);

        $this->actingAs($user)->postJson("/api/v1/savings-goals/{$goal->id}/contributions", [
            'amount' => '5000',
            'contributed_on' => today()->toDateString(),
            'from_account_id' => $wallet->id,
        ])->assertCreated();

        $this->actingAs($user)
            ->getJson('/api/v1/reports/cash-flow')
            ->assertOk()
            ->assertJsonPath('data.totals.expense_minor', 0)
            ->assertJsonPath('data.totals.saved_minor', 5_000_00);
    }

    /**
     * Without a dedicated account the allocation is recorded against the
     * source account, which still must not count as spending.
     */
    public function test_a_contribution_without_a_goal_account_is_excluded_from_spending(): void
    {
        [$user, $wallet] = $this->userWithAccount(50_000_00);
        $goal = SavingsGoal::factory()->for($user)->create(['account_id' => null]);

        $this->actingAs($user)->postJson("/api/v1/savings-goals/{$goal->id}/contributions", [
            'amount' => '2000',
            'contributed_on' => today()->toDateString(),
            'from_account_id' => $wallet->id,
        ])->assertCreated();

        $this->assertSame(48_000_00, $this->balanceOf($wallet));

        $this->actingAs($user)
            ->getJson('/api/v1/reports/cash-flow')
            ->assertOk()
            ->assertJsonPath('data.totals.expense_minor', 0)
            ->assertJsonPath('data.totals.saved_minor', 2_000_00);

        $this->actingAs($user)
            ->getJson('/api/v1/reports/categories')
            ->assertOk()
            ->assertJsonCount(0, 'data');
    }

    public function test_a_contribution_can_be_recorded_without_touching_an_account(): void
    {
        [$user, $wallet] = $this->userWithAccount(50_000_00);
        $goal = SavingsGoal::factory()->for($user)->create();

        $this->actingAs($user)->postJson("/api/v1/savings-goals/{$goal->id}/contributions", [
            'amount' => '1000',
            'contributed_on' => today()->toDateString(),
        ])->assertCreated()->assertJsonPath('goal.current_amount_minor', 1_000_00);

        $this->assertSame(50_000_00, $this->balanceOf($wallet));
        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_removing_a_contribution_reverses_the_money_movement(): void
    {
        $user = User::factory()->create();
        $wallet = Account::factory()->for($user)->cash()->withOpeningBalance(50_000_00)->create();
        $savings = Account::factory()->for($user)->withOpeningBalance(0)->create(['name' => 'Savings']);
        $goal = SavingsGoal::factory()->for($user)->create(['account_id' => $savings->id]);

        $contribution = $this->actingAs($user)
            ->postJson("/api/v1/savings-goals/{$goal->id}/contributions", [
                'amount' => '5000',
                'contributed_on' => today()->toDateString(),
                'from_account_id' => $wallet->id,
            ])
            ->json('contribution.id');

        $this->actingAs($user)
            ->deleteJson("/api/v1/savings-goals/{$goal->id}/contributions/{$contribution}")
            ->assertOk()
            ->assertJsonPath('goal.current_amount_minor', 0);

        $this->assertSame(50_000_00, $this->balanceOf($wallet));
        $this->assertSame(0, $this->balanceOf($savings));
    }

    public function test_reaching_the_target_marks_the_goal_achieved(): void
    {
        [$user, $wallet] = $this->userWithAccount(50_000_00);
        $goal = SavingsGoal::factory()->for($user)->target(5_000_00)->create();

        $this->actingAs($user)->postJson("/api/v1/savings-goals/{$goal->id}/contributions", [
            'amount' => '5000',
            'contributed_on' => today()->toDateString(),
            'from_account_id' => $wallet->id,
        ])->assertCreated()
            ->assertJsonPath('goal.is_achieved', true)
            ->assertJsonPath('goal.progress', 1)
            ->assertJsonPath('goal.remaining_minor', 0);
    }

    public function test_it_suggests_a_monthly_amount_to_reach_the_target_date(): void
    {
        [$user] = $this->userWithAccount();
        $goal = SavingsGoal::factory()->for($user)->target(60_000_00)->create([
            'target_date' => today()->addMonths(6)->toDateString(),
        ]);

        $this->actingAs($user)
            ->getJson("/api/v1/savings-goals/{$goal->id}")
            ->assertOk()
            ->assertJsonPath('data.suggested_monthly_minor', 10_000_00);
    }

    public function test_it_requires_a_name_and_a_target(): void
    {
        [$user] = $this->userWithAccount();

        $this->actingAs($user)
            ->postJson('/api/v1/savings-goals', [])
            ->assertInvalid(['name', 'target_amount_minor']);
    }

    public function test_it_rejects_a_target_date_in_the_past(): void
    {
        [$user] = $this->userWithAccount();

        $this->actingAs($user)->postJson('/api/v1/savings-goals', [
            'name' => 'Too late',
            'target_amount' => '1000',
            'target_date' => today()->subDay()->toDateString(),
        ])->assertInvalid(['target_date']);
    }

    public function test_it_hides_another_users_goal(): void
    {
        [$user] = $this->userWithAccount();
        $theirs = SavingsGoal::factory()->for(User::factory())->create();

        $this->actingAs($user)->getJson("/api/v1/savings-goals/{$theirs->id}")->assertNotFound();
    }
}
