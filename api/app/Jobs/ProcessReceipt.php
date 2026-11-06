<?php

namespace App\Jobs;

use App\Models\Receipt;
use App\Services\Receipts\ReceiptProcessor;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Queue\Queueable;

class ProcessReceipt implements ShouldQueue
{
    use Queueable;

    public int $tries = 3;

    public int $timeout = 120;

    /**
     * Back off before retrying so a rate-limited OCR provider gets room.
     *
     * @var array<int, int>
     */
    public array $backoff = [10, 60];

    public function __construct(
        public readonly int $receiptId,
    ) {}

    public function handle(ReceiptProcessor $processor): void
    {
        $receipt = Receipt::query()
            ->withoutGlobalScopes()
            ->with('user')
            ->find($this->receiptId);

        if ($receipt === null) {
            return;
        }

        $processor->process($receipt);
    }

    /**
     * @return array<int, string>
     */
    public function tags(): array
    {
        return ['receipt:'.$this->receiptId];
    }
}
