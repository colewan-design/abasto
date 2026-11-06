<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class UpdateCategoryRequest extends FormRequest
{
    use ValidatesOwnedRecords;

    public function authorize(): bool
    {
        return $this->user() !== null;
    }

    /**
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        $category = $this->route('category');

        return [
            'name' => [
                'sometimes', 'string', 'max:60',
                Rule::unique('categories', 'name')
                    ->where('user_id', $this->user()->getKey())
                    ->where('type', $category?->type->value)
                    ->ignore($category?->getKey()),
            ],
            'category_group_id' => ['nullable', 'integer', $this->ownedBy('category_groups')],
            'parent_id' => ['nullable', 'integer', $this->ownedCategory()],
            'color' => ['nullable', 'string', 'max:9'],
            'icon' => ['nullable', 'string', 'max:40'],
            'keywords' => ['nullable', 'array', 'max:60'],
            'keywords.*' => ['string', 'max:60'],
            'sort_order' => ['sometimes', 'integer', 'min:0', 'max:999'],
            'archived' => ['sometimes', 'boolean'],
        ];
    }
}
