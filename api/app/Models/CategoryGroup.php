<?php

namespace App\Models;

use App\Enums\CategoryType;
use App\Models\Concerns\BelongsToUser;
use App\Models\Scopes\OwnedByCurrentUser;
use Database\Factories\CategoryGroupFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Attributes\ScopedBy;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\HasMany;

#[Fillable(['name', 'type', 'color', 'icon', 'sort_order'])]
#[ScopedBy(OwnedByCurrentUser::class)]
class CategoryGroup extends Model
{
    /** @use HasFactory<CategoryGroupFactory> */
    use BelongsToUser, HasFactory;

    /**
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'type' => CategoryType::class,
            'is_system' => 'boolean',
        ];
    }

    public function categories(): HasMany
    {
        return $this->hasMany(Category::class);
    }

    #[Scope]
    protected function ofType(Builder $query, CategoryType|string $type): void
    {
        $query->where('type', $type instanceof CategoryType ? $type->value : $type);
    }
}
