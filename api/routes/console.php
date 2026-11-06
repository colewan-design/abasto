<?php

use Illuminate\Support\Facades\Schedule;

/*
|--------------------------------------------------------------------------
| Scheduled work
|--------------------------------------------------------------------------
|
| Run with a single cron entry on the server:
|
|   * * * * * cd /path/to/api && php artisan schedule:run >> /dev/null 2>&1
|
*/

// Post salary, rent, subscriptions and other repeating transactions as they
// come due, just after midnight in the application's timezone.
Schedule::command('recurring:run')
    ->dailyAt('00:10')
    ->withoutOverlapping()
    ->onOneServer();

// Remind people about bills entering their reminder window, and about anything
// already overdue.
Schedule::command('bills:remind')
    ->dailyAt('08:00')
    ->withoutOverlapping()
    ->onOneServer();

// Budget alerts also fire on write, but this catches a month that tipped over
// a threshold through a recurring charge rather than a manual entry.
Schedule::command('budgets:check')
    ->dailyAt('20:00')
    ->withoutOverlapping()
    ->onOneServer();

// Guest demo accounts are disposable; clear out the ones nobody came back to.
Schedule::command('demo:prune')
    ->hourly()
    ->withoutOverlapping()
    ->onOneServer();

// Balances are maintained incrementally, so this is a safety net that reports
// and repairs drift rather than a routine recalculation.
Schedule::command('accounts:recalculate')
    ->weeklyOn(1, '03:00')
    ->onOneServer();

// Housekeeping for the framework's own tables.
Schedule::command('queue:prune-failed --hours=168')->daily();
Schedule::command('sanctum:prune-expired --hours=24')->daily();
Schedule::command('model:prune')->daily();
