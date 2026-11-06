<?php

namespace App\Services\Ocr;

use App\Contracts\ReceiptOcrDriver;
use App\Support\Ocr\OcrResult;
use Illuminate\Support\Facades\Process;
use RuntimeException;

/**
 * Runs a local Tesseract binary, for self-hosted deployments that would rather
 * not send receipt images to a third party.
 */
class TesseractOcrDriver implements ReceiptOcrDriver
{
    public function __construct(
        private readonly ReceiptTextParser $parser,
    ) {}

    public function extract(string $imageContents, string $mimeType, ?string $clientText = null): OcrResult
    {
        if (! $this->isAvailable()) {
            throw new RuntimeException('The tesseract binary was not found.');
        }

        $config = config('budget.ocr.tesseract');
        $source = tempnam(sys_get_temp_dir(), 'receipt_');

        if ($source === false) {
            throw new RuntimeException('Unable to create a temporary file for OCR.');
        }

        try {
            file_put_contents($source, $imageContents);

            $result = Process::timeout($config['timeout'])->run([
                $config['binary'],
                $source,
                'stdout',
                '-l', $config['language'],
                '--psm', '6',
            ]);

            if ($result->failed()) {
                throw new RuntimeException('Tesseract failed: '.$result->errorOutput());
            }

            $text = trim($result->output());
        } finally {
            @unlink($source);
        }

        if ($text === '') {
            return OcrResult::empty($this->name());
        }

        return $this->parser->parse($text, $this->name());
    }

    public function isAvailable(): bool
    {
        $binary = config('budget.ocr.tesseract.binary');

        return Process::run([$binary, '--version'])->successful();
    }

    public function name(): string
    {
        return 'tesseract';
    }
}
