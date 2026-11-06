<?php

namespace App\Services\Receipts;

use App\Enums\TransactionType;
use App\Models\Receipt;
use App\Models\Transaction;
use Illuminate\Support\Carbon;

/**
 * Warns when a receipt looks like one that was already recorded.
 *
 * Three checks, strongest first: the exact same image bytes, another receipt
 * with the same merchant, date and total, and an expense that was already
 * entered by hand for the same purchase. The result is a warning rather than a
 * block — people do buy the same coffee twice in a day.
 */
class DuplicateDetector
{
    /**
     * @return array{
     *     is_duplicate: bool,
     *     score: float,
     *     reason: string|null,
     *     receipt_id: int|null,
     *     transaction_id: int|null
     * }
     */
    public function check(Receipt $receipt): array
    {
        if ($match = $this->sameImage($receipt)) {
            return $this->result(1.0, 'Same photo was already uploaded.', receiptId: $match->getKey());
        }

        if ($receipt->total_minor === null) {
            return $this->clear();
        }

        if ($match = $this->similarReceipt($receipt)) {
            return $this->result(
                0.85,
                sprintf(
                    'A receipt from %s for %s on %s is already saved.',
                    $match->merchant ?? 'the same merchant',
                    $match->total()?->format() ?? '',
                    $match->receipt_date?->format('M j, Y') ?? 'the same date',
                ),
                receiptId: $match->getKey(),
            );
        }

        if ($match = $this->matchingTransaction($receipt)) {
            return $this->result(
                0.7,
                sprintf(
                    'You already recorded %s at %s on %s.',
                    $match->amount()->format(),
                    $match->merchant ?? 'this merchant',
                    $match->occurred_on->format('M j, Y'),
                ),
                transactionId: $match->getKey(),
            );
        }

        return $this->clear();
    }

    /**
     * Record the outcome on the receipt so the review screen and later reads
     * both see the same warning.
     */
    public function apply(Receipt $receipt): Receipt
    {
        $check = $this->check($receipt);

        $receipt->forceFill([
            'duplicate_of_receipt_id' => $check['receipt_id'],
            'duplicate_score' => $check['is_duplicate'] ? $check['score'] : null,
            'extracted' => array_merge($receipt->extracted ?? [], ['duplicate' => $check]),
        ])->saveQuietly();

        return $receipt;
    }

    private function sameImage(Receipt $receipt): ?Receipt
    {
        if ($receipt->content_hash === null) {
            return null;
        }

        return Receipt::query()
            ->withoutGlobalScopes()
            ->where('user_id', $receipt->user_id)
            ->where('content_hash', $receipt->content_hash)
            ->whereKeyNot($receipt->getKey())
            ->first();
    }

    private function similarReceipt(Receipt $receipt): ?Receipt
    {
        $tolerance = (int) max(1, abs($receipt->total_minor) * (float) config('budget.receipts.duplicate_amount_tolerance'));
        $window = (int) config('budget.receipts.duplicate_window_days');
        $date = $receipt->receipt_date ?? Carbon::parse($receipt->created_at);

        return Receipt::query()
            ->withoutGlobalScopes()
            ->where('user_id', $receipt->user_id)
            ->whereKeyNot($receipt->getKey())
            ->whereBetween('total_minor', [$receipt->total_minor - $tolerance, $receipt->total_minor + $tolerance])
            ->when(
                $receipt->merchant_normalized !== null,
                fn ($query) => $query->where('merchant_normalized', $receipt->merchant_normalized),
            )
            ->whereBetween('receipt_date', [
                $date->copy()->subDays($window)->toDateString(),
                $date->copy()->addDays($window)->toDateString(),
            ])
            ->first();
    }

    private function matchingTransaction(Receipt $receipt): ?Transaction
    {
        if ($receipt->merchant_normalized === null || $receipt->receipt_date === null) {
            return null;
        }

        $tolerance = (int) max(1, abs($receipt->total_minor) * (float) config('budget.receipts.duplicate_amount_tolerance'));

        return Transaction::query()
            ->withoutGlobalScopes()
            ->where('user_id', $receipt->user_id)
            ->where('type', TransactionType::Expense->value)
            ->whereNull('receipt_id')
            ->where('merchant_normalized', $receipt->merchant_normalized)
            ->whereBetween('amount_minor', [$receipt->total_minor - $tolerance, $receipt->total_minor + $tolerance])
            ->whereDate('occurred_on', $receipt->receipt_date)
            ->first();
    }

    /**
     * @return array{is_duplicate: bool, score: float, reason: string|null, receipt_id: int|null, transaction_id: int|null}
     */
    private function result(float $score, string $reason, ?int $receiptId = null, ?int $transactionId = null): array
    {
        return [
            'is_duplicate' => true,
            'score' => $score,
            'reason' => $reason,
            'receipt_id' => $receiptId,
            'transaction_id' => $transactionId,
        ];
    }

    /**
     * @return array{is_duplicate: bool, score: float, reason: null, receipt_id: null, transaction_id: null}
     */
    private function clear(): array
    {
        return [
            'is_duplicate' => false,
            'score' => 0.0,
            'reason' => null,
            'receipt_id' => null,
            'transaction_id' => null,
        ];
    }
}
