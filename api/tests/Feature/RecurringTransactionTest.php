<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\RecurringRule;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

class RecurringTransactionTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    public function test_it_schedules_the_first_run_from_the_start_date(): void
    {
        [$user, $account] = $this->userWithAccount();

        $this->actingAs($user)->postJson('/api/v1/recurring-rules', [
            'name' => 'Netflix',
            'type' => 'expense',
            'account_id' => $account->id,
            'amount' => '549',
            'frequency' => 'monthly',
            'by_month_day' => 5,
            'start_date' => '2026-10-01',
        ])->assertCreated()->assertJsonPath('data.next_run_on', '2026-10-05');
    }

    public function test_it_previews_the_next_few_run_dates(): void
    {
        [$user, $account] = $this->userWithAccount();

        $this->actingAs($user)
            ->postJson('/api/v1/recurring-rules', [
                'name' => 'Rent',
                'type' => 'expense',
                'account_id' => $account->id,
                'amount' => '12000',
                'frequency' => 'monthly',
                'by_month_day' => 3,
                'start_date' => '2026-10-01',
            ])
            ->assertCreated()
            ->assertJsonPath('data.upcoming_dates', ['2026-10-03', '2026-11-03', '2026-12-03']);
    }

    public function test_running_a_due_rule_posts_the_transaction_and_moves_the_schedule_on(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $rule = RecurringRule::factory()->for($user)->for($account)->due(today()->toDateString())->create([
            'amount_minor' => 549_00,
            'merchant' => 'Netflix',
        ]);

        $this->actingAs($user)
            ->postJson("/api/v1/recurring-rules/{$rule->id}/run")
            ->assertOk()
            ->assertJsonPath('message', '1 transaction created.');

        $this->assertDatabaseHas('transactions', [
            'recurring_rule_id' => $rule->id,
            'amount_minor' => 549_00,
            'created_via' => 'recurring',
            'merchant' => 'Netflix',
        ]);

        $this->assertSame(10_000_00 - 549_00, $this->balanceOf($account));
        $this->assertTrue($rule->refresh()->next_run_on->isAfter(today()));
    }

    /**
     * Someone who has not opened the app for a while should see each salary
     * and each rent payment, not one lump.
     */
    public function test_a_rule_catches_up_on_every_occurrence_it_missed(): void
    {
        [$user, $account] = $this->userWithAccount();
        $rule = RecurringRule::factory()->for($user)->for($account)->create([
            'frequency' => 'monthly',
            'by_month_day' => 1,
            'start_date' => today()->subMonths(3)->startOfMonth()->toDateString(),
            'next_run_on' => today()->subMonths(3)->startOfMonth()->toDateString(),
        ]);

        $this->artisan('recurring:run')->assertSuccessful();

        $this->assertSame(4, Transaction::query()->withoutGlobalScopes()->where('recurring_rule_id', $rule->id)->count());
    }

    public function test_running_twice_does_not_post_the_same_occurrence_again(): void
    {
        [$user, $account] = $this->userWithAccount();
        $rule = RecurringRule::factory()->for($user)->for($account)->due(today()->toDateString())->create();

        $this->artisan('recurring:run')->assertSuccessful();
        $this->artisan('recurring:run')->assertSuccessful();

        $this->assertSame(1, Transaction::query()->withoutGlobalScopes()->where('recurring_rule_id', $rule->id)->count());
    }

    public function test_a_rule_stops_at_its_end_date(): void
    {
        [$user, $account] = $this->userWithAccount();
        $rule = RecurringRule::factory()->for($user)->for($account)->create([
            'frequency' => 'monthly',
            'start_date' => today()->subMonths(2)->toDateString(),
            'next_run_on' => today()->subMonths(2)->toDateString(),
            'end_date' => today()->subMonth()->toDateString(),
        ]);

        $this->artisan('recurring:run')->assertSuccessful();

        $rule->refresh();

        $this->assertFalse($rule->is_active);
        $this->assertNull($rule->next_run_on);
    }

    /**
     * A rule anchored on the 31st still has to fire in a short month.
     */
    public function test_a_month_end_anchor_clamps_to_the_last_day_of_a_short_month(): void
    {
        [$user, $account] = $this->userWithAccount();
        $rule = RecurringRule::factory()->for($user)->for($account)->create([
            'frequency' => 'monthly',
            'by_month_day' => 31,
            'start_date' => '2027-01-31',
            'next_run_on' => '2027-01-31',
        ]);

        $this->travelTo('2027-02-28');
        $this->artisan('recurring:run')->assertSuccessful();

        $dates = Transaction::query()
            ->withoutGlobalScopes()
            ->where('recurring_rule_id', $rule->id)
            ->pluck('occurred_on')
            ->map(fn ($date): string => $date->toDateString())
            ->all();

        $this->assertSame(['2027-01-31', '2027-02-28'], $dates);
    }

    public function test_a_recurring_transfer_posts_both_legs(): void
    {
        $user = User::factory()->create();
        $from = Account::factory()->for($user)->withOpeningBalance(10_000_00)->create(['name' => 'Savings']);
        $to = Account::factory()->for($user)->cash()->withOpeningBalance(0)->create();

        $rule = RecurringRule::factory()->for($user)->for($from)->due(today()->toDateString())->create([
            'type' => 'transfer',
            'transfer_account_id' => $to->id,
            'amount_minor' => 600_00,
        ]);

        $this->actingAs($user)->postJson("/api/v1/recurring-rules/{$rule->id}/run")->assertOk();

        $this->assertSame(9_400_00, $this->balanceOf($from));
        $this->assertSame(600_00, $this->balanceOf($to));
        $this->assertSame(2, Transaction::query()->withoutGlobalScopes()->count());
    }

    public function test_a_transfer_rule_requires_a_destination_account(): void
    {
        [$user, $account] = $this->userWithAccount();

        $this->actingAs($user)->postJson('/api/v1/recurring-rules', [
            'name' => 'Move to savings',
            'type' => 'transfer',
            'account_id' => $account->id,
            'amount' => '1000',
            'frequency' => 'monthly',
            'start_date' => today()->toDateString(),
        ])->assertInvalid(['transfer_account_id']);
    }

    public function test_it_hides_another_users_rule(): void
    {
        [$user] = $this->userWithAccount();
        $theirs = RecurringRule::factory()->for(User::factory())->create();

        $this->actingAs($user)->getJson("/api/v1/recurring-rules/{$theirs->id}")->assertNotFound();
    }
}
