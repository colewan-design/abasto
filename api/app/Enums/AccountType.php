<?php

namespace App\Enums;

enum AccountType: string
{
    case Cash = 'cash';
    case Bank = 'bank';
    case Ewallet = 'ewallet';
    case CreditCard = 'credit_card';
    case Savings = 'savings';

    public function label(): string
    {
        return match ($this) {
            self::Cash => 'Cash',
            self::Bank => 'Bank account',
            self::Ewallet => 'E-wallet',
            self::CreditCard => 'Credit card',
            self::Savings => 'Savings',
        };
    }

    public function defaultIcon(): string
    {
        return match ($this) {
            self::Cash => 'wallet',
            self::Bank => 'bank',
            self::Ewallet => 'phone',
            self::CreditCard => 'credit-card',
            self::Savings => 'piggy-bank',
        };
    }

    /**
     * Credit cards hold a negative balance as money owed, so they are reported
     * as liabilities rather than available funds.
     */
    public function isLiability(): bool
    {
        return $this === self::CreditCard;
    }
}
