<?php

namespace App\Enums;

enum CreatedVia: string
{
    case Manual = 'manual';
    case Receipt = 'receipt';
    case Recurring = 'recurring';
    case Bill = 'bill';
    case Transfer = 'transfer';
    case Demo = 'demo';
    case Import = 'import';
}
