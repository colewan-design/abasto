<?php

namespace App\Console\Commands;

use App\Models\User;
use Illuminate\Console\Attributes\Description;
use Illuminate\Console\Attributes\Signature;
use Illuminate\Console\Command;

#[Signature('demo:prune {--days= : Remove guest accounts idle for this many days}')]
#[Description('Delete abandoned guest demo accounts and their sample data')]
class PruneDemoAccounts extends Command
{
    public function handle(): int
    {
        $days = (int) ($this->option('days') ?? config('budget.demo.retention_days'));
        $cutoff = now()->subDays(max(1, $days));
        $removed = 0;

        User::query()
            ->demo()
            ->where(fn ($query) => $query
                ->where('last_active_at', '<', $cutoff)
                ->orWhere(fn ($query) => $query->whereNull('last_active_at')->where('created_at', '<', $cutoff)))
            ->each(function (User $user) use (&$removed): void {
                // Deleting the user cascades its rows and the observer removes
                // the stored receipt images.
                $user->delete();
                $removed++;
            });

        $this->info(sprintf(
            '%d abandoned demo %s removed (idle for more than %d days).',
            $removed,
            str('account')->plural($removed),
            $days,
        ));

        return self::SUCCESS;
    }
}
