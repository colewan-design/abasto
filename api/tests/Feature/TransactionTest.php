<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

class TransactionTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    public function test_it_rejects_a_request_without_a_token(): void
    {
        $this->getJson('/api/v1/transactions')->assertUnauthorized();
    }

    public function test_it_records_an_expense_and_reduces_the_account_balance(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $groceries = $this->category($user, 'Groceries');

        $response = $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'category_id' => $groceries->id,
            'amount' => '432.50',
            'occurred_on' => '2026-10-14',
            'merchant' => 'Corner Grocery',
        ]);

        $response->assertCreated()
            ->assertJsonPath('data.amount_minor', 43250)
            ->assertJsonPath('data.direction', 'outflow')
            ->assertJsonPath('data.formatted_amount', '₱432.50');

        $this->assertDatabaseHas('transactions', [
            'user_id' => $user->id,
            'account_id' => $account->id,
            'amount_minor' => 43250,
            'type' => 'expense',
            'merchant' => 'Corner Grocery',
        ]);

        $this->assertSame(10_000_00 - 43250, $this->balanceOf($account));
    }

    public function test_it_records_income_as_an_inflow(): void
    {
        [$user, $account] = $this->userWithAccount(0);
        $salary = $this->incomeCategory($user);

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'income',
            'account_id' => $account->id,
            'category_id' => $salary->id,
            'amount' => '27500',
            'occurred_on' => '2026-10-15',
        ])->assertCreated()->assertJsonPath('data.direction', 'inflow');

        $this->assertSame(27_500_00, $this->balanceOf($account));
    }

    public function test_it_requires_an_amount_an_account_and_a_date(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)
            ->postJson('/api/v1/transactions', [])
            ->assertUnprocessable()
            ->assertInvalid(['type', 'account_id', 'amount_minor', 'occurred_on']);
    }

    public function test_it_rejects_an_amount_of_zero(): void
    {
        [$user, $account] = $this->userWithAccount();

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'amount' => '0',
            'occurred_on' => '2026-10-14',
        ])->assertInvalid(['amount_minor' => 'The amount must be greater than zero.']);
    }

    public function test_it_refuses_an_account_belonging_to_another_user(): void
    {
        [$user] = $this->userWithAccount();
        $stranger = Account::factory()->for(User::factory())->create();

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $stranger->id,
            'amount' => '10',
            'occurred_on' => '2026-10-14',
        ])->assertInvalid(['account_id']);

        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_it_hides_another_users_transaction(): void
    {
        [$user] = $this->userWithAccount();
        $theirs = Transaction::factory()->for(User::factory())->create();

        $this->actingAs($user)->getJson("/api/v1/transactions/{$theirs->id}")->assertNotFound();
    }

    public function test_it_lists_only_the_authenticated_users_transactions(): void
    {
        [$user, $account] = $this->userWithAccount();
        Transaction::factory()->of($user, $account)->count(3)->create();
        Transaction::factory()->for(User::factory())->count(2)->create();

        $this->actingAs($user)
            ->getJson('/api/v1/transactions')
            ->assertOk()
            ->assertJsonCount(3, 'data')
            ->assertJsonPath('meta.totals.count', 3);
    }

    public function test_it_splits_a_transaction_across_categories(): void
    {
        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');
        $household = $this->category($user, 'Household Supplies');

        $response = $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'amount' => '1048.32',
            'occurred_on' => '2026-10-14',
            'merchant' => 'Metro Supermarket',
            'splits' => [
                ['category_id' => $groceries->id, 'amount' => '680.00'],
                ['category_id' => $household->id, 'amount' => '368.32'],
            ],
        ]);

        $response->assertCreated()
            ->assertJsonPath('data.has_splits', true)
            ->assertJsonCount(2, 'data.splits');

        // The larger split becomes the headline category for list views.
        $response->assertJsonPath('data.category_id', $groceries->id);

        $this->assertDatabaseHas('transaction_splits', ['category_id' => $groceries->id, 'amount_minor' => 68000]);
        $this->assertDatabaseHas('transaction_splits', ['category_id' => $household->id, 'amount_minor' => 36832]);
    }

    public function test_it_refuses_splits_that_do_not_add_up_to_the_transaction(): void
    {
        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'amount' => '1000.00',
            'occurred_on' => '2026-10-14',
            'splits' => [['category_id' => $groceries->id, 'amount' => '600.00']],
        ])->assertInvalid(['splits' => 'The splits add up to ₱600.00 but the transaction is ₱1,000.00.']);

        $this->assertDatabaseCount('transaction_splits', 0);
    }

    public function test_it_moves_the_balance_when_the_amount_changes(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $transaction = Transaction::factory()->of($user, $account)->expense()->amount(100_00)->create();
        $account->refresh();
        $this->travelTo(now());

        $this->actingAs($user)
            ->patchJson("/api/v1/transactions/{$transaction->id}", ['amount' => '250.00'])
            ->assertOk()
            ->assertJsonPath('data.amount_minor', 25000);

        // The factory writes the row without touching balances, so only the
        // difference applied by the update is observable here.
        $this->assertSame(10_000_00 - 150_00, $this->balanceOf($account->refresh()));
    }

    public function test_it_restores_the_balance_when_a_transaction_is_deleted(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);

        $created = $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'expense',
            'account_id' => $account->id,
            'amount' => '500.00',
            'occurred_on' => '2026-10-14',
        ])->json('data.id');

        $this->assertSame(9_500_00, $this->balanceOf($account));

        $this->actingAs($user)->deleteJson("/api/v1/transactions/{$created}")->assertOk();

        $this->assertSame(10_000_00, $this->balanceOf($account));
        $this->assertSoftDeleted('transactions', ['id' => $created]);
    }

    public function test_it_searches_by_merchant_and_note(): void
    {
        [$user, $account] = $this->userWithAccount();
        Transaction::factory()->of($user, $account)->create(['merchant' => 'Daily Grind Coffee']);
        Transaction::factory()->of($user, $account)->create(['merchant' => 'Shell Station']);

        $this->actingAs($user)
            ->getJson('/api/v1/transactions?q=coffee')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.merchant', 'Daily Grind Coffee');
    }

    public function test_it_filters_by_date_range(): void
    {
        [$user, $account] = $this->userWithAccount();
        Transaction::factory()->of($user, $account)->on('2026-09-10')->create();
        Transaction::factory()->of($user, $account)->on('2026-10-10')->create();

        $this->actingAs($user)
            ->getJson('/api/v1/transactions?from=2026-10-01&to=2026-10-31')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.occurred_on', '2026-10-10');
    }

    public function test_the_transfer_endpoint_is_the_only_way_to_create_a_transfer(): void
    {
        [$user, $account] = $this->userWithAccount();

        $this->actingAs($user)->postJson('/api/v1/transactions', [
            'type' => 'transfer',
            'account_id' => $account->id,
            'amount' => '100',
            'occurred_on' => '2026-10-14',
        ])->assertInvalid(['type']);
    }
}
