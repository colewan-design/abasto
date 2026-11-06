<?php

namespace App\Services\Ocr;

use App\Contracts\ReceiptOcrDriver;
use App\Support\Ocr\OcrResult;
use Illuminate\Support\Facades\Log;
use InvalidArgumentException;
use Throwable;

/**
 * Chooses and runs the configured OCR driver.
 *
 * If the configured driver is unavailable or errors out, extraction falls back
 * to the heuristic driver rather than failing the upload: a receipt the user
 * has to finish by hand still beats losing the capture.
 */
class OcrManager
{
    public function __construct(
        private readonly HeuristicOcrDriver $heuristic,
        private readonly AnthropicOcrDriver $anthropic,
        private readonly GoogleVisionOcrDriver $googleVision,
        private readonly TesseractOcrDriver $tesseract,
    ) {}

    public function driver(?string $name = null): ReceiptOcrDriver
    {
        return match ($name ?? config('budget.ocr.driver')) {
            'heuristic', null => $this->heuristic,
            'anthropic' => $this->anthropic,
            'google_vision' => $this->googleVision,
            'tesseract' => $this->tesseract,
            default => throw new InvalidArgumentException("Unknown receipt OCR driver [{$name}]."),
        };
    }

    public function extract(string $imageContents, string $mimeType, ?string $clientText = null, ?string $driver = null): OcrResult
    {
        $primary = $this->driver($driver);

        if (! $primary->isAvailable()) {
            Log::warning('Receipt OCR driver is unavailable, falling back.', ['driver' => $primary->name()]);

            return $this->heuristic->extract($imageContents, $mimeType, $clientText);
        }

        try {
            return $primary->extract($imageContents, $mimeType, $clientText);
        } catch (Throwable $exception) {
            Log::error('Receipt OCR failed, falling back.', [
                'driver' => $primary->name(),
                'message' => $exception->getMessage(),
            ]);

            if ($primary->name() === $this->heuristic->name()) {
                throw $exception;
            }

            return $this->heuristic->extract($imageContents, $mimeType, $clientText);
        }
    }
}
