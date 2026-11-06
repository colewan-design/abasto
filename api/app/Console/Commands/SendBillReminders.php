<?php

namespace App\Console\Commands;

use App\Services\BillService;
use Illuminate\Console\Attributes\Description;
use Illuminate\Console\Attributes\Signature;
use Illuminate\Console\Command;

#[Signature('bills:remind')]
#[Description('Send reminders for bills that are due soon or overdue')]
class SendBillReminders extends Command
{
    public function handle(BillService $bills): int
    {
        $sent = $bills->sendReminders();

        $this->info(sprintf('%d %s sent.', $sent, str('reminder')->plural($sent)));

        return self::SUCCESS;
    }
}
