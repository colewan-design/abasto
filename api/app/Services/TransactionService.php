<?php

namespace App\Services;

use App\Enums\CreatedVia;
use App\Enums\TransactionDirection;
use App\Enums\TransactionType;
use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use App\Support\Money;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

/**
 * Writes to the ledger.
 *
 * Every mutation runs in a transaction and keeps three things in step: the
 * stored rows, the owning account balances, and the derived state that depends
 * on them (splits, tags, savings progress, budget alerts).
 */
class TransactionService
{
    public function __construct(
        private readonly AccountBalanceService $balances,
        private readonly CategorySuggester $suggester,
        private readonly BudgetService $budgets,
        private readonly SavingsGoalService $savingsGoals,
    ) {}

    /**
     * Record an income or expense.
     *
     * @param  array{
     *     account_id: int,
     *     category_id?: int|null,
     *     type: string,
     *     amount_minor: int,
     *     occurred_on: string,
     *     occurred_at?: string|null,
     *     merchant?: string|null,
     *     note?: string|null,
     *     receipt_id?: int|null,
     *     bill_id?: int|null,
     *     savings_goal_id?: int|null,
     *     recurring_rule_id?: int|null,
     *     tags?: array<int, int>,
     *     splits?: array<int, array{category_id: int|null, amount_minor: int, note?: string|null}>,
     *     created_via?: string
     * }  $data
     */
    public function create(User $user, array $data): Transaction
    {
        $type = TransactionType::from($data['type']);

        if ($type === TransactionType::Transfer) {
            throw ValidationException::withMessages([
                'type' => 'Use the transfer endpoint to move money between accounts.',
            ]);
        }

        $account = $this->resolveAccount($user, $data['account_id']);

        return DB::transaction(function () use ($user, $data, $type, $account): Transaction {
            $transaction = new Transaction;

            $transaction->fill([
                'account_id' => $account->getKey(),
                'category_id' => $data['category_id'] ?? null,
                'type' => $type->value,
                'direction' => $type->defaultDirection()->value,
                'amount_minor' => abs($data['amount_minor']),
                'currency_code' => $account->currency_code,
                'occurred_on' => $data['occurred_on'],
                'occurred_at' => $data['occurred_at'] ?? null,
                'merchant' => $data['merchant'] ?? null,
                'note' => $data['note'] ?? null,
                'receipt_id' => $data['receipt_id'] ?? null,
                'bill_id' => $data['bill_id'] ?? null,
                'savings_goal_id' => $data['savings_goal_id'] ?? null,
                'recurring_rule_id' => $data['recurring_rule_id'] ?? null,
                'created_via' => $data['created_via'] ?? CreatedVia::Manual->value,
                'is_demo' => $user->is_demo,
            ]);

            $transaction->user_id = $user->getKey();
            $transaction->save();

            $this->syncSplits($transaction, $data['splits'] ?? []);
            $this->syncTags($transaction, $data['tags'] ?? null);

            $this->balances->apply($transaction);
            $this->afterWrite($user, $transaction);

            return $transaction->refresh();
        });
    }

    /**
     * @param  array<string, mixed>  $data
     */
    public function update(Transaction $transaction, array $data): Transaction
    {
        $user = $transaction->user;

        return DB::transaction(function () use ($transaction, $data, $user): Transaction {
            $before = [
                'account_id' => $transaction->account_id,
                'signed_minor' => $transaction->signedMinor(),
                'category_id' => $transaction->category_id,
                'merchant' => $transaction->merchant,
            ];

            if (array_key_exists('account_id', $data)) {
                $account = $this->resolveAccount($user, $data['account_id']);
                $transaction->account_id = $account->getKey();
                $transaction->currency_code = $account->currency_code;
            }

            foreach (['category_id', 'occurred_on', 'occurred_at', 'merchant', 'note', 'savings_goal_id'] as $field) {
                if (array_key_exists($field, $data)) {
                    $transaction->{$field} = $data[$field];
                }
            }

            if (array_key_exists('amount_minor', $data)) {
                $transaction->amount_minor = abs($data['amount_minor']);
            }

            $transaction->save();

            if (array_key_exists('splits', $data)) {
                $this->syncSplits($transaction, $data['splits'] ?? []);
            }

            if (array_key_exists('tags', $data)) {
                $this->syncTags($transaction, $data['tags']);
            }

            $this->balances->move($before, $transaction);

            if ($transaction->transfer_group !== null) {
                $this->syncTransferCounterpart($transaction);
            }

            $this->afterWrite($user, $transaction, correctedFrom: $before['category_id']);

            return $transaction->refresh();
        });
    }

