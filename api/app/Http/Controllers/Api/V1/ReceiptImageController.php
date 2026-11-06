<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Models\Receipt;
use App\Services\Receipts\ReceiptStorage;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\StreamedResponse;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

/**
 * Serves a receipt image.
 *
 * Images live on a private disk and are reached only through a signed URL that
 * expires, so a leaked link cannot be replayed indefinitely and nothing is
 * exposed by guessing a path. The signature names the receipt, and the row is
 * still resolved through the owner scope.
 */
class ReceiptImageController extends Controller
{
    public function __construct(
        private readonly ReceiptStorage $storage,
    ) {}

    public function show(Request $request, Receipt $receipt): StreamedResponse
    {
        $thumbnail = $request->string('variant')->toString() === 'thumbnail';
        $path = $thumbnail ? $receipt->thumbnail_path : $receipt->image_path;

        if ($path === null || ! $this->storage->disk()->exists($path)) {
            throw new NotFoundHttpException('That receipt image is no longer available.');
        }

        return $this->storage->disk()->response($path, null, [
            'Content-Type' => $thumbnail ? 'image/jpeg' : ($receipt->mime_type ?? 'image/jpeg'),
            'Cache-Control' => 'private, max-age=3600',
        ]);
    }
}
