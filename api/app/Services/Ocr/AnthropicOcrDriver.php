<?php

namespace App\Services\Ocr;

use Anthropic\Client;
use Anthropic\Messages\Base64ImageSource;
use App\Contracts\ReceiptOcrDriver;
use App\Support\Money;
use App\Support\Ocr\OcrLineItem;
use App\Support\Ocr\OcrResult;
use Illuminate\Support\Facades\Log;
use RuntimeException;

/**
 * Reads a receipt with Claude's vision support.
 *
 * The request uses structured outputs, so the model answers with JSON that
 * already matches the review payload — including a per-field confidence score,
 * which is what drives the "check this" highlighting on the review screen.
 * Amounts come back in major units and are converted here, because asking a
 * model for integer minor units invites off-by-100 mistakes.
 */
class AnthropicOcrDriver implements ReceiptOcrDriver
{
    private const PROMPT = <<<'PROMPT'
    You are reading a photograph of a purchase receipt. Extract what is printed
    on it. Follow these rules:

    - Report amounts as decimal numbers in the receipt's own currency, exactly
      as printed. Do not convert currencies and do not invent a tax line that
      is not shown.
    - `total` is the final amount the customer paid, not the subtotal.
    - List every purchased line item with its own price. If a line shows a
      quantity and a unit price, report both. Skip loyalty points, change due,
      and payment card details.
    - Use ISO format (YYYY-MM-DD) for the transaction date printed on the
      receipt. If no date is printed, leave it null.
    - Set each `confidence` value to how clearly that field is legible: 1.0 for
      crisp printed text, below 0.7 when the image is blurred, cut off, or you
      had to guess.
    - Leave a field null rather than guessing at it.
    PROMPT;

    public function __construct(
        private readonly ReceiptTextParser $parser,
    ) {}

    public function extract(string $imageContents, string $mimeType, ?string $clientText = null): OcrResult
    {
        if (! $this->isAvailable()) {
            throw new RuntimeException('ANTHROPIC_API_KEY is not configured.');
        }

        $config = config('budget.ocr.anthropic');

        $message = $this->client()->messages->create(
            model: $config['model'],
            maxTokens: $config['max_tokens'],
            system: self::PROMPT,
            messages: [[
                'role' => 'user',
                'content' => array_values(array_filter([
                    [
                        'type' => 'image',
                        'source' => Base64ImageSource::with(
                            data: base64_encode($imageContents),
                            mediaType: $this->mediaType($mimeType),
                        ),
                    ],
                    $clientText === null ? null : [
                        'type' => 'text',
                        'text' => "Text recognised on the device, which may be incomplete:\n\n".$clientText,
                    ],
                    ['type' => 'text', 'text' => 'Extract this receipt.'],
                ])),
            ]],
            outputConfig: ['format' => ['type' => 'json_schema', 'schema' => $this->schema()]],
            requestOptions: ['timeout' => $config['timeout']],
        );

        $payload = $this->decode($message);

        if ($payload === null) {
            Log::warning('Receipt extraction returned no JSON payload.', ['stop_reason' => $message->stopReason]);

            return $clientText === null
                ? OcrResult::empty($this->name(), $clientText)
                : $this->parser->parse($clientText, $this->name());
        }

        return $this->toResult($payload, $clientText);
    }

    public function isAvailable(): bool
    {
        return filled(config('budget.ocr.anthropic.api_key'));
    }

    public function name(): string
    {
        return 'anthropic';
    }

    private function client(): Client
    {
        return new Client(
            apiKey: config('budget.ocr.anthropic.api_key'),
            baseUrl: config('budget.ocr.anthropic.base_url'),
        );
    }

    /**
     * @return array<string, mixed>|null
     */
    private function decode(object $message): ?array
    {
        foreach ($message->content as $block) {
            if ($block->type !== 'text') {
                continue;
            }

            $decoded = json_decode($block->text, true);

            if (is_array($decoded)) {
                return $decoded;
            }
        }

        return null;
    }

