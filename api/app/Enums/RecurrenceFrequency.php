<?php

namespace App\Enums;

use Illuminate\Support\Carbon;

enum RecurrenceFrequency: string
{
    case Daily = 'daily';
    case Weekly = 'weekly';
    case Biweekly = 'biweekly';
    case Monthly = 'monthly';
    case Quarterly = 'quarterly';
    case Yearly = 'yearly';

    /**
     * Advance a date by one cycle of this frequency.
     *
     * Month-based frequencies clamp to the end of the target month so a rule
     * anchored on the 31st still fires in February.
     */
    public function advance(Carbon $from, int $interval = 1, ?int $anchorDay = null): Carbon
    {
        $interval = max(1, $interval);

        return match ($this) {
            self::Daily => $from->copy()->addDays($interval),
            self::Weekly => $from->copy()->addWeeks($interval),
            self::Biweekly => $from->copy()->addWeeks(2 * $interval),
            self::Monthly => $this->addMonths($from, $interval, $anchorDay),
            self::Quarterly => $this->addMonths($from, 3 * $interval, $anchorDay),
            self::Yearly => $this->addMonths($from, 12 * $interval, $anchorDay),
        };
    }

    public function label(): string
    {
        return match ($this) {
            self::Daily => 'Daily',
            self::Weekly => 'Weekly',
            self::Biweekly => 'Every 2 weeks',
            self::Monthly => 'Monthly',
            self::Quarterly => 'Quarterly',
            self::Yearly => 'Yearly',
        };
    }

    private function addMonths(Carbon $from, int $months, ?int $anchorDay): Carbon
    {
        $anchorDay ??= (int) $from->day;
        $target = $from->copy()->day(1)->addMonths($months);

        return $target->day(min($anchorDay, $target->daysInMonth));
    }
}
