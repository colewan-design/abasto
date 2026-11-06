<?php

namespace App\Observers;

use App\Models\Receipt;
use App\Models\User;
use Illuminate\Support\Facades\Storage;

class UserObserver
{
    /**
     * Database rows cascade on delete, but stored receipt images do not —
     * remove them so deleting an account (or pruning an abandoned demo) does
     * not leave someone's purchases on disk.
     */
    public function deleting(User $user): void
    {
        $disk = Storage::disk(config('budget.receipts.disk'));

        Receipt::query()
            ->withoutGlobalScopes()
            ->withTrashed()
            ->where('user_id', $user->getKey())
            ->select(['id', 'image_path', 'thumbnail_path'])
            ->chunkById(200, function ($receipts) use ($disk): void {
                $disk->delete(
                    $receipts->flatMap(fn (Receipt $receipt): array => array_values(array_filter([
                        $receipt->image_path,
                        $receipt->thumbnail_path,
                    ])))->all()
                );
            });
    }
}
