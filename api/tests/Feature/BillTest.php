<?php

namespace Tests\Feature;

use App\Enums\BillPaymentStatus;
use App\Models\Bill;
use App\Models\User;
use App\Notifications\BillDueReminder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Notification;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

class BillTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    public function test_it_lists_upcoming_bills_with_days_until_due(): void
    {
        [$user] = $this->userWithAccount();
        Bill::factory()->for($user)->dueIn(5)->create(['name' => 'Electricity']);
        Bill::factory()->for($user)->dueIn(90)->create(['name' => 'Insurance']);

        $this->actingAs($user)
            ->getJson('/api/v1/bills/upcoming?days=30')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.name', 'Electricity')
            ->assertJsonPath('data.0.days_until_due', 5);
    }

    public function test_it_marks_an_overdue_bill(): void
    {
        [$user] = $this->userWithAccount();
        Bill::factory()->for($user)->overdue(3)->create();

        $this->actingAs($user)
            ->getJson('/api/v1/bills')
            ->assertOk()
            ->assertJsonPath('data.0.is_overdue', true)
            ->assertJsonPath('data.0.days_until_due', -3)
            ->assertJsonPath('meta.overdue_count', 1);
    }

    /**
     * Paying a bill records the expense and links the two, which is what stops
     * the same payment being entered a second time by hand.
     */
    public function test_paying_a_bill_records_a_linked_expense_and_moves_the_balance(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $utilities = $this->category($user, 'Utilities');
        $bill = Bill::factory()->for($user)->create([
            'name' => 'Electricity',
            'amount_minor' => 2_875_00,
            'category_id' => $utilities->id,
            'due_date' => today()->toDateString(),
        ]);

        $response = $this->actingAs($user)->postJson("/api/v1/bills/{$bill->id}/pay", [
            'account_id' => $account->id,
        ]);

        $response->assertCreated()->assertJsonPath('payment.status', BillPaymentStatus::Paid->value);

        $transactionId = $response->json('payment.transaction_id');

        $this->assertNotNull($transactionId);
        $this->assertDatabaseHas('transactions', [
            'id' => $transactionId,
            'bill_id' => $bill->id,
            'category_id' => $utilities->id,
            'amount_minor' => 2_875_00,
            'created_via' => 'bill',
        ]);

        $this->assertSame(10_000_00 - 2_875_00, $this->balanceOf($account));
    }

    public function test_paying_a_monthly_bill_advances_it_to_the_next_due_date(): void
    {
        [$user, $account] = $this->userWithAccount();
        $bill = Bill::factory()->for($user)->create(['due_date' => '2026-10-08']);

        $this->actingAs($user)
            ->postJson("/api/v1/bills/{$bill->id}/pay", ['account_id' => $account->id])
            ->assertCreated()
            ->assertJsonPath('bill.due_date', '2026-11-08');
    }

    public function test_paying_a_one_time_bill_closes_it(): void
    {
        [$user, $account] = $this->userWithAccount();
        $bill = Bill::factory()->for($user)->oneTime()->create();

        $this->actingAs($user)
            ->postJson("/api/v1/bills/{$bill->id}/pay", ['account_id' => $account->id])
            ->assertCreated()
            ->assertJsonPath('bill.is_active', false);
    }

    /**
     * For a payment the user already entered themselves, the bill is marked
     * paid without writing a second expense.
     */
    public function test_a_bill_can_be_marked_paid_without_recording_an_expense(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $bill = Bill::factory()->for($user)->create();

        $response = $this->actingAs($user)->postJson("/api/v1/bills/{$bill->id}/pay", [
            'account_id' => $account->id,
            'record_expense' => false,
        ]);

        $response->assertCreated()->assertJsonPath('payment.transaction_id', null);

        $this->assertDatabaseCount('transactions', 0);
        $this->assertSame(10_000_00, $this->balanceOf($account));
    }

    public function test_undoing_a_payment_removes_the_expense_and_restores_the_due_date(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $bill = Bill::factory()->for($user)->create(['due_date' => '2026-10-08', 'amount_minor' => 1_000_00]);

        $payment = $this->actingAs($user)
            ->postJson("/api/v1/bills/{$bill->id}/pay", ['account_id' => $account->id])
            ->json('payment');

        $this->actingAs($user)
            ->deleteJson("/api/v1/bills/{$bill->id}/payments/{$payment['id']}")
            ->assertOk()
            ->assertJsonPath('bill.due_date', '2026-10-08');

        $this->assertSoftDeleted('transactions', ['id' => $payment['transaction_id']]);
        $this->assertSame(10_000_00, $this->balanceOf($account));
    }

    public function test_skipping_a_cycle_advances_the_bill_without_recording_a_payment(): void
    {
        [$user] = $this->userWithAccount();
        $bill = Bill::factory()->for($user)->create(['due_date' => '2026-10-08']);

        $this->actingAs($user)
            ->postJson("/api/v1/bills/{$bill->id}/skip")
            ->assertOk()
            ->assertJsonPath('payment.status', BillPaymentStatus::Skipped->value)
            ->assertJsonPath('bill.due_date', '2026-11-08');

        $this->assertDatabaseCount('transactions', 0);
    }

    public function test_a_payment_cannot_be_undone_through_another_bill(): void
    {
        [$user, $account] = $this->userWithAccount();
        $bill = Bill::factory()->for($user)->create();
        $other = Bill::factory()->for($user)->create(['name' => 'Internet']);

        $payment = $this->actingAs($user)
            ->postJson("/api/v1/bills/{$bill->id}/pay", ['account_id' => $account->id])
            ->json('payment');

        $this->actingAs($user)
            ->deleteJson("/api/v1/bills/{$other->id}/payments/{$payment['id']}")
            ->assertNotFound();
    }

    public function test_it_hides_another_users_bill(): void
    {
        [$user] = $this->userWithAccount();
        $theirs = Bill::factory()->for(User::factory())->create();

        $this->actingAs($user)->getJson("/api/v1/bills/{$theirs->id}")->assertNotFound();
    }

    public function test_it_reminds_only_about_bills_inside_their_reminder_window(): void
    {
        Notification::fake();

        [$user] = $this->userWithAccount();
        Bill::factory()->for($user)->dueIn(2)->create(['name' => 'Due soon', 'reminder_days_before' => 3]);
        Bill::factory()->for($user)->dueIn(20)->create(['name' => 'Later', 'reminder_days_before' => 3]);

        $this->artisan('bills:remind')->assertSuccessful();

        Notification::assertSentTimes(BillDueReminder::class, 1);
    }

    public function test_it_does_not_remind_twice_for_the_same_cycle(): void
    {
        Notification::fake();

        [$user] = $this->userWithAccount();
        Bill::factory()->for($user)->dueIn(1)->create(['reminder_days_before' => 3]);

        $this->artisan('bills:remind')->assertSuccessful();
        $this->artisan('bills:remind')->assertSuccessful();

        Notification::assertSentTimes(BillDueReminder::class, 1);
    }

    public function test_the_reminder_states_when_the_bill_is_due(): void
    {
        [$user] = $this->userWithAccount();
        $bill = Bill::factory()->for($user)->dueIn(1)->create(['name' => 'Internet', 'amount_minor' => 1_699_00]);

        $payload = (new BillDueReminder($bill, 1))->toArray($user);

        $this->assertSame('bill.due', $payload['type']);
        $this->assertSame('Internet is due tomorrow', $payload['title']);

        $overdue = (new BillDueReminder($bill, -2))->toArray($user);

        $this->assertSame('bill.overdue', $overdue['type']);
        $this->assertSame('Internet is 2 days overdue', $overdue['title']);
    }
}
