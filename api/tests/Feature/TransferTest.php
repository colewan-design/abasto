<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

/**
 * Transfers are the feature most likely to corrupt a budget, because the
 * obvious implementation counts the same money twice.
 */
class TransferTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    private function twoAccounts(): array
    {
        $user = User::factory()->create();

        $from = Account::factory()->for($user)->withOpeningBalance(10_000_00)->create(['name' => 'Savings']);
        $to = Account::factory()->for($user)->cash()->withOpeningBalance(500_00)->create();

        return [$user, $from, $to];
    }

    public function test_it_writes_both_legs_and_moves_both_balances(): void
    {
        [$user, $from, $to] = $this->twoAccounts();

        $response = $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $to->id,
            'amount' => '1500.00',
            'occurred_on' => '2026-10-14',
        ]);

        $response->assertCreated()
            ->assertJsonPath('legs.outflow.direction', 'outflow')
            ->assertJsonPath('legs.inflow.direction', 'inflow')
            ->assertJsonPath('legs.outflow.amount_minor', 150000)
            ->assertJsonPath('legs.inflow.amount_minor', 150000);

        $this->assertSame(8_500_00, $this->balanceOf($from));
        $this->assertSame(2_000_00, $this->balanceOf($to));

        $this->assertDatabaseCount('transactions', 2);
    }

    public function test_both_legs_share_one_transfer_group(): void
    {
        [$user, $from, $to] = $this->twoAccounts();

        $response = $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $to->id,
            'amount' => '100',
            'occurred_on' => '2026-10-14',
        ]);

        $this->assertSame(
            $response->json('legs.outflow.transfer_group'),
            $response->json('legs.inflow.transfer_group'),
        );
    }

    /**
     * This is the whole point of the pairing: moving money between your own
     * accounts must not appear as income or as spending.
     */
    public function test_a_transfer_is_excluded_from_income_and_expenses(): void
    {
        [$user, $from, $to] = $this->twoAccounts();

        $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $to->id,
            'amount' => '1500.00',
            'occurred_on' => today()->toDateString(),
        ])->assertCreated();

        $this->actingAs($user)
            ->getJson('/api/v1/reports/cash-flow')
            ->assertOk()
            ->assertJsonPath('data.totals.income_minor', 0)
            ->assertJsonPath('data.totals.expense_minor', 0)
            ->assertJsonPath('data.totals.net_minor', 0);
    }

    public function test_a_transfer_is_excluded_from_the_category_report(): void
    {
        [$user, $from, $to] = $this->twoAccounts();

        $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $to->id,
            'amount' => '1500.00',
            'occurred_on' => today()->toDateString(),
        ])->assertCreated();

        $this->actingAs($user)
            ->getJson('/api/v1/reports/categories')
            ->assertOk()
            ->assertJsonCount(0, 'data');
    }

    /**
     * The transactions list shows one row per transfer, but an account's own
     * ledger has to show the leg that touched that account.
     */
    public function test_the_list_collapses_a_transfer_to_one_row_while_an_account_shows_its_own_leg(): void
    {
        [$user, $from, $to] = $this->twoAccounts();

        $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $to->id,
            'amount' => '1500.00',
            'occurred_on' => '2026-10-14',
        ])->assertCreated();

        $this->actingAs($user)
            ->getJson('/api/v1/transactions')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.direction', 'outflow');

        $this->actingAs($user)
            ->getJson("/api/v1/accounts/{$to->id}/transactions")
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.direction', 'inflow');
    }

    public function test_deleting_one_leg_removes_the_other_and_restores_both_balances(): void
    {
        [$user, $from, $to] = $this->twoAccounts();

        $outflow = $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $to->id,
            'amount' => '1500.00',
            'occurred_on' => '2026-10-14',
        ])->json('legs.outflow.id');

        $this->actingAs($user)->deleteJson("/api/v1/transactions/{$outflow}")->assertOk();

        $this->assertSame(10_000_00, $this->balanceOf($from));
        $this->assertSame(500_00, $this->balanceOf($to));
        $this->assertSame(0, $this->actingAs($user)->getJson('/api/v1/transactions')->json('meta.totals.count'));
    }

    public function test_it_refuses_a_transfer_to_the_same_account(): void
    {
        [$user, $from] = $this->twoAccounts();

        $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $from->id,
            'amount' => '100',
            'occurred_on' => '2026-10-14',
        ])->assertInvalid(['to_account_id' => 'Choose a different account to transfer into.']);
    }

    public function test_it_refuses_a_transfer_between_different_currencies(): void
    {
        $user = User::factory()->create();
        $peso = Account::factory()->for($user)->create(['currency_code' => 'PHP', 'name' => 'Peso']);
        $dollar = Account::factory()->for($user)->create(['currency_code' => 'USD', 'name' => 'Dollar']);

        $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $peso->id,
            'to_account_id' => $dollar->id,
            'amount' => '100',
            'occurred_on' => '2026-10-14',
        ])->assertInvalid(['to_account_id']);

        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_it_refuses_an_account_belonging_to_another_user(): void
    {
        [$user, $from] = $this->twoAccounts();
        $stranger = Account::factory()->for(User::factory())->create();

        $this->actingAs($user)->postJson('/api/v1/transfers', [
            'from_account_id' => $from->id,
            'to_account_id' => $stranger->id,
            'amount' => '100',
            'occurred_on' => '2026-10-14',
        ])->assertInvalid(['to_account_id']);

        $this->assertDatabaseCount('transactions', 0);
    }
}
