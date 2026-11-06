<?php

namespace Tests\Feature;

use App\Enums\ReceiptStatus;
use App\Jobs\ProcessReceipt;
use App\Models\MerchantCategoryRule;
use App\Models\Receipt;
use App\Models\ReceiptItem;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Queue;
use Illuminate\Support\Facades\Storage;
use Tests\Concerns\CreatesLedgerData;
use Tests\TestCase;

class ReceiptTest extends TestCase
{
    use CreatesLedgerData, RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();

        Storage::fake('receipts');
    }

    public function test_it_stores_the_image_and_queues_extraction(): void
    {
        Queue::fake();
        [$user] = $this->userWithAccount();

        $response = $this->actingAs($user)->postJson('/api/v1/receipts', [
            'image' => UploadedFile::fake()->image('receipt.jpg', 800, 1400),
        ]);

        $response->assertCreated()->assertJsonPath('data.status', ReceiptStatus::Pending->value);

        $receipt = Receipt::query()->withoutGlobalScopes()->sole();
        Storage::disk('receipts')->assertExists($receipt->image_path);

        Queue::assertPushed(ProcessReceipt::class, fn (ProcessReceipt $job): bool => $job->receiptId === $receipt->id);
    }

    /**
     * The Android app recognises text on-device and uploads it, which lets the
     * server produce a draft without calling a paid OCR service.
     */
    public function test_it_extracts_a_draft_from_text_recognised_on_the_device(): void
    {
        [$user] = $this->userWithAccount();
        $this->category($user, 'Groceries', ['bread', 'milk']);

        $response = $this->actingAs($user)->postJson('/api/v1/receipts', [
            'image' => UploadedFile::fake()->image('receipt.jpg'),
            'recognized_text' => "CORNER GROCERY\nDate: 2026-10-14\n\nWheat Bread 89.00\nFresh Milk 115.00\n\nTOTAL 204.00",
            'process' => true,
        ]);

        $response->assertCreated()
            ->assertJsonPath('data.status', ReceiptStatus::Processed->value)
            ->assertJsonPath('data.merchant', 'Corner Grocery')
            ->assertJsonPath('data.receipt_date', '2026-10-14')
            ->assertJsonPath('data.total_minor', 20400)
            ->assertJsonCount(2, 'data.items');
    }

    public function test_it_suggests_a_category_for_each_line_from_the_users_keywords(): void
    {
        [$user] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries', ['bread', 'milk']);
        $household = $this->category($user, 'Household Supplies', ['dish soap']);

        $response = $this->actingAs($user)->postJson('/api/v1/receipts', [
            'image' => UploadedFile::fake()->image('receipt.jpg'),
            'recognized_text' => "METRO MART\nDate: 2026-10-14\n\nWheat Bread 89.00\nDish Soap 139.00\n\nTOTAL 228.00",
            'process' => true,
        ]);

        $response->assertCreated();

        $this->assertDatabaseHas('receipt_items', ['description' => 'Wheat Bread', 'suggested_category_id' => $groceries->id]);
        $this->assertDatabaseHas('receipt_items', ['description' => 'Dish Soap', 'suggested_category_id' => $household->id]);
    }

    public function test_it_requires_an_image(): void
    {
        [$user] = $this->userWithAccount();

        $this->actingAs($user)
            ->postJson('/api/v1/receipts', [])
            ->assertInvalid(['image' => 'Attach a photo of the receipt.']);
    }

    public function test_it_rejects_a_file_that_is_not_an_image(): void
    {
        [$user] = $this->userWithAccount();

        $this->actingAs($user)
            ->postJson('/api/v1/receipts', ['image' => UploadedFile::fake()->create('statement.pdf', 100, 'application/pdf')])
            ->assertInvalid(['image']);
    }

    public function test_it_hides_another_users_receipt(): void
    {
        [$user] = $this->userWithAccount();
        $theirs = Receipt::factory()->for(User::factory())->create();

        $this->actingAs($user)->getJson("/api/v1/receipts/{$theirs->id}")->assertNotFound();
    }

    /**
     * The plan's worked example: one supermarket receipt covering groceries
     * and household supplies becomes a single expense split in two, with the
     * tax shared out so the splits still total the amount charged.
     */
    public function test_confirming_a_mixed_receipt_splits_it_across_categories(): void
    {
        [$user, $account] = $this->userWithAccount();
        $groceries = $this->category($user, 'Groceries');
        $household = $this->category($user, 'Household Supplies');

        $receipt = Receipt::factory()->for($user)->create([
            'merchant' => 'Metro Supermarket',
            'receipt_date' => '2026-10-14',
            'subtotal_minor' => 93600,
            'tax_minor' => 11232,
            'total_minor' => 104832,
        ]);

        ReceiptItem::factory()->for($receipt)->describing('Wheat Bread', 50_000)->create(['category_id' => $groceries->id]);
        ReceiptItem::factory()->for($receipt)->describing('Dish Soap', 43_600)->create(['category_id' => $household->id]);

        $response = $this->actingAs($user)->postJson("/api/v1/receipts/{$receipt->id}/confirm", [
            'account_id' => $account->id,
        ]);

        $response->assertCreated()
            ->assertJsonPath('transaction.amount_minor', 104832)
            ->assertJsonPath('transaction.has_splits', true)
            ->assertJsonCount(2, 'transaction.splits')
            ->assertJsonPath('receipt.status', ReceiptStatus::Confirmed->value);

        $splits = collect($response->json('transaction.splits'));

        // The ₱936.00 of items carries ₱112.32 of VAT, so each line is scaled
        // by 1.12 and the two splits still come to the ₱1,048.32 charged.
        $this->assertSame(104832, (int) $splits->sum('amount_minor'));
        $this->assertSame(56_000, (int) $splits->firstWhere('category_id', $groceries->id)['amount_minor']);
        $this->assertSame(48_832, (int) $splits->firstWhere('category_id', $household->id)['amount_minor']);
    }

    public function test_confirming_a_receipt_records_the_expense_against_the_account(): void
    {
        [$user, $account] = $this->userWithAccount(10_000_00);
        $receipt = Receipt::factory()->for($user)->total(280_00)->create(['merchant' => 'Daily Grind Coffee']);

        $this->actingAs($user)
            ->postJson("/api/v1/receipts/{$receipt->id}/confirm", ['account_id' => $account->id])
            ->assertCreated();

        $this->assertSame(10_000_00 - 280_00, $this->balanceOf($account));
        $this->assertDatabaseHas('transactions', [
            'receipt_id' => $receipt->id,
            'amount_minor' => 28000,
            'created_via' => 'receipt',
        ]);
    }

    public function test_it_refuses_to_confirm_the_same_receipt_twice(): void
    {
        [$user, $account] = $this->userWithAccount();
        $receipt = Receipt::factory()->for($user)->confirmed()->create();

        $this->actingAs($user)
            ->postJson("/api/v1/receipts/{$receipt->id}/confirm", ['account_id' => $account->id])
            ->assertInvalid(['receipt' => 'This receipt has already been saved.']);
    }

    /**
     * A duplicate is a warning rather than a block — people do buy the same
     * coffee twice — but it has to be acknowledged explicitly.
     */
    public function test_a_duplicate_receipt_must_be_acknowledged_before_it_is_saved(): void
    {
        [$user, $account] = $this->userWithAccount();
        $original = Receipt::factory()->for($user)->confirmed()->create([
            'merchant' => 'Metro Supermarket',
            'receipt_date' => '2026-10-14',
            'subtotal_minor' => 50_000,
            'tax_minor' => 0,
            'total_minor' => 50_000,
        ]);

        $duplicate = Receipt::factory()->for($user)->create([
            'merchant' => 'Metro Supermarket',
            'receipt_date' => '2026-10-14',
            'subtotal_minor' => 50_000,
            'tax_minor' => 0,
            'total_minor' => 50_000,
            'duplicate_of_receipt_id' => $original->id,
            'duplicate_score' => 0.85,
        ]);

        $this->actingAs($user)
            ->postJson("/api/v1/receipts/{$duplicate->id}/confirm", ['account_id' => $account->id])
            ->assertInvalid(['acknowledge_duplicate']);

        $this->assertDatabaseMissing('transactions', ['receipt_id' => $duplicate->id]);

        $this->actingAs($user)
            ->postJson("/api/v1/receipts/{$duplicate->id}/confirm", [
                'account_id' => $account->id,
                'acknowledge_duplicate' => true,
            ])
            ->assertCreated();

        $this->assertDatabaseHas('transactions', ['receipt_id' => $duplicate->id]);
    }

    public function test_it_flags_a_re_upload_of_the_same_photo_as_a_duplicate(): void
    {
        [$user] = $this->userWithAccount();
        $image = UploadedFile::fake()->image('receipt.jpg');

        $this->actingAs($user)->postJson('/api/v1/receipts', [
            'image' => $image,
            'recognized_text' => "CORNER GROCERY\nDate: 2026-10-14\nTOTAL 204.00",
            'process' => true,
        ])->assertCreated();

        $second = $this->actingAs($user)->postJson('/api/v1/receipts', [
            'image' => UploadedFile::fake()->image('receipt.jpg'),
            'recognized_text' => "CORNER GROCERY\nDate: 2026-10-14\nTOTAL 204.00",
            'process' => true,
        ]);

        $second->assertCreated()->assertJsonPath('data.duplicate.is_duplicate', true);
        $this->assertNotNull($second->json('data.duplicate.reason'));
    }

    /**
     * Correcting a suggestion is the training signal that makes the next
     * receipt from the same merchant land in the right place.
     */
    public function test_correcting_a_category_on_confirm_is_learned_for_next_time(): void
    {
        [$user, $account] = $this->userWithAccount();
        $suggested = $this->category($user, 'Groceries');
        $chosen = $this->category($user, 'Household Supplies');

        $receipt = Receipt::factory()->for($user)->total(139_00)->create(['merchant' => 'Home Essentials']);
        $item = ReceiptItem::factory()->for($receipt)->describing('Dish Soap', 139_00)->create([
            'suggested_category_id' => $suggested->id,
        ]);

        $this->actingAs($user)->postJson("/api/v1/receipts/{$receipt->id}/confirm", [
            'account_id' => $account->id,
            'items' => [['id' => $item->id, 'category_id' => $chosen->id]],
        ])->assertCreated();

        $this->assertDatabaseHas('merchant_category_rules', [
            'user_id' => $user->id,
            'merchant_pattern' => MerchantCategoryRule::normalize('Home Essentials'),
            'category_id' => $chosen->id,
        ]);

        $this->actingAs($user)
            ->postJson('/api/v1/categories/suggest', ['merchant' => 'Home Essentials'])
            ->assertOk()
            ->assertJsonPath('category.id', $chosen->id)
            ->assertJsonPath('source', 'learned');
    }

    public function test_it_requires_the_total_before_a_receipt_can_be_saved(): void
    {
        [$user, $account] = $this->userWithAccount();
        $receipt = Receipt::factory()->for($user)->pending()->create();

        $this->actingAs($user)
            ->postJson("/api/v1/receipts/{$receipt->id}/confirm", ['account_id' => $account->id])
            ->assertInvalid(['total_minor' => 'Enter the receipt total before saving.']);
    }

    public function test_the_image_route_rejects_an_unsigned_url(): void
    {
        [$user] = $this->userWithAccount();
        $receipt = Receipt::factory()->for($user)->create();

        $this->actingAs($user)
            ->get("/api/v1/receipts/{$receipt->id}/image")
            ->assertForbidden();
    }
}
