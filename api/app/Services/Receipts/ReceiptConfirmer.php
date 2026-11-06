<?php

namespace App\Services\Receipts;

use App\Enums\CreatedVia;
use App\Enums\ReceiptStatus;
use App\Enums\TransactionType;
use App\Models\Receipt;
use App\Models\ReceiptItem;
use App\Models\Transaction;
use App\Models\User;
use App\Services\CategorySuggester;
use App\Services\TransactionService;
use App\Support\Money;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

/**
 * Turns a reviewed receipt into an expense.
 *
 * The user's corrections are saved back onto the receipt (so the stored record
 * matches what they confirmed), fed to the suggester as training signal, and
 * used to build the transaction — including the category splits that let one
 * supermarket receipt land partly in Groceries and partly in Household
 * Supplies while remaining a single receipt record.
 */
class ReceiptConfirmer
{
    public function __construct(
        private readonly TransactionService $transactions,
        private readonly CategorySuggester $suggester,
    ) {}

    /**
     * @param  array{
     *     account_id: int,
     *     merchant?: string|null,
     *     receipt_date?: string|null,
     *     total_minor?: int|null,
     *     category_id?: int|null,
     *     note?: string|null,
     *     tags?: array<int, int>,
     *     items?: array<int, array{id: int, category_id?: int|null}>,
     *     splits?: array<int, array{category_id: int|null, amount_minor: int, note?: string|null}>,
     *     acknowledge_duplicate?: bool
     * }  $data
     */
    public function confirm(User $user, Receipt $receipt, array $data): Transaction
    {
        if ($receipt->status === ReceiptStatus::Confirmed) {
            throw ValidationException::withMessages([
                'receipt' => 'This receipt has already been saved.',
            ]);
        }

        if ($receipt->isDuplicate() && ! ($data['acknowledge_duplicate'] ?? false)) {
            throw ValidationException::withMessages([
                'acknowledge_duplicate' => $receipt->extracted['duplicate']['reason']
                    ?? 'This looks like a receipt you have already saved.',
            ]);
        }

        return DB::transaction(function () use ($user, $receipt, $data): Transaction {
            $this->applyCorrections($receipt, $data);
            $this->applyItemCategories($user, $receipt, $data['items'] ?? []);

            $total = $receipt->total_minor;

            if ($total === null || $total === 0) {
                throw ValidationException::withMessages([
                    'total_minor' => 'Enter the receipt total before saving.',
                ]);
            }

            $splits = $data['splits'] ?? $this->splitsFromItems($receipt, abs($total));
            $category = $data['category_id'] ?? $this->primaryCategory($user, $receipt, $splits);

            $transaction = $this->transactions->create($user, [
                'account_id' => $data['account_id'],
                'category_id' => $category,
                'type' => TransactionType::Expense->value,
                'amount_minor' => abs($total),
                'occurred_on' => ($receipt->receipt_date ?? today())->toDateString(),
                'merchant' => $receipt->merchant,
                'note' => $data['note'] ?? null,
                'receipt_id' => $receipt->getKey(),
                'tags' => $data['tags'] ?? [],
                'splits' => count($splits) > 1 ? $splits : [],
                'created_via' => CreatedVia::Receipt->value,
            ]);

            $receipt->forceFill([
                'status' => ReceiptStatus::Confirmed->value,
                'confirmed_at' => now(),
            ])->saveQuietly();

            return $transaction;
        });
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function applyCorrections(Receipt $receipt, array $data): void
    {
        foreach (['merchant', 'receipt_date', 'total_minor', 'currency_code', 'payment_method'] as $field) {
            if (array_key_exists($field, $data) && $data[$field] !== null) {
                $receipt->{$field} = $data[$field];
            }
        }

        $receipt->currency_code ??= $receipt->user->currency_code;
        $receipt->save();
    }

    /**
     * Save the category the user picked for each line and learn from it.
     *
     * @param  array<int, array{id: int, category_id?: int|null}>  $items
     */
    private function applyItemCategories(User $user, Receipt $receipt, array $items): void
    {
        if ($items === []) {
            return;
        }

        $lines = $receipt->items()->get()->keyBy('id');

        foreach ($items as $input) {
            /** @var ReceiptItem|null $line */
            $line = $lines->get($input['id']);

            if ($line === null) {
                continue;
            }

            $line->forceFill(['category_id' => $input['category_id'] ?? null])->save();

            if (($input['category_id'] ?? null) !== null) {
                $this->suggester->learn(
                    $user,
                    $line->description,
                    $input['category_id'],
                    $line->suggested_category_id,
                );
            }
        }

        $receipt->load('items');
    }

    /**
     * Group the line items by category and scale the groups up to the receipt
     * total, so tax and discounts are shared out and the splits always sum to
     * the amount charged.
     *
     * @return array<int, array{category_id: int|null, amount_minor: int, note: string|null}>
     */
    private function splitsFromItems(Receipt $receipt, int $total): array
    {
        $byCategory = $receipt->items
            ->groupBy(fn (ReceiptItem $item): string => (string) ($item->effectiveCategoryId() ?? ''))
            ->map(fn ($items): int => (int) $items->sum('total_minor'))
            ->filter(fn (int $amount): bool => $amount > 0);

        if ($byCategory->count() < 2) {
            return [];
        }

        $allocated = Money::ofMinor($total, $receipt->currency_code)->allocate($byCategory->all());

        return collect($allocated)
            ->map(fn (Money $money, string $categoryId): array => [
                'category_id' => $categoryId === '' ? null : (int) $categoryId,
                'amount_minor' => $money->minor,
                'note' => null,
            ])
            ->values()
            ->all();
    }

    /**
     * @param  array<int, array{category_id: int|null, amount_minor: int, note?: string|null}>  $splits
     */
    private function primaryCategory(User $user, Receipt $receipt, array $splits): ?int
    {
        if ($splits !== []) {
            $largest = collect($splits)->sortByDesc('amount_minor')->first();

            return $largest['category_id'] ?? null;
        }

        $fromItems = $receipt->items
            ->map(fn (ReceiptItem $item): ?int => $item->effectiveCategoryId())
            ->filter()
            ->first();

        return $fromItems ?? $this->suggester->suggestForMerchant($user, $receipt->merchant)['category_id'];
    }
}
