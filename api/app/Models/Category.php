<?php

namespace App\Models;

use App\Enums\CategoryType;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use Database\Factories\CategoryFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable([
    'category_group_id',
    'parent_id',
    'name',
    'type',
    'color',
    'icon',
    'keywords',
    'sort_order',
])]
#[ScopedBy(OwnedByCurrentUser::class)]
class Category extends Model
{
    /** @use HasFactory<CategoryFactory> */
    use BelongsToUser, HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'type' => CategoryType::class,
            'keywords' => 'array',
            'is_system' => 'boolean',
            'archived_at' => 'datetime',
        ];
    }

    public function group(): BelongsTo
    {
        return $this->belongsTo(CategoryGroup::class, 'category_group_id');
    }

    public function parent(): BelongsTo
    {
        return $this->belongsTo(self::class, 'parent_id');
    }

    public function children(): HasMany
    {
        return $this->hasMany(self::class, 'parent_id');
    }

    public function transactions(): HasMany
    {
        return $this->hasMany(Transaction::class);
    }

    public function merchantRules(): HasMany
    {
        return $this->hasMany(MerchantCategoryRule::class);
    }

    #[Scope]
    protected function active(Builder $query): void
    {
        $query->whereNull('archived_at');
    }

    #[Scope]
    protected function ofType(Builder $query, CategoryType|string $type): void
    {
        $query->where('type', $type instanceof CategoryType ? $type->value : $type);
    }

    public function isArchived(): bool
    {
        return $this->archived_at !== null;
    }
}
