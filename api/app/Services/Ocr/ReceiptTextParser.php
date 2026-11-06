<?php

namespace App\Services\Ocr;

use App\Support\Money;
use App\Support\Ocr\OcrLineItem;
use App\Support\Ocr\OcrResult;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;

/**
 * Turns the raw text of a receipt into structured fields.
 *
 * Shared by every driver that produces plain text — on-device recognition,
 * Google Vision and Tesseract all hand their output here, so the parsing rules
 * (and the confidence they report) are identical no matter where the text came
 * from.
 */
class ReceiptTextParser
{
    /** Lines that mark the end of the itemised section. */
    private const TOTAL_KEYWORDS = [
        'grand total', 'total amount', 'amount due', 'balance due', 'total due',
        'total sales', 'net total', 'total',
    ];

    private const SKIP_IN_TOTAL = [
        'subtotal', 'sub total', 'total items', 'total qty', 'total savings',
        'total discount', 'vatable', 'vat exempt', 'zero rated', 'total vat',
    ];

    private const ITEM_STOP_WORDS = [
        'subtotal', 'sub total', 'total', 'vat', 'tax', 'cash', 'change',
        'tender', 'amount due', 'balance', 'discount', 'thank you', 'customer',
        'cashier', 'invoice', 'receipt', 'tin', 'serial', 'min ', 'sn ', 'ref',
        'terminal', 'batch', 'approval', 'card', 'visa', 'master', 'gcash',
        'maya', 'paymaya', 'points', 'loyalty', 'items sold', 'qty',
    ];

    public function parse(string $text, string $driver = 'heuristic'): OcrResult
    {
        $lines = $this->lines($text);

        if ($lines === []) {
            return OcrResult::empty($driver, $text);
        }

        $currency = $this->detectCurrency($text);
        $merchant = $this->findMerchant($lines);
        $date = $this->findDate($text);
        $total = $this->findLabelledAmount($lines, self::TOTAL_KEYWORDS, self::SKIP_IN_TOTAL, $currency);
        $subtotal = $this->findLabelledAmount($lines, ['subtotal', 'sub total'], [], $currency);
        $tax = $this->findLabelledAmount($lines, ['vat amount', 'total vat', 'vat 12', 'sales tax', 'tax'], ['vat exempt', 'zero rated', 'vatable', 'non-vat'], $currency);
        $tip = $this->findLabelledAmount($lines, ['tip', 'gratuity', 'service charge'], [], $currency);
        $discount = $this->findLabelledAmount($lines, ['discount', 'promo', 'less'], ['total discount'], $currency);
        $items = $this->findItems($lines, $currency);

        $total ??= $this->largestAmount($lines, $currency);

        if ($total === null && $items !== []) {
            $total = array_sum(array_map(fn (OcrLineItem $item): int => $item->totalMinor, $items));
        }

        return new OcrResult(
            driver: $driver,
            merchant: $merchant,
            receiptDate: $date?->toDateString(),
            currency: $currency,
            subtotalMinor: $subtotal,
            taxMinor: $tax,
            tipMinor: $tip,
            discountMinor: $discount,
            totalMinor: $total,
            paymentMethod: $this->findPaymentMethod($text),
            items: $items,
            rawText: $text,
            confidence: $this->scoreConfidence($merchant, $date, $total, $subtotal, $tax, $items),
        );
    }

    /**
     * @return array<int, string>
     */
    private function lines(string $text): array
    {
        return collect(preg_split('/\r\n|\r|\n/', $text) ?: [])
            ->map(fn (string $line): string => trim(preg_replace('/\s+/', ' ', $line) ?? $line))
            ->reject(fn (string $line): bool => $line === '')
            ->values()
            ->all();
    }

    /**
     * The shop name is almost always in the first few lines, above the address
     * and the tax registration numbers.
     *
     * @param  array<int, string>  $lines
     */
    private function findMerchant(array $lines): ?string
    {
        $noise = ['receipt', 'invoice', 'tin', 'vat reg', 'official', 'sales', 'welcome', 'thank', 'www', 'http', 'tel', 'phone'];

        foreach (array_slice($lines, 0, 6) as $line) {
            $lower = Str::lower($line);

            if (Str::length($line) < 3 || preg_match('/^[\d\W]+$/', $line)) {
                continue;
            }

            foreach ($noise as $word) {
                if (str_contains($lower, $word)) {
                    continue 2;
                }
            }

            if (preg_match('/[a-z]{3,}/i', $line) !== 1) {
                continue;
            }

            return Str::limit(Str::title($line), 120, '');
        }

        return null;
    }

