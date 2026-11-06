<?php

namespace App\Enums;

enum TransactionType: string
{
    case Income = 'income';
    case Expense = 'expense';
    case Transfer = 'transfer';

    /**
     * Transfers move money between the user's own accounts, so reports must
     * exclude them to avoid inflating income and expenses.
     */
    public function countsTowardsCashFlow(): bool
    {
        return $this !== self::Transfer;
    }

    public function defaultDirection(): TransactionDirection
    {
        return match ($this) {
            self::Income => TransactionDirection::Inflow,
            self::Expense, self::Transfer => TransactionDirection::Outflow,
        };
    }

    public function categoryType(): ?CategoryType
    {
        return match ($this) {
            self::Income => CategoryType::Income,
            self::Expense => CategoryType::Expense,
            self::Transfer => null,
        };
    }

    public function label(): string
    {
        return match ($this) {
            self::Income => 'Income',
            self::Expense => 'Expense',
            self::Transfer => 'Transfer',
        };
    }
}
