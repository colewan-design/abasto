<?php

namespace App\Enums;

use Illuminate\Support\Carbon;

enum BillRecurrence: string
{
    case None = 'none';
    case Weekly = 'weekly';
    case Monthly = 'monthly';
    case Quarterly = 'quarterly';
    case Semiannual = 'semiannual';
    case Yearly = 'yearly';

    public function repeats(): bool
    {
        return $this !== self::None;
    }

    /**
     * The next due date after `$dueDate`, or null for a one-off bill.
     */
    public function nextDueDate(Carbon $dueDate, int $interval = 1): ?Carbon
    {
        $interval = max(1, $interval);
        $anchorDay = (int) $dueDate->day;

        $months = match ($this) {
            self::None => null,
            self::Weekly => null,
            self::Monthly => 1 * $interval,
            self::Quarterly => 3 * $interval,
            self::Semiannual => 6 * $interval,
            self::Yearly => 12 * $interval,
        };

        if ($this === self::None) {
            return null;
        }

        if ($this === self::Weekly) {
            return $dueDate->copy()->addWeeks($interval);
        }

        $target = $dueDate->copy()->day(1)->addMonths($months);

        return $target->day(min($anchorDay, $target->daysInMonth));
    }

    public function label(): string
    {
        return match ($this) {
            self::None => 'One time',
            self::Weekly => 'Weekly',
            self::Monthly => 'Monthly',
            self::Quarterly => 'Quarterly',
            self::Semiannual => 'Every 6 months',
            self::Yearly => 'Yearly',
        };
    }
}