    public function delete(Transaction $transaction): void
    {
        DB::transaction(function () use ($transaction): void {
            $counterpart = $transaction->transferCounterpart();

            $this->balances->revert($transaction);
            $transaction->delete();

            if ($counterpart !== null) {
                $this->balances->revert($counterpart);
                $counterpart->delete();
            }

            if ($transaction->savings_goal_id !== null) {
                $this->savingsGoals->recalculate($transaction->savingsGoal);
            }
        });
    }

    /**
     * Move money between two of the user's own accounts.
     *
     * Stored as a pair of rows sharing a `transfer_group`: an outflow on the
     * source account and an inflow on the destination. Reports skip
     * `type = transfer`, so a transfer never shows up as income or expense.
     *
     * @param  array{
     *     from_account_id: int,
     *     to_account_id: int,
     *     amount_minor: int,
     *     occurred_on: string,
     *     note?: string|null,
     *     savings_goal_id?: int|null,
     *     created_via?: string
     * }  $data
     * @return array{outflow: Transaction, inflow: Transaction}
     */
    public function createTransfer(User $user, array $data): array
    {
        $from = $this->resolveAccount($user, $data['from_account_id']);
        $to = $this->resolveAccount($user, $data['to_account_id']);

        if ($from->is($to)) {
            throw ValidationException::withMessages([
                'to_account_id' => 'Choose a different account to transfer into.',
            ]);
        }

        if ($from->currency_code !== $to->currency_code) {
            throw ValidationException::withMessages([
                'to_account_id' => "Both accounts must use the same currency. {$from->name} is in {$from->currency_code} and {$to->name} is in {$to->currency_code}.",
            ]);
        }

        return DB::transaction(function () use ($user, $data, $from, $to): array {
            $group = (string) Str::uuid();
            $amount = abs($data['amount_minor']);

            $outflow = $this->writeTransferLeg($user, [
                'account_id' => $from->getKey(),
                'transfer_account_id' => $to->getKey(),
                'direction' => TransactionDirection::Outflow,
                'amount_minor' => $amount,
                'currency_code' => $from->currency_code,
                'transfer_group' => $group,
                'occurred_on' => $data['occurred_on'],
                'note' => $data['note'] ?? null,
                'savings_goal_id' => $data['savings_goal_id'] ?? null,
                'merchant' => $to->name,
                'created_via' => $data['created_via'] ?? CreatedVia::Transfer->value,
            ]);

            $inflow = $this->writeTransferLeg($user, [
                'account_id' => $to->getKey(),
                'transfer_account_id' => $from->getKey(),
                'direction' => TransactionDirection::Inflow,
                'amount_minor' => $amount,
                'currency_code' => $to->currency_code,
                'transfer_group' => $group,
                'occurred_on' => $data['occurred_on'],
                'note' => $data['note'] ?? null,
                'savings_goal_id' => $data['savings_goal_id'] ?? null,
                'merchant' => $from->name,
                'created_via' => $data['created_via'] ?? CreatedVia::Transfer->value,
            ]);

            $this->balances->apply($outflow);
            $this->balances->apply($inflow);

            if (($data['savings_goal_id'] ?? null) !== null) {
                $this->savingsGoals->syncFromTransfer($inflow);
            }

            return ['outflow' => $outflow, 'inflow' => $inflow];
        });
    }

