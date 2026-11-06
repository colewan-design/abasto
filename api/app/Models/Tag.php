<?php

namespace App\Models;

use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use Database\Factories\TagFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;

#[Fillable(['name', 'color'])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Tag extends Model
{
    /** @use HasFactory<TagFactory> */
    use BelongsToUser, HasFactory;

    public function transactions(): BelongsToMany
    {
        return $this->belongsToMany(Transaction::class);
    }
}
