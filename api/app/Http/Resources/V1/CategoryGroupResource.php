<?php

namespace App\Http\Resources\V1;

use App\Models\CategoryGroup;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin CategoryGroup
 */
class CategoryGroupResource extends JsonResource
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
            'is_system' => (bool) $this->is_system,
            'sort_order' => $this->sort_order,
            'categories' => CategoryResource::collection($this->whenLoaded('categories')),
            'categories_count' => $this->whenCounted('categories'),
        ];
    }
}
