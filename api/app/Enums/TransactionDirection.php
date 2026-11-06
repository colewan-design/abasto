<?php

namespace App\Enums;

enum TransactionDirection: string
{
    case Inflow = 'inflow';
    case Outflow = 'outflow';

    /**
     * Multiplier applied to a stored (always positive) minor amount when
     * summing a ledger.
     */
    public function sign(): int
    {
        return $this === self::Inflow ? 1 : -1;
    }

    public function opposite(): self
    {
        return $this === self::Inflow ? self::Outflow : self::Inflow;
    }
}