    private function findDate(string $text): ?Carbon
    {
        $patterns = [
            '/\b(\d{4}-\d{2}-\d{2})\b/' => ['Y-m-d'],
            '/\b(\d{1,2}\/\d{1,2}\/\d{4})\b/' => ['m/d/Y', 'd/m/Y'],
            '/\b(\d{1,2}\/\d{1,2}\/\d{2})\b/' => ['m/d/y', 'd/m/y'],
            '/\b(\d{1,2}-\d{1,2}-\d{4})\b/' => ['m-d-Y', 'd-m-Y'],
            '/\b(\d{1,2}\.\d{1,2}\.\d{4})\b/' => ['d.m.Y', 'm.d.Y'],
            '/\b([A-Za-z]{3,9} \d{1,2},? \d{4})\b/' => ['F j, Y', 'F j Y', 'M j, Y', 'M j Y'],
            '/\b(\d{1,2} [A-Za-z]{3,9} \d{4})\b/' => ['j F Y', 'j M Y'],
        ];

        foreach ($patterns as $pattern => $formats) {
            if (preg_match($pattern, $text, $matches) !== 1) {
                continue;
            }

            foreach ($formats as $format) {
                $date = $this->tryDate($matches[1], $format);

                if ($date !== null) {
                    return $date;
                }
            }
        }

        return null;
    }

    private function tryDate(string $value, string $format): ?Carbon
    {
        try {
            $date = Carbon::createFromFormat('!'.$format, $value);
        } catch (\Throwable) {
            return null;
        }

        if ($date === false) {
            return null;
        }

        return $date->isBetween(today()->subYears(5), today()->addDays(2)) ? $date : null;
    }

    /**
     * Find the amount on the last line that mentions one of `$keywords`.
     *
     * Receipts list a subtotal before the total, and loyalty blocks repeat the
     * word "total", so the last match wins — except for lines that look like a
     * different kind of total altogether.
     *
     * @param  array<int, string>  $lines
     * @param  array<int, string>  $keywords
     * @param  array<int, string>  $exclude
     */
    private function findLabelledAmount(array $lines, array $keywords, array $exclude, ?string $currency): ?int
    {
        $found = null;

        foreach ($lines as $line) {
            $lower = Str::lower($line);

            foreach ($exclude as $word) {
                if (str_contains($lower, $word)) {
                    continue 2;
                }
            }

            foreach ($keywords as $keyword) {
                if (! str_contains($lower, $keyword)) {
                    continue;
                }

                $amount = $this->lastAmount($line, $currency);

                if ($amount !== null) {
                    $found = $amount;
                }

                break;
            }
        }

        return $found;
    }

    /**
     * @param  array<int, string>  $lines
     * @return array<int, OcrLineItem>
     */
    private function findItems(array $lines, ?string $currency): array
    {
        $items = [];

        foreach ($lines as $line) {
            $lower = Str::lower($line);

            foreach (self::ITEM_STOP_WORDS as $word) {
                if (str_contains($lower, $word)) {
                    continue 2;
                }
            }

            if (preg_match('/^(?<desc>.*?[A-Za-z]{2,}.*?)\s+[₱$€£¥]?\s*(?<amount>-?\d[\d,]*\.\d{2})\s*[A-Za-z]?$/u', $line, $matches) !== 1) {
                continue;
            }

            $description = trim($matches['desc']);
            $total = $this->toMinor($matches['amount'], $currency);

            if ($total === null || $total <= 0 || Str::length($description) < 2) {
                continue;
            }

            [$quantity, $unitPrice, $description] = $this->extractQuantity($description, $total, $currency);

            $items[] = new OcrLineItem(
                description: Str::limit(Str::title($description), 120, ''),
                totalMinor: $total,
                quantity: $quantity,
                unitPriceMinor: $unitPrice,
                confidence: 0.6,
            );
        }

        return $items;
    }

    /**
     * Pull a leading or trailing quantity out of an item description:
     * "2 x Bottled Water", "Bottled Water 2 @ 25.00", "3 PCS Soap".
     *
     * @return array{0: float, 1: int|null, 2: string}
     */
    private function extractQuantity(string $description, int $total, ?string $currency): array
    {
        $patterns = [
            '/^(?<qty>\d+(?:\.\d+)?)\s*(?:x|@|pcs?|pc|pack)?\s+(?<rest>.+)$/i',
            '/^(?<rest>.+?)\s+(?<qty>\d+(?:\.\d+)?)\s*(?:x|@)\s*[\d,.]+$/i',
        ];

        foreach ($patterns as $pattern) {
            if (preg_match($pattern, $description, $matches) !== 1) {
                continue;
            }

            $quantity = (float) $matches['qty'];

            if ($quantity <= 0 || $quantity > 999) {
                continue;
            }

            return [
                $quantity,
                $quantity > 0 ? (int) round($total / $quantity) : null,
                trim($matches['rest']),
            ];
        }

        return [1.0, $total, $description];
    }

