<?php

namespace Tests\Unit;

use App\Services\Ocr\ReceiptTextParser;
use App\Support\Ocr\OcrLineItem;
use Tests\TestCase;

class ReceiptTextParserTest extends TestCase
{
    private function receipt(): string
    {
        return <<<'TEXT'
        METRO SUPERMARKET
        123 Market Street, Quezon City
        TIN 004-123-456-000
        VAT REG TIN

        Date: 2026-10-14
        Receipt #: 884210

        Whole Wheat Bread              89.00
        2 x Fresh Milk 1L             230.00
        Free Range Eggs               189.00
        Dish Soap                     139.00
        Laundry Detergent             289.00

        SUBTOTAL                      936.00
        VAT 12%                       112.32
        TOTAL                       1,048.32
        CASH                        1,100.00
        CHANGE                         51.68

        Thank you for shopping with us!
        TEXT;
    }

    public function test_it_reads_the_merchant_from_the_header(): void
    {
        $result = (new ReceiptTextParser)->parse($this->receipt());

        $this->assertSame('Metro Supermarket', $result->merchant);
    }

    public function test_it_reads_the_transaction_date(): void
    {
        $result = (new ReceiptTextParser)->parse($this->receipt());

        $this->assertSame('2026-10-14', $result->receiptDate);
    }

    /**
     * The last labelled total wins, so the subtotal printed above it — and the
     * cash tendered printed below it — are not mistaken for the amount paid.
     */
    public function test_it_prefers_the_total_over_the_subtotal_and_the_cash_tendered(): void
    {
        $result = (new ReceiptTextParser)->parse($this->receipt());

        $this->assertSame(104832, $result->totalMinor);
        $this->assertSame(93600, $result->subtotalMinor);
        $this->assertSame(11232, $result->taxMinor);
    }

    public function test_it_reads_the_line_items_and_skips_the_totals_block(): void
    {
        $result = (new ReceiptTextParser)->parse($this->receipt());

        $descriptions = array_map(fn (OcrLineItem $item): string => $item->description, $result->items);

        $this->assertSame([
            'Whole Wheat Bread',
            'Fresh Milk 1L',
            'Free Range Eggs',
            'Dish Soap',
            'Laundry Detergent',
        ], $descriptions);
    }

    public function test_it_reads_a_quantity_prefix_into_quantity_and_unit_price(): void
    {
        $result = (new ReceiptTextParser)->parse($this->receipt());

        $milk = $result->items[1];

        $this->assertSame('Fresh Milk 1L', $milk->description);
        $this->assertSame(2.0, $milk->quantity);
        $this->assertSame(23000, $milk->totalMinor);
        $this->assertSame(11500, $milk->unitPriceMinor);
    }

    /**
     * When subtotal plus tax reconciles with the total, the amount does not
     * need to be flagged for review.
     */
    public function test_a_total_that_reconciles_with_subtotal_and_tax_is_high_confidence(): void
    {
        $result = (new ReceiptTextParser)->parse($this->receipt());

        $this->assertSame(0.95, $result->confidence['total_minor']);
    }

    public function test_it_detects_the_payment_method_and_currency(): void
    {
        $result = (new ReceiptTextParser)->parse("SHELL STATION\nTOTAL $45.00\nVISA ****1234");

        $this->assertSame('Visa', $result->paymentMethod);
        $this->assertSame('USD', $result->currency);
    }

    public function test_it_returns_an_empty_result_for_text_with_nothing_to_read(): void
    {
        $result = (new ReceiptTextParser)->parse('   ');

        $this->assertTrue($result->isEmpty());
        $this->assertNull($result->totalMinor);
    }

    /**
     * A photo of a till roll often loses the itemised block; the total is
     * still worth recovering so the user only has to pick a category.
     */
    public function test_it_falls_back_to_the_largest_amount_when_nothing_is_labelled(): void
    {
        $result = (new ReceiptTextParser)->parse("CORNER STORE\n\n12.00\n480.50\n9.75");

        $this->assertSame(48050, $result->totalMinor);
    }

    public function test_it_ignores_a_date_that_is_implausible(): void
    {
        $result = (new ReceiptTextParser)->parse("SOME SHOP\nDate: 1999-01-01\nTOTAL 50.00");

        $this->assertNull($result->receiptDate);
    }
}
