<?php

namespace App\Enums;

enum ReceiptStatus: string
{
    case Pending = 'pending';
    case Processing = 'processing';
    case Processed = 'processed';
    case Failed = 'failed';
    case Confirmed = 'confirmed';

    public function isReviewable(): bool
    {
        return in_array($this, [self::Processed, self::Failed], true);
    }

    public function isTerminal(): bool
    {
        return $this === self::Confirmed;
    }
}
