<?php

namespace App\Services;

use App\Enums\CreatedVia;
use App\Enums\TransactionType;
use App\Models\RecurringRule;
use App\Models\Transaction;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Materializes recurring transactions — salary, rent, subscriptions,
 * installments — into the ledger when they come due.
 *
 * Runs catch up: a rule whose `next_run_on` is weeks in the past creates every
 * missed occurrence, so a user returning after a break sees the salary and
 * rent they actually received and paid rather than one lump.
 */
class RecurringRuleRunner
{
    private const MAX_CATCH_UP = 60;

    public function __construct(
        private readonly TransactionService $transactions,
    ) {}

    /**
     * Run every rule that is due across all users.
     *
     * @return array{rules: int, transactions: int}
     */
    public function runAllDue(?Carbon $on = null): array
    {
        $on ??= today();
        $rules = 0;
        $created = 0;

        RecurringRule::query()
            ->withoutGlobalScopes()
            ->with(['user', 'account'])
            ->where('is_active', true)
            ->whereNotNull('next_run_on')
            ->whereDate('next_run_on', '<=', $on->toDateString())
            ->each(function (RecurringRule $rule) use ($on, &$rules, &$created): void {
                $rules++;
                $created += count($this->run($rule, $on));
            });

        return ['rules' => $rules, 'transactions' => $created];
    }

    /**
     * Create every occurrence of one rule that is due on or before `$on`.
     *
     * @return array<int, Transaction>
     */
    public function run(RecurringRule $rule, ?Carbon $on = null): array
    {
        $on ??= today();
        $created = [];

        for ($i = 0; $i < self::MAX_CATCH_UP; $i++) {
            $due = $rule->next_run_on;

            if ($due === null || $due->isAfter($on) || ! $rule->is_active) {
                break;
            }

            if ($rule->end_date !== null && $due->isAfter($rule->end_date)) {
                $rule->forceFill(['is_active' => false, 'next_run_on' => null])->save();

                break;
            }

            $created[] = $this->createOccurrence($rule, $due);
        }

        return array_values(array_filter($created));
    }

    /**
     * Set a rule's first run date from its start date, used on create.
     */
    public function scheduleFirstRun(RecurringRule $rule): RecurringRule
    {
        $start = $rule->start_date->copy();

        if ($rule->by_month_day !== null && in_array($rule->frequency->value, ['monthly', 'quarterly', 'yearly'], true)) {
            $start = $start->day(min($rule->by_month_day, $start->daysInMonth));

            if ($start->isBefore($rule->start_date)) {
                $start = $rule->frequency->advance($start, $rule->interval, $rule->by_month_day);
            }
        }

        $rule->forceFill(['next_run_on' => $start->toDateString()])->save();

        return $rule;
    }

    /**
     * Preview the next few dates a rule will fire, for the client to display.
     *
     * @return array<int, string>
     */
    public function preview(RecurringRule $rule, int $count = 3): array
    {
        $dates = [];
        $cursor = $rule->next_run_on?->copy() ?? $rule->start_date->copy();

        for ($i = 0; $i < $count; $i++) {
            if ($rule->end_date !== null && $cursor->isAfter($rule->end_date)) {
                break;
            }

            $dates[] = $cursor->toDateString();
            $cursor = $rule->frequency->advance($cursor, $rule->interval, $rule->by_month_day);
        }

        return $dates;
    }

    private function createOccurrence(RecurringRule $rule, Carbon $due): ?Transaction
    {
        return DB::transaction(function () use ($rule, $due): ?Transaction {
            $user = $rule->user;
            $transaction = null;

            $alreadyPosted = Transaction::query()
                ->withoutGlobalScopes()
                ->where('recurring_rule_id', $rule->getKey())
                ->whereDate('occurred_on', $due)
                ->exists();

            if (! $alreadyPosted) {
                $transaction = $rule->type === TransactionType::Transfer
                    ? $this->transactions->createTransfer($user, [
                        'from_account_id' => $rule->account_id,
                        'to_account_id' => $rule->transfer_account_id,
                        'amount_minor' => $rule->amount_minor,
                        'occurred_on' => $due->toDateString(),
                        'note' => $rule->note,
                        'created_via' => CreatedVia::Recurring->value,
                    ])['outflow']
                    : $this->transactions->create($user, [
                        'account_id' => $rule->account_id,
                        'category_id' => $rule->category_id,
                        'type' => $rule->type->value,
                        'amount_minor' => $rule->amount_minor,
                        'occurred_on' => $due->toDateString(),
                        'merchant' => $rule->merchant,
                        'note' => $rule->note,
                        'recurring_rule_id' => $rule->getKey(),
                        'created_via' => CreatedVia::Recurring->value,
                    ]);

                if ($rule->type === TransactionType::Transfer && $transaction !== null) {
                    $transaction->forceFill(['recurring_rule_id' => $rule->getKey()])->saveQuietly();
                }
            }

            $next = $rule->dateAfter($due);

            $rule->forceFill([
                'last_run_on' => $due->toDateString(),
                'occurrences_created' => $rule->occurrences_created + 1,
                'next_run_on' => $next?->toDateString(),
                'is_active' => $next !== null,
            ])->save();

            return $transaction;
        });
    }
}
