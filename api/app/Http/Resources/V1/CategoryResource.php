<?php

namespace App\Http\Resources\V1;

use App\Models\Category;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin Category
 */
class CategoryResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'type' => $this->type->value,
            'color' => $this->color,
            'icon' => $this->icon,
            'parent_id' => $this->parent_id,
            'category_group_id' => $this->category_group_id,
            'group' => $this->whenLoaded('group', fn () => [
                'id' => $this->group->id,
                'name' => $this->group->name,
                'color' => $this->group->color,
                'icon' => $this->group->icon,
            ]),
            'is_system' => (bool) $this->is_system,
            'is_archived' => $this->isArchived(),
            'keywords' => $this->keywords ?? [],
            'sort_order' => $this->sort_order,
            'transactions_count' => $this->whenCounted('transactions'),
        ];
    }
}
