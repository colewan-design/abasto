<?php

namespace App\Enums;

enum BillPaymentStatus: string
{
    case Unpaid = 'unpaid';
    case Paid = 'paid';
    case Skipped = 'skipped';

    public function isSettled(): bool
    {
        return $this !== self::Unpaid;
    }
}
