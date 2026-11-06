<?php

namespace App\Services\Ocr;

use App\Contracts\ReceiptOcrDriver;
use App\Support\Ocr\OcrResult;
use Illuminate\Support\Facades\Http;
use RuntimeException;

/**
 * Reads a receipt with Google Cloud Vision's document text detection, then
 * hands the text to the shared parser.
 */
class GoogleVisionOcrDriver implements ReceiptOcrDriver
{
    public function __construct(
        private readonly ReceiptTextParser $parser,
    ) {}

    public function extract(string $imageContents, string $mimeType, ?string $clientText = null): OcrResult
    {
        if (! $this->isAvailable()) {
            throw new RuntimeException('GOOGLE_VISION_API_KEY is not configured.');
        }

        $config = config('budget.ocr.google_vision');

        $response = Http::timeout($config['timeout'])
            ->asJson()
            ->post($config['endpoint'].'?key='.$config['api_key'], [
                'requests' => [[
                    'image' => ['content' => base64_encode($imageContents)],
                    'features' => [['type' => 'DOCUMENT_TEXT_DETECTION']],
                ]],
            ]);

        if ($response->failed()) {
            throw new RuntimeException('Google Vision request failed: '.$response->status().' '.$response->body());
        }

        $text = $response->json('responses.0.fullTextAnnotation.text') ?? $clientText;

        if ($text === null || trim($text) === '') {
            return OcrResult::empty($this->name());
        }

        return $this->parser->parse($text, $this->name());
    }

    public function isAvailable(): bool
    {
        return filled(config('budget.ocr.google_vision.api_key'));
    }

    public function name(): string
    {
        return 'google_vision';
    }
}
