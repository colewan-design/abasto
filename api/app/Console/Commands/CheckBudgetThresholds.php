<?php

namespace App\Console\Commands;

use App\Models\User;
use App\Services\BudgetService;
use Illuminate\Console\Attributes\Description;
use Illuminate\Console\Attributes\Signature;
use Illuminate\Console\Command;

#[Signature('budgets:check')]
#[Description('Notify users who are approaching or over a category budget')]
class CheckBudgetThresholds extends Command
{
    public function handle(BudgetService $budgets): int
    {
        $checked = 0;

        User::query()
            ->whereHas('budgets', fn ($query) => $query->whereDate('period_start', today()->startOfMonth()))
            ->each(function (User $user) use ($budgets, &$checked): void {
                $budgets->checkThresholds($user, today());
                $checked++;
            });

        $this->info(sprintf('%d %s checked.', $checked, str('budget')->plural($checked)));

        return self::SUCCESS;
    }
}
