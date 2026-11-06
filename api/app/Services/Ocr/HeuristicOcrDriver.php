<?php

namespace App\Services\Ocr;

use App\Contracts\ReceiptOcrDriver;
use App\Support\Ocr\OcrResult;

/**
 * Parses text the client already recognised, with no external service.
 *
 * The Android app runs ML Kit text recognition on-device and uploads the text
 * alongside the image, so this driver is a complete path for both offline use
 * and local development: it needs no credentials and costs nothing. Without
 * client text there is nothing to read, and the receipt comes back empty for
 * the user to fill in manually.
 */
class HeuristicOcrDriver implements ReceiptOcrDriver
{
    public function __construct(
        private readonly ReceiptTextParser $parser,
    ) {}

    public function extract(string $imageContents, string $mimeType, ?string $clientText = null): OcrResult
    {
        if ($clientText === null || trim($clientText) === '') {
            return OcrResult::empty($this->name());
        }

        return $this->parser->parse($clientText, $this->name());
    }

    public function isAvailable(): bool
    {
        return true;
    }

    public function name(): string
    {
        return 'heuristic';
    }
}
