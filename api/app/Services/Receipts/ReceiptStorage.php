<?php

namespace App\Services\Receipts;

use App\Models\Receipt;
use App\Models\User;
use Illuminate\Contracts\Filesystem\Filesystem;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Intervention\Image\Drivers\Gd\Driver;
use Intervention\Image\ImageManager;
use Throwable;

/**
 * Stores receipt images on a private disk, one folder per user.
 *
 * A byte hash is recorded on upload so re-uploading the same photo is caught
 * immediately, and a downscaled thumbnail is written for list views so the
 * client never downloads a multi-megabyte photo to draw a 48px preview.
 */
class ReceiptStorage
{
    /**
     * @return array{
     *     image_path: string,
     *     thumbnail_path: string|null,
     *     original_filename: string|null,
     *     mime_type: string,
     *     size_bytes: int,
     *     content_hash: string
     * }
     */
    public function store(User $user, UploadedFile $file): array
    {
        $contents = $file->get();
        $extension = Str::lower($file->getClientOriginalExtension() ?: $file->extension() ?: 'jpg');
        $name = Str::uuid()->toString();
        $directory = "u{$user->getKey()}";

        $imagePath = "{$directory}/{$name}.{$extension}";

        $this->disk()->put($imagePath, $contents);

        return [
            'image_path' => $imagePath,
            'thumbnail_path' => $this->writeThumbnail($contents, "{$directory}/{$name}_thumb.jpg"),
            'original_filename' => $file->getClientOriginalName(),
            'mime_type' => $file->getMimeType() ?: 'image/jpeg',
            'size_bytes' => strlen($contents),
            'content_hash' => hash('sha256', $contents),
        ];
    }

    public function contents(Receipt $receipt): string
    {
        return (string) $this->disk()->get($receipt->image_path);
    }

    public function exists(Receipt $receipt): bool
    {
        return $this->disk()->exists($receipt->image_path);
    }

    public function path(Receipt $receipt, bool $thumbnail = false): ?string
    {
        $path = $thumbnail ? $receipt->thumbnail_path : $receipt->image_path;

        return $path === null ? null : $this->disk()->path($path);
    }

    public function delete(Receipt $receipt): void
    {
        $this->disk()->delete(array_values(array_filter([
            $receipt->image_path,
            $receipt->thumbnail_path,
        ])));
    }

    public function disk(): Filesystem
    {
        return Storage::disk(config('budget.receipts.disk'));
    }

    /**
     * A failed thumbnail is not worth failing an upload over — the original is
     * already stored and the client can fall back to it.
     */
    private function writeThumbnail(string $contents, string $path): ?string
    {
        try {
            $image = (new ImageManager(new Driver))->read($contents);
            $encoded = $image
                ->scaleDown(width: (int) config('budget.receipts.thumbnail_width'))
                ->toJpeg(quality: 75);

            $this->disk()->put($path, (string) $encoded);

            return $path;
        } catch (Throwable $exception) {
            Log::warning('Unable to create a receipt thumbnail.', ['message' => $exception->getMessage()]);

            return null;
        }
    }
}
