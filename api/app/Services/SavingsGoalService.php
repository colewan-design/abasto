<?php

namespace App\Services;

use App\Enums\CreatedVia;
use App\Enums\TransactionType;
use App\Models\SavingsContribution;
use App\Models\SavingsGoal;
use App\Models\Transaction;
use App\Models\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

/**
 * Savings goals and the contributions that fund them.
 *
 * A contribution is recorded two ways depending on how the user keeps their
 * money: as a transfer into the account linked to the goal, or — when there is
 * no separate account — as an expense stamped with `savings_goal_id`. Either
 * way the amount is excluded from spending reports, so putting money aside
 * never looks like spending it.
 */
class SavingsGoalService
{
    public function __construct(
        private readonly AccountBalanceService $balances,
    ) {}

    /**
     * @param  array{
     *     amount_minor: int,
     *     contributed_on: string,
     *     from_account_id?: int|null,
     *     note?: string|null
     * }  $data
     */
    public function contribute(User $user, SavingsGoal $goal, array $data): SavingsContribution
    {
        return DB::transaction(function () use ($user, $goal, $data): SavingsContribution {
            $amount = abs($data['amount_minor']);
            $transaction = null;

            if (($data['from_account_id'] ?? null) !== null) {
                $transaction = $goal->account_id !== null
                    ? $this->recordTransfer($user, $goal, $data, $amount)
                    : $this->recordAllocation($user, $goal, $data, $amount);
            }

            $contribution = $goal->contributions()->create([
                'transaction_id' => $transaction?->getKey(),
                'amount_minor' => $amount,
                'contributed_on' => $data['contributed_on'],
                'note' => $data['note'] ?? null,
            ]);

            $this->recalculate($goal);

            return $contribution;
        });
    }

    public function removeContribution(SavingsContribution $contribution): void
    {
        DB::transaction(function () use ($contribution): void {
            $goal = $contribution->goal;
            $transaction = $contribution->transaction;

            if ($transaction !== null) {
                $counterpart = $transaction->transferCounterpart();

                $this->balances->revert($transaction);
                $transaction->delete();

                if ($counterpart !== null) {
                    $this->balances->revert($counterpart);
                    $counterpart->delete();
                }
            }

            $contribution->delete();

            $this->recalculate($goal);
        });
    }

    /**
     * Recompute a goal's saved amount from its contributions.
     */
    public function recalculate(?SavingsGoal $goal): void
    {
        if ($goal === null) {
            return;
        }

        $total = (int) $goal->contributions()->sum('amount_minor');

        $goal->forceFill([
            'current_amount_minor' => $total,
            'achieved_at' => $total >= $goal->target_amount_minor
                ? ($goal->achieved_at ?? now())
                : null,
        ])->saveQuietly();
    }

    /**
     * Create the contribution record for a transfer that was made directly
     * into a goal's account.
     */
    public function syncFromTransfer(Transaction $inflow): void
    {
        if ($inflow->savings_goal_id === null) {
            return;
        }

        $goal = $inflow->savingsGoal;

        if ($goal === null || $goal->contributions()->where('transaction_id', $inflow->getKey())->exists()) {
            return;
        }

        $goal->contributions()->create([
            'transaction_id' => $inflow->getKey(),
            'amount_minor' => $inflow->amount_minor,
            'contributed_on' => $inflow->occurred_on,
            'note' => $inflow->note,
        ]);

        $this->recalculate($goal);
    }

    /**
     * @param  array<string, mixed>  $data
     */
    private function recordTransfer(User $user, SavingsGoal $goal, array $data, int $amount): Transaction
    {
        $group = (string) Str::uuid();

        $outflow = $this->leg($user, [
            'account_id' => $data['from_account_id'],
            'transfer_account_id' => $goal->account_id,
            'direction' => 'outflow',
            'amount_minor' => $amount,
            'occurred_on' => $data['contributed_on'],
            'merchant' => $goal->name,
            'note' => $data['note'] ?? null,
            'transfer_group' => $group,
            'savings_goal_id' => $goal->getKey(),
            'type' => TransactionType::Transfer->value,
        ]);

        $inflow = $this->leg($user, [
            'account_id' => $goal->account_id,
            'transfer_account_id' => $data['from_account_id'],
            'direction' => 'inflow',
            'amount_minor' => $amount,
            'occurred_on' => $data['contributed_on'],
            'merchant' => $goal->name,
            'note' => $data['note'] ?? null,
            'transfer_group' => $group,
            'savings_goal_id' => $goal->getKey(),
            'type' => TransactionType::Transfer->value,
        ]);

        $this->balances->apply($outflow);
        $this->balances->apply($inflow);

        return $outflow;
    }

    /**
     * Set money aside without a dedicated account: an outflow stamped with the
     * goal so reports can separate saving from spending.
     *
     * @param  array<string, mixed>  $data
     */
    private function recordAllocation(User $user, SavingsGoal $goal, array $data, int $amount): Transaction
    {
        $transaction = $this->leg($user, [
            'account_id' => $data['from_account_id'],
            'transfer_account_id' => null,
            'direction' => 'outflow',
            'amount_minor' => $amount,
            'occurred_on' => $data['contributed_on'],
            'merchant' => $goal->name,
            'note' => $data['note'] ?? null,
            'transfer_group' => null,
            'savings_goal_id' => $goal->getKey(),
            'type' => TransactionType::Expense->value,
        ]);

        $this->balances->apply($transaction);

        return $transaction;
    }

    /**
     * @param  array<string, mixed>  $attributes
     */
    private function leg(User $user, array $attributes): Transaction
    {
        $account = $user->accounts()->whereKey($attributes['account_id'])->firstOrFail();

        $transaction = new Transaction;

        $transaction->fill([
            'account_id' => $account->getKey(),
            'transfer_account_id' => $attributes['transfer_account_id'],
            'type' => $attributes['type'],
            'direction' => $attributes['direction'],
            'amount_minor' => $attributes['amount_minor'],
            'currency_code' => $account->currency_code,
            'occurred_on' => $attributes['occurred_on'],
            'merchant' => $attributes['merchant'],
            'note' => $attributes['note'],
            'transfer_group' => $attributes['transfer_group'],
            'savings_goal_id' => $attributes['savings_goal_id'],
            'created_via' => CreatedVia::Manual->value,
            'is_demo' => $user->is_demo,
        ]);

        $transaction->user_id = $user->getKey();
        $transaction->save();

        return $transaction;
    }
}