    /**
     * @param  array<int, string>  $lines
     */
    private function largestAmount(array $lines, ?string $currency): ?int
    {
        $largest = null;

        foreach ($lines as $line) {
            if (preg_match_all('/-?\d[\d,]*\.\d{2}/', $line, $matches) === 0) {
                continue;
            }

            foreach ($matches[0] as $match) {
                $minor = $this->toMinor($match, $currency);

                if ($minor !== null && ($largest === null || $minor > $largest)) {
                    $largest = $minor;
                }
            }
        }

        return $largest;
    }

    private function lastAmount(string $line, ?string $currency): ?int
    {
        if (preg_match_all('/-?\d[\d,]*(?:\.\d{1,2})?/', $line, $matches) === 0) {
            return null;
        }

        $candidates = array_filter(
            $matches[0],
            fn (string $value): bool => str_contains($value, '.') || str_contains($value, ',') || Str::length($value) > 2,
        );

        $value = end($candidates) ?: end($matches[0]);

        return $value === false ? null : $this->toMinor($value, $currency);
    }

    private function toMinor(string $value, ?string $currency): ?int
    {
        $cleaned = str_replace([',', ' '], '', $value);

        if (! is_numeric($cleaned)) {
            return null;
        }

        return Money::ofMajor($cleaned, $currency)->minor;
    }

    private function detectCurrency(string $text): ?string
    {
        $symbols = ['₱' => 'PHP', '€' => 'EUR', '£' => 'GBP', '¥' => 'JPY', '₩' => 'KRW', '₫' => 'VND', '₹' => 'INR'];

        foreach ($symbols as $symbol => $code) {
            if (str_contains($text, $symbol)) {
                return $code;
            }
        }

        foreach (array_keys((array) config('budget.currencies')) as $code) {
            if (preg_match('/\b'.preg_quote((string) $code, '/').'\b/', $text) === 1) {
                return (string) $code;
            }
        }

        if (preg_match('/\bPHP\b|\bpeso/i', $text) === 1) {
            return 'PHP';
        }

        return str_contains($text, '$') ? 'USD' : null;
    }

    private function findPaymentMethod(string $text): ?string
    {
        $methods = [
            'gcash' => 'GCash', 'paymaya' => 'Maya', 'maya' => 'Maya',
            'grabpay' => 'GrabPay', 'shopeepay' => 'ShopeePay',
            'visa' => 'Visa', 'mastercard' => 'Mastercard', 'master card' => 'Mastercard',
            'amex' => 'Amex', 'jcb' => 'JCB', 'debit' => 'Debit card',
            'credit card' => 'Credit card', 'apple pay' => 'Apple Pay',
            'google pay' => 'Google Pay', 'cash' => 'Cash',
        ];

        $lower = Str::lower($text);

        foreach ($methods as $needle => $label) {
            if (str_contains($lower, $needle)) {
                return $label;
            }
        }

        return null;
    }

    /**
     * @param  array<int, OcrLineItem>  $items
     * @return array<string, float>
     */
    private function scoreConfidence(?string $merchant, ?Carbon $date, ?int $total, ?int $subtotal, ?int $tax, array $items): array
    {
        $confidence = [
            'merchant' => $merchant === null ? 0.0 : 0.75,
            'receipt_date' => $date === null ? 0.0 : 0.8,
            'total_minor' => $total === null ? 0.0 : 0.7,
            'items' => $items === [] ? 0.0 : 0.6,
        ];

        // When subtotal + tax reconciles with the total, the numbers corroborate
        // each other and the amount can be trusted without review.
        if ($total !== null && $subtotal !== null) {
            $expected = $subtotal + ($tax ?? 0);

            if (abs($expected - $total) <= 2 || abs($subtotal - $total) <= 2) {
                $confidence['total_minor'] = 0.95;
            }
        }

        if ($items !== [] && $total !== null) {
            $sum = array_sum(array_map(fn (OcrLineItem $item): int => $item->totalMinor, $items));

            if ($total > 0 && abs($sum - $total) <= max(100, (int) ($total * 0.02))) {
                $confidence['items'] = 0.9;
            }
        }

        return $confidence;
    }
}
