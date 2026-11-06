<?php

namespace App\Services;

use App\Enums\BillPaymentStatus;
use App\Enums\CreatedVia;
use App\Enums\TransactionType;
use App\Models\Bill;
use App\Models\BillPayment;
use App\Models\User;
use App\Notifications\BillDueReminder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Bills, due dates and reminders.
 *
 * Marking a bill paid records the expense and links it to the payment, which
 * is what stops the same bill being entered twice — once as a bill and again
 * as a manual expense.
 */
class BillService
{
    public function __construct(
        private readonly TransactionService $transactions,
    ) {}

    /**
     * Bills due within a window, overdue ones first.
     *
     * @return Collection<int, Bill>
     */
    public function upcoming(User $user, int $withinDays = 30)
    {
        return $user->bills()
            ->active()
            ->with(['account', 'category'])
            ->whereDate('due_date', '<=', today()->addDays($withinDays))
            ->orderBy('due_date')
            ->get();
    }

    /**
     * Record a payment against a bill and advance it to the next due date.
     *
     * @param  array{
     *     account_id?: int|null,
     *     amount_minor?: int|null,
     *     paid_on?: string|null,
     *     note?: string|null,
     *     record_expense?: bool
     * }  $data
     */
    public function pay(User $user, Bill $bill, array $data = []): BillPayment
    {
        return DB::transaction(function () use ($user, $bill, $data): BillPayment {
            $amount = abs((int) ($data['amount_minor'] ?? $bill->amount_minor));
            $paidOn = $data['paid_on'] ?? today()->toDateString();
            $accountId = $data['account_id'] ?? $bill->account_id;
            $transaction = null;

            if (($data['record_expense'] ?? true) && $accountId !== null) {
                $transaction = $this->transactions->create($user, [
                    'account_id' => $accountId,
                    'category_id' => $bill->category_id,
                    'type' => TransactionType::Expense->value,
                    'amount_minor' => $amount,
                    'occurred_on' => $paidOn,
                    'merchant' => $bill->payee ?? $bill->name,
                    'note' => $data['note'] ?? "Payment for {$bill->name}",
                    'bill_id' => $bill->getKey(),
                    'created_via' => CreatedVia::Bill->value,
                ]);
            }

            $payment = $bill->payments()->updateOrCreate(
                ['period_due_date' => $bill->due_date->toDateString()],
                [
                    'transaction_id' => $transaction?->getKey(),
                    'amount_minor' => $amount,
                    'paid_on' => $paidOn,
                    'status' => BillPaymentStatus::Paid->value,
                ],
            );

            $this->advance($bill, $paidOn);

            return $payment->refresh();
        });
    }

    /**
     * Skip this cycle without recording a payment.
     */
    public function skip(Bill $bill): BillPayment
    {
        return DB::transaction(function () use ($bill): BillPayment {
            $payment = $bill->payments()->updateOrCreate(
                ['period_due_date' => $bill->due_date->toDateString()],
                [
                    'amount_minor' => $bill->amount_minor,
                    'status' => BillPaymentStatus::Skipped->value,
                    'paid_on' => null,
                ],
            );

            $this->advance($bill, null);

            return $payment;
        });
    }

    /**
     * Undo a payment: delete the linked expense and move the bill back to the
     * cycle it was paid for.
     */
    public function unpay(BillPayment $payment): void
    {
        DB::transaction(function () use ($payment): void {
            $bill = $payment->bill;

            if ($payment->transaction !== null) {
                $this->transactions->delete($payment->transaction);
            }

            $bill->forceFill([
                'due_date' => $payment->period_due_date,
                'last_paid_on' => $bill->payments()
                    ->where('status', BillPaymentStatus::Paid->value)
                    ->whereKeyNot($payment->getKey())
                    ->max('paid_on'),
            ])->save();

            $payment->delete();
        });
    }

    /**
     * Send reminders for bills entering their reminder window, plus overdue
     * ones. `reminded_at` is stamped per cycle so a reminder is sent once.
     *
     * @return int Number of reminders sent.
     */
    public function sendReminders(): int
    {
        $sent = 0;

        Bill::query()
            ->withoutGlobalScopes()
            ->with('user')
            ->where('is_active', true)
            ->whereDate('due_date', '<=', today()->addDays(30))
            ->each(function (Bill $bill) use (&$sent): void {
                $days = (int) $bill->daysUntilDue();

                if ($days > (int) $bill->reminder_days_before) {
                    return;
                }

                if ($bill->reminded_at !== null && $bill->reminded_at->greaterThan($bill->due_date->copy()->subDays((int) $bill->reminder_days_before + 1))) {
                    return;
                }

                $bill->user->notify(new BillDueReminder($bill, $days));
                $bill->forceFill(['reminded_at' => now()])->saveQuietly();

                $sent++;
            });

        return $sent;
    }

    /**
     * Move a recurring bill to its next due date, or close a one-off bill.
     */
    private function advance(Bill $bill, ?string $paidOn): void
    {
        $next = $bill->nextDueDate();

        $bill->forceFill([
            'due_date' => $next ?? $bill->due_date,
            'is_active' => $next !== null,
            'last_paid_on' => $paidOn ?? $bill->last_paid_on,
            'reminded_at' => null,
        ])->save();
    }

    /**
     * Backfill the unpaid cycles a bill has accumulated, so the history shows
     * what was missed rather than silently skipping it.
     *
     * @return int Number of cycles recorded.
     */
    public function recordMissedCycles(Bill $bill, ?Carbon $until = null): int
    {
        $until ??= today();
        $recorded = 0;
        $cursor = $bill->due_date->copy();

        while ($cursor->isBefore($until) && $recorded < 24) {
            $exists = $bill->payments()->whereDate('period_due_date', $cursor)->exists();

            if (! $exists) {
                $bill->payments()->create([
                    'period_due_date' => $cursor->toDateString(),
                    'amount_minor' => $bill->amount_minor,
                    'status' => BillPaymentStatus::Unpaid->value,
                ]);

                $recorded++;
            }

            $next = $bill->recurrence->nextDueDate($cursor, $bill->recurrence_interval);

            if ($next === null) {
                break;
            }

            $cursor = $next;
        }

        return $recorded;
    }
}