    /**
     * Attach a transaction to a receipt and split it across categories.
     *
     * @param  array<int, array{category_id: int|null, amount_minor: int, note?: string|null}>  $splits
     */
    public function syncSplits(Transaction $transaction, array $splits): void
    {
        $transaction->splits()->delete();

        $splits = array_values(array_filter($splits, fn (array $split): bool => (int) $split['amount_minor'] !== 0));

        if ($splits === []) {
            $transaction->forceFill(['has_splits' => false])->saveQuietly();

            return;
        }

        $total = array_sum(array_column($splits, 'amount_minor'));

        if (abs($total) !== $transaction->amount_minor) {
            throw ValidationException::withMessages([
                'splits' => sprintf(
                    'The splits add up to %s but the transaction is %s.',
                    Money::ofMinor(abs($total), $transaction->currency_code)->format(),
                    $transaction->amount()->format(),
                ),
            ]);
        }

        foreach ($splits as $index => $split) {
            $transaction->splits()->create([
                'category_id' => $split['category_id'] ?? null,
                'amount_minor' => abs($split['amount_minor']),
                'note' => $split['note'] ?? null,
                'sort_order' => $index,
            ]);
        }

        $largest = collect($splits)->sortByDesc(fn (array $split): int => abs($split['amount_minor']))->first();

        $transaction->forceFill([
            'has_splits' => true,
            'category_id' => $largest['category_id'] ?? $transaction->category_id,
        ])->saveQuietly();
    }

    /**
     * @param  array<int, int>|null  $tagIds
     */
    private function syncTags(Transaction $transaction, ?array $tagIds): void
    {
        if ($tagIds === null) {
            return;
        }

        $owned = $transaction->user->tags()->whereKey($tagIds)->pluck('id')->all();

        $transaction->tags()->sync($owned);
    }

    /**
     * @param  array<string, mixed>  $attributes
     */
    private function writeTransferLeg(User $user, array $attributes): Transaction
    {
        $transaction = new Transaction;

        $transaction->fill([
            'account_id' => $attributes['account_id'],
            'transfer_account_id' => $attributes['transfer_account_id'],
            'type' => TransactionType::Transfer->value,
            'direction' => $attributes['direction']->value,
            'amount_minor' => $attributes['amount_minor'],
            'currency_code' => $attributes['currency_code'],
            'occurred_on' => $attributes['occurred_on'],
            'merchant' => $attributes['merchant'],
            'note' => $attributes['note'],
            'transfer_group' => $attributes['transfer_group'],
            'savings_goal_id' => $attributes['savings_goal_id'],
            'created_via' => $attributes['created_via'],
            'is_demo' => $user->is_demo,
        ]);

        $transaction->user_id = $user->getKey();
        $transaction->save();

        return $transaction;
    }

    /**
     * Mirror amount, date and note changes onto the other leg of a transfer.
     */
    private function syncTransferCounterpart(Transaction $transaction): void
    {
        $counterpart = $transaction->transferCounterpart();

        if ($counterpart === null) {
            return;
        }

        $before = [
            'account_id' => $counterpart->account_id,
            'signed_minor' => $counterpart->signedMinor(),
        ];

        $counterpart->forceFill([
            'amount_minor' => $transaction->amount_minor,
            'occurred_on' => $transaction->occurred_on,
            'note' => $transaction->note,
        ])->save();

        $this->balances->move($before, $counterpart);
    }

    private function resolveAccount(User $user, int $accountId): Account
    {
        $account = $user->accounts()->whereKey($accountId)->first();

        if ($account === null) {
            throw ValidationException::withMessages([
                'account_id' => 'That account does not exist.',
            ]);
        }

        return $account;
    }

    /**
     * Derived work that follows any income or expense write: learn the
     * merchant mapping, refresh savings progress, and check budget limits.
     */
    private function afterWrite(User $user, Transaction $transaction, ?int $correctedFrom = null): void
    {
        if ($transaction->merchant !== null && $transaction->category_id !== null) {
            $this->suggester->learn($user, $transaction->merchant, $transaction->category_id, $correctedFrom);
        }

        if ($transaction->savings_goal_id !== null) {
            $this->savingsGoals->recalculate($transaction->savingsGoal);
        }

        if ($transaction->type === TransactionType::Expense) {
            $this->budgets->checkThresholds($user, $transaction->occurred_on);
        }
    }
}