    /**
     * @param  array<string, mixed>  $payload
     */
    private function toResult(array $payload, ?string $clientText): OcrResult
    {
        $currency = $this->currency($payload['currency'] ?? null);
        $confidence = is_array($payload['confidence'] ?? null) ? $payload['confidence'] : [];

        $items = array_values(array_map(
            fn (array $item): OcrLineItem => new OcrLineItem(
                description: (string) ($item['description'] ?? 'Item'),
                totalMinor: (int) $this->minor($item['total'] ?? null, $currency),
                quantity: (float) ($item['quantity'] ?? 1),
                unitPriceMinor: $this->minor($item['unit_price'] ?? null, $currency),
                confidence: (float) ($item['confidence'] ?? 0.8),
            ),
            array_filter(
                is_array($payload['items'] ?? null) ? $payload['items'] : [],
                fn ($item): bool => is_array($item) && ($item['total'] ?? null) !== null,
            ),
        ));

        return new OcrResult(
            driver: $this->name(),
            merchant: $this->string($payload['merchant'] ?? null),
            receiptDate: $this->string($payload['date'] ?? null),
            currency: $currency,
            subtotalMinor: $this->minor($payload['subtotal'] ?? null, $currency),
            taxMinor: $this->minor($payload['tax'] ?? null, $currency),
            tipMinor: $this->minor($payload['tip'] ?? null, $currency),
            discountMinor: $this->minor($payload['discount'] ?? null, $currency),
            totalMinor: $this->minor($payload['total'] ?? null, $currency),
            paymentMethod: $this->string($payload['payment_method'] ?? null),
            items: $items,
            rawText: $clientText,
            confidence: [
                'merchant' => (float) ($confidence['merchant'] ?? 0.8),
                'receipt_date' => (float) ($confidence['date'] ?? 0.8),
                'total_minor' => (float) ($confidence['total'] ?? 0.8),
                'items' => (float) ($confidence['items'] ?? 0.7),
            ],
        );
    }

    private function minor(int|float|string|null $value, ?string $currency): ?int
    {
        if ($value === null || $value === '') {
            return null;
        }

        return Money::ofMajor($value, $currency)->minor;
    }

    private function string(mixed $value): ?string
    {
        return is_string($value) && trim($value) !== '' ? trim($value) : null;
    }

    private function currency(mixed $value): ?string
    {
        return is_string($value) && Money::isSupported($value) ? strtoupper($value) : null;
    }

    /**
     * Claude accepts JPEG, PNG, GIF and WebP; anything else is sent as JPEG,
     * which is what the uploader normalises to.
     */
    private function mediaType(string $mimeType): string
    {
        return in_array($mimeType, ['image/jpeg', 'image/png', 'image/gif', 'image/webp'], true)
            ? $mimeType
            : 'image/jpeg';
    }

    /**
     * @return array<string, mixed>
     */
    private function schema(): array
    {
        $amount = ['type' => ['number', 'null']];

        return [
            'type' => 'object',
            'properties' => [
                'merchant' => ['type' => ['string', 'null'], 'description' => 'Store or business name'],
                'date' => ['type' => ['string', 'null'], 'description' => 'Transaction date as YYYY-MM-DD'],
                'currency' => ['type' => ['string', 'null'], 'description' => 'ISO 4217 code, e.g. PHP or USD'],
                'subtotal' => $amount,
                'tax' => $amount,
                'tip' => $amount,
                'discount' => $amount,
                'total' => $amount,
                'payment_method' => ['type' => ['string', 'null']],
                'items' => [
                    'type' => 'array',
                    'items' => [
                        'type' => 'object',
                        'properties' => [
                            'description' => ['type' => 'string'],
                            'quantity' => ['type' => ['number', 'null']],
                            'unit_price' => $amount,
                            'total' => ['type' => 'number'],
                            'confidence' => ['type' => ['number', 'null']],
                        ],
                        'required' => ['description', 'quantity', 'unit_price', 'total', 'confidence'],
                        'additionalProperties' => false,
                    ],
                ],
                'confidence' => [
                    'type' => 'object',
                    'properties' => [
                        'merchant' => ['type' => 'number'],
                        'date' => ['type' => 'number'],
                        'total' => ['type' => 'number'],
                        'items' => ['type' => 'number'],
                    ],
                    'required' => ['merchant', 'date', 'total', 'items'],
                    'additionalProperties' => false,
                ],
            ],
            'required' => [
                'merchant', 'date', 'currency', 'subtotal', 'tax', 'tip',
                'discount', 'total', 'payment_method', 'items', 'confidence',
            ],
            'additionalProperties' => false,
        ];
    }
}
