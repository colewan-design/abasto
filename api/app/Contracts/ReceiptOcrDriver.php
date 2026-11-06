<?php

namespace App\Contracts;

use App\Support\Ocr\OcrResult;

interface ReceiptOcrDriver
{
    /**
     * Read a receipt image.
     *
     * @param  string  $imageContents  Raw bytes of the uploaded image.
     * @param  string  $mimeType  Image MIME type, e.g. `image/jpeg`.
     * @param  string|null  $clientText  Text the client already recognised
     *                                   on-device (ML Kit on Android), which
     *                                   lets a driver skip a paid OCR call.
     */
    public function extract(string $imageContents, string $mimeType, ?string $clientText = null): OcrResult;

    /**
     * Whether this driver has the credentials or binaries it needs.
     */
    public function isAvailable(): bool;

    public function name(): string;
}
