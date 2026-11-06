<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Services\AccountBalanceService;
use Illuminate\Console\Attributes\Description;
use Illuminate\Console\Attributes\Signature;
use Illuminate\Console\Command;

#[Signature('accounts:recalculate {--user= : Limit to one user id}')]
#[Description('Rebuild account balances from the ledger and report any drift')]
class RecalculateAccountBalances extends Command
{
    public function handle(AccountBalanceService $balances): int
    {
        $users = User::query()
            ->when($this->option('user') !== null, fn ($query) => $query->whereKey($this->option('user')))
            ->pluck('id');

        $corrections = [];

        foreach ($users as $userId) {
            foreach ($balances->recalculateAll((int) $userId) as $correction) {
                $corrections[] = [$userId, $correction['account'], $correction['from'], $correction['to']];
            }
        }

        if ($corrections === []) {
            $this->info('All balances matched the ledger.');

            return self::SUCCESS;
        }

        $this->table(['User', 'Account', 'Was', 'Now'], $corrections);
        $this->warn(count($corrections).' '.str('balance')->plural(count($corrections)).' corrected.');

        return self::SUCCESS;
    }
}
