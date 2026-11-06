<?php

namespace App\Services;

use App\Models\Account;
use App\Models\Transaction;
use Illuminate\Support\Facades\DB;

/**
 * Keeps `accounts.current_balance_minor` in step with the ledger.
 *
 * Balances are maintained incrementally so the dashboard never has to sum a
 * user's full history, and `recalculate()` can rebuild any account from its
 * transactions if the two ever drift.
 */
class AccountBalanceService
{
    /**
     * Apply a transaction's effect to its account balance.
     */
    public function apply(Transaction $transaction): void
    {
        $this->adjust($transaction->account_id, $transaction->signedMinor());
    }

    /**
     * Remove a transaction's effect from its account balance.
     */
    public function revert(Transaction $transaction): void
    {
        $this->adjust($transaction->account_id, -$transaction->signedMinor());
    }

    /**
     * Move a transaction's effect when the amount, direction or account change.
     *
     * @param  array{account_id: int, signed_minor: int}  $before
     */
    public function move(array $before, Transaction $after): void
    {
        $this->adjust($before['account_id'], -$before['signed_minor']);
        $this->adjust($after->account_id, $after->signedMinor());
    }

    public function adjust(int $accountId, int $deltaMinor): void
    {
        if ($deltaMinor === 0) {
            return;
        }

        Account::query()
            ->withoutGlobalScopes()
            ->whereKey($accountId)
            ->update([
                'current_balance_minor' => DB::raw("current_balance_minor + {$deltaMinor}"),
                'updated_at' => now(),
            ]);
    }

    /**
     * Rebuild one account's balance from its opening balance and ledger.
     */
    public function recalculate(Account $account): int
    {
        $ledger = Transaction::query()
            ->withoutGlobalScopes()
            ->where('account_id', $account->getKey())
            ->selectRaw("COALESCE(SUM(CASE WHEN direction = 'inflow' THEN amount_minor ELSE -amount_minor END), 0) AS total")
            ->value('total');

        $balance = $account->opening_balance_minor + (int) $ledger;

        $account->forceFill(['current_balance_minor' => $balance])->saveQuietly();

        return $balance;
    }

    /**
     * Rebuild every account for a user and report the ones that had drifted.
     *
     * @return array<int, array{account: string, from: int, to: int}>
     */
    public function recalculateAll(int $userId): array
    {
        $corrections = [];

        Account::query()
            ->withoutGlobalScopes()
            ->where('user_id', $userId)
            ->each(function (Account $account) use (&$corrections): void {
                $before = $account->current_balance_minor;
                $after = $this->recalculate($account);

                if ($before !== $after) {
                    $corrections[] = ['account' => $account->name, 'from' => $before, 'to' => $after];
                }
            });

        return $corrections;
    }
}
