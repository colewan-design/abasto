<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

/**
 * The guest demo exists so someone can look around before signing in. Its
 * sample data must never reach a personal account, and the personal account
 * must start clean.
 */
class GuestDemoTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake('receipts');
    }

    public function test_it_issues_a_token_for_a_demo_account_filled_with_sample_data(): void
    {
        $response = $this->postJson('/api/v1/auth/guest');

        $response->assertCreated()
            ->assertJsonPath('user.is_guest', true)
            ->assertJsonPath('user.onboarding_completed', true)
            ->assertJsonStructure(['token', 'token_type', 'user', 'needs_onboarding']);

        $guest = User::query()->where('is_demo', true)->sole();

        $this->assertGreaterThan(0, $guest->accounts()->count());
        $this->assertGreaterThan(0, $guest->categories()->count());
        $this->assertGreaterThan(0, $guest->transactions()->count());
        $this->assertGreaterThan(0, $guest->bills()->count());
        $this->assertGreaterThan(0, $guest->savingsGoals()->count());
        $this->assertGreaterThan(0, $guest->receipts()->count());
        $this->assertGreaterThan(0, $guest->budgets()->count());
    }

    public function test_every_sample_record_is_flagged_as_demo_data(): void
    {
        $this->postJson('/api/v1/auth/guest')->assertCreated();

        $guest = User::query()->where('is_demo', true)->sole();

        $this->assertSame(0, $guest->transactions()->where('is_demo', false)->count());
        $this->assertSame(0, $guest->receipts()->where('is_demo', false)->count());
    }

    public function test_a_guest_cannot_see_another_accounts_records(): void
    {
        $owner = User::factory()->create();
        $account = Account::factory()->for($owner)->create();
        $theirs = Transaction::factory()->of($owner, $account)->create(['merchant' => 'Private Shop']);

        $token = $this->postJson('/api/v1/auth/guest')->json('token');
        $headers = ['Authorization' => "Bearer {$token}"];

        $this->getJson('/api/v1/transactions?q=Private Shop', $headers)
            ->assertOk()
            ->assertJsonPath('meta.totals.count', 0);

        $this->getJson("/api/v1/transactions/{$theirs->id}", $headers)->assertNotFound();
        $this->getJson("/api/v1/accounts/{$account->id}", $headers)->assertNotFound();
    }

    public function test_resetting_rebuilds_the_sample_data(): void
    {
        $token = $this->postJson('/api/v1/auth/guest')->json('token');
        $headers = ['Authorization' => "Bearer {$token}"];

        $guest = User::query()->where('is_demo', true)->sole();
        $firstTransaction = $guest->transactions()->orderBy('id')->first();

        $this->postJson('/api/v1/demo/reset', [], $headers)
            ->assertOk()
            ->assertJsonPath('message', 'Demo data has been reset.');

        $this->assertDatabaseMissing('transactions', ['id' => $firstTransaction->id]);
        $this->assertGreaterThan(0, $guest->transactions()->count());
    }

    public function test_a_personal_account_cannot_reset_the_demo(): void
    {
        $user = User::factory()->create();

        $this->actingAs($user)->postJson('/api/v1/demo/reset')->assertForbidden();
    }

    public function test_a_new_personal_account_starts_with_nothing_recorded(): void
    {
        $response = $this->postJson('/api/v1/auth/register', [
            'name' => 'Ana',
            'email' => 'ana@example.com',
            'password' => 'correct-horse-battery',
            'password_confirmation' => 'correct-horse-battery',
        ]);

        $response->assertCreated()
            ->assertJsonPath('user.is_guest', false)
            ->assertJsonPath('needs_onboarding', true);

        $user = User::query()->where('email', 'ana@example.com')->sole();

        $this->assertSame(0, $user->accounts()->count());
        $this->assertSame(0, $user->transactions()->count());
        $this->assertNull($user->onboarding_completed_at);
    }

    public function test_deleting_an_account_removes_its_records(): void
    {
        $token = $this->postJson('/api/v1/auth/guest')->json('token');
        $guest = User::query()->where('is_demo', true)->sole();

        $this->deleteJson('/api/v1/me', ['confirm' => true], ['Authorization' => "Bearer {$token}"])
            ->assertOk();

        $this->assertDatabaseMissing('users', ['id' => $guest->id]);
        $this->assertDatabaseMissing('transactions', ['user_id' => $guest->id]);
        $this->assertDatabaseMissing('accounts', ['user_id' => $guest->id]);
    }
}
