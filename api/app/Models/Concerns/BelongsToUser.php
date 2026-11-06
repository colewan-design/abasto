<?php

namespace App\Models\Concerns;

use App\Models\Scopes\OwnedByCurrentUser;
use App\Models\User;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Facades\Auth;

/**
 * Shared behaviour for records owned by a single user.
 *
 * Pair with `#[ScopedBy(OwnedByCurrentUser::class)]` so reads are filtered and
 * writes are stamped with the owner automatically.
 */
trait BelongsToUser
{
    public static function bootBelongsToUser(): void
    {
        static::creating(function ($model): void {
            if ($model->user_id === null && Auth::hasUser()) {
                $model->user_id = Auth::id();
            }
        });
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /**
     * Restrict a query to a specific owner, ignoring the current session.
     */
    #[Scope]
    protected function ownedBy(Builder $query, User|int $user): void
    {
        $query->withoutGlobalScope(OwnedByCurrentUser::class)
            ->where($this->qualifyColumn('user_id'), $user instanceof User ? $user->getKey() : $user);
    }
}
