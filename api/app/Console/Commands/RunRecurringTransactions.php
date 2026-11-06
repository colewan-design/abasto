<?php

namespace App\Console\Commands;

use App\Services\RecurringRuleRunner;
use Illuminate\Console\Attributes\Description;
use Illuminate\Console\Attributes\Signature;
use Illuminate\Console\Command;
use Illuminate\Support\Carbon;

#[Signature('recurring:run {--date= : Run as if today were this date (Y-m-d)}')]
#[Description('Post recurring transactions that have come due')]
class RunRecurringTransactions extends Command
{
    public function handle(RecurringRuleRunner $runner): int
    {
        $on = $this->option('date') === null ? today() : Carbon::parse($this->option('date'));

        $result = $runner->runAllDue($on);

        $this->info(sprintf(
            '%d %s due, %d %s created.',
            $result['rules'],
            str('rule')->plural($result['rules']),
            $result['transactions'],
            str('transaction')->plural($result['transactions']),
        ));

        return self::SUCCESS;
    }
}
