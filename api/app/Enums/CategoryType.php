<?php

namespace App\Enums;

enum CategoryType: string
{
    case Income = 'income';
    case Expense = 'expense';

    public function transactionType(): TransactionType
    {
        return match ($this) {
            self::Income => TransactionType::Income,
            self::Expense => TransactionType::Expense,
        };
    }
}
